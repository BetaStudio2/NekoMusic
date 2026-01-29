package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.neko.music.R
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusic
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.manager.TokenManager
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    playlistCover: String?,
    onBackClick: () -> Unit,
    onMusicClick: (com.neko.music.data.model.Music) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val playlistApi = remember { PlaylistApi(tokenManager.getToken()) }

    // 歌单音乐列表
    var musicList by remember { mutableStateOf<List<PlaylistMusic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // 加载歌单音乐
    LaunchedEffect(playlistId) {
        try {
            isLoading = true
            val token = tokenManager.getToken()
            if (token != null) {
                val response: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlistId)
                Log.d("PlaylistDetailScreen", "加载歌单音乐: playlistId=$playlistId, success=${response.success}")
                if (response.success) {
                    musicList = response.musicList ?: emptyList()
                    Log.d("PlaylistDetailScreen", "加载到${musicList.size}首音乐")
                } else {
                    errorMessage = response.message
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDetailScreen", "加载歌单音乐失败", e)
            errorMessage = "加载失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // 获取封面URL
    val coverUrl = remember(playlistCover) {
        if (playlistCover != null) {
            "https://music.cnmsb.xin${playlistCover}"
        } else {
            "https://music.cnmsb.xin/api/user/avatar/default"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图片 - 使用渐变遮罩
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFFFFFF)
                            ),
                            startY = 0f,
                            endY = 400f
                        )
                    )
            )
        }

        // 内容层
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶部标题栏
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "歌单详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(200.dp))

            // 歌单信息卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp,
                    hoveredElevation = 12.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // 歌单封面
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "歌单封面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 歌单名称
                    Text(
                        text = playlistName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 歌曲数量
                    Text(
                        text = "${musicList.size} 首歌曲",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 播放全部按钮
                    Button(
                        onClick = {
                            // 播放第一首
                            if (musicList.isNotEmpty()) {
                                val firstMusic = musicList[0]
                                onMusicClick(
                                    com.neko.music.data.model.Music(
                                        firstMusic.id,
                                        firstMusic.title,
                                        firstMusic.artist,
                                        firstMusic.coverPath ?: "",
                                        firstMusic.duration,
                                        "",
                                        "",
                                        0,
                                        ""
                                    )
                                )
                            }
                        },
                        enabled = musicList.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed,
                            disabledContainerColor = RoseRed.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "播放全部",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 歌曲列表标题
            Text(
                text = "歌曲列表",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 歌曲列表
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoseRed)
                }
            } else if (errorMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            } else if (musicList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无歌曲",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    itemsIndexed(musicList) { index, music ->
                        PlaylistMusicItem(
                            music = music,
                            position = index + 1,
                            onClick = {
                                onMusicClick(
                                    com.neko.music.data.model.Music(
                                        music.id,
                                        music.title,
                                        music.artist,
                                        music.coverPath ?: "",
                                        music.duration,
                                        "",
                                        "",
                                        0,
                                        ""
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistMusicItem(
    music: PlaylistMusic,
    position: Int,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val coverUrl = remember(music.coverPath) {
        if (!music.coverPath.isNullOrEmpty()) {
            "https://music.cnmsb.xin${music.coverPath}"
        } else {
            "https://music.cnmsb.xin/api/music/cover/${music.id}"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 序号
        Text(
            text = "$position",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.width(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // 封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // 歌曲信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 时长
        Text(
            text = formatTime(music.duration * 1000L),
            fontSize = 13.sp,
            color = Color.Gray
        )
    }
}