<template>
  <div class="search-results-view">
    <div class="search-results-container">
      <div v-if="searchResults && searchResults.length > 0" class="results-list">
        <div 
          v-for="result in searchResults" 
          :key="result.id" 
          class="result-item"
        >
          <img 
            :src="getCoverUrl(result.id)" 
            :alt="result.title"
            class="result-cover"
            @error="handleImageError"
          />
          <div class="result-info" @click="selectResult(result)">
            <div class="result-title">{{ result.title }}</div>
            <div class="result-artist">作曲：{{ result.artist }}</div>
            <div class="result-album">专辑：{{ result.album || '未知专辑' }}</div>
          </div>
          <div class="result-actions">
            <button @click.stop="playMusic(result)" class="play-btn" title="播放">
              ▶️
            </button>
            <button @click.stop="toggleFavorite(result)" class="favorite-btn" :class="{ 'is-favorite': isFavorite(result.id) }" :title="isFavorite(result.id) ? '取消收藏' : '收藏'">
              {{ isFavorite(result.id) ? '❤️' : '🤍' }}
            </button>
            <button @click.stop="downloadMusic(result)" class="download-btn" title="下载">
              ⬇️
            </button>
          </div>
        </div>
      </div>
      <div v-else-if="searchQuery && !searchResults" class="no-results">
        <h3>正在搜索 "{{ searchQuery }}"...</h3>
      </div>
      <div v-else-if="searchQuery && searchResults && searchResults.length === 0" class="no-results">
        <h3>未找到 "{{ searchQuery }}" 的相关结果</h3>
        <p>联系我们反馈补全音乐</p>
        <a href="mailto:support@cnmsb.xin">support@cnmsb.xin</a>
      </div>
      <div v-else class="no-results">
        <h3>请输入搜索关键词</h3>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const route = useRoute()
const searchQuery = ref('')
const searchResults = ref(null)
const favoriteMusicIds = ref(new Set()) // 存储收藏的音乐ID
const router = useRouter()

// 初始化搜索查询
if (route.params.query) {
  searchQuery.value = decodeURIComponent(route.params.query)
}

// 搜索音乐
const searchMusic = async (query) => {
  if (!query.trim()) return
  
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: query
      })
    })
    
    const data = await response.json()
    
    if (response.ok) {
      if (data.success && data.results) {
        searchResults.value = data.results
        console.log('搜索成功:', data.results)
      } else {
        // 当后端返回null时
        searchResults.value = data.results || []
        console.log('未找到匹配结果:', data.message)
      }
    } else {
      console.error('搜索失败:', data.message || '未知错误')
      searchResults.value = []
    }
  } catch (error) {
    console.error('搜索请求失败:', error)
    searchResults.value = []
  }
}

// 选择结果项
const selectResult = (result) => {
  // 点击搜索结果跳转到详情页面
  router.push(`/detail/${result.id}`)
}

// 播放音乐 - 通过全局播放器播放
const playMusic = async (result) => {
  // 先获取当前播放列表，如果没有则从后端获取
  let playlist = JSON.parse(localStorage.getItem('globalPlaylist') || '[]');
  
  // 检查当前音乐是否已经在播放列表中
  const existingIndex = playlist.findIndex(item => item.id === result.id);
  if (existingIndex === -1) {
    // 如果当前音乐不在播放列表中，则添加到列表中
    playlist.push(result);
    // 保存更新后的播放列表
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist));
    
    // 立即广播播放列表更新事件，确保 GlobalPlayer 组件收到通知
    const playlistEvent = new CustomEvent('playlistUpdated', {
      detail: {
        playlist: playlist
      }
    });
    window.dispatchEvent(playlistEvent);
  }
  
  // 设置当前播放的音乐到localStorage，触发全局播放器
  localStorage.setItem('currentPlayingMusic', JSON.stringify(result));
  
  // 立即更新播放状态为播放，并清零时间（从0.1开始）
  const state = {
    isPlaying: true,
    currentTime: 0.1,  // 确保进度条从0.1开始
    duration: result.duration || 0
  };
  localStorage.setItem('globalPlayerState', JSON.stringify(state));
  
  // 立即广播播放状态变化
  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: state.isPlaying,
      currentTime: state.currentTime,
      duration: state.duration,
      currentMusic: result
    }
  });
  window.dispatchEvent(event);
  
  // 立即触发强制播放
  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'));
  }, 10);
  
  // 再次确保播放器状态同步
  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'));
  }, 100);
}

