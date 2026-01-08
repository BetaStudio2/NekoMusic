package com.neko.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.neko.music.ui.theme.DeepBlue
import com.neko.music.ui.theme.RoseRed

@Composable
fun MineScreen(
    onRecentPlayClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    username: String? = null,
    userId: Int = -1,
    onLoginSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        MineHeader(
            onLoginClick = onLoginClick,
            isLoggedIn = isLoggedIn,
            username = username,
            userId = userId,
            onLogoutClick = onLogoutClick
        )
        Spacer(modifier = Modifier.height(20.dp))
        MineContent(
            onRecentPlayClick = onRecentPlayClick,
            isLoggedIn = isLoggedIn,
            onLogoutClick = onLogoutClick
        )
    }
}

@Composable
fun MineHeader(
    onLoginClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    username: String? = null,
    userId: Int = -1,
    onLogoutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(
                        enabled = !isLoggedIn,
                        onClick = onLoginClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn && userId != -1) {
                    // 显示用户头像
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://music.cnmsb.xin/api/user/avatar/$userId")
                            .crossfade(true)
                            .build(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 显示默认头像
                    Text(
                        text = "🐱",
                        fontSize = 36.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isLoggedIn && username != null) username else "Neko用户",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun MineContent(
    onRecentPlayClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    onLogoutClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        MineStats()
        
        Spacer(modifier = Modifier.height(20.dp))
        
        MineMenu(
            onRecentPlayClick = onRecentPlayClick,
            isLoggedIn = isLoggedIn,
            onLogoutClick = onLogoutClick
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 底部版权信息
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "蜀ICP备2025177767号-1 如有侵权请联系support@cnmsb.xin",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Text(
                text = "© 2025-2026 Fantasy Network「梦幻网络」 保留所有权利.",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MineStats() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = RoseRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("0", "动态")
        StatItem("0", "关注")
        StatItem("0", "粉丝")
    }
}

@Composable
fun StatItem(count: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun MineMenu(onRecentPlayClick: () -> Unit = {}, isLoggedIn: Boolean = false, onLogoutClick: () -> Unit = {}) {
    Column {
        MenuItem("我的音乐", "🎵")
        MenuItem("我的收藏", "❤️")
        MenuItem("最近播放", "🕐", onClick = onRecentPlayClick)
        if (isLoggedIn) {
            MenuItem("退出登录", "🚪", onClick = onLogoutClick)
        }
    }
}

@Composable
fun MenuItem(title: String, icon: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = ">",
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}