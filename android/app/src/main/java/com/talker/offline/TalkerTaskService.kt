package com.talker.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

class TalkerTaskService : Service() {
    companion object {
        private const val ACTION_START = "com.talker.offline.action.START_TASK"
        private const val ACTION_COMPLETE = "com.talker.offline.action.COMPLETE_TASK"
        private const val EXTRA_TITLE = "task_title"
        private const val EXTRA_KIND = "task_kind"
        private const val EXTRA_MESSAGE = "task_message"
        private const val EXTRA_SUCCESS = "task_success"
        private const val EXTRA_NOTIFY = "task_notify"
        private const val CHANNEL_PROGRESS = "talker_task_progress"
        private const val CHANNEL_COMPLETE = "talker_task_complete"
        private const val NOTIFICATION_ID = 7301
        private const val COMPLETION_ID = 7302

        fun start(context: Context, title: String, kind: String) {
            val intent = Intent(context, TalkerTaskService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_KIND, kind)
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun complete(context: Context, title: String, message: String, success: Boolean, notifyUser: Boolean) {
            val intent = Intent(context, TalkerTaskService::class.java).apply {
                action = ACTION_COMPLETE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_SUCCESS, success)
                putExtra(EXTRA_NOTIFY, notifyUser)
                putExtra(EXTRA_KIND, "media")
            }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Talker يعمل في الخلفية"
        val kind = intent?.getStringExtra(EXTRA_KIND) ?: "data"
        startForegroundCompat(title, kind)

        when (intent?.action) {
            ACTION_START -> acquireWakeLock()
            ACTION_COMPLETE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "انتهت المهمة."
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, true)
                val notifyUser = intent.getBooleanExtra(EXTRA_NOTIFY, false)
                if (notifyUser) showCompletionNotification(title, message, success)
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf(startId)
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(title: String, kind: String) {
        val notification = buildProgressNotification(title)
        if (Build.VERSION.SDK_INT >= 35 && kind == "media") {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock?.isHeld == true) return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, packageName + ":TalkerLongTask").apply {
            setReferenceCounted(false)
            acquire(6L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "عمليات Talker", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعارات تنزيل النموذج وتوليد الصوت أثناء العمل"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COMPLETE, "اكتمال مهام Talker", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعار عند اكتمال تنزيل النموذج أو توليد الصوت"
            }
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 7301, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildProgressNotification(title: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_PROGRESS)
        } else {
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("سيستمر Talker في العمل حتى مع إطفاء الشاشة.")
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun showCompletionNotification(title: String, message: String, success: Boolean) {
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_COMPLETE)
        } else {
            Notification.Builder(this)
        }.setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        try { manager.notify(COMPLETION_ID, notification) } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
