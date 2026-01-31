<template>
  <div class="playlist-detail-view">
    <!-- 移动端下载横幅 -->
    <div v-if="isMobile" class="mobile-download-banner">
      <div class="banner-content">
        <span class="banner-text">📱 下载 App 获得更好的体验</span>
        <a href="/mobile-download" class="banner-link">立即下载</a>
        <button @click="closeBanner" class="banner-close">✕</button>
      </div>
    </div>
    
    <div class="playlist-detail-container">
      <div class="playlist-header">
        <button @click="goBack" class="back-btn">← 返回</button>
        <div class="playlist-info">
          <div class="playlist-cover">
            <img 
              :src="getPlaylistCover()" 
              alt="歌单封面"
              @error="handlePlaylistCoverError"
            />
          </div>
          <div class="playlist-details">
            <h1>{{ playlist?.name }}</h1>
            <p v-if="playlist?.description" class="playlist-description">{{ playlist.description }}</p>
            <p class="playlist-meta">{{ playlist?.musicCount }} 首歌曲</p>
          </div>
        </div>
        <button 
          v-if="musicList.length > 0" 
          @click="playAll" 
          class="play-all-btn"
          title="播放全部"
        >
          <svg class="play-all-icon" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5v14l11-7z"/>
          </svg>
          播放全部
        </button>
      </div>
      
      <div v-if="loading" class="loading">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="musicList.length > 0" class="music-list">
        <div 
          v-for="(music, index) in musicList" 
          :key="music.id" 
          class="music-item"
        >
          <div class="music-index">{{ index + 1 }}</div>
          <div class="music-cover" @click="playMusic(music)">
            <img 
              :src="getCoverUrl(music.id)" 
              :alt="music.title"
              @error="handleCoverError"
            />
          </div>
          <div class="music-info" @click="playMusic(music)">
            <div class="music-title">{{ music.title }}</div>
            <div class="music-artist">{{ music.artist }}</div>
          </div>
          <div class="music-duration">{{ formatDuration(music.duration) }}</div>
          <button 
            v-if="isOwner" 
            @click="removeMusic(music.id)" 
            class="remove-btn"
            title="移除"
          >
            ✕
          </button>
        </div>
      </div>
      
      <div v-else class="empty-state">
        <div class="empty-icon">🎵</div>
        <p>歌单暂无音乐</p>
      </div>
    </div>
    
    <!-- 添加音乐对话框 -->
    <div v-if="showAddMusic" class="modal-overlay" @click="closeAddMusicDialog">
      <div class="modal-content" @click.stop>
        <h3>添加音乐</h3>
        <div class="search-box">
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="搜索音乐..."
            @input="handleSearch"
          />
        </div>
        <div v-if="searchResults.length > 0" class="search-results">
          <div 
            v-for="music in searchResults" 
            :key="music.id" 
            class="search-result-item"
            @click="addMusicToPlaylist(music)"
          >
            <div class="result-cover">
              <img 
                v-if="music.coverUrl" 
                :src="music.coverUrl" 
                :alt="music.title"
              />
              <div v-else class="default-cover">🎵</div>
            </div>
            <div class="result-info">
              <div class="result-title">{{ music.title }}</div>
              <div class="result-artist">{{ music.artist }}</div>
            </div>
            <button class="add-btn-small">➕</button>
          </div>
        </div>
        <div v-else-if="searchQuery" class="no-results">
          <p>未找到相关音乐</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()
const route = useRoute()

const playlist = ref(null)
const musicList = ref([])
const loading = ref(true)
const showAddMusic = ref(false)
const searchQuery = ref('')
const searchResults = ref([])

const playlistId = computed(() => route.params.id)
const currentUser = computed(() => {
  const userStr = localStorage.getItem('user')
  return userStr ? JSON.parse(userStr) : null
})

const isOwner = computed(() => {
  if (!currentUser.value || !playlist.value) return false
  return currentUser.value.id === playlist.value.userId
})

// 移动端检测
const isMobile = computed(() => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera
  return /android|ipad|iphone|ipod/i.test(userAgent)
})

// 下载横幅显示状态
const showBanner = ref(true)

