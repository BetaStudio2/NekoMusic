package com.neko.music

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.neko.music.ui.screens.MineScreen
import com.neko.music.ui.screens.PlayerScreen
import com.neko.music.ui.screens.PlaylistScreen
import com.neko.music.ui.screens.SearchResultScreen
import com.neko.music.ui.theme.Neko云音乐Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    
    // 启动时恢复上次播放的音乐
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            playerManager.restoreLastPlayed(context)
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
                        MineScreen()
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
                                navController.navigate("player/${music.id}/${music.title}/${music.artist}")
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
                        val title = backStackEntry.arguments?.getString("title") ?: ""
                        val artist = backStackEntry.arguments?.getString("artist") ?: ""
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
                        val title = currentMusicTitle ?: "未知歌曲"
                        val artist = currentMusicArtist ?: "未知歌手"
                        navController.navigate("player/$id/$title/$artist")
                    },
                    onPlaylistClick = {
                        showPlaylist = true
                    }
                )
                
                BottomNavigationBar(navController = navController)
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
    }
}