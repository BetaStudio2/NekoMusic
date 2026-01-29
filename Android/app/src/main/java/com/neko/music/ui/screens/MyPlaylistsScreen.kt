package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImage
import com.neko.music.R
import androidx.compose.ui.zIndex
import coil.request.ImageRequest
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusic
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.api.PlaylistListResponse
import com.neko.music.data.api.PlaylistResponse
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.model.Playlist
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MyPlaylistsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val playlistApi = remember { PlaylistApi(tokenManager.getToken()) }
    val favoriteApi = remember { FavoriteApi() }
    
    // 歌单数据
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var favoritesCount by remember { mutableStateOf(0) }
    var favorites by remember { mutableStateOf<List<com.neko.music.data.api.FavoriteMusic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    // 初始化 - 加载歌单和收藏数据
    LaunchedEffect(Unit) {
        try {
            val token = tokenManager.getToken()
            if (token != null) {
                // 加载歌单列表
                val playlistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                Log.d("MyPlaylistsScreen", "歌单API响应: success=${playlistResponse.success}, message=${playlistResponse.message}")
                if (playlistResponse.success) {
                    // 转换PlaylistInfo到Playlist
                    playlists = playlistResponse.playlists?.map { info ->
                        Log.d("MyPlaylistsScreen", "API返回歌单: id=${info.id}, name=${info.name}, coverPath=${info.coverPath}")
                        Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath)
                    } ?: emptyList()
                    Log.d("MyPlaylistsScreen", "歌单列表: ${playlists.size}个")
                    playlists.forEach { 
                        Log.d("MyPlaylistsScreen", "转换后歌单: id=${it.id}, name=${it.name}, coverPath=${it.coverPath}")
                    }
                } else {
                    Log.e("MyPlaylistsScreen", "加载歌单失败: ${playlistResponse.message}")
                }
                
                // 加载收藏列表
                val favoriteResponse = favoriteApi.getFavorites(token)
                Log.d("MyPlaylistsScreen", "收藏API响应: success=${favoriteResponse.success}, 数量=${favoriteResponse.favorites.size}")
                if (favoriteResponse.success) {
                    favoritesCount = favoriteResponse.favorites.size
                    favorites = favoriteResponse.favorites
                    if (favorites.isNotEmpty()) {
                        Log.d("MyPlaylistsScreen", "第一首音乐: id=${favorites[0].id}, title=${favorites[0].title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MyPlaylistsScreen", "初始化失败", e)
            errorMessage = "加载失败: ${e.message}"
            showError = true
        } finally {
            isLoading = false
        }
    }
    
    // 获取完整的歌单列表（包括"我的收藏"）
    val allPlaylists = remember(playlists, favoritesCount) {
        listOf(
            Playlist(0, "我的收藏", favoritesCount, 1, "2026-01-15", null)
        ) + playlists
    }
    
    // 创建/编辑歌单对话框
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogPlaylistName by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    // 歌单详情
    var showPlaylistDetail by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var playlistMusicList by remember { mutableStateOf<List<PlaylistMusic>>(emptyList()) }
    var isLoadingMusic by remember { mutableStateOf(false) }
    
    // 创建或更新歌单
    val createOrUpdatePlaylist = {
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response: PlaylistResponse = if (editingPlaylist != null) {
                        // 更新歌单
                        playlistApi.updatePlaylist(editingPlaylist!!.id, dialogPlaylistName)
                    } else {
                        // 创建歌单
                        playlistApi.createPlaylist(dialogPlaylistName)
                    }
                    
                    if (response.success) {
                        // 重新加载歌单列表
                        val newPlaylistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                        if (newPlaylistResponse.success) {
                            playlists = newPlaylistResponse.playlists?.map { info ->
                                Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath)
                            } ?: emptyList()
                        }
                        showCreateDialog = false
                        dialogPlaylistName = ""
                        editingPlaylist = null
                    } else {
                        errorMessage = response.message
                        showError = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MyPlaylistsScreen", "操作失败", e)
                errorMessage = "操作失败: ${e.message}"
                showError = true
            }
        }
    }
    
    // 删除歌单
    val deletePlaylist = { playlist: Playlist ->
        scope.launch {
            try {
                val response: PlaylistResponse = playlistApi.deletePlaylist(playlist.id)
                if (response.success) {
                    // 重新加载歌单列表
                    val newPlaylistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                    if (newPlaylistResponse.success) {
                        playlists = newPlaylistResponse.playlists?.map { info ->
                            Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath)
                        } ?: emptyList()
                    }
                } else {
                    errorMessage = response.message
                    showError = true
                }
            } catch (e: Exception) {
                Log.e("MyPlaylistsScreen", "删除失败", e)
                errorMessage = "删除失败: ${e.message}"
                showError = true
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景图片 - 使用WindowInsets处理状态栏
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.list_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
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
                    .background(Color.Transparent)
                    .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = "我的歌单",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoseRed)
                }
            } else if (allPlaylists.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "🎵",
                            fontSize = 64.sp
                        )
                        Text(
                            text = "还没有歌单",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = {
                                editingPlaylist = null
                                dialogPlaylistName = ""
                                showCreateDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoseRed
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "创建歌单",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // 歌单列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(allPlaylists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            favorites = favorites,
                            onEdit = {
                                if (playlist.id != 0) { // "我的收藏"不能编辑
                                    editingPlaylist = playlist
                                    dialogPlaylistName = playlist.name
                                    showCreateDialog = true
                                }
                            },
                            onDelete = {
                                if (playlist.id != 0) { // "我的收藏"不能删除
                                    deletePlaylist(playlist)
                                }
                            },
                            onClick = {
                                // 打开歌单详情
                                selectedPlaylist = playlist
                                showPlaylistDetail = true
                                scope.launch {
                                    try {
                                        isLoadingMusic = true
                                        val token = tokenManager.getToken()
                                        if (token != null) {
                                            val response: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlist.id)
                                            if (response.success) {
                                                playlistMusicList = response.musicList ?: emptyList()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MyPlaylistsScreen", "加载歌单音乐失败", e)
                                    } finally {
                                        isLoadingMusic = false
                                    }
                                }
                            }
                        )
                    }
                    
                    // 添加创建按钮项
                    item {
                        Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clickable {
                                            editingPlaylist = null
                                            dialogPlaylistName = ""
                                            showCreateDialog = true
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.85f)
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 4.dp,
                                        hoveredElevation = 6.dp
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = RoseRed
                                    )
                                    Text(
                                        text = "创建新歌单",
                                        fontSize = 16.sp,
                                        color = RoseRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 创建/编辑歌单对话框
        if (showCreateDialog) {
            PlaylistDialog(
                title = if (editingPlaylist != null) "编辑歌单" else "创建歌单",
                playlistName = dialogPlaylistName,
                onNameChange = { dialogPlaylistName = it },
                onConfirm = { createOrUpdatePlaylist() },
                onDismiss = { 
                    showCreateDialog = false
                    dialogPlaylistName = ""
                    editingPlaylist = null
                }
            )
        }
        
        // 错误提示
        if (showError) {
            LaunchedEffect(showError) {
                if (showError) {
                    kotlinx.coroutines.delay(2000)
                    showError = false
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .shadow(
                            elevation = 12.dp,
                            spotColor = RoseRed.copy(alpha = 0.35f),
                            ambientColor = Color.Gray.copy(alpha = 0.18f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        // 歌单详情对话框
        if (showPlaylistDetail && selectedPlaylist != null) {
            PlaylistDetailDialog(
                playlist = selectedPlaylist!!,
                musicList = playlistMusicList,
                isLoading = isLoadingMusic,
                onDismiss = {
                    showPlaylistDetail = false
                    selectedPlaylist = null
                    playlistMusicList = emptyList()
                },
                onPlayMusic = { music ->
                    // TODO: 播放音乐
                }
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    favorites: List<com.neko.music.data.api.FavoriteMusic>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val isMyFavorites = playlist.id == 0 // "我的收藏"不能编辑/删除
    
    // 确定要显示的封面URL
    val coverUrl = remember(playlist, favorites) {
        val url = when {
            playlist.id == 0 && playlist.name == "我的收藏" -> {
                // "我的收藏"使用第一首收藏音乐的封面
                val firstFavorite = favorites.firstOrNull()
                if (firstFavorite != null) {
                    "https://music.cnmsb.xin/api/music/cover/${firstFavorite.id}"
                } else {
                    // 没有收藏，使用默认头像
                    "https://music.cnmsb.xin/api/user/avatar/default"
                }
            }
            !playlist.coverPath.isNullOrEmpty() -> {
                // 歌单有自己的封面
                "https://music.cnmsb.xin${playlist.coverPath}"
            }
            else -> {
                // 没有封面，使用默认头像
                "https://music.cnmsb.xin/api/user/avatar/default"
            }
        }
        Log.d("PlaylistItem", "歌单ID=${playlist.id}, 名称=${playlist.name}, coverPath=${playlist.coverPath}, coverUrl=$url")
        url
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .shadow(
                elevation = 6.dp,
                spotColor = RoseRed.copy(alpha = 0.2f),
                ambientColor = Color.Gray.copy(alpha = 0.1f)
            )
            .clickable {
                isPressed = true
                onClick()
            }
            .scale(scale)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 歌单封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // 始终显示图片（封面或默认头像）
            androidx.compose.foundation.Image(
                painter = rememberAsyncImagePainter(coverUrl),
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.width(20.dp))
        
        // 歌单信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "${playlist.musicCount} 首歌曲",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        
        // 操作按钮（"我的收藏"不显示）
        if (!isMyFavorites) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "编辑",
                        tint = RoseRed
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailDialog(
    playlist: Playlist,
    musicList: List<PlaylistMusic>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPlayMusic: (PlaylistMusic) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = RoseRed.copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playlist.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                
                // 分割线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(Color(0xFFE8E8E8))
                )
                
                // 音乐列表
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RoseRed)
                    }
                } else if (musicList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "歌单暂无音乐",
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
                        items(musicList.size) { index ->
                            val music = musicList[index]
                            MusicListItem(
                                music = music,
                                position = index + 1,
                                onClick = { onPlayMusic(music) }
                            )
                        }
                    }
                }
                
                // 底部按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        //height = 48.dp
                    ) {
                        Text(
                            text = "关闭",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MusicListItem(
    music: PlaylistMusic,
    position: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .clickable(onClick = onClick)
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
            textAlign = TextAlign.Center
        )
        
        // 封面
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            if (!music.coverPath.isNullOrEmpty()) {
                AsyncImage(
                    model = "https://music.cnmsb.xin${music.coverPath}",
                    contentDescription = "封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "🎵",
                    fontSize = 20.sp
                )
            }
        }
        
        // 歌曲信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = music.artist,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 时长
        Text(
            text = formatTime(music.duration * 1000L),
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
@Composable
fun PlaylistDialog(
    title: String,
    playlistName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = RoseRed.copy(alpha = 0.35f),
                    ambientColor = Color.Gray.copy(alpha = 0.18f)
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = onNameChange,
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "取消",
                            fontSize = 17.sp,
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Button(
                        onClick = onConfirm,
                        enabled = playlistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed,
                            disabledContainerColor = RoseRed.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "确定",
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}