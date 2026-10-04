package com.tether.app.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.tether.app.MainActivity
import com.tether.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Focus timer running as a foreground service.
 *
 * All time-keeping lives in the pure [TimerEngine]; this class only handles
 * Android concerns: foreground notification, persistence (a session survives
 * the process being killed, common on MIUI/HyperOS) and binding.
 */
class TetherTimerService : Service() {

    private val binder = TimerBinder()
    private val handler = Handler(Looper.getMainLooper())

    private var state: TimerState? = null

    var groupId = ""
        private set

    val mode: TimerMode
        get() = state?.mode ?: TimerMode.STOPWATCH

    val currentPhase: TimerPhase
        get() = state?.phase ?: TimerPhase.FOCUSING

    private val tick = object : Runnable {
        override fun run() {
            advance()
            handler.postDelayed(this, 1000)
        }
    }

    inner class TimerBinder : Binder() {
        fun getService(): TetherTimerService = this@TetherTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP -> {
                if (_activeGroupId.value != null || restoreState()) stopTimer() else stopSelf()
                return START_NOT_STICKY
            }
            // null intent = system restarted us after the process was killed.
            intent == null || intent.action == ACTION_RESTORE -> {
                if (_activeGroupId.value == null && !restoreState()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            _activeGroupId.value == null -> startNewSession(intent)
            // A session is already running: keep it, ignore the duplicate start.
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Not allowed to go foreground right now (e.g. background restart
            // on Android 12+). State stays saved and is restored on next launch.
            android.util.Log.w("TetherTimer", "startForeground failed", e)
            _activeGroupId.value = null
            handler.removeCallbacks(tick)
            stopSelf()
            return START_NOT_STICKY
        }

        _activeGroupId.value = groupId
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun startNewSession(intent: Intent) {
        groupId = intent.getStringExtra(EXTRA_GROUP_ID) ?: ""
        val mode = try {
            TimerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: TimerMode.STOPWATCH.name)
        } catch (e: IllegalArgumentException) {
            TimerMode.STOPWATCH
        }
        state = TimerEngine.start(
            mode = mode,
            now = SystemClock.elapsedRealtime(),
            focusMinutes = intent.getIntExtra(EXTRA_POMO_FOCUS, 25),
            breakMinutes = intent.getIntExtra(EXTRA_POMO_BREAK, 5)
        )
        saveState()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        _activeGroupId.value = null
        super.onDestroy()
    }

    // ── Time keeping (delegates to TimerEngine) ──────────────────

    private fun advance() {
        val current = state ?: return
        val result = TimerEngine.advance(current, SystemClock.elapsedRealtime())
        state = result.state
        if (result.warnBreakEnding) sendBreakWarning()
        if (result.phaseChanged) {
            saveState()
            updateNotification()
        }
    }

    /** Focus time only (breaks excluded). */
    val focusSeconds: Long
        get() {
            advance()
            val s = state ?: return 0L
            return TimerEngine.focusMillis(s, SystemClock.elapsedRealtime()) / 1000
        }

    /** What the timer face shows: elapsed focus (stopwatch) or time left (countdowns). */
    val currentSeconds: Long
        get() {
            advance()
            val s = state ?: return 0L
            return TimerEngine.displaySeconds(s, SystemClock.elapsedRealtime())
        }

    fun startBreak(minutes: Int) {
        val s = state ?: return
        state = TimerEngine.startBreak(s, minutes, SystemClock.elapsedRealtime()) ?: return
        saveState()
        updateNotification()
    }

    /** Stops the session and returns the focused seconds to log. */
    fun stopTimer(): Long {
        val seconds = focusSeconds
        handler.removeCallbacks(tick)
        clearState()
        state = null
        _activeGroupId.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return seconds
    }

    private fun sendBreakWarning() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val warningNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Break ending soon")
            .setContentText("2 minutes left in your break")
            .setSmallIcon(R.drawable.ic_flame)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1002, warningNotification)
    }

