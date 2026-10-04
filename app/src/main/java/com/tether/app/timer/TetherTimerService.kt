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
 * Time is computed from SystemClock.elapsedRealtime() timestamps rather than
 * by counting ticks, so it stays exact when the screen is off or the CPU
 * sleeps. State is persisted, so a session survives the app process being
 * killed (common on MIUI/HyperOS) and is restored on the next launch.
 */
class TetherTimerService : Service() {

    enum class TimerMode { STOPWATCH, POMODORO }
    enum class Phase { FOCUSING, BREAK }

    private val binder = TimerBinder()
    private val handler = Handler(Looper.getMainLooper())

    var mode = TimerMode.STOPWATCH
        private set
    var currentPhase = Phase.FOCUSING
        private set
    var groupId = ""
        private set

    private var pomodoroFocusMinutes = 25
    private var pomodoroBreakMinutes = 5

    /** Focus time completed in earlier phases. */
    private var focusAccumMs = 0L
    /** elapsedRealtime when the current phase started. */
    private var phaseStartAt = 0L
    /** elapsedRealtime when the current countdown phase ends (Pomodoro, or stopwatch break). */
    private var phaseEndAt = 0L
    private var breakLengthMs = 0L
    private var breakWarned = false

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
        mode = try {
            TimerMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "STOPWATCH")
        } catch (e: IllegalArgumentException) {
            TimerMode.STOPWATCH
        }
        val now = SystemClock.elapsedRealtime()
        currentPhase = Phase.FOCUSING
        focusAccumMs = 0L
        phaseStartAt = now
        breakWarned = false
        if (mode == TimerMode.POMODORO) {
            pomodoroFocusMinutes = intent.getIntExtra(EXTRA_POMO_FOCUS, 25)
            pomodoroBreakMinutes = intent.getIntExtra(EXTRA_POMO_BREAK, 5)
            phaseEndAt = now + pomodoroFocusMinutes * 60_000L
        } else {
            phaseEndAt = 0L
        }
        saveState()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        _activeGroupId.value = null
        super.onDestroy()
    }

    // ── Time keeping ─────────────────────────────────────────────

    /** Moves through any phase boundaries that have passed. */
    private fun advance() {
        if (!hasSession) return
        val now = SystemClock.elapsedRealtime()
        var changed = false

        if (mode == TimerMode.STOPWATCH) {
            if (currentPhase == Phase.BREAK && now >= phaseEndAt) {
                currentPhase = Phase.FOCUSING
                phaseStartAt = phaseEndAt  // focus resumes exactly when the break ended
                changed = true
            }
        } else {
            while (now >= phaseEndAt) {
                if (currentPhase == Phase.FOCUSING) {
                    focusAccumMs += pomodoroFocusMinutes * 60_000L
                    currentPhase = Phase.BREAK
                    breakLengthMs = pomodoroBreakMinutes * 60_000L
                    phaseStartAt = phaseEndAt
                    phaseEndAt += breakLengthMs
                } else {
                    currentPhase = Phase.FOCUSING
                    phaseStartAt = phaseEndAt
                    phaseEndAt += pomodoroFocusMinutes * 60_000L
                }
                breakWarned = false
                changed = true
            }
        }

        if (currentPhase == Phase.BREAK && !breakWarned &&
            breakLengthMs > 120_000L && phaseEndAt - now <= 120_000L
        ) {
            breakWarned = true
            sendBreakWarning()
            changed = true
        }

        if (changed) {
            saveState()
            updateNotification()
        }
    }

    /** False until a session has actually been started or restored. */
    private val hasSession: Boolean
        get() = phaseStartAt != 0L

    private fun focusMillis(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!hasSession) return 0L
        return focusAccumMs + if (currentPhase == Phase.FOCUSING) now - phaseStartAt else 0L
    }

    /** Focus time only (breaks excluded). */
    val focusSeconds: Long
        get() {
            advance()
            return focusMillis() / 1000
        }

    /** What the timer face shows: elapsed focus (stopwatch) or time left (countdowns). */
    val currentSeconds: Long
        get() {
            advance()
            if (!hasSession) return 0L
            val now = SystemClock.elapsedRealtime()
            return if (mode == TimerMode.STOPWATCH && currentPhase == Phase.FOCUSING) {
                focusMillis(now) / 1000
            } else {
                ((phaseEndAt - now).coerceAtLeast(0L) + 999) / 1000
            }
        }

    fun startBreak(minutes: Int) {
        if (mode != TimerMode.STOPWATCH || currentPhase != Phase.FOCUSING) return
        val now = SystemClock.elapsedRealtime()
        focusAccumMs += now - phaseStartAt
        currentPhase = Phase.BREAK
        breakLengthMs = minutes * 60_000L
        phaseStartAt = now
        phaseEndAt = now + breakLengthMs
        breakWarned = false
        saveState()
        updateNotification()
    }

    /** Stops the session and returns the focused seconds to log. */
    fun stopTimer(): Long {
        advance()
        val seconds = focusMillis() / 1000
        handler.removeCallbacks(tick)
        clearState()
        phaseStartAt = 0L
        focusAccumMs = 0L
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
        prefs().edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_GROUP, groupId)
            .putString(KEY_MODE, mode.name)
            .putString(KEY_PHASE, currentPhase.name)
            .putLong(KEY_FOCUS_ACCUM, focusAccumMs)
            .putLong(KEY_PHASE_START, phaseStartAt)
            .putLong(KEY_PHASE_END, phaseEndAt)
            .putLong(KEY_BREAK_LEN, breakLengthMs)
            .putBoolean(KEY_BREAK_WARNED, breakWarned)
            .putInt(KEY_POMO_FOCUS, pomodoroFocusMinutes)
            .putInt(KEY_POMO_BREAK, pomodoroBreakMinutes)
            .putLong(KEY_SAVED_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_SAVED_WALL, System.currentTimeMillis())
            .apply()
    }

    private fun restoreState(): Boolean {
        val p = prefs()
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        return try {
            groupId = p.getString(KEY_GROUP, "") ?: ""
            mode = TimerMode.valueOf(p.getString(KEY_MODE, TimerMode.STOPWATCH.name)!!)
            currentPhase = Phase.valueOf(p.getString(KEY_PHASE, Phase.FOCUSING.name)!!)
            focusAccumMs = p.getLong(KEY_FOCUS_ACCUM, 0L)
            phaseStartAt = p.getLong(KEY_PHASE_START, 0L)
            phaseEndAt = p.getLong(KEY_PHASE_END, 0L)
            breakLengthMs = p.getLong(KEY_BREAK_LEN, 0L)
            breakWarned = p.getBoolean(KEY_BREAK_WARNED, false)
            pomodoroFocusMinutes = p.getInt(KEY_POMO_FOCUS, 25)
            pomodoroBreakMinutes = p.getInt(KEY_POMO_BREAK, 5)

            // elapsedRealtime restarts from 0 after a reboot: shift timestamps
            // using the wall clock so the session continues correctly.
            val savedElapsed = p.getLong(KEY_SAVED_ELAPSED, 0L)
            val savedWall = p.getLong(KEY_SAVED_WALL, 0L)
            val nowElapsed = SystemClock.elapsedRealtime()
            if (nowElapsed < savedElapsed) {
                val shift = nowElapsed - (savedElapsed + (System.currentTimeMillis() - savedWall))
                phaseStartAt += shift
                if (phaseEndAt != 0L) phaseEndAt += shift
            }
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
        val countUp = mode == TimerMode.STOPWATCH && currentPhase == Phase.FOCUSING
        val chronometerBase = if (countUp) wallNow - focusMillis(now)
            else wallNow + (phaseEndAt - now).coerceAtLeast(0L)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether — Active Session")
            .setContentText(if (currentPhase == Phase.FOCUSING) "Focusing" else "Break")
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
