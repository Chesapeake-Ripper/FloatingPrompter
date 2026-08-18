package com.floating.prompter

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
import androidx.core.app.NotificationCompat

class FloatingService : Service() {

    private var floatingWindowManager: FloatingWindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForegroundServiceWithNotification()

        floatingWindowManager = FloatingWindowManager(this) {
            stopSelf()
        }
        floatingWindowManager?.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val text = intent?.getStringExtra(EXTRA_TEXT)
                val alpha = intent?.getFloatExtra(EXTRA_ALPHA, -1f) ?: -1f
                val fontSize = intent?.getFloatExtra(EXTRA_FONT_SIZE, -1f) ?: -1f

                text?.let { floatingWindowManager?.updateText(it) }
                if (alpha >= 0) {
                    floatingWindowManager?.updateAlpha(alpha)
                }
                if (fontSize > 0) {
                    floatingWindowManager?.updateFontSize(fontSize)
                }
            }
            ACTION_UPDATE_TEXT -> {
                val text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
                floatingWindowManager?.updateText(text)
            }
            ACTION_UPDATE_ALPHA -> {
                val alpha = intent?.getFloatExtra(EXTRA_ALPHA, 0.9f) ?: 0.9f
                floatingWindowManager?.updateAlpha(alpha)
            }
            ACTION_UPDATE_FONT_SIZE -> {
                val fontSize = intent?.getFloatExtra(EXTRA_FONT_SIZE, 14f) ?: 14f
                floatingWindowManager?.updateFontSize(fontSize)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        // 重点：START_STICKY 保证常驻后台，被杀后自动恢复
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "关闭悬浮窗", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        floatingWindowManager?.destroy()
        floatingWindowManager = null
    }

    companion object {
        const val CHANNEL_ID = "channel_floating_prompter_service"
        const val NOTIFICATION_ID = 10086

        const val ACTION_START = "com.floating.prompter.ACTION_START"
        const val ACTION_UPDATE_TEXT = "com.floating.prompter.ACTION_UPDATE_TEXT"
        const val ACTION_UPDATE_ALPHA = "com.floating.prompter.ACTION_UPDATE_ALPHA"
        const val ACTION_UPDATE_FONT_SIZE = "com.floating.prompter.ACTION_UPDATE_FONT_SIZE"
        const val ACTION_STOP = "com.floating.prompter.ACTION_STOP"

        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ALPHA = "extra_alpha"
        const val EXTRA_FONT_SIZE = "extra_font_size"

        var isRunning = false
    }
}
