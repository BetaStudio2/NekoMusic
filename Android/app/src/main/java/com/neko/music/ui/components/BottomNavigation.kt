package com.neko.music.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.ui.theme.*

sealed class BottomNavItem(
    val route: String,
    val title: String
) {
    object Home : BottomNavItem("home", "首页")
    object Mine : BottomNavItem("mine", "我的")
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Mine
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 计算选中项的索引
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
    
    // 水滴指示器动画
    val density = LocalDensity.current
    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { 
            (selectedIndex * (with(density) { 400.dp.toPx() } / items.size)).toDp() 
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                spotColor = RoseRed.copy(alpha = 0.25f),
                ambientColor = Color.Gray.copy(alpha = 0.12f)
            ),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            // 导航栏内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                // 导航栏文字
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items.forEach { item ->
                        val isSelected = currentRoute == item.route
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) RoseRed else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    isPlaying: Boolean = false,
    songTitle: String = "",
    artist: String = "",
    coverUrl: String? = null,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val scaleValue by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                spotColor = RoseRed.copy(alpha = 0.2f),
                ambientColor = Color.Gray.copy(alpha = 0.1f)
            ),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            SakuraPink.copy(alpha = 0.05f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        isPressed = true
                        onPlayerClick()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .scale(scaleValue)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：封面、歌曲信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 封面
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
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
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = "Cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = "🎵",
                                fontSize = 24.sp
                            )
                        }
                    }
                    
                    // 歌曲信息
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = songTitle.ifEmpty { "暂无播放" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.ifEmpty { "点击播放音乐" },
                            fontSize = 13.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // 右侧：播放/暂停按钮、播放列表
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放/暂停按钮（带圆形进度条）
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 圆形进度条
                        Canvas(
                            modifier = Modifier.size(44.dp)
                        ) {
                            val strokeWidth = 3.dp.toPx()
                            val radius = size.minDimension / 2 - strokeWidth / 2 - 2.dp.toPx()
                            val center = Offset(size.width / 2, size.height / 2)
                            
                            // 背景圆环
                            drawCircle(
                                color = Color(0xFFE0E0E0),
                                radius = radius,
                                center = center,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                            
                            // 进度圆环
                            if (progress > 0f) {
                                drawArc(
                                    color = RoseRed,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    ),
                                    size = Size(radius * 2, radius * 2),
                                    topLeft = Offset(center.x - radius, center.y - radius)
                                )
                            }
                        }
                        
                        // 播放/暂停按钮
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            RoseRed,
                                            SakuraPink
                                        )
                                    )
                                )
                                .clickable(onClick = onPlayPauseClick)
                                .shadow(
                                    elevation = 4.dp,
                                    spotColor = RoseRed.copy(alpha = 0.4f),
                                    ambientColor = RoseRed.copy(alpha = 0.2f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    // 播放列表按钮
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF5F5F5))
                            .clickable(onClick = onPlaylistClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.playlist),
                            contentDescription = "Playlist",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}