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
                    isLoading = false
                    errorMessage = error
                }
            } else {
                // 歌单搜索（暂未实现）
                isLoading = false
                errorMessage = "歌单搜索功能暂未完善"
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
                searchResults.isEmpty() && searchQuery.isEmpty() && searchHistory.isNotEmpty() -> {
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
                searchResults.isEmpty() && searchQuery.isNotEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到相关音乐",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                searchResults.isEmpty() && searchQuery.isEmpty() && searchHistory.isEmpty() -> {
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
                    MusicList(
                        musics = searchResults,
                        onMusicClick = onMusicClick
                    )
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