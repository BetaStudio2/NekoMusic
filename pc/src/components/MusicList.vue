<template>
  <div class="music-list">
    <div class="list-header">
      <h2>{{ title }}</h2>
      <div v-if="showSearch" class="search-box">
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="搜索音乐..." 
          @input="handleSearch"
        />
      </div>
    </div>
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>
    <div v-else-if="musicList.length === 0" class="empty">
      <p>暂无音乐</p>
    </div>
    <div v-else class="list-container">
      <div class="list-header-row">
        <span class="col-index">#</span>
        <span class="col-title">标题</span>
        <span class="col-artist">艺术家</span>
        <span class="col-album">专辑</span>
        <span class="col-duration">时长</span>
        <span class="col-actions">操作</span>
      </div>
      <div 
        v-for="(music, index) in filteredList" 
        :key="music.id"
        :class="['music-item', { playing: currentMusic?.id === music.id }]"
        @dblclick="playMusic(music)"
      >
        <span class="col-index">
          <span v-if="currentMusic?.id === music.id" class="playing-icon">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
            </svg>
          </span>
          <span v-else>{{ index + 1 }}</span>
        </span>
        <span class="col-title">
          <img :src="getCoverUrl(music.id)" alt="封面" class="cover" @error="handleCoverError" />
          <span class="title-text">{{ music.title }}</span>
        </span>
        <span class="col-artist">{{ music.artist }}</span>
        <span class="col-album">{{ music.album || '-' }}</span>
        <span class="col-duration">{{ formatDuration(music.duration) }}</span>
        <span class="col-actions">
          <button class="action-btn" @click="playMusic(music)" title="播放">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path fill="currentColor" d="M8 5v14l11-7z"/>
            </svg>
          </button>
          <button v-if="showFavorite" class="action-btn" @click="toggleFavorite(music)" title="收藏">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path :fill="isFavorite(music.id) ? '#e91e63' : 'currentColor'" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
            </svg>
          </button>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  title: {
    type: String,
    default: '音乐列表'
  },
  showSearch: {
    type: Boolean,
    default: false
  },
  showFavorite: {
    type: Boolean,
    default: false
  },
  fetchFunction: {
    type: Function,
    default: null
  }
})

const router = useRouter()
const musicList = ref([])
const loading = ref(false)
const searchQuery = ref('')
const currentMusic = ref(null)
const favorites = ref([])

const filteredList = computed(() => {
  if (!searchQuery.value) return musicList.value
  const query = searchQuery.value.toLowerCase()
  return musicList.value.filter(music => 
    music.title.toLowerCase().includes(query) ||
    music.artist.toLowerCase().includes(query) ||
    (music.album && music.album.toLowerCase().includes(query))
  )
})

const fetchMusicList = async () => {
  if (!props.fetchFunction) return
  
  loading.value = true
  try {
    const result = await props.fetchFunction()
    musicList.value = result || []
  } catch (error) {
    console.error('获取音乐列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 搜索逻辑通过 filteredList 自动处理
}

const getCoverUrl = (id) => {
  return `http://localhost:9999/api/music/cover/${id}`
}

const handleCoverError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40"><rect width="40" height="40" fill="%236a5acd"/><text x="20" y="25" font-family="Arial" font-size="16" fill="white" text-anchor="middle">M</text></svg>'
}

const formatDuration = (seconds) => {
  if (!seconds) return '-'
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const playMusic = (music) => {
  currentMusic.value = music
  localStorage.setItem('currentMusic', JSON.stringify(music))
  window.dispatchEvent(new CustomEvent('music-play', { detail: music }))
}

const isFavorite = (musicId) => {
  return favorites.value.some(f => f.id === musicId)
}

const toggleFavorite = async (music) => {
  const token = localStorage.getItem('userToken')
  if (!token) {
    alert('请先登录')
    return
  }

  try {
    if (isFavorite(music.id)) {
      await fetch(`http://localhost:9999/api/user/favorites/${music.id}`, {
        method: 'DELETE',
        headers: { 'Authorization': token }
      })
      favorites.value = favorites.value.filter(f => f.id !== music.id)
    } else {
      await fetch(`http://localhost:9999/api/user/favorites`, {
        method: 'POST',
        headers: { 
          'Authorization': token,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ musicId: music.id })
      })
      favorites.value.push(music)
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
  }
}

onMounted(() => {
  const savedMusic = localStorage.getItem('currentMusic')
  if (savedMusic) {
    try {
      currentMusic.value = JSON.parse(savedMusic)
    } catch (e) {
      console.error('解析当前音乐失败:', e)
    }
  }
  
  fetchMusicList()
})

defineExpose({
  fetchMusicList
})
</script>

<style scoped>
.music-list {
  padding: 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.list-header h2 {
  margin: 0;
  color: #333;
  font-size: 24px;
}

.search-box {
  position: relative;
}

.search-box input {
  padding: 8px 16px;
  border: 1px solid #ddd;
  border-radius: 20px;
  width: 250px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.search-box input:focus {
  border-color: #667eea;
}

.loading, .empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.list-container {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.list-header-row {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 12px 16px;
  background: #f5f5f5;
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.music-item {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.2s;
  align-items: center;
}

.music-item:hover {
  background: #f9f9f9;
}

.music-item.playing {
  background: rgba(102, 126, 234, 0.1);
}

.music-item.playing .title-text {
  color: #667eea;
  font-weight: 500;
}

.col-index {
  color: #999;
  font-size: 13px;
  text-align: center;
}

.playing-icon {
  color: #667eea;
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-title {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.cover {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.title-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: #333;
}

.col-artist, .col-album {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-duration {
  font-size: 13px;
  color: #999;
  text-align: center;
}

.col-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.2s;
}

.action-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}
</style>