    // ── Persistence ──────────────────────────────────────────────

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun saveState() {
        val s = state ?: return
        prefs().edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_GROUP, groupId)
            .putString(KEY_MODE, s.mode.name)
            .putString(KEY_PHASE, s.phase.name)
            .putLong(KEY_FOCUS_ACCUM, s.focusAccumMs)
            .putLong(KEY_PHASE_START, s.phaseStartAt)
            .putLong(KEY_PHASE_END, s.phaseEndAt)
            .putLong(KEY_BREAK_LEN, s.breakLengthMs)
            .putBoolean(KEY_BREAK_WARNED, s.breakWarned)
            .putInt(KEY_POMO_FOCUS, s.pomodoroFocusMinutes)
            .putInt(KEY_POMO_BREAK, s.pomodoroBreakMinutes)
            .putLong(KEY_SAVED_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_SAVED_WALL, System.currentTimeMillis())
            .apply()
    }

    private fun restoreState(): Boolean {
        val p = prefs()
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        return try {
            groupId = p.getString(KEY_GROUP, "") ?: ""
            val saved = TimerState(
                mode = TimerMode.valueOf(p.getString(KEY_MODE, TimerMode.STOPWATCH.name)!!),
                phase = TimerPhase.valueOf(p.getString(KEY_PHASE, TimerPhase.FOCUSING.name)!!),
                focusAccumMs = p.getLong(KEY_FOCUS_ACCUM, 0L),
                phaseStartAt = p.getLong(KEY_PHASE_START, 0L),
                phaseEndAt = p.getLong(KEY_PHASE_END, 0L),
                breakLengthMs = p.getLong(KEY_BREAK_LEN, 0L),
                breakWarned = p.getBoolean(KEY_BREAK_WARNED, false),
                pomodoroFocusMinutes = p.getInt(KEY_POMO_FOCUS, 25),
                pomodoroBreakMinutes = p.getInt(KEY_POMO_BREAK, 5)
            )
            state = TimerEngine.restoreAfterSave(
                saved,
                savedElapsed = p.getLong(KEY_SAVED_ELAPSED, 0L),
                savedWall = p.getLong(KEY_SAVED_WALL, 0L),
                nowElapsed = SystemClock.elapsedRealtime(),
                nowWall = System.currentTimeMillis()
            )
            advance()
            true
        } catch (e: Exception) {
            clearState()
            false
        }
    }

    private fun clearState() {
        prefs().edit().clear().apply()
    }

    // ── Notification ─────────────────────────────────────────────

    private fun updateNotification() {
        if (_activeGroupId.value == null) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Uses the system chronometer, so Android ticks the clock itself; the app
     * only re-posts the notification when the phase changes.
     */
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_GROUP, groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )
        val now = SystemClock.elapsedRealtime()
        val wallNow = System.currentTimeMillis()
        val s = state
        val countUp = s == null || (s.mode == TimerMode.STOPWATCH && s.phase == TimerPhase.FOCUSING)
        val chronometerBase = when {
            s == null -> wallNow
            countUp -> wallNow - TimerEngine.focusMillis(s, now)
            else -> wallNow + (s.phaseEndAt - now).coerceAtLeast(0L)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether — Active Session")
            .setContentText(if (currentPhase == TimerPhase.FOCUSING) "Focusing" else "Break")
            .setSmallIcon(R.drawable.ic_flame)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(true)
            .setWhen(chronometerBase)
            .setUsesChronometer(true)
            .setChronometerCountDown(!countUp)
            .build()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID, "Timer Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "timer_channel"

        private const val PREFS_NAME = "tether_timer"
        private const val KEY_ACTIVE = "active"
        private const val KEY_GROUP = "group_id"
        private const val KEY_MODE = "mode"
        private const val KEY_PHASE = "phase"
        private const val KEY_FOCUS_ACCUM = "focus_accum"
        private const val KEY_PHASE_START = "phase_start"
        private const val KEY_PHASE_END = "phase_end"
        private const val KEY_BREAK_LEN = "break_len"
        private const val KEY_BREAK_WARNED = "break_warned"
        private const val KEY_POMO_FOCUS = "pomo_focus"
        private const val KEY_POMO_BREAK = "pomo_break"
        private const val KEY_SAVED_ELAPSED = "saved_elapsed"
        private const val KEY_SAVED_WALL = "saved_wall"

        private val _activeGroupId = MutableStateFlow<String?>(null)

        /** Group of the running session, or null when no session is active. */
        val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

        val isRunning: Boolean
            get() = _activeGroupId.value != null

        const val ACTION_STOP = "com.tether.app.STOP_TIMER"
        const val ACTION_RESTORE = "com.tether.app.RESTORE_TIMER"
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_MODE = "timerMode"
        const val EXTRA_POMO_FOCUS = "pomoFocus"
        const val EXTRA_POMO_BREAK = "pomoBreak"

        /**
         * Restarts a session that was cut off when the app process was killed.
         * Called from MainActivity (foreground, so starting the service is allowed).
         */
        fun restoreIfNeeded(context: Context) {
            if (isRunning) return
            val active = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)
            if (!active) return
            val intent = Intent(context, TetherTimerService::class.java)
                .setAction(ACTION_RESTORE)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                android.util.Log.w("TetherTimer", "restore failed", e)
            }
        }
    }
}
