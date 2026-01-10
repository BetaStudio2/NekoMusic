package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit = {},
    onNavigateToFavorite: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    val toastMessage = remember { androidx.compose.runtime.mutableStateOf("") }
    val showToast = remember { androidx.compose.runtime.mutableStateOf(false) }
    
    // 更新状态
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // 启动时检查更新
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val info = updateManager.checkUpdate()
                if (info != null && info.isUpdateAvailable) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "检查更新失败", e)
            }
        }
    }
    
    // 下载并安装更新
    val downloadAndInstall = {
        scope.launch {
            isDownloading = true
            downloadProgress = 0f
            
            try {
                val apkFile = updateManager.downloadApk(
                    updateInfo!!.updateUrl,
                    { downloaded, total ->
                        if (total > 0) {
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                    }
                )
                
                if (apkFile != null) {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateSuccessDialog = true
                    updateManager.installApk(apkFile)
                } else {
                    isDownloading = false
                    showUpdateDialog = false
                    showUpdateErrorDialog = true
                    errorMessage = "下载失败，请稍后重试"
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "下载更新失败", e)
                isDownloading = false
                showUpdateDialog = false
                showUpdateErrorDialog = true
                errorMessage = "下载失败：${e.message}"
            }
        }
    }
    
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    // 浮动动画
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        // 显示土司消息
        if (showToast.value && toastMessage.value.isNotEmpty()) {
            LaunchedEffect(showToast.value) {
                if (showToast.value) {
                    kotlinx.coroutines.delay(2000)
                    showToast.value = false
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
                            elevation = 8.dp,
                            spotColor = RoseRed.copy(alpha = 0.3f),
                            ambientColor = Color.Gray.copy(alpha = 0.15f)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = toastMessage.value,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
        
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                // 头部区域
                HeaderSection(
                    onSearchClick = onSearchClick,
                    floatOffset = floatOffset
                )
                
                // 欢迎横幅
                WelcomeBanner()
                
                // 快速访问
                QuickAccessSection(
                    onNavigateToFavorite = onNavigateToFavorite
                )
                
                // 推荐歌单
                RecommendedPlaylists()
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            versionName = updateInfo!!.versionName,
            versionCode = updateInfo!!.versionCode,
            onConfirm = { downloadAndInstall() },
            onDismiss = { showUpdateDialog = false }
        )
    }
    
    if (isDownloading) {
        DownloadProgressDialog(
            progress = downloadProgress,
            onDismiss = { isDownloading = false }
        )
    }
    
    if (showUpdateSuccessDialog) {
        UpdateSuccessDialog(
            onDismiss = { showUpdateSuccessDialog = false }
        )
    }
    
    if (showUpdateErrorDialog) {
        UpdateErrorDialog(
            message = errorMessage,
            onDismiss = { showUpdateErrorDialog = false }
        )
    }
}

@Composable
fun HeaderSection(
    onSearchClick: () -> Unit,
    floatOffset: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6B5B95),
                        RoseRed
                    )
                )
            )
    ) {
        // 装饰圆圈
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = SakuraPink.copy(alpha = 0.15f),
                radius = 120.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.3f)
            )
            drawCircle(
                color = SkyBlue.copy(alpha = 0.1f),
                radius = 80.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.7f)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "欢迎回来",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Neko云音乐",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // 浮动音乐图标
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .offset(y = floatOffset.dp)
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 28.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 搜索框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(horizontal = 20.dp)
                    .clickable {
                        Log.d("HomeScreen", "搜索框被点击")
                        onSearchClick()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "搜索音乐、歌手、专辑...",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun WelcomeBanner() {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(100.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        SakuraPink.copy(alpha = 0.3f),
                        SkyBlue.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                spotColor = RoseRed.copy(alpha = 0.2f),
                ambientColor = Color.Gray.copy(alpha = 0.1f)
            )
            .clickable {
                // 暂未实现
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "✨ 探索音乐世界",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "发现你喜欢的音乐",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = "🎧",
                fontSize = 36.sp
            )
        }
    }
}

@Composable
fun QuickAccessSection(
    onNavigateToFavorite: () -> Unit
) {
    val toastMessage = remember { androidx.compose.runtime.mutableStateOf("") }
    val showToast = remember { androidx.compose.runtime.mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = "快速访问",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickAccessItem(
                icon = "🎵",
                label = "我的音乐",
                onClick = {
                    toastMessage.value = "暂未实现"
                    showToast.value = true
                }
            )
            QuickAccessItem(
                icon = "❤️",
                label = "我喜欢",
                onClick = {
                    onNavigateToFavorite()
                }
            )
            QuickAccessItem(
                icon = "📻",
                label = "电台",
                onClick = {
                    toastMessage.value = "暂未实现"
                    showToast.value = true
                }
            )
            QuickAccessItem(
                icon = "🎤",
                label = "歌手",
                onClick = {
                    toastMessage.value = "暂未实现"
                    showToast.value = true
                }
            )
        }
    }
}

@Composable
fun QuickAccessItem(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
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
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            RoseRed.copy(alpha = 0.1f),
                            SakuraPink.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 28.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RecommendedPlaylists() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "推荐歌单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "查看全部",
                fontSize = 14.sp,
                color = RoseRed,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(listOf("流行", "摇滚", "古典", "电子", "爵士")) { genre ->
                PlaylistCard(genre = genre)
            }
        }
    }
}

@Composable
fun PlaylistCard(genre: String) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        when (genre) {
                            "流行" -> RoseRed
                            "摇滚" -> Color(0xFF6B5B95)
                            "古典" -> Lilac
                            "电子" -> SkyBlue
                            "爵士" -> Peach
                            else -> RoseRed
                        }.copy(alpha = 0.8f),
                        Color.White
                    )
                )
            )
            .scale(scale)
            .shadow(
                elevation = 6.dp,
                spotColor = RoseRed.copy(alpha = 0.3f),
                ambientColor = Color.Gray.copy(alpha = 0.15f)
            )
            .clickable {
                isPressed = true
                // 暂未实现
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (genre) {
                    "流行" -> "🎸"
                    "摇滚" -> "🎤"
                    "古典" -> "🎻"
                    "电子" -> "🎹"
                    "爵士" -> "🎷"
                    else -> "🎵"
                },
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = genre,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "100+ 首歌曲",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

// 对话框组件保持不变
@Composable
fun UpdateDialog(
    versionName: String,
    versionCode: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = "🎉 发现新版本",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "新版本：$versionName",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "版本号：$versionCode",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(28.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "稍后",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "立即更新",
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadProgressDialog(
    progress: Float,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⏳ 正在下载更新",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = RoseRed,
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun UpdateSuccessDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓",
                    fontSize = 56.sp,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "下载完成",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "正在安装更新...",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .shadow(
                    elevation = 8.dp,
                    spotColor = RoseRed.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            Column(
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = "❌ 更新失败",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = message,
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "确定",
                            fontSize = 16.sp,
                            color = RoseRed
                        )
                    }
                }
            }
        }
    }
}