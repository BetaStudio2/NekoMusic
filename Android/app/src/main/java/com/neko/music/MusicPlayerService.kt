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
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = MusicPlayerManager.getInstance(this)
        createNotificationChannel()

        // 启动前台服务以确保后台播放正常
        startForeground(NOTIFICATION_ID, createMusicNotification())

        // 监听定时关闭剩余时间变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.sleepTimerRemainingSeconds.collect { remainingSeconds ->
                updateMusicNotification()
            }
        }

        // 监听播放状态变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                updateMusicNotification()
            }
        }

        // 监听当前音乐变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.currentMusicTitle.collect {
                updateMusicNotification()
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

    private fun createMusicNotification(): Notification {
        val title = playerManager.currentMusicTitle.value ?: "Neko云音乐"
        val artist = playerManager.currentMusicArtist.value ?: ""
        val isPlaying = playerManager.isPlaying.value
        val remainingSeconds = playerManager.sleepTimerRemainingSeconds.value

        // 创建点击通知打开应用的 PendingIntent
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (remainingSeconds > 0) {
            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60

            val timeText = buildString {
                if (hours > 0) append("${hours}小时")
                if (minutes > 0) append("${minutes}分钟")
                append("${seconds}秒后关闭")
            }

            "$artist - $timeText"
        } else {
            artist
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateMusicNotification() {
        notificationManager.notify(NOTIFICATION_ID, createMusicNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注意：不再释放 MusicPlayerManager，保持播放器始终活跃状态
        // 释放已被禁用以防止 "Ignoring messages sent after release" 错误
    }
}