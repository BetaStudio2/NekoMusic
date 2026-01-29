package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neko.music.R
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.model.Playlist
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MyPlaylistsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Demo数据
    var playlists by remember { mutableStateOf(listOf(
        Playlist(1, "我喜欢的音乐", 128, 1, "2026-01-15"),
        Playlist(2, "工作专用", 45, 1, "2026-01-20"),
        Playlist(3, "运动歌单", 23, 1, "2026-01-25"),
        Playlist(4, "睡前音乐", 67, 1, "2026-01-28")
    ))}
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    // 创建/编辑歌单对话框
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogPlaylistName by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    
    // 创建或更新歌单（Demo）
    val createOrUpdatePlaylist = {
        if (dialogPlaylistName.isNotBlank()) {
            if (editingPlaylist != null) {
                // 更新歌单
                playlists = playlists.map { 
                    if (it.id == editingPlaylist!!.id) 
                        it.copy(name = dialogPlaylistName) 
                    else 
                        it 
                }
            } else {
                // 创建新歌单（添加到第二位，保留"我的收藏"在第一位）
                val newId = (playlists.maxOfOrNull { it.id } ?: 0) + 1
                val newPlaylist = Playlist(newId, dialogPlaylistName, 0, 1, "2026-01-29")
                playlists = listOf(playlists[0]) + listOf(newPlaylist) + playlists.drop(1)
            }
            showCreateDialog = false
            dialogPlaylistName = ""
            editingPlaylist = null
        }
    }
    
    // 删除歌单（Demo）- 不能删除"我的收藏"
    val deletePlaylist = { playlist: Playlist ->
        if (playlist.id != 1) { // 不能删除"我的收藏"
            playlists = playlists.filter { it.id != playlist.id }
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
            } else if (playlists.isEmpty()) {
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
                    items(playlists) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onEdit = {
                                if (playlist.id != 1) { // "我的收藏"不能编辑
                                    editingPlaylist = playlist
                                    dialogPlaylistName = playlist.name
                                    showCreateDialog = true
                                }
                            },
                            onDelete = {
                                if (playlist.id != 1) { // "我的收藏"不能删除
                                    deletePlaylist(playlist)
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
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val isMyFavorites = playlist.id == 1 // "我的收藏"不能编辑/删除
    
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
                // TODO: 点击歌单跳转到详情页
            }
            .scale(scale)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 歌单封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isMyFavorites) RoseRed.copy(alpha = 0.15f)
                    else Color.White
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMyFavorites) "❤️" else "🎵",
                fontSize = 24.sp
            )
        }
        
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
            Spacer(modifier = Modifier.height(4.dp))
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "编辑",
                        tint = RoseRed
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
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