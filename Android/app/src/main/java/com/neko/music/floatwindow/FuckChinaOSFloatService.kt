package com.neko.music.floatwindow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.LocalContext
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FuckChinaOSFloatService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var isViewAdded = false
    private var shouldShow = true // 控制是否应该显示悬浮窗
    private var isAppInForeground = false // 应用是否在前台
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private var appVisibilityJob: Job? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    companion object {
        const val ACTION_SHOW = "com.neko.music.action.SHOW_FLOAT"
        const val ACTION_HIDE = "com.neko.music.action.HIDE_FLOAT"
        const val ACTION_UPDATE = "com.neko.music.action.UPDATE_FLOAT"
        private var instance: FuckChinaOSFloatService? = null
        
        fun getInstance(): FuckChinaOSFloatService? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createFloatView()
        showFloatView() // 初始化 layoutParams
        startAppVisibilityMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatView()
            ACTION_HIDE -> hideFloatView()
            ACTION_UPDATE -> updateFloatView()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createFloatView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatView = layoutInflater.inflate(R.layout.float_window_layout, null)
        
        // 设置点击事件
        val btnPlayPause = floatView?.findViewById<ImageButton>(R.id.float_play_pause)
        val btnPrevious = floatView?.findViewById<ImageButton>(R.id.float_previous)
        val btnNext = floatView?.findViewById<ImageButton>(R.id.float_next)
        val layoutFloat = floatView?.findViewById<LinearLayout>(R.id.float_layout)
        
        btnPlayPause?.setOnClickListener {
            val playerManager = MusicPlayerManager.getInstance(this)
            if (playerManager.isPlaying.value) {
                playerManager.pause()
            } else {
                playerManager.togglePlayPause()
            }
        }
        
        btnPrevious?.setOnClickListener {
            MusicPlayerManager.getInstance(this).previous()
        }
        
        btnNext?.setOnClickListener {
            MusicPlayerManager.getInstance(this).next()
        }
        
        layoutFloat?.setOnClickListener {
            // 点击悬浮窗打开应用
            val openIntent = Intent(this, com.neko.music.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(openIntent)
        }
    }

    private fun showFloatView() {
        if (isViewAdded || floatView == null) return
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        
        layoutParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        
        try {
            windowManager?.addView(floatView, layoutParams)
            isViewAdded = true
            updateFloatView()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideFloatView() {
        if (!isViewAdded || floatView == null || windowManager == null) return
        
        try {
            windowManager?.removeView(floatView)
            isViewAdded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFloatView() {
        val playerManager = MusicPlayerManager.getInstance(this)
        
        val tvTitle = floatView?.findViewById<TextView>(R.id.float_title)
        val tvArtist = floatView?.findViewById<TextView>(R.id.float_artist)
        val btnPlayPause = floatView?.findViewById<ImageButton>(R.id.float_play_pause)
        
        tvTitle?.text = playerManager.currentMusicTitle.value ?: "Neko云音乐"
        tvArtist?.text = playerManager.currentMusicArtist.value ?: "暂无播放"
        
        btnPlayPause?.setImageResource(
            if (playerManager.isPlaying.value) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatView()
        serviceScope.cancel()
        instance = null
    }

    private fun startAppVisibilityMonitor() {
        appVisibilityJob = serviceScope.launch {
            while (isActive) {
                val inForeground = checkAppInForeground()
                
                if (inForeground != isAppInForeground) {
                    isAppInForeground = inForeground
                    if (isAppInForeground) {
                        // 应用进入前台，隐藏悬浮窗
                        if (isViewAdded) {
                            windowManager?.removeView(floatView)
                            isViewAdded = false
                        }
                    } else {
                        // 应用进入后台，显示悬浮窗
                        if (!isViewAdded && floatView != null && layoutParams != null) {
                            windowManager?.addView(floatView, layoutParams)
                            isViewAdded = true
                        }
                    }
                }
                delay(500) // 每0.5秒检查一次
            }
        }
    }

    private fun checkAppInForeground(): Boolean {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: return false
            
            for (process in runningProcesses) {
                if (process.processName == packageName) {
                    return process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}