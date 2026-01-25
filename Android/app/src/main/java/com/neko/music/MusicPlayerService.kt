package com.neko.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.launch

class MusicPlayerService : Service() {

    private lateinit var playerManager: MusicPlayerManager
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var isForeground = false

    companion object {
        private const val CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 1

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = MusicPlayerManager.getInstance(this)
        createNotificationChannel()

        // 监听定时关闭剩余时间变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.sleepTimerRemainingSeconds.collect { remainingSeconds ->
                if (remainingSeconds > 0) {
                    // 有定时关闭时显示通知
                    if (!isForeground) {
                        startForeground(NOTIFICATION_ID, createNotification())
                    } else {
                        updateNotification()
                    }
                } else {
                    // 没有定时关闭时停止前台服务并取消通知
                    if (isForeground) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notificationManager.cancelAll()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放通知"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val remainingSeconds = playerManager.sleepTimerRemainingSeconds.value

        // 没有设置定时关闭时取消所有通知
        if (remainingSeconds <= 0) {
            notificationManager.cancelAll()
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60

        val timeText = buildString {
            if (hours > 0) append("${hours}小时")
            if (minutes > 0) append("${minutes}分钟")
            append("${seconds}秒")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("将在 $timeText 后关闭")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}