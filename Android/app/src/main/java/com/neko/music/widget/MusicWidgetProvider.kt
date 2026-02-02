package com.neko.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.neko.music.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.neko.music.action.PREVIOUS"
        const val ACTION_NEXT = "com.neko.music.action.NEXT"
        const val ACTION_UPDATE_WIDGET = "com.neko.music.action.UPDATE_WIDGET"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val playerManager = MusicPlayerManager.getInstance(context)
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                } else {
                    playerManager.togglePlayPause()
                }
                updateAllWidgets(context)
            }
            ACTION_PREVIOUS -> {
                MusicPlayerManager.getInstance(context).previous()
                updateAllWidgets(context)
            }
            ACTION_NEXT -> {
                MusicPlayerManager.getInstance(context).next()
                updateAllWidgets(context)
            }
            ACTION_UPDATE_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val playerManager = MusicPlayerManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.music_widget)

        // 更新歌曲信息
        views.setTextViewText(R.id.widget_song_title, playerManager.currentMusicTitle.value ?: "Neko云音乐")
        views.setTextViewText(R.id.widget_artist_name, playerManager.currentMusicArtist.value ?: "暂无播放")

        // 更新播放/暂停按钮图标
        val playPauseIcon = if (playerManager.isPlaying.value) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // 设置点击事件
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingIntent(context, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_previous, getPendingIntent(context, ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(context, ACTION_NEXT))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, MusicWidgetProvider::class.java)
        )
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}