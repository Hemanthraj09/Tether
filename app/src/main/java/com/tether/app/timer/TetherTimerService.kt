package com.tether.app.timer

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.tether.app.MainActivity
import com.tether.app.R
import java.util.*

class TetherTimerService : Service() {

    enum class TimerMode { STOPWATCH, POMODORO }
    enum class Phase { FOCUSING, BREAK }

    private val binder = TimerBinder()
    private var timer: Timer? = null
    
    var mode = TimerMode.STOPWATCH
    var currentPhase = Phase.FOCUSING
    var focusSeconds = 0L
    var currentSeconds = 0L
    var groupId = ""
    
    private var pomodoroFocusMinutes = 25
    private var pomodoroBreakMinutes = 5
    
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "timer_channel"

    inner class TimerBinder : Binder() {
        fun getService(): TetherTimerService = this@TetherTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopTimer()
            return START_NOT_STICKY
        }

        groupId = intent?.getStringExtra(EXTRA_GROUP_ID) ?: ""
        val modeStr = intent?.getStringExtra(EXTRA_MODE) ?: "STOPWATCH"
        mode = TimerMode.valueOf(modeStr)
        
        if (mode == TimerMode.POMODORO) {
            pomodoroFocusMinutes = intent?.getIntExtra(EXTRA_POMO_FOCUS, 25) ?: 25
            pomodoroBreakMinutes = intent?.getIntExtra(EXTRA_POMO_BREAK, 5) ?: 5
            currentSeconds = pomodoroFocusMinutes * 60L
        } else {
            currentSeconds = 0L
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startTimerTask()

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun startTimerTask() {
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                updateTimer()
            }
        }, 1000, 1000)
    }

    private fun updateTimer() {
        if (mode == TimerMode.STOPWATCH) {
            if (currentPhase == Phase.FOCUSING) {
                currentSeconds++
                focusSeconds++
            } else {
                currentSeconds--
                if (currentSeconds <= 120 && currentSeconds > 119) {
                    sendBreakWarning()
                }
                if (currentSeconds <= 0) {
                    currentPhase = Phase.FOCUSING
                    currentSeconds = focusSeconds // Back to stopwatch count? Or 0? 
                    // Usually stopwatch resumes from where it paused.
                }
            }
        } else { // POMODORO
            currentSeconds--
            if (currentPhase == Phase.FOCUSING) {
                focusSeconds++
            }
            
            if (currentSeconds <= 0) {
                if (currentPhase == Phase.FOCUSING) {
                    currentPhase = Phase.BREAK
                    currentSeconds = pomodoroBreakMinutes * 60L
                } else {
                    currentPhase = Phase.FOCUSING
                    currentSeconds = pomodoroFocusMinutes * 60L
                }
            }
        }
        Handler(Looper.getMainLooper()).post {
            updateNotification()
        }
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

    fun startBreak(minutes: Int) {
        if (mode == TimerMode.STOPWATCH) {
            currentPhase = Phase.BREAK
            currentSeconds = minutes * 60L
        }
    }

    fun stopTimer() {
        timer?.cancel()
        broadcastFinished()
        stopForeground(true)
        isRunning = false
        stopSelf()
    }

    private fun broadcastFinished() {
        val intent = Intent(ACTION_TIMER_FINISHED)
        intent.putExtra(EXTRA_FOCUS_SECONDS, focusSeconds)
        intent.putExtra(EXTRA_GROUP_ID, groupId)
        intent.putExtra(EXTRA_MODE, mode.name)
        sendBroadcast(intent)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigateToGroupId", groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )
        val timeStr = formatTime(currentSeconds)
        val phaseStr = if (currentPhase == Phase.FOCUSING)
            "Focusing" else "Break"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether — Active Session")
            .setContentText("$phaseStr • $timeStr")
            .setSmallIcon(R.drawable.ic_flame)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Timer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun formatTime(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    companion object {
        var isRunning = false
            private set

        const val ACTION_STOP = "com.tether.app.STOP_TIMER"
        const val ACTION_TIMER_FINISHED = "com.tether.app.TIMER_FINISHED"
        const val ACTION_SESSION_LOGGED = "com.tether.app.SESSION_LOGGED"
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_MODE = "timerMode"
        const val EXTRA_FOCUS_SECONDS = "focusSeconds"
        const val EXTRA_POMO_FOCUS = "pomoFocus"
        const val EXTRA_POMO_BREAK = "pomoBreak"
    }
}
