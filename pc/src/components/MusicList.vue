<template>
  <div class="music-list">
    <div class="list-header">
      <h2 class="section-title">{{ title }}</h2>
      <div v-if="showSearch" class="search-box">
        <svg class="search-icon" viewBox="0 0 20 20">
          <path fill="currentColor" d="M8 3a5 5 0 100 10A5 5 0 008 3zM0 8a8 8 0 1114.32 4.906l5.387 5.387a1 1 0 01-1.414 1.414l-5.387-5.387A8 8 0 010 8z"/>
        </svg>
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
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" width="64" height="64">
          <path fill="currentColor" d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
        </svg>
      </div>
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
      <div class="music-items">
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
            <div class="cover-wrapper">
              <img :src="getCoverUrl(music.id)" alt="封面" class="cover" @error="handleCoverError" />
              <div class="cover-overlay">
                <svg class="play-overlay-icon" viewBox="0 0 24 24" width="24" height="24">
                  <path fill="currentColor" d="M8 5v14l11-7z"/>
                </svg>
              </div>
            </div>
            <span class="title-text">{{ music.title }}</span>
          </span>
          <span class="col-artist">{{ music.artist }}</span>
          <span class="col-album">{{ music.album || '-' }}</span>
          <span class="col-duration">{{ formatDuration(music.duration) }}</span>
          <span class="col-actions">
            <button class="action-btn" @click.stop="playMusic(music)" title="播放">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path fill="currentColor" d="M8 5v14l11-7z"/>
              </svg>
            </button>
            <button v-if="showFavorite" class="action-btn" @click.stop="toggleFavorite(music)" title="收藏">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path :fill="isFavorite(music.id) ? '#e91e63' : 'currentColor'" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
              </svg>
            </button>
          </span>
        </div>
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
  return `https://music.cnmsb.xin/api/music/cover/${id}`
}

const handleCoverError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="40" height="40" fill="url(%23grad)" rx="8"/><text x="20" y="26" font-family="Arial" font-size="16" fill="white" text-anchor="middle" font-weight="bold">M</text></svg>'
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
      await fetch(`https://music.cnmsb.xin/api/user/favorites/${music.id}`, {
        method: 'DELETE',
        headers: { 'Authorization': token }
      })
      favorites.value = favorites.value.filter(f => f.id !== music.id)
    } else {
      await fetch(`https://music.cnmsb.xin/api/user/favorites`, {
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
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: white;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-sm);
  border: 2px solid transparent;
  transition: all var(--transition-normal);
  width: 280px;
}

.search-box:focus-within {
  border-color: var(--primary);
  box-shadow: var(--shadow-md);
}

.search-icon {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.search-box input::placeholder {
  color: var(--text-muted);
}

.loading, .empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(102, 126, 234, 0.1);
  border-top: 4px solid var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: var(--primary);
}

.list-container {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.list-header-row {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
  border-bottom: 1px solid var(--border-light);
}

.music-items {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.music-item {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  transition: all var(--transition-normal);
  align-items: center;
  position: relative;
}

.music-item:last-child {
  border-bottom: none;
}

.music-item:hover {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.03) 0%, rgba(118, 75, 162, 0.03) 100%);
}

.music-item:hover .cover-wrapper .cover-overlay {
  opacity: 1;
}

.music-item.playing {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
}

.music-item.playing .title-text {
  color: var(--primary);
  font-weight: 600;
}

.music-item.playing .cover {
  box-shadow: 0 0 20px rgba(102, 126, 234, 0.3);
}

.col-index {
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
}

.playing-icon {
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-title {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.cover-wrapper {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: all var(--transition-normal);
}

.cover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all var(--transition-fast);
}

.play-overlay-icon {
  color: white;
  transform: scale(0.8);
  transition: all var(--transition-fast);
}

.cover-wrapper:hover .play-overlay-icon {
  transform: scale(1);
}

.title-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.col-artist, .col-album {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-duration {
  font-size: 13px;
  color: var(--text-muted);
  text-align: center;
  font-weight: 500;
}

.col-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all var(--transition-normal);
}

.action-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary);
  transform: scale(1.1);
}

/* 滚动条样式 */
.music-items::-webkit-scrollbar {
  width: 6px;
}

.music-items::-webkit-scrollbar-track {
  background: transparent;
}

.music-items::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.music-items::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}
</style>