// 关闭下载横幅
const closeBanner = () => {
  showBanner.value = false
  localStorage.setItem('mobileDownloadBannerClosed', 'true')
}

const getToken = () => {
  return localStorage.getItem('userToken')
}

const fetchPlaylistDetail = async () => {
  loading.value = true
  try {
    // 先获取歌单的基本信息
    await fetchPlaylistInfo()
    
    // 然后获取歌单的音乐列表（不需要token）
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/music/${playlistId.value}`, {
      method: 'GET'
    })
    
    const data = await response.json()
    if (data.success) {
      musicList.value = data.musicList || []
      // 更新歌单的音乐数量
      if (playlist.value && data.total !== undefined) {
        playlist.value.musicCount = data.total
      }
    } else {
      toast.error(data.message || '获取歌单详情失败')
    }
  } catch (error) {
    console.error('获取歌单详情失败:', error)
    toast.error('获取歌单详情失败')
  } finally {
    loading.value = false
  }
}

const fetchPlaylistInfo = async () => {
  try {
    // 直接获取歌单详情（不需要token）
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/playlist/${playlistId.value}`, {
      method: 'GET'
    })
    
    const data = await response.json()
    if (data.success && data.playlist) {
      playlist.value = data.playlist
      console.log('歌单信息加载成功:', playlist.value)
    } else {
      console.warn('未找到歌单:', playlistId.value)
      toast.error('歌单不存在')
    }
  } catch (error) {
    console.error('获取歌单信息失败:', error)
    // 不显示错误提示，因为可能只是未登录
  }
}

const playMusic = (music) => {
  const musicData = {
    id: music.id,
    title: music.title,
    artist: music.artist,
    album: music.album || '',
    duration: music.duration || 0,
    coverUrl: music.coverPath ? `${API_CONFIG.BASE_URL}${music.coverPath}` : null,
    fileUrl: `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`
  }
  
  localStorage.setItem('currentPlayingMusic', JSON.stringify(musicData))
  
  const state = {
    isPlaying: true,
    currentTime: 0.1,
    duration: musicData.duration
  }
  localStorage.setItem('globalPlayerState', JSON.stringify(state))
  
  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: state.isPlaying,
      currentTime: state.currentTime,
      duration: state.duration,
      currentMusic: musicData
    }
  })
  window.dispatchEvent(event)
  
  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'))
  }, 10)
}

const playAll = () => {
  if (musicList.value.length === 0) {
    toast.warning('歌单为空')
    return
  }
  
  // 将整个歌单设置为播放列表
  const playlist = musicList.value.map(music => ({
    id: music.id,
    title: music.title,
    artist: music.artist,
    album: music.album || '',
    duration: music.duration || 0,
    coverUrl: music.coverPath ? `${API_CONFIG.BASE_URL}${music.coverPath}` : null,
    fileUrl: `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`
  }))
  
  localStorage.setItem('globalPlaylist', JSON.stringify(playlist))
  
  // 广播播放列表更新事件
  const playlistEvent = new CustomEvent('playlistUpdated', {
    detail: {
      playlist: playlist
    }
  })
  window.dispatchEvent(playlistEvent)
  
  // 播放第一首
  if (playlist.length > 0) {
    playMusic(musicList.value[0])
  }
  
  toast.success(`已开始播放全部 ${playlist.length} 首歌曲`)
}

const formatDuration = (seconds) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

const getPlaylistCover = () => {
  // 如果歌单有音乐，使用第一首音乐的封面
  if (musicList.value && musicList.value.length > 0) {
    return getCoverUrl(musicList.value[0].id)
  }
  // 如果没有音乐，使用用户头像
  const userId = currentUser.value ? currentUser.value.id : 'default';
  return `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`;
}

