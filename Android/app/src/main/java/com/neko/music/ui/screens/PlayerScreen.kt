package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.neko.music.service.MusicPlayerManager
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

enum class PlayMode {
    LIST_LOOP,    // 列表循环
    SINGLE_LOOP,  // 单曲循环
    SHUFFLE       // 随机播放
}

@Composable
fun PlayerScreen(
    music: Music,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    
    val currentTime by remember { derivedStateOf { formatTime(currentPosition) } }
    val totalTime by remember { derivedStateOf { formatTime(duration) } }
    val currentProgressSeconds by remember { derivedStateOf { currentPosition / 1000f } }
    
    var musicFileUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    val isFavorite by playerManager.isFavorite.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var playMode by remember { mutableStateOf(PlayMode.LIST_LOOP) }
    
    val musicApi = remember { MusicApi() }
    val scope = rememberCoroutineScope()
    
    // 加载音乐文件URL，只在音乐ID不同时才重新播放
    LaunchedEffect(music.id) {
        isLoading = true
        scope.launch {
            musicFileUrl = musicApi.getMusicFileUrl(music)
            isLoading = false
            musicFileUrl?.let { url ->
                // 只在音乐ID不同时才播放
                if (currentMusicId != music.id) {
                    // 获取完整的封面URL
                    val fullCoverUrl = if (music.coverFilePath.isNotEmpty()) {
                        "https://music.cnmsb.xin${music.coverFilePath}"
                    } else {
                        "https://music.cnmsb.xin/api/music/cover/${music.id}"
                    }
                    Log.d("PlayerScreen", "封面URL: $fullCoverUrl, coverFilePath: ${music.coverFilePath}")
                    playerManager.playMusic(url, music.id, music.title, music.artist, music.coverFilePath, fullCoverUrl)
                }
            }
            Log.d("PlayerScreen", "音乐文件URL: $musicFileUrl, 当前音乐ID: $currentMusicId")
        }
    }
    
    // 加载歌词
    LaunchedEffect(music.id) {
        scope.launch {
            val result = musicApi.getMusicLyrics(music)
            result.fold(
                onSuccess = { lyricsText ->
                    lyrics = parseLrcLyrics(lyricsText)
                    Log.d("PlayerScreen", "歌词加载成功，共 ${lyrics.size} 行")
                },
                onFailure = { error ->
                    Log.e("PlayerScreen", "歌词加载失败: ${error.message}")
                }
            )
        }
    }
    
    Column(
    
            modifier = Modifier
    
                .fillMaxSize()
    
                .background(Color.White)
    
                .statusBarsPadding()
    
        ) {
    
            TopBar(
    
                onBackClick = onBackClick,
    
                onMenuClick = {},
    
                onPlaylistClick = onPlaylistClick
    
            )
    
            
    
            Spacer(modifier = Modifier.height(16.dp))
    
            
    
            // 封面视图和歌词视图容器
    
            Box(
    
                modifier = Modifier
    
                    .fillMaxWidth()
    
                    .weight(1f)
    
            ) {
    
                // 封面视图
    
                            androidx.compose.animation.AnimatedVisibility(
    
                                visible = !showLyrics,
    
                                modifier = Modifier.fillMaxSize(),
    
                                enter = fadeIn(),
    
                                exit = fadeOut()
    
                            ) {
    
                                Box(
    
                                    modifier = Modifier.fillMaxSize(),
    
                                    contentAlignment = Alignment.Center
    
                                ) {
    
                                    CoverImage(
    
                                        music = music,
    
                                        onClick = { showLyrics = true }
    
                                    )
    
                                }
    
                            }
    
                
    
                // 歌词视图
    
                androidx.compose.animation.AnimatedVisibility(
    
                    visible = showLyrics,
    
                    modifier = Modifier.fillMaxSize(),
    
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
    
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    
                ) {
    
                    Box(
    
                        modifier = Modifier.fillMaxSize()
    
                    ) {
    
                        LyricsView(
    
                            lyrics = lyrics,
    
                            currentProgressSeconds = currentProgressSeconds,
    
                            isLoading = isLoading,
    
                            onClick = { showLyrics = false },
    
                            modifier = Modifier.fillMaxSize()
    
                        )
    
                    }
    
                }
    
            }
    
            
    
            Spacer(modifier = Modifier.height(8.dp))
    
            
    
            // 歌曲信息和收藏按钮 - 紧贴在进度条上方
    
            LyricSongInfoBar(
    
                music = music,
    
                isFavorite = isFavorite,
    
                onFavoriteClick = { playerManager.toggleFavorite() },
                showLyrics = showLyrics
            )

            Spacer(modifier = Modifier.height(8.dp))
    
            
    
                        // 进度条
    
            
    
                                            ProgressSlider(
    
            
    
                                                progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
    
            
    
                                                currentTime = currentTime,
    
            
    
                                                totalTime = totalTime,
    
            
    
                                                isLoading = isLoading,
    
            
    
                                                onProgressChange = { 
    
            
    
                                                    if (duration > 0) {
    
            
    
                                                        playerManager.seekTo((it * duration).toLong())
    
            
    
                                                    }
    
            
    
                                                }
    
            
    
                                            )
    
            
    
            Spacer(modifier = Modifier.height(12.dp))
    
            
    
                        PlaybackControls(
    
            
    
            
    
            
    
            
    
            
    
                                    isPlaying = isPlaying,
    
            
    
            
    
            
    
            
    
            
    
                                    isLoading = isLoading,
    
            
    
            
    
            
    
            
    
            
    
                                    musicFileUrl = musicFileUrl,
    
            
    
            
    
            
    
            
    
            
    
                                    playMode = playMode,
    
            
    
            
    
            
    
            
    
            
    
                                    onPlayPauseClick = {
    
            
    
                                        playerManager.togglePlayPause()
    
            
    
                                    },
    
            
    
            
    
            
    
            
    
            
    
                                    onPreviousClick = {},
    
            
    
            
    
            
    
            
    
            
    
                                    onNextClick = {},
    
            
    
            
    
            
    
            
    
            
    
                                    onPlaylistClick = onPlaylistClick,
    
            
    
            
    
            
    
            
    
            
    
                                    onPlayModeClick = {
    
            
    
            
    
            
    
            
    
            
    
                                        playMode = when (playMode) {
    
            
    
            
    
            
    
            
    
            
    
                                            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
    
            
    
            
    
            
    
            
    
            
    
                                            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
    
            
    
            
    
            
    
            
    
            
    
                                            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
    
            
    
            
    
            
    
            
    
            
    
                                        }
    
            
    
            
    
            
    
            
    
            
    
                                    }
    
            
    
            
    
            
    
            
    
            
    
                                )
    
            
    
                        Spacer(modifier = Modifier.height(24.dp))
    
            
    
                    }
    
            
    
            }
    
            
    
            
    
            
    
            private fun formatTime(milliseconds: Long): String {
    
            
    
                val seconds = (milliseconds / 1000) % 60
    
            
    
                val minutes = (milliseconds / (1000 * 60)) % 60
    
            
    
                return String.format("%d:%02d", minutes, seconds)
    
            
    
            }

