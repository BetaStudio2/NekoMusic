package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import com.neko.music.data.manager.AppUpdateManager
import com.neko.music.data.manager.UpdateInfo
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.DeepBlue
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    
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
            // 检查更新
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        HeaderSection(onSearchClick = onSearchClick)
    }
    
    // 更新提示对话框
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            versionName = updateInfo!!.versionName,
            versionCode = updateInfo!!.versionCode,
            onConfirm = { downloadAndInstall() },
            onDismiss = { showUpdateDialog = false }
        )
    }
    
    // 下载进度对话框
    if (isDownloading) {
        DownloadProgressDialog(
            progress = downloadProgress,
            onDismiss = { isDownloading = false }
        )
    }
    
    // 更新成功提示
    if (showUpdateSuccessDialog) {
        UpdateSuccessDialog(
            onDismiss = { showUpdateSuccessDialog = false }
        )
    }
    
    // 更新失败提示
    if (showUpdateErrorDialog) {
        UpdateErrorDialog(
            message = errorMessage,
            onDismiss = { showUpdateErrorDialog = false }
        )
    }
}

/**
 * 更新提示对话框
 */
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
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "发现新版本",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepBlue
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "稍后",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    androidx.compose.material3.Button(
                        onClick = onConfirm,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        )
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

/**
 * 下载进度对话框
 */
@Composable
fun DownloadProgressDialog(
    progress: Float,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "正在下载更新...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepBlue
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

/**
 * 更新成功对话框
 */
@Composable
fun UpdateSuccessDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓",
                    fontSize = 48.sp,
                    color = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "下载完成",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepBlue
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

/**
 * 更新失败对话框
 */
@Composable
fun UpdateErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "更新失败",
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
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
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

@Composable
fun HeaderSection(
    onSearchClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepBlue,
                        RoseRed
                    )
                )
            )
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Column {
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Neko云音乐",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
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
                    text = "搜索音乐",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}