const handleCoverError = (event) => {
  console.log('封面加载失败，使用默认封面')
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`
}

const handlePlaylistCoverError = (event) => {
  console.log('歌单封面加载失败，使用默认头像')
  const userId = currentUser.value ? currentUser.value.id : 'default';
  event.target.src = `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`;
}

const showAddMusicDialog = () => {
  showAddMusic.value = true
}

const closeAddMusicDialog = () => {
  showAddMusic.value = false
  searchQuery.value = ''
  searchResults.value = []
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    return
  }
  
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: searchQuery.value.trim()
      })
    })
    
    const data = await response.json()
    if (data.success) {
      searchResults.value = data.data.results || []
    }
  } catch (error) {
    console.error('搜索失败:', error)
  }
}

const addMusicToPlaylist = async (music) => {
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/music/add`, {
      method: 'POST',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        playlistId: playlistId.value,
        musicId: music.id
      })
    })
    
    const data = await response.json()
    if (data.success) {
      toast.success('音乐添加成功')
      closeAddMusicDialog()
      await fetchPlaylistDetail()
    } else {
      toast.error(data.message || '添加音乐失败')
    }
  } catch (error) {
    console.error('添加音乐失败:', error)
    toast.error('添加音乐失败')
  }
}

const removeMusic = async (musicId) => {
  if (!confirm('确定要移除这首音乐吗？')) return
  
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/music/remove`, {
      method: 'POST',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        playlistId: playlistId.value,
        musicId: musicId
      })
    })
    
    const data = await response.json()
    if (data.success) {
      toast.success('音乐移除成功')
      await fetchPlaylistDetail()
    } else {
      toast.error(data.message || '移除音乐失败')
    }
  } catch (error) {
    console.error('移除音乐失败:', error)
    toast.error('移除音乐失败')
  }
}

const editPlaylist = () => {
  router.push(`/playlists`)
}

const confirmDelete = () => {
  if (!confirm(`确定要删除歌单"${playlist.value?.name}"吗？此操作不可恢复。`)) return
  
  router.push(`/playlists`)
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  fetchPlaylistDetail()
})
</script>

<style scoped>
.playlist-detail-view {
  min-height: calc(100vh - 80px);
  padding: 20px;
}

/* 移动端下载横幅 */
.mobile-download-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 15px 20px;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.banner-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  position: relative;
}

.banner-text {
  color: white;
  font-size: 0.95em;
  font-weight: 500;
}

.banner-link {
  background: white;
  color: #667eea;
  text-decoration: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 0.9em;
  font-weight: 600;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.banner-link:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.banner-close {
  position: absolute;
  right: 0;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.2em;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.banner-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

.playlist-detail-container {
  max-width: 1000px;
  margin: 0 auto;
}

.playlist-header {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 30px;
  margin-bottom: 30px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 4px 15px rgba(31, 38, 135, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.3);
  flex-wrap: wrap;
}

.back-btn {
  background: rgba(255, 255, 255, 0.5);
  color: #333;
  border: 1px solid rgba(255, 255, 255, 0.3);
  padding: 10px 20px;
  border-radius: 15px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  backdrop-filter: blur(5px);
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.7);
  transform: translateY(-2px);
}

.playlist-info {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
  min-width: 0;
}

.playlist-cover {
  width: 120px;
  height: 120px;
  border-radius: 15px;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.playlist-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.playlist-icon {
  font-size: 60px;
}

.playlist-details {
  flex: 1;
  min-width: 0;
}

.playlist-details h1 {
  color: #333;
  font-size: 2em;
  margin: 0 0 10px 0;
  font-weight: 600;
}

.playlist-description {
  color: #666;
  margin: 0 0 10px 0;
  line-height: 1.5;
}

.playlist-meta {
  color: #888;
  margin: 0;
  font-size: 1.1em;
}

.play-all-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 25px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.play-all-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.play-all-icon {
  width: 20px;
  height: 20px;
}

.playlist-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.add-btn,
.edit-btn,
.delete-btn {
  background: rgba(255, 255, 255, 0.5);
  color: #333;
  border: 1px solid rgba(255, 255, 255, 0.3);
  padding: 12px 24px;
  border-radius: 15px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  backdrop-filter: blur(5px);
}

.add-btn:hover,
.edit-btn:hover {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-color: transparent;
  transform: translateY(-2px);
}

.delete-btn:hover {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  border-color: transparent;
  transform: translateY(-2px);
}

.loading {
  text-align: center;
  color: #666;
  padding: 60px 0;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 4px solid rgba(102, 126, 234, 0.2);
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.music-list {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(31, 38, 135, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.music-item {
  display: flex;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.3s ease;
  cursor: pointer;
}

.music-item:last-child {
  border-bottom: none;
}

.music-item:hover {
  background: rgba(255, 255, 255, 0.4);
}

.music-index {
  color: #9ca3af;
  width: 40px;
  font-size: 1.2em;
  text-align: center;
}

.music-cover {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  overflow: hidden;
  margin-right: 20px;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.music-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-cover {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
}

.music-info {
  flex: 1;
  min-width: 0;
}

.music-title {
  color: #333;
  font-size: 1.1em;
  font-weight: 600;
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.music-artist {
  color: #666;
  font-size: 0.95em;
}

.music-duration {
  color: #9ca3af;
  margin-right: 20px;
}

.remove-btn {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.2em;
  transition: all 0.3s ease;
}

.remove-btn:hover {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  border-color: transparent;
  transform: scale(1.1);
}

.empty-state {
  text-align: center;
  color: #666;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 80px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.add-music-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 15px 40px;
  border-radius: 25px;
  font-size: 1.1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 20px;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.add-music-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(5px);
}

.modal-content {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 30px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(31, 38, 135, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.modal-content h3 {
  margin: 0 0 20px 0;
  color: #333;
  font-size: 1.5em;
}

.search-box {
  margin-bottom: 20px;
}

.search-box input {
  width: 100%;
  padding: 14px 18px;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-radius: 15px;
  font-size: 1em;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(5px);
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
}

.search-results {
  max-height: 400px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.search-result-item:hover {
  background: rgba(255, 255, 255, 0.5);
  transform: translateY(-2px);
}

.result-cover {
  width: 50px;
  height: 50px;
  border-radius: 10px;
  overflow: hidden;
  margin-right: 15px;
  flex-shrink: 0;
}

.result-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.result-info {
  flex: 1;
  min-width: 0;
}

.result-title {
  color: #333;
  font-weight: 600;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-artist {
  color: #666;
  font-size: 0.9em;
}

.add-btn-small {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.2em;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.add-btn-small:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.no-results {
  text-align: center;
  color: #666;
  padding: 40px 0;
}

@media (max-width: 768px) {
  .playlist-detail-view {
    padding-top: 70px; /* 为下载横幅留出空间 */
  }
  
  .mobile-download-banner {
    padding: 12px 15px;
  }
  
  .banner-content {
    flex-direction: column;
    gap: 10px;
  }
  
  .banner-text {
    font-size: 0.85em;
  }
  
  .banner-link {
    padding: 8px 16px;
    font-size: 0.85em;
  }
  
  .banner-close {
    position: absolute;
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
  }
  
  .playlist-header {
    flex-direction: column;
    text-align: center;
    gap: 15px;
  }
  
  .playlist-info {
    flex-direction: column;
    text-align: center;
    width: 100%;
  }
  
  .playlist-cover {
    width: 100px;
    height: 100px;
  }
  
  .playlist-details h1 {
    font-size: 1.5em;
  }
  
  .playlist-description {
    font-size: 0.9em;
  }
  
  .playlist-meta {
    font-size: 0.95em;
  }
  
  .back-btn {
    width: 100%;
    justify-content: center;
  }
  
  .play-all-btn {
    width: 100%;
    justify-content: center;
  }
  
  .music-item {
    padding: 15px;
    flex-wrap: wrap;
  }
  
  .music-index {
    width: 30px;
    font-size: 1em;
  }
  
  .music-cover {
    width: 50px;
    height: 50px;
    margin-right: 15px;
  }
  
  .music-title {
    font-size: 1em;
  }
  
  .music-artist {
    font-size: 0.85em;
  }
  
  .music-duration {
    font-size: 0.85em;
    margin-right: 10px;
  }
  
  .remove-btn {
    width: 32px;
    height: 32px;
    font-size: 1em;
  }
  
  .modal-content {
    padding: 20px;
  }
  
  .modal-content h3 {
    font-size: 1.3em;
  }
  
  .search-box input {
    padding: 12px 14px;
  }
  
  .search-result-item {
    padding: 10px;
  }
  
  .result-cover {
    width: 45px;
    height: 45px;
  }
  
  .add-btn-small {
    width: 32px;
    height: 32px;
  }
}
</style>