@Composable
fun TopBar(
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onPlaylistClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.Black
            )
        }
        
        Text(
            text = "正在播放",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
        
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多",
                tint = Color.Black
            )
        }
    }
}

@Composable
fun CoverImage(
    music: Music,
    onClick: () -> Unit
) {
    val musicApi = remember { MusicApi() }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
    }
    
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RoseRed.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrEmpty()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text(
                text = "🎵",
                fontSize = 60.sp
            )
        }
    }
}

@Composable
fun MusicInfoWithFavorite(
    music: Music,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        // 歌名和歌手 - 居中
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = music.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = music.artist,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
        
        // 收藏按钮 - 右侧固定位置
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) RoseRed else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MusicInfo(
    music: Music,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = music.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = music.artist,
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) RoseRed else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun LyricSongInfoBar(
    music: Music,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    showLyrics: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        if (showLyrics) {
            // 歌词界面：歌名和歌手在左侧
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                // 歌名 - 可横向滚动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = music.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // 歌手
                Text(
                    text = music.artist,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        } else {
            // 封面界面：歌名和歌手居中
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = music.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = music.artist,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // 收藏按钮 - 始终在右侧固定位置
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) RoseRed else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LyricsSongInfo(
    music: Music,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // 歌名和歌手 - 左侧
        Column(
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            // 歌名 - 可横向滚动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Text(
                    text = music.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 歌手
            Text(
                text = music.artist,
                fontSize = 14.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
        
        // 收藏按钮 - 右侧固定位置
        IconButton(
            onClick = {},
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "收藏",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    currentProgressSeconds: Float,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.LazyListState()
    val currentIndex = remember(lyrics, currentProgressSeconds) {
        lyrics.indexOfLast { it.time <= currentProgressSeconds }
    }
    
    // 自动滚动到当前歌词，使其居中
    androidx.compose.runtime.LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lyrics.isNotEmpty()) {
            try {
                listState.animateScrollToItem(currentIndex)
            } catch (e: Exception) {
                // 忽略滚动错误
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(color = RoseRed)
            }
            lyrics.isEmpty() -> {
                Text(
                    text = "暂无歌词",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
            else -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 添加顶部占位，让第一行歌词也能居中
                    item {
                        Spacer(modifier = Modifier.height(300.dp))
                    }
                    
                    items(lyrics.size) { index ->
                        val line = lyrics[index]
                        val isCurrentLine = index == currentIndex
                        
                        Text(
                            text = line.text,
                            fontSize = if (isCurrentLine) 18.sp else 14.sp,
                            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrentLine) RoseRed else Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                    
                    // 添加底部占位，让最后一行歌词也能居中
                    item {
                        Spacer(modifier = Modifier.height(300.dp))
                    }
                }
            }
        }
    }
}

/**
 * LRC 歌词行数据类
 */
data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String // 歌词文本
)

/**
 * 解析 LRC 格式歌词
 * 解析时间标签并提取歌词文本
 */
private fun parseLrcLyrics(lrcText: String): List<LrcLine> {
    val lines = lrcText.lines()
    val result = mutableListOf<LrcLine>()
    
    for (line in lines) {
        // 匹配时间标签 [00:00.000] 或 [00:00.60]
        val timePattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")
        val match = timePattern.find(line)
        
        if (match != null) {
            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val centiseconds = match.groupValues[3].toInt()
            
            // 计算总时间（秒）
            val time = minutes * 60 + seconds + centiseconds / 1000f
            
            // 提取歌词文本（移除所有标签）
            var text = line
            // 移除时间标签
            text = timePattern.replace(text, "")
            // 移除元数据标签 [ti:xxx], [ar:xxx] 等
            text = Regex("""\[[a-z]{2}:.*?\]""").replace(text, "")
            
            text = text.trim()
            
            if (text.isNotEmpty()) {
                result.add(LrcLine(time, text))
            }
        }
    }
    
    return result
}

@Composable
fun ProgressSlider(
    progress: Float,
    currentTime: String,
    totalTime: String,
    isLoading: Boolean,
    onProgressChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        if (!isLoading) {
            Slider(
                value = progress,
                onValueChange = onProgressChange,
                colors = SliderDefaults.colors(
                    activeTrackColor = RoseRed,
                    inactiveTrackColor = Color(0xFFE0E0E0),
                    thumbColor = RoseRed
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentTime,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = totalTime,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    musicFileUrl: String?,
    playMode: PlayMode,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onPlayModeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：播放模式、上一首
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放模式按钮
            IconButton(
                onClick = onPlayModeClick,
                modifier = Modifier.size(40.dp)
            ) {
                val iconRes = when (playMode) {
                    PlayMode.LIST_LOOP -> R.drawable.list_loop
                    PlayMode.SINGLE_LOOP -> R.drawable.single_loop
                    PlayMode.SHUFFLE -> R.drawable.shuffle
                }
                Icon(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = "Play Mode",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 上一首按钮
            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.previous_song),
                    contentDescription = "Previous",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // 中间：播放/暂停按钮
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(RoseRed, CircleShape)
                .clickable(enabled = !isLoading && musicFileUrl != null, onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                musicFileUrl == null -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Loading",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                else -> {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.pause else R.drawable.play
                        ),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        
        // 右侧：下一首、播放列表
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 下一首按钮
            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.next_song),
                    contentDescription = "Next",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // 播放列表按钮
            IconButton(
                onClick = onPlaylistClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.playlist),
                    contentDescription = "Playlist",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}