package com.neko.music.service

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.neko.music.data.manager.PlaylistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicPlayerManager private constructor(context: Context) {
    
    private val playlistManager = PlaylistManager.getInstance(context)
    private val appContext = context.applicationContext
    
    private val player = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // 媒体会话
    private val mediaSession = MediaSessionCompat(appContext, "MusicPlayerSession")
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _currentMusicUrl = MutableStateFlow<String?>(null)
    val currentMusicUrl: StateFlow<String?> = _currentMusicUrl.asStateFlow()
    
    private val _currentMusicTitle = MutableStateFlow<String?>(null)
    val currentMusicTitle: StateFlow<String?> = _currentMusicTitle.asStateFlow()
    
    private val _currentMusicArtist = MutableStateFlow<String?>(null)
    val currentMusicArtist: StateFlow<String?> = _currentMusicArtist.asStateFlow()
    
    private val _currentMusicCover = MutableStateFlow<String?>(null)
    val currentMusicCover: StateFlow<String?> = _currentMusicCover.asStateFlow()
    
    private val _currentMusicId = MutableStateFlow<Int?>(null)
    val currentMusicId: StateFlow<Int?> = _currentMusicId.asStateFlow()
    
    private var updateJob: Job? = null
    private var fadeJob: Job? = null
    
    init {
        // 设置媒体会话
        mediaSession.isActive = true
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                fadeIn()
            }
            
            override fun onPause() {
                fadeOut {}
            }
            
            override fun onSeekTo(pos: Long) {
                player.seekTo(pos)
            }
        })
        
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                updatePlaybackState()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {}
                    Player.STATE_BUFFERING -> {}
                    Player.STATE_READY -> {
                        _duration.value = player.duration
                    }
                    Player.STATE_ENDED -> {
                        _isPlaying.value = false
                        player.seekTo(0)
                        updatePlaybackState()
                    }
                }
            }
        })
        
        updatePlaybackState()
    }
    
    private fun updatePlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                player.currentPosition,
                1.0f
            )
        
        mediaSession.setPlaybackState(stateBuilder.build())
        
        // 更新媒体元数据
        val title = _currentMusicTitle.value ?: ""
        val artist = _currentMusicArtist.value ?: ""
        if (title.isNotEmpty() || artist.isNotEmpty()) {
            val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, _currentMusicCover.value)
                .build()
            mediaSession.setMetadata(metadata)
        }
    }
    
    private fun startPositionUpdate() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (true) {
                delay(100)
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                }
            }
        }
    }
    
    private fun stopPositionUpdate() {
        updateJob?.cancel()
        updateJob = null
    }
    
    // 淡入效果
    private fun fadeIn() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            player.volume = 0f
            player.play()
            _isPlaying.value = true
            startPositionUpdate()
            
            val steps = 20
            val stepDelay = 300L / steps
            for (i in 1..steps) {
                delay(stepDelay)
                player.volume = i.toFloat() / steps
            }
            player.volume = 1f
        }
    }
    
    // 淡出效果
    private fun fadeOut(onComplete: () -> Unit) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = 20
            val stepDelay = 300L / steps
            for (i in steps downTo 1) {
                delay(stepDelay)
                player.volume = i.toFloat() / steps
            }
            player.volume = 0f
            player.pause()
            _isPlaying.value = false
            stopPositionUpdate()
            player.volume = 1f
            onComplete()
        }
    }
    
    fun playMusic(url: String, id: Int? = null, title: String? = null, artist: String? = null, cover: String? = null, fullCoverUrl: String? = null) {
        if (_currentMusicUrl.value != url) {
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            _currentMusicUrl.value = url
            _currentMusicId.value = id
            _currentMusicTitle.value = title
            _currentMusicArtist.value = artist
            _currentMusicCover.value = fullCoverUrl ?: cover
            
            // 保存到播放列表
            if (id != null && title != null && artist != null && id > 0) {
                scope.launch {
                    val music = com.neko.music.data.model.Music(
                        id = id,
                        title = title,
                        artist = artist,
                        album = "",
                        duration = 0,
                        filePath = url,
                        coverFilePath = cover ?: "",
                        uploadUserId = 0,
                        createdAt = ""
                    )
                    playlistManager.addToPlaylist(music)
                }
            }
            
            // 淡入播放
            fadeIn()
        } else {
            // 已有音乐，直接播放
            fadeIn()
        }
    }
    
    fun pause() {
        fadeOut {}
    }
    
    fun togglePlayPause() {
        if (_isPlaying.value) {
            fadeOut {}
        } else {
            fadeIn()
        }
    }
    
    fun seekTo(position: Long) {
        player.seekTo(position)
        _currentPosition.value = position
        updatePlaybackState()
    }
    
    fun release() {
        stopPositionUpdate()
        fadeJob?.cancel()
        mediaSession.release()
        player.release()
    }
    
    suspend fun restoreLastPlayed(context: Context) {
        val lastPlayed = playlistManager.getLastPlayed()
        lastPlayed?.let { music ->
            val musicApi = com.neko.music.data.api.MusicApi()
            val url = musicApi.getMusicFileUrl(music)
            val fullCoverUrl = if (music.coverFilePath.isNotEmpty()) {
                "https://music.cnmsb.xin${music.coverFilePath}"
            } else {
                "https://music.cnmsb.xin/api/music/cover/${music.id}"
            }
            
            _currentMusicUrl.value = url
            _currentMusicId.value = music.id
            _currentMusicTitle.value = music.title
            _currentMusicArtist.value = music.artist
            _currentMusicCover.value = fullCoverUrl
            
            // 准备但不自动播放
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }
    
    companion object {
        @Volatile
        private var instance: MusicPlayerManager? = null
        
        fun getInstance(context: Context): MusicPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: MusicPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}