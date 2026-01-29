<template>
  <div class="playlist-detail-view">
    <div class="playlist-detail-container">
      <div class="playlist-header">
        <button @click="goBack" class="back-btn">← 返回</button>
        <div class="playlist-info">
          <div class="playlist-cover">
            <span class="playlist-icon">🎵</span>
          </div>
          <div class="playlist-details">
            <h1>{{ playlist?.name }}</h1>
            <p v-if="playlist?.description" class="playlist-description">{{ playlist.description }}</p>
            <p class="playlist-meta">{{ playlist?.musicCount }} 首歌曲</p>
            <!-- 调试信息 -->
            <p v-if="!playlist?.description" class="debug-info" style="color: #999; font-size: 0.8em;">(无描述)</p>
          </div>
        </div>
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
              v-if="music.coverPath" 
              :src="API_CONFIG.BASE_URL + music.coverPath" 
              :alt="music.title"
            />
            <div v-else class="default-cover">🎵</div>
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

const getToken = () => {
  return localStorage.getItem('userToken')
}

const fetchPlaylistDetail = async () => {
  loading.value = true
  try {
    // 先获取歌单的基本信息
    await fetchPlaylistInfo()
    
    // 然后获取歌单的音乐列表
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/music/${playlistId.value}`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
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
    const token = getToken()
    // 从所有歌单中找到当前歌单
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlists`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success) {
      const allPlaylists = data.playlists || []
      const currentPlaylist = allPlaylists.find(p => p.id === parseInt(playlistId.value))
      if (currentPlaylist) {
        playlist.value = currentPlaylist
        console.log('歌单信息加载成功:', playlist.value)
      } else {
        console.warn('未找到歌单:', playlistId.value)
        toast.error('歌单不存在')
      }
    } else {
      console.error('获取歌单列表失败:', data.message)
    }
  } catch (error) {
    console.error('获取歌单信息失败:', error)
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

const formatDuration = (seconds) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
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
  padding: 40px 20px;
}

.playlist-detail-container {
  max-width: 1000px;
  margin: 0 auto;
}

.playlist-header {
  background: white;
  border-radius: 20px;
  padding: 30px;
  margin-bottom: 30px;
  display: flex;
  align-items: center;
  gap: 30px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.back-btn {
  background: #f3f4f6;
  color: #666;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.back-btn:hover {
  background: #e5e7eb;
}

.playlist-info {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
}

.playlist-cover {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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

.playlist-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.add-btn,
.edit-btn,
.delete-btn {
  background: #f3f4f6;
  color: #666;
  border: none;
  padding: 12px 24px;
  border-radius: 12px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.add-btn:hover,
.edit-btn:hover {
  background: #667eea;
  color: white;
}

.delete-btn:hover {
  background: #ef4444;
  color: white;
}

.loading {
  text-align: center;
  color: #666;
  padding: 60px 0;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 4px solid #e5e7eb;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.music-list {
  background: white;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.music-item {
  display: flex;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #f3f4f6;
  transition: all 0.3s ease;
  cursor: pointer;
}

.music-item:last-child {
  border-bottom: none;
}

.music-item:hover {
  background: #f9fafb;
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
  background: #fef2f2;
  color: #ef4444;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.2em;
  transition: all 0.3s ease;
}

.remove-btn:hover {
  background: #ef4444;
  color: white;
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
  background: #667eea;
  color: white;
  border: none;
  padding: 15px 40px;
  border-radius: 25px;
  font-size: 1.1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 20px;
}

.add-music-btn:hover {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(102, 126, 234, 0.3);
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
}

.modal-content {
  background: white;
  border-radius: 20px;
  padding: 30px;
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
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
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  font-size: 1em;
  box-sizing: border-box;
}

.search-box input:focus {
  outline: none;
  border-color: #667eea;
}

.search-results {
  max-height: 400px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 10px;
}

.search-result-item:hover {
  background: #f3f4f6;
}

.result-cover {
  width: 50px;
  height: 50px;
  border-radius: 8px;
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
  background: #667eea;
  color: white;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.2em;
  transition: all 0.3s ease;
}

.add-btn-small:hover {
  background: #5568d3;
  transform: scale(1.1);
}

.no-results {
  text-align: center;
  color: #666;
  padding: 40px 0;
}

@media (max-width: 768px) {
  .playlist-header {
    flex-direction: column;
    text-align: center;
  }
  
  .playlist-info {
    flex-direction: column;
    text-align: center;
  }
  
  .playlist-actions {
    flex-direction: row;
    width: 100%;
  }
  
  .add-btn,
  .edit-btn,
  .delete-btn {
    flex: 1;
  }
}
</style>