// 下载音乐
const downloadMusic = async (result) => {
  try {
    // 使用fetch API获取音乐文件
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/file/${result.id}`);
    const blob = await response.blob();

    // 从 Content-Type 响应头中提取正确的文件扩展名
    const contentType = response.headers.get('Content-Type') || 'audio/mpeg';
    const extension = mapContentTypeToExtension(contentType);

    // 创建下载链接
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = result.filename || `${result.title}.${extension}`;

    // 添加到DOM，点击并移除
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // 释放URL对象
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('下载音乐失败:', error);

    // 如果fetch方法失败，回退到直接链接方法
    const link = document.createElement('a');
    link.href = `${API_CONFIG.BASE_URL}/api/music/file/${result.id}`;
    // 回退时尝试使用 fileFormat，如果没有则默认 mp3
    const extension = result.fileFormat || 'mp3';
    link.download = result.filename || `${result.title}.${extension}`;
    link.target = '_blank'; // 在新标签页中打开，而不是当前页面
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  }
}

// 将 Content-Type 映射到文件扩展名
const mapContentTypeToExtension = (contentType) => {
  const type = contentType.toLowerCase();
  if (type.includes('flac')) return 'flac';
  if (type.includes('wav')) return 'wav';
  if (type.includes('ogg')) return 'ogg';
  if (type.includes('aac')) return 'aac';
  if (type.includes('m4a') || type.includes('mp4')) return 'm4a';
  if (type.includes('wma')) return 'wma';
  if (type.includes('ape')) return 'ape';
  if (type.includes('mpeg') || type.includes('mp3')) return 'mp3';
  console.warn('未知的 Content-Type:', contentType, '使用 mp3');
  return 'mp3';
}

// 获取用户token
const getToken = () => {
  return localStorage.getItem('userToken');
}

// 检查用户是否登录
const isLoggedIn = () => {
  return !!getToken();
}

// 检查音乐是否已收藏
const isFavorite = (musicId) => {
  return favoriteMusicIds.value.has(musicId);
}

// 切换收藏状态
const toggleFavorite = async (result) => {
  if (!isLoggedIn()) {
    toast.error('请先登录');
    return;
  }
  
  const token = getToken();
  
  if (isFavorite(result.id)) {
    // 取消收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${result.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': token
        }
      });
      
      const data = await response.json();
      if (data.success) {
        favoriteMusicIds.value.delete(result.id);
        toast.success('取消收藏成功');
      } else {
        console.error('取消收藏失败:', data.message);
        toast.error('取消收藏失败: ' + data.message);
      }
    } catch (error) {
      console.error('取消收藏失败:', error);
      toast.error('取消收藏失败');
    }
  } else {
    // 添加收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
        method: 'POST',
        headers: {
          'Authorization': token,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ musicId: result.id })
      });
      
      const data = await response.json();
      if (data.success) {
        favoriteMusicIds.value.add(result.id);
        toast.success('收藏成功');
      } else {
        console.error('收藏失败:', data.message);
        toast.error('收藏失败: ' + data.message);
      }
    } catch (error) {
      console.error('收藏失败:', error);
      toast.error('收藏失败');
    }
  }
}

// 获取收藏列表
const fetchFavorites = async () => {
  if (!isLoggedIn()) {
    return;
  }
  
  try {
    const token = getToken();
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    });
    
    const data = await response.json();
    if (data.success) {
      // 提取所有收藏的音乐ID
      favoriteMusicIds.value = new Set(data.favorites.map(m => m.id));
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error);
  }
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  // 返回新的API端点，通过音乐ID获取封面
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  // 如果图片加载失败，使用后端API的默认图标
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`;
}

// 监听路由参数变化
watch(
  () => route.params.query,
  (newQuery) => {
    if (newQuery) {
      searchQuery.value = decodeURIComponent(newQuery)
      searchMusic(searchQuery.value)
    } else {
      searchResults.value = null
    }
  }
)

onMounted(async () => {
  if (searchQuery.value && searchQuery.value !== 'undefined') {
    await searchMusic(searchQuery.value)
  }
  // 获取收藏列表
  await fetchFavorites()
})
</script>

<style scoped>
.search-results-container {
  max-width: 800px;
  margin: 20px auto;
  padding: 20px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-item {
  padding: 15px 20px;
  cursor: pointer;
  transition: background-color 0.2s, transform 0.2s;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  display: flex;
  align-items: center;
  gap: 15px;
}

.result-item:hover {
  background-color: rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 0 10px rgba(106, 90, 205, 0.3);
}

.result-cover {
  width: 50px;
  height: 50px;
  object-fit: cover;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}

.result-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-grow: 1;
  gap: 2px;
}

.result-title {
  font-weight: bold;
  color: #5c4b7b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 1rem;
}

.result-artist {
  color: #9370db;
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-album {
  color: #a0a0a0;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  align-items: center;
}

.play-btn, .download-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-size: 0.8rem;
}

.play-btn:hover, .download-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 0 8px rgba(106, 90, 205, 0.6);
}

.favorite-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, rgba(255, 105, 180, 0.8), rgba(255, 20, 147, 0.8));
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  font-size: 0.8rem;
}

.favorite-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 0 8px rgba(255, 105, 180, 0.6);
}

.favorite-btn.is-favorite {
  background: linear-gradient(135deg, rgba(255, 69, 0, 0.8), rgba(220, 20, 60, 0.8));
}

.no-results {
  text-align: center;
  padding: 40px;
  color: #887bb0;
}

.no-results h3 {
  font-size: 1.5rem;
  color: #6a5acd;
}
</style>