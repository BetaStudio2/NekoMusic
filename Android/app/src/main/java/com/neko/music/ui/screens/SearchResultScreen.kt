package com.neko.music.ui.screens

import android.util.Log
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.SearchHistoryManager
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchHistory
import com.neko.music.ui.theme.RoseRed
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.headers
import io.ktor.http.headers
import kotlinx.coroutines.launch

@Composable
fun SearchResultScreen(
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = LocalContext.current
    val historyManager = remember { SearchHistoryManager(context) }
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var searchType by remember { mutableStateOf("music") } // music 或 playlist
    var searchResults by remember { mutableStateOf<List<Music>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<com.neko.music.data.api.PlaylistInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchHistory by remember { mutableStateOf<List<SearchHistory>>(emptyList()) }
    
    val musicApi = remember { MusicApi(context) }
    val scope = rememberCoroutineScope()
    
    // 实时搜索 - 输入后立即请求
    androidx.compose.runtime.LaunchedEffect(searchQuery, searchType) {
        if (searchQuery.isNotEmpty()) {
            Log.d("SearchScreen", "实时搜索: $searchQuery, 类型: $searchType")
            isLoading = true
            if (searchType == "music") {
                performSearch(musicApi, searchQuery, scope) { results, error ->
                    searchResults = results
                    playlistResults = emptyList()
                    isLoading = false
                    errorMessage = error
                }
            } else {
                // 歌单搜索
                performPlaylistSearch(searchQuery, scope) { results, error ->
                    playlistResults = results
                    searchResults = emptyList()
                    isLoading = false
                    errorMessage = error
                }
            }
        } else {
            searchResults = emptyList()
            playlistResults = emptyList()
        }
    }
    
    // 初始查询
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchQuery = initialQuery
        }
    }
    
    // 加载搜索历史
    androidx.compose.runtime.LaunchedEffect(Unit) {
        searchHistory = historyManager.getSearchHistory()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { 
                searchQuery = it
                Log.d("SearchScreen", "输入: $it")
            },
            onSearch = {
                if (searchQuery.isNotEmpty()) {
                    Log.d("SearchScreen", "手动触发搜索: $searchQuery")
                    historyManager.saveSearch(searchQuery)
                    searchHistory = historyManager.getSearchHistory()
                    isLoading = true
                    performSearch(musicApi, searchQuery, scope) { results, error ->
                        searchResults = results
                        isLoading = false
                        errorMessage = error
                    }
                }
            },
            onBackClick = onBackClick
        )

        // 搜索类型选择
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchTypeButton(
                text = "单曲",
                isSelected = searchType == "music",
                onClick = { searchType = "music" }
            )
            SearchTypeButton(
                text = "歌单",
                isSelected = searchType == "playlist",
                onClick = { searchType = "playlist" }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RoseRed)
                    }
                }
                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "搜索失败",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
                searchResults.isEmpty() && playlistResults.isEmpty() && searchQuery.isEmpty() && searchHistory.isNotEmpty() -> {
                    SearchHistoryList(
                        history = searchHistory,
                        onItemClick = { query ->
                            searchQuery = query
                        },
                        onClearClick = {
                            historyManager.clearHistory()
                            searchHistory = emptyList()
                        }
                    )
                }
                searchResults.isEmpty() && playlistResults.isEmpty() && searchQuery.isNotEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchType == "music") "未找到相关音乐" else "未找到相关歌单",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                searchResults.isEmpty() && playlistResults.isEmpty() && searchQuery.isEmpty() && searchHistory.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "还没有搜索历史欸",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    if (searchType == "music") {
                        MusicList(
                            musics = searchResults,
                            onMusicClick = onMusicClick
                        )
                    } else {
                        PlaylistList(
                            playlists = playlistResults,
                            onPlaylistClick = { playlist ->
                                // TODO: 导航到歌单详情页面
                                Log.d("SearchScreen", "点击歌单: ${playlist.name} (ID: ${playlist.id})")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.composeWidth(4.dp))
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 0.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.composeWidth(8.dp))
                    
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = {
                            onQueryChange(it)
                            Log.d("SearchScreen", "输入: $it")
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.Black,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(RoseRed),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                Log.d("SearchScreen", "触发搜索: $query")
                                onSearch()
                            }
                        )
                    )
                    
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索音乐",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
        
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE0E0E0))
        )
    }
}

