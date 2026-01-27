package com.neko.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(
    onBackClick: () -> Unit = {},
    userId: Int = -1,
    username: String = "",
    email: String = "",
    onAvatarUpdate: (ByteArray) -> Unit = {},
    onPasswordUpdate: (oldPassword: String, newPassword: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 头像更新时间戳，用于绕过缓存
    var avatarUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 选中的图片 URI
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
        }
    }
    
    // 显示更换头像对话框
    var showAvatarDialog by remember { mutableStateOf(false) }
    
    // 显示修改密码对话框
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // 显示加载状态
    var isLoading by remember { mutableStateOf(false) }
    
    // 显示Toast消息
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    // 显示成功提示
    var showSuccess by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "账号信息",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 头像区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(RoseRed, SakuraPink)
                            )
                        )
                        .clickable { showAvatarDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://music.cnmsb.xin/api/user/avatar/$userId?t=$avatarUpdateTime")
                            .crossfade(true)
                            .build(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // 相机图标
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📷",
                            fontSize = 32.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // 用户信息卡片
            InfoCard(
                icon = "👤",
                title = "用户名",
                value = username,
                showArrow = false
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoCard(
                icon = "📧",
                title = "邮箱",
                value = email,
                showArrow = false
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoCard(
                icon = "🔐",
                title = "密码",
                value = "••••••••",
                showArrow = true,
                onClick = { showPasswordDialog = true }
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // 提示信息
            Text(
                text = "点击头像可以更换头像",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
        
        // 更换头像对话框
        if (showAvatarDialog) {
            AlertDialog(
                onDismissRequest = { showAvatarDialog = false },
                title = { Text("更换头像") },
                text = { Text("是否要从相册选择新头像？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAvatarDialog = false
                            imagePickerLauncher.launch("image/*")
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAvatarDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
        
        // 处理图片选择结果
        LaunchedEffect(selectedImageUri) {
            selectedImageUri?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val imageData = inputStream?.readBytes()
                    inputStream?.close()
                    
                    if (imageData != null) {
                        onAvatarUpdate(imageData)
                        // 重置时间戳以刷新头像
                        avatarUpdateTime = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    // 处理错误
                }
            }
        }
        
        // 修改密码对话框
        if (showPasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onConfirm = { oldPassword, newPassword ->
                    onPasswordUpdate(oldPassword, newPassword)
                    showPasswordDialog = false
                }
            )
        }
        
        // 加载中提示
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoseRed)
            }
        }
        
        // 成功提示
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
                    .shadow(
                        elevation = 8.dp,
                        spotColor = RoseRed.copy(alpha = 0.3f)
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "成功",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = toastMessage ?: "操作成功",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            LaunchedEffect(showSuccess) {
                kotlinx.coroutines.delay(2000)
                showSuccess = false
            }
        }
    }
}

@Composable
fun InfoCard(
    icon: String,
    title: String,
    value: String,
    showArrow: Boolean = false,
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
            .shadow(
                elevation = 2.dp,
                spotColor = RoseRed.copy(alpha = 0.15f),
                ambientColor = Color.Gray.copy(alpha = 0.08f)
            )
            .scale(scale)
            .clickable(enabled = showArrow) {
                isPressed = true
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (showArrow) {
            Text(
                text = "›",
                fontSize = 20.sp,
                color = Color.Gray.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (oldPassword: String, newPassword: String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    fun validateAndConfirm() {
        when {
            oldPassword.isEmpty() -> errorMessage = "请输入原密码"
            newPassword.isEmpty() -> errorMessage = "请输入新密码"
            confirmPassword.isEmpty() -> errorMessage = "请确认新密码"
            newPassword != confirmPassword -> errorMessage = "两次输入的密码不一致"
            newPassword.length < 6 -> errorMessage = "新密码长度不能少于6位"
            else -> {
                onConfirm(oldPassword, newPassword)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 原密码
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { 
                        oldPassword = it
                        errorMessage = null
                    },
                    label = { Text("原密码") },
                    singleLine = true,
                    visualTransformation = if (showOldPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOldPassword = !showOldPassword }) {
                            Icon(
                                imageVector = if (showOldPassword) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = if (showOldPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 新密码
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { 
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("新密码") },
                    singleLine = true,
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = if (showNewPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 确认新密码
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { 
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("确认新密码") },
                    singleLine = true,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = if (showConfirmPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                )
                
                // 错误提示
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validateAndConfirm() }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}