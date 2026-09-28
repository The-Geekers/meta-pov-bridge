package fr.thefrenchgeekers.metapov

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps DAT camera streaming and SRT transmission alive while the app is
 * backgrounded or the phone screen is locked.
 */
class StreamingService : Service() {
    companion object {
        private const val CHANNEL_ID = "meta_pov_streaming"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "fr.thefrenchgeekers.metapov.STOP_STREAMING_SERVICE"
        private const val WAKELOCK_TAG = "MetaPOVBridge::StreamingWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, StreamingService::class.java).apply {
                    `package` = context.packageName
                },
            )
        }

        fun stop(context: Context) {
            context.startForegroundService(
                Intent(context, StreamingService::class.java).apply {
                    `package` = context.packageName
                    action = ACTION_STOP
                },
            )
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Meta POV streaming",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Ray-Ban Meta streaming active in the background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Meta POV Bridge")
            .setContentText("Streaming active")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG,
        ).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }
}
