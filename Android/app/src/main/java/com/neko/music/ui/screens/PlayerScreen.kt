package com.neko.music.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.neko.music.service.PlayMode
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
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
import com.neko.music.ui.theme.SakuraPink
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import kotlinx.coroutines.launch

// 格式化时间显示（毫秒转 mm:ss）
fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    return String.format("%d:%02d", minutes, seconds)
}

// LRC歌词行数据类
data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String // 歌词文本
)

// 解析 LRC 格式歌词
fun parseLrcLyrics(lrcText: String): List<LrcLine> {
    val lines = lrcText.lines()
    val result = mutableListOf<LrcLine>()
    
    for (line in lines) {
        // 匹配时间标签 [00:00.000] 或 [00:00.60]
        val timePattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")
        val match = timePattern.find(line)
        
        if (match != null) {
            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val milliseconds = match.groupValues[3].toInt()
            
            // 计算总时间（秒）
            val time = minutes * 60 + seconds + milliseconds / 1000f
            
            // 提取歌词文本（移除时间标签）
            val text = line.replace(timePattern, "").trim()
            
            if (text.isNotEmpty()) {
                result.add(LrcLine(time, text))
            }
        }
    }
    
    return result
}

@Composable
fun PlayerScreen(
    music: Music,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val tokenManager = com.neko.music.data.manager.TokenManager(context)

    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val currentMusicTitle by playerManager.currentMusicTitle.collectAsState()
    val currentMusicArtist by playerManager.currentMusicArtist.collectAsState()
    val currentMusicCover by playerManager.currentMusicCover.collectAsState()

    // 检查登录状态
    val isLoggedIn = tokenManager.isLoggedIn()

    val currentTime by remember { derivedStateOf { formatTime(currentPosition) } }
    val totalTime by remember { derivedStateOf { formatTime(duration) } }
    val currentProgressSeconds by remember { derivedStateOf { currentPosition / 1000f } }

    var musicFileUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isFavorite by playerManager.isFavorite.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    val playMode by playerManager.playMode.collectAsState()
    val playbackSpeed by playerManager.playbackSpeed.collectAsState()

    // 登录提示
    var showLoginToast by remember { mutableStateOf(false) }
    val playModeChanged by playerManager.playModeChanged.collectAsState()
    var showPlayModeToast by remember { mutableStateOf(false) }

    // 分享对话框
    var showShareDialog by remember { mutableStateOf(false) }
    var showShareToast by remember { mutableStateOf(false) }
    var shareToastMessage by remember { mutableStateOf("") }

    // 从播放器获取当前音乐信息
    val currentMusic = remember(currentMusicId) {
        val id = currentMusicId
        val title = currentMusicTitle
        val artist = currentMusicArtist
        if (id != null && title != null && artist != null) {
            Music(
                id = id,
                title = title,
                artist = artist,
                album = "",
                duration = duration.toInt(),
                filePath = musicFileUrl ?: "",
                coverFilePath = currentMusicCover ?: "",
                uploadUserId = 0,
                createdAt = ""
            )
        } else {
            music
        }
    }

    val musicApi = remember { MusicApi(context) }
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
                    val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                        "https://music.cnmsb.xin${music.coverFilePath}"
                    } else {
                        "https://music.cnmsb.xin/api/music/cover/${music.id}"
                    }
                    Log.d(
                        "PlayerScreen",
                        "封面URL: $fullCoverUrl, coverFilePath: ${music.coverFilePath}"
                    )
                    playerManager.playMusic(
                        url,
                        music.id,
                        music.title,
                        music.artist,
                        music.coverFilePath ?: "",
                        fullCoverUrl
                    )
                }
            }
            Log.d("PlayerScreen", "音乐文件URL: $musicFileUrl, 当前音乐ID: $currentMusicId")
        }
    }

    // 加载歌词
    LaunchedEffect(currentMusic.id) {
        scope.launch {
            val result = musicApi.getMusicLyrics(currentMusic)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
        ) {
            TopBar(
                onBackClick = onBackClick,
                onMenuClick = { showShareDialog = true },
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
                            music = currentMusic,
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
                            modifier = Modifier.fillMaxSize(),
                            listState = lyricsListState
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 歌曲信息和收藏按钮 - 紧贴在进度条上方
            LyricSongInfoBar(
                music = currentMusic,
                isFavorite = isFavorite,
                onFavoriteClick = {
                    if (isLoggedIn) {
                        playerManager.toggleFavorite()
                    } else {
                        showLoginToast = true
                    }
                },
                showLyrics = showLyrics,
                isLoggedIn = isLoggedIn
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
                onPreviousClick = { playerManager.previous() },
                onNextClick = { playerManager.next() },
                onPlaylistClick = onPlaylistClick,
                onPlayModeClick = { playerManager.togglePlayMode() }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    // 控制播放模式提示的显示
    LaunchedEffect(playModeChanged) {
        if (playModeChanged > 0) {
            showPlayModeToast = true
            delay(2000)
            showPlayModeToast = false
        }
    }

    // 控制登录提示的显示
    LaunchedEffect(showLoginToast) {
        if (showLoginToast) {
            delay(2000)
            showLoginToast = false
        }
    }
    
    // 播放模式提示（悬浮窗，层级最高）
    AnimatedVisibility(
        visible = showPlayModeToast,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 80.dp)
                .height(32.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (playMode) {
                    PlayMode.LIST_LOOP -> "列表循环"
                    PlayMode.SINGLE_LOOP -> "单曲循环"
                    PlayMode.SHUFFLE -> "随机播放"
                },
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // 登录提示（悬浮窗，层级最高）
    AnimatedVisibility(
        visible = showLoginToast,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 80.dp)
                .height(32.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "请先登录",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }

    // 分享对话框
    if (showShareDialog) {
        ShareDialog(
            music = currentMusic,
            onDismiss = { showShareDialog = false },
            onCopyLink = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val shareText = "【${currentMusic.title}-${currentMusic.artist}】 Neko云音乐 https://music.cnmsb.xin/detail/${currentMusic.id}"
                        val clip = android.content.ClipData.newPlainText("音乐链接", shareText)
                        clipboardManager.setPrimaryClip(clip)
                        shareToastMessage = "链接已复制"
                    } catch (e: Exception) {
                        shareToastMessage = "复制失败"
                    }
                    showShareToast = true
                }
            },
            onDownload = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val downloadHelper = com.neko.music.util.DownloadHelper(context)
                        val result = downloadHelper.downloadMusicWithLyrics(currentMusic)
                        result.fold(
                            onSuccess = { message ->
                                shareToastMessage = message
                            },
                            onFailure = { error ->
                                shareToastMessage = "下载失败: ${error.message}"
                            }
                        )
                    } catch (e: Exception) {
                        shareToastMessage = "下载失败: ${e.message}"
                    }
                    showShareToast = true
                }
            },
            onShareToTwitter = {
                scope.launch {
                    showShareDialog = false
                    try {
                        val shareText = "我在Neko云音乐发现宝藏音乐！${currentMusic.artist}唱的《${currentMusic.title}》https://music.cnmsb.xin/detail/${currentMusic.id} 大家快来听喵~"
                        val encodedText = java.net.URLEncoder.encode(shareText, "UTF-8")

                        // 先尝试使用Twitter应用
                        val twitterIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("twitter://post?message=$encodedText")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // 尝试启动Twitter应用
                        try {
                            context.startActivity(twitterIntent)
                        } catch (e: Exception) {
                            // 如果Twitter应用未安装，使用网页版
                            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://twitter.com/intent/tweet?text=$encodedText")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(webIntent)
                        }
                    } catch (e: Exception) {
                        shareToastMessage = "分享失败"
                        showShareToast = true
                    }
                }
            },
            onSpeedChange = { speed ->
                playerManager.setPlaybackSpeed(speed)
                shareToastMessage = "倍速: ${speed}x"
                showShareToast = true
            },
            currentSpeed = playbackSpeed
        )
    }

    // 分享提示（悬浮窗，层级最高）
    LaunchedEffect(showShareToast) {
        if (showShareToast) {
            delay(2000)
            showShareToast = false
        }
    }

    AnimatedVisibility(
        visible = showShareToast,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 80.dp)
                .height(32.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shareToastMessage,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
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
            val context = LocalContext.current
            val musicApi = remember { MusicApi(context) }
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
                    onFavoriteClick: () -> Unit,
                    isLoggedIn: Boolean = true
        ) {            Box(
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
            showLyrics: Boolean,
            isLoggedIn: Boolean = true
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
            modifier: Modifier = Modifier,
            listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
        ) {
            val currentIndex = remember(lyrics, currentProgressSeconds) {
                lyrics.indexOfLast { it.time <= currentProgressSeconds }
            }

            // 自动滚动到当前歌词，使其居中
            androidx.compose.runtime.LaunchedEffect(currentIndex) {
                android.util.Log.d("LyricsView", "LaunchedEffect: currentIndex=$currentIndex, lyrics.size=${lyrics.size}")
                if (currentIndex >= 0 && lyrics.isNotEmpty()) {
                    try {
                        // 延迟一下，避免频繁触发
                        kotlinx.coroutines.delay(50)
                        // 简单地滚动到当前歌词
                        listState.animateScrollToItem(currentIndex, 0)
                        android.util.Log.d("LyricsView", "Scroll to index=$currentIndex")
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsView", "Scroll error: ${e.message}", e)
                    }
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 32.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RoseRed)
                        }
                    }

                    lyrics.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无歌词",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    else -> {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 添加顶部占位，让第一行歌词也能居中
                            item {
                                Spacer(modifier = Modifier.height(250.dp))
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
                        .clickable(
                            enabled = !isLoading && musicFileUrl != null,
                            onClick = onPlayPauseClick
                        ),
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


@Composable
fun ShareDialog(
    music: Music,
    onDismiss: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onShareToTwitter: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    currentSpeed: Float = 1.0f
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            // 底部弹出面板
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 横向滚动的分享列表
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.twitter,
                                    label = "分享到推特",
                                    color = Color(0xFF1DA1F2),
                                    onClick = onShareToTwitter
                                )
                            }
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.copy_link,
                                    label = "复制链接",
                                    color = RoseRed,
                                    onClick = onCopyLink
                                )
                            }
                            item {
                                ShareGridItem(
                                    iconRes = R.drawable.download,
                                    label = "下载",
                                    color = Color(0xFF6B5B95),
                                    onClick = onDownload
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 倍速选择器
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "倍速",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    count = 6,
                                    key = { index -> index }
                                ) { index ->
                                    val speed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)[index]
                                    SpeedChip(
                                        speed = speed,
                                        isSelected = speed == currentSpeed,
                                        onClick = { onSpeedChange(speed) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 分割线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(Color(0xFFE8E8E8))
                        )

                        // 取消按钮
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "取消",
                                fontSize = 17.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareGridItem(
    icon: String? = null,
    iconRes: Int? = null,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color)
                .shadow(
                    elevation = 4.dp,
                    spotColor = color.copy(alpha = 0.3f),
                    ambientColor = color.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else if (icon != null) {
                Text(
                    text = icon,
                    fontSize = 28.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) RoseRed else Color(0xFFF5F5F5)
    val textColor = if (isSelected) Color.White else Color.Gray

    Box(
        modifier = Modifier
            .height(36.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            fontSize = 14.sp,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
