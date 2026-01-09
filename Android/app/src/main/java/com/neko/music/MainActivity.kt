package com.neko.music

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.Text
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.BottomNavigationBar
import com.neko.music.ui.components.BottomNavItem
import com.neko.music.ui.components.MiniPlayer
import com.neko.music.ui.components.PlaylistBottomSheet
import com.neko.music.ui.screens.HomeScreen
import com.neko.music.ui.screens.LoginScreen
import com.neko.music.ui.screens.MineScreen
import com.neko.music.ui.screens.PlayerScreen
import com.neko.music.ui.screens.PlaylistScreen
import com.neko.music.ui.screens.RecentPlayScreen
import com.neko.music.ui.screens.RegisterScreen
import com.neko.music.ui.screens.SearchResultScreen
import com.neko.music.ui.screens.FavoriteScreen
import com.neko.music.ui.theme.Neko云音乐Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val PREFS_NAME = "app_prefs"
    private val KEY_FIRST_LAUNCH = "first_launch"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查是否是首次启动
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            // 首次启动，显示开屏
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        // 启动音乐播放服务（前台服务，保持后台运行）
        MusicPlayerService.startService(this)

        setContent {
            Neko云音乐Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onBackPressed() {
        // 将返回键行为转换为 Home 键行为，让应用挂起到后台而不是退出
        moveTaskToBack(false)
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()
    
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
    val currentRoute = navBackStackEntry?.destination?.route
    
    // 检查是否在播放页面
    val isPlayerScreen = currentRoute?.startsWith("player") == true
    
    // 获取播放器状态
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentMusicUrl by playerManager.currentMusicUrl.collectAsState()
    val currentMusicTitle by playerManager.currentMusicTitle.collectAsState()
    val currentMusicArtist by playerManager.currentMusicArtist.collectAsState()
    val currentMusicCover by playerManager.currentMusicCover.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    
    // 计算播放进度
    val progress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    
    // 更新播放进度
    androidx.compose.runtime.LaunchedEffect(currentPosition, duration) {
        if (duration > 0) {
            progress.floatValue = currentPosition.toFloat() / duration.toFloat()
        }
    }
    
    // 播放列表显示状态
    var showPlaylist by androidx.compose.runtime.remember { mutableStateOf(false) }

    // 底部控件可见性状态
    var showBottomControls by androidx.compose.runtime.remember { mutableStateOf(true) }

    // 登录和注册页面显示状态
    var showLoginScreen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showRegisterScreen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showLogoutDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    
    // 登录状态，用于触发界面更新
    var isLoggedIn by androidx.compose.runtime.remember { mutableStateOf(false) }
    var currentUsername by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var currentUserId by androidx.compose.runtime.remember { mutableStateOf(-1) }
    
    // 初始化登录状态
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val tokenManager = com.neko.music.data.manager.TokenManager(context)
        isLoggedIn = tokenManager.isLoggedIn()
        currentUsername = tokenManager.getUsername()
        currentUserId = tokenManager.getUserId()

        // 初始化收藏管理器
        playerManager.initializeFavoriteManager()
    }
    
    // 跟踪是否从播放页面返回
    var returningFromPlayer by androidx.compose.runtime.remember { mutableStateOf(false) }
    
    // 启动时恢复上次播放的音乐
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            playerManager.restoreLastPlayed(context)
            // 等待音乐恢复播放后再检查收藏状态
            kotlinx.coroutines.delay(1000) // 等待1秒确保音乐信息已加载
            playerManager.checkFavoriteStatus()
        }
    }
    
    // 监听是否在播放页面，从播放页面返回时延迟显示底部控件
    androidx.compose.runtime.LaunchedEffect(isPlayerScreen) {
        if (isPlayerScreen) {
            // 进入播放页面，立即隐藏底部控件（无动画）
            showBottomControls = false
            returningFromPlayer = true
        } else if (returningFromPlayer) {
            // 从播放页面返回，延迟0.5秒后显示带动画
            kotlinx.coroutines.delay(500)
            showBottomControls = true
            returningFromPlayer = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容区域
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Home.route
                ) {
                    composable(BottomNavItem.Home.route) {
                        HomeScreen(
                            onSearchClick = {
                                Log.d("MainActivity", "导航到搜索页面")
                                navController.navigate("search")
                            }
                        )
                    }
                    composable(BottomNavItem.Mine.route) {
                        MineScreen(
                            onRecentPlayClick = {
                                navController.navigate("recent_play")
                            },
                            onLoginClick = {
                                showLoginScreen = true
                            },
                            onLogoutClick = {
                                showLogoutDialog = true
                            },
                            onFavoriteClick = {
                                navController.navigate("favorites")
                            },
                            isLoggedIn = isLoggedIn,
                            username = currentUsername,
                            userId = currentUserId,
                            onLoginSuccess = {
                                // 登录成功后更新状态
                                val tokenManager = com.neko.music.data.manager.TokenManager(context)
                                isLoggedIn = tokenManager.isLoggedIn()
                                currentUsername = tokenManager.getUsername()
                                currentUserId = tokenManager.getUserId()
                            }
                        )
                    }
                    composable("recent_play") {
                        RecentPlayScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onMusicClick = { music ->
                                val id = music.id
                                val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                                val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                                navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                            }
                        )
                    }
                    composable("favorites") {
                        FavoriteScreen(
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onMusicClick = { music ->
                                val id = music.id
                                val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                                val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                                navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                            }
                        )
                    }
                    composable(
                        route = "search?query={query}",
                        arguments = listOf(
                            navArgument("query") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val query = backStackEntry.arguments?.getString("query") ?: ""
                        Log.d("MainActivity", "搜索页面加载，查询: $query")
                        SearchResultScreen(
                            initialQuery = query,
                            onBackClick = {
                                Log.d("MainActivity", "从搜索页面返回")
                                navController.popBackStack()
                            },
                            onMusicClick = { music ->
                                Log.d("MainActivity", "点击音乐: ${music.title}")
                                val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                                val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                                navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                            }
                        )
                    }
                    composable(
                        route = "player/{id}/{title}/{artist}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.IntType },
                            navArgument("title") { type = NavType.StringType },
                            navArgument("artist") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getInt("id") ?: 0
                        val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
                        val artist = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("artist") ?: "", "UTF-8")
                        val music = Music(id, title, artist, "", 0, "", "", 0, "")
                        Log.d("MainActivity", "播放页面加载: $title")
                        PlayerScreen(
                            music = music,
                            onBackClick = {
                                Log.d("MainActivity", "从播放页面返回")
                                navController.popBackStack()
                            },
                            onPlaylistClick = {
                                showPlaylist = true
                            }
                        )
                    }
                }
            }
            
            // 只在非播放页面显示迷你播放器和底部导航栏
            if (!isPlayerScreen) {
                if (returningFromPlayer) {
                    // 从播放页面返回时使用动画
                    key(showBottomControls) {
                        AnimatedVisibility(
                            visible = showBottomControls,
                            enter = androidx.compose.animation.slideInVertically(
                                initialOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(
                                    durationMillis = 200,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                                )
                            ),
                            exit = androidx.compose.animation.fadeOut(
                                animationSpec = tween(durationMillis = 0)
                            )
                        ) {
                            androidx.compose.foundation.layout.Column {
                                MiniPlayer(
                                    isPlaying = isPlaying,
                                    songTitle = currentMusicTitle ?: "暂无播放",
                                    artist = currentMusicArtist ?: "",
                                    coverUrl = currentMusicCover,
                                    progress = progress.floatValue,
                                    onPlayPauseClick = {
                                        playerManager.togglePlayPause()
                                    },
                                    onPlayerClick = {
                                        // 跳转到播放页面，传递当前音乐ID
                                        val id = currentMusicId ?: 0
                                        val encodedTitle = java.net.URLEncoder.encode(currentMusicTitle ?: "未知歌曲", "UTF-8")
                                        val encodedArtist = java.net.URLEncoder.encode(currentMusicArtist ?: "未知歌手", "UTF-8")
                                        navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                                    },
                                    onPlaylistClick = {
                                        showPlaylist = true
                                    }
                                )
                                
                                BottomNavigationBar(navController = navController)
                            }
                        }
                    }
                } else {
                    // 其他情况直接显示
                    androidx.compose.foundation.layout.Column {
                        MiniPlayer(
                            isPlaying = isPlaying,
                            songTitle = currentMusicTitle ?: "暂无播放",
                            artist = currentMusicArtist ?: "",
                            coverUrl = currentMusicCover,
                            progress = progress.floatValue,
                            onPlayPauseClick = {
                                playerManager.togglePlayPause()
                            },
                            onPlayerClick = {
                                // 跳转到播放页面，传递当前音乐ID
                                val id = currentMusicId ?: 0
                                val encodedTitle = java.net.URLEncoder.encode(currentMusicTitle ?: "未知歌曲", "UTF-8")
                                val encodedArtist = java.net.URLEncoder.encode(currentMusicArtist ?: "未知歌手", "UTF-8")
                                navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                            },
                            onPlaylistClick = {
                                showPlaylist = true
                            }
                        )
                        
                        BottomNavigationBar(navController = navController)
                    }
                }
            }
        }
        
        // 播放列表弹窗（在所有控件之上，覆盖显示）
        PlaylistScreen(
            isVisible = showPlaylist,
            currentMusicId = currentMusicId,
            onBackClick = {
                showPlaylist = false
            },
            onMusicClick = { music ->
                // 播放选中的音乐
                scope.launch {
                    val musicApi = com.neko.music.data.api.MusicApi()
                    val url = musicApi.getMusicFileUrl(music)
                    val fullCoverUrl = if (music.coverFilePath.isNotEmpty()) {
                        "https://music.cnmsb.xin${music.coverFilePath}"
                    } else {
                        "https://music.cnmsb.xin/api/music/cover/${music.id}"
                    }
                    playerManager.playMusic(url, music.id, music.title, music.artist, music.coverFilePath, fullCoverUrl)
                }
                showPlaylist = false
            }
        )

        // 登录和注册页面（在最顶层显示）
        AnimatedVisibility(
            visible = showLoginScreen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 150)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                                    exit = slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(durationMillis = 150)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 150)),            modifier = Modifier.zIndex(Float.MAX_VALUE)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LoginScreen(
                    onLoginSuccess = {
                        showLoginScreen = false
                        // 更新登录状态
                        val tokenManager = com.neko.music.data.manager.TokenManager(context)
                        isLoggedIn = tokenManager.isLoggedIn()
                        currentUsername = tokenManager.getUsername()
                        currentUserId = tokenManager.getUserId()
                    },
                    onBackClick = {
                        showLoginScreen = false
                    },
                    onRegisterClick = {
                        showLoginScreen = false
                        showRegisterScreen = true
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showRegisterScreen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 150)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                                    exit = slideOutHorizontally(
                                        targetOffsetX = { -it },
                                        animationSpec = tween(durationMillis = 150)
                                    ) + fadeOut(animationSpec = tween(durationMillis = 150)),            modifier = Modifier.zIndex(Float.MAX_VALUE)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RegisterScreen(
                    onRegisterSuccess = {
                        showRegisterScreen = false
                    },
                    onBackClick = {
                        showRegisterScreen = false
                    },
                    onLoginClick = {
                        showRegisterScreen = false
                        showLoginScreen = true
                    }
                )
            }
        }

        // 退出登录确认对话框
        if (showLogoutDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("退出登录") },
                text = { Text("确定要退出登录吗？") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            // 清除登录状态
                            val tokenManager = com.neko.music.data.manager.TokenManager(context)
                            tokenManager.clearToken()
                            // 更新UI状态
                            isLoggedIn = false
                            currentUsername = null
                            currentUserId = -1
                            showLogoutDialog = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showLogoutDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
        }
    }
}