@Composable
fun PlaylistList(
    playlists: List<com.neko.music.data.api.PlaylistInfo>,
    onPlaylistClick: (com.neko.music.data.api.PlaylistInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: com.neko.music.data.api.PlaylistInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 封面
            if (!playlist.coverPath.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = if (playlist.coverPath.startsWith("/")) {
                        "https://music.cnmsb.xin${playlist.coverPath}"
                    } else {
                        playlist.coverPath
                    },
                    contentDescription = "歌单封面",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                // 默认封面
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = RoseRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "歌单",
                        tint = RoseRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 歌单信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (playlist.description != null) {
                    Text(
                        text = playlist.description,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${playlist.musicCount} 首音乐",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun MusicList(
    musics: List<Music>,
    onMusicClick: (Music) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(musics) { music ->
            MusicItem(
                music = music,
                onClick = { onMusicClick(music) }
            )
        }
    }
}

@Composable
fun MusicItem(
    music: Music,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    val scope = rememberCoroutineScope()
    var coverUrl by remember { mutableStateOf<String?>(null) }
    
    androidx.compose.runtime.LaunchedEffect(music.id) {
        scope.launch {
            coverUrl = musicApi.getMusicCoverUrl(music)
            Log.d("MusicItem", "封面URL: $coverUrl, music.coverFilePath: ${music.coverFilePath}")
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = RoseRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!coverUrl.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = coverUrl,
                    contentDescription = "封面",
                    modifier = Modifier.size(44.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = "🎵",
                    fontSize = 22.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.composeWidth(12.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "作者：${music.artist}",
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SearchHistoryList(
    history: List<SearchHistory>,
    onItemClick: (String) -> Unit,
    onClearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "搜索历史",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "搜索历史",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
            
            IconButton(
                onClick = onClearClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "清除历史",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(history) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            color = Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onItemClick(item.query) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.query,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

private fun performSearch(
    api: MusicApi,
    query: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (List<Music>, String?) -> Unit
) {
    scope.launch {
        val result = api.searchMusic(query)
        result.fold(
            onSuccess = { musics ->
                Log.d("SearchScreen", "请求成功 - 找到 ${musics.size} 条结果")
                onResult(musics, null)
            },
onFailure = { error ->
                Log.e("SearchScreen", "请求失败 - ${error.message}")
                onResult(emptyList(), error.message)
            }
        )
    }
}

suspend fun performPlaylistSearch(
    query: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (List<com.neko.music.data.api.PlaylistInfo>, String?) -> Unit
) {
    scope.launch {
        try {
            val client = io.ktor.client.HttpClient()
            val response = client.post("https://music.cnmsb.xin/api/playlists/search") {
                headers {
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                        {
                            "query": "$query"
                        }
                        """.trimIndent()
                )
            }
            
            val responseText = response.body<String>()
            Log.d("SearchScreen", "歌单搜索响应: $responseText")
            
            // 简单解析 JSON 响应
            if (responseText.contains("\"success\":true")) {
                // 提取 results 数组
                val resultsRegex = """"results":\s*\[(.*?)\]""".toRegex()
                                    val match = resultsRegex.find(responseText)
                                    if (match != null) {
                                        val resultsJson = match.groupValues[1]
                                        // 简化处理：从 JSON 中提取歌单信息
                                        val playlists = mutableListOf<com.neko.music.data.api.PlaylistInfo>()
                                        // 匹配完整的歌单信息，包括 firstMusicCover
                                        val playlistRegex = """"id":\s*(\d+),\s*"userId":\s*\d+,\s*"name":\s*"([^"]*)"(?:,\s*"description":\s*"([^"]*)")?,\s*"musicCount":\s*(\d+).*?,"firstMusicCover":\s*"([^"]*)"""".toRegex()                    
                    playlistRegex.findAll(resultsJson).forEach { matchResult ->
                        val id = matchResult.groupValues[1].toIntOrNull() ?: 0
                        val name = matchResult.groupValues[2]
                        val description = matchResult.groupValues[3].ifBlank { null }
                        val musicCount = matchResult.groupValues[4].toIntOrNull() ?: 0
                        val firstMusicCover = matchResult.groupValues[5]
                        
                        playlists.add(
                            com.neko.music.data.api.PlaylistInfo(
                                id = id,
                                name = name,
                                description = description,
                                coverPath = firstMusicCover,
                                musicCount = musicCount,
                                createdAt = "",
                                updatedAt = ""
                            )
                        )
                    }
                    
                    Log.d("SearchScreen", "搜索到 ${playlists.size} 个歌单")
                    onResult(playlists, null)
                } else {
                    onResult(emptyList(), "未找到歌单")
                }
            } else {
                onResult(emptyList(), "搜索失败")
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "歌单搜索请求失败 - ${e.message}", e)
            onResult(emptyList(), e.message)
        }
    }
}

@Composable
fun SearchTypeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .background(
                color = if (isSelected) RoseRed else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}