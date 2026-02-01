<template>
  <div class="player-view">
    <!-- 关闭按钮 -->
    <button class="close-btn" @click="closePlayer">
      <svg viewBox="0 0 24 24" width="24" height="24">
        <path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
      </svg>
    </button>

    <div v-if="currentMusic" class="player-content">
      <!-- 封面区域 -->
      <div class="cover-section">
        <div class="cover-container" :class="{ rotating: isPlaying }">
          <img :src="getCoverUrl(currentMusic.id)" alt="封面" class="album-cover" @error="handleCoverError" />
          <div class="cover-center"></div>
        </div>
      </div>

      <!-- 歌曲信息 -->
      <div class="music-info">
        <h1 class="music-title">{{ currentMusic.title }}</h1>
        <p class="music-artist">{{ currentMusic.artist }}</p>
        <p class="music-album">{{ currentMusic.album || '-' }}</p>
      </div>

      <!-- 歌词区域 -->
      <div class="lyrics-section">
        <div class="lyrics-container" ref="lyricsContainer">
          <div v-if="lyrics && lyrics.length > 0" class="lyrics-content">
            <p 
              v-for="(line, index) in lyrics" 
              :key="index"
              :class="['lyric-line', { active: currentLyricIndex === index }]"
              @click="seekToLyric(line.time)"
            >
              {{ line.text }}
            </p>
          </div>
          <div v-else class="no-lyrics">
            <svg viewBox="0 0 24 24" width="48" height="48">
              <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
            <p>暂无歌词</p>
          </div>
        </div>
      </div>

      <!-- 播放控制 -->
      <div class="player-controls">
        <!-- 进度条 -->
        <div class="progress-section">
          <span class="time">{{ formatTime(currentTime) }}</span>
          <div class="progress-bar" @click="seekTo">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
            <div class="progress-thumb" :style="{ left: progress + '%' }"></div>
          </div>
          <span class="time">{{ formatTime(duration) }}</span>
        </div>

        <!-- 控制按钮 -->
        <div class="control-buttons">
          <button class="control-btn" @click="togglePlayMode" :title="playModeTitle">
            <svg v-if="playMode === 'list'" viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/>
            </svg>
            <svg v-else-if="playMode === 'single'" viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/>
            </svg>
          </button>
          <button class="control-btn" @click="previous" title="上一首">
            <svg viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
            </svg>
          </button>
          <button class="control-btn play-btn" @click="togglePlay">
            <svg v-if="!isPlaying" viewBox="0 0 24 24" width="32" height="32">
              <path fill="currentColor" d="M8 5v14l11-7z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="32" height="32">
              <path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
            </svg>
          </button>
          <button class="control-btn" @click="next" title="下一首">
            <svg viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
            </svg>
          </button>
          <button class="control-btn favorite-btn" @click="toggleFavorite" :class="{ active: isFavorite }" title="收藏">
            <svg viewBox="0 0 24 24" width="24" height="24">
              <path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <div v-else class="no-music">
      <svg viewBox="0 0 24 24" width="64" height="64">
        <path fill="currentColor" d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
      </svg>
      <p>暂无播放音乐</p>
      <button class="btn-back" @click="closePlayer">返回</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import apiConfig from '../config/apiConfig'

const router = useRouter()
const currentMusic = ref(null)
const isPlaying = ref(false)
const playMode = ref('list')
const currentTime = ref(0)
const duration = ref(0)
const lyrics = ref([])
const currentLyricIndex = ref(0)
const audioElement = ref(null)
const lyricsContainer = ref(null)

const isFavorite = ref(false)
const favorites = ref([])

const playModeTitle = computed(() => {
  const titles = {
    'list': '列表循环',
    'single': '单曲循环',
    'shuffle': '随机播放'
  }
  return titles[playMode.value] || '列表循环'
})

const progress = computed(() => {
  if (duration.value === 0) return 0
  return (currentTime.value / duration.value) * 100
})

const getCoverUrl = (id) => {
  return `https://music.cnmsb.xin/api/music/cover/${id}`
}

const formatTime = (seconds) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const closePlayer = () => {
  router.back()
}

const togglePlay = () => {
  if (!audioElement.value || !currentMusic.value) return
  
  if (isPlaying.value) {
    audioElement.value.pause()
    isPlaying.value = false
  } else {
    audioElement.value.play()
    isPlaying.value = true
  }
}

const previous = () => {
  window.dispatchEvent(new CustomEvent('tray-previous'))
}

const next = () => {
  window.dispatchEvent(new CustomEvent('tray-next'))
}

const togglePlayMode = () => {
  const modes = ['list', 'single', 'shuffle']
  const currentIndex = modes.indexOf(playMode.value)
  const nextIndex = (currentIndex + 1) % modes.length
  playMode.value = modes[nextIndex]
  window.dispatchEvent(new CustomEvent('tray-set-play-mode', { detail: playMode.value }))
}

const toggleFavorite = async () => {
  if (!currentMusic.value) return
  
  const token = localStorage.getItem('token')
  if (!token) {
    window.dispatchEvent(new CustomEvent('show-toast', { 
      detail: { message: '请先登录', type: 'error' } 
    }))
    return
  }
  
  try {
    if (isFavorite.value) {
      const response = await fetch(`${apiConfig.BASE_URL}${apiConfig.USER_FAVORITES_DELETE(currentMusic.value.id)}`, {
        method: 'DELETE',
        headers: { 'Authorization': token }
      })
      
      if (response.ok) {
        favorites.value = favorites.value.filter(f => f.id !== currentMusic.value.id)
        isFavorite.value = false
        window.dispatchEvent(new CustomEvent('show-toast', { 
          detail: { message: '已取消收藏', type: 'success' } 
        }))
      }
    } else {
      const response = await fetch(`${apiConfig.BASE_URL}${apiConfig.USER_FAVORITES}`, {
        method: 'POST',
        headers: { 
          'Authorization': token,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ musicId: currentMusic.value.id })
      })
      
      if (response.ok) {
        favorites.value.push(currentMusic.value)
        isFavorite.value = true
        window.dispatchEvent(new CustomEvent('show-toast', { 
          detail: { message: '收藏成功', type: 'success' } 
        }))
      }
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
  }
}

const seekTo = (event) => {
  if (!audioElement.value || !duration.value) return
  
  const rect = event.currentTarget.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percentage = Math.max(0, Math.min(1, x / rect.width))
  audioElement.value.currentTime = percentage * duration.value
}

const seekToLyric = (time) => {
  if (!audioElement.value) return
  audioElement.value.currentTime = time
}

const parseLyrics = (lyricsText) => {
  if (!lyricsText) return []
  
  const lines = lyricsText.split('\n')
  const parsed = []
  
  for (const line of lines) {
    const match = line.match(/\[(\d+):(\d+)\.(\d+)\](.*)/)
    if (match) {
      const minutes = parseInt(match[1])
      const seconds = parseInt(match[2])
      const milliseconds = parseInt(match[3])
      const time = minutes * 60 + seconds + milliseconds / 1000
      const text = match[4].trim()
      
      if (text) {
        parsed.push({ time, text })
      }
    }
  }
  
  return parsed
}

const loadLyrics = async () => {
  if (!currentMusic.value) return
  
  try {
    const response = await fetch(`${apiConfig.BASE_URL}${apiConfig.MUSIC_LYRICS(currentMusic.value.id)}`)
    const result = await response.json()
    
    if (result.success && result.lyrics) {
      lyrics.value = parseLyrics(result.lyrics)
    }
  } catch (error) {
    console.error('加载歌词失败:', error)
  }
}

const checkFavoriteStatus = () => {
  if (!currentMusic.value) return
  isFavorite.value = favorites.value.some(f => f.id === currentMusic.value.id)
}

const loadFavorites = async () => {
  const token = localStorage.getItem('token')
  if (!token) return
  
  try {
    const response = await fetch(`${apiConfig.BASE_URL}${apiConfig.USER_FAVORITES}`, {
      headers: { 'Authorization': token }
    })
    
    if (response.ok) {
      const result = await response.json()
      if (result.success && result.favorites) {
        favorites.value = result.favorites
        checkFavoriteStatus()
      }
    }
  } catch (error) {
    console.error('加载收藏列表失败:', error)
  }
}

const handleTimeUpdate = () => {
  if (!audioElement.value) return
  
  currentTime.value = audioElement.value.currentTime
  
  // 更新歌词高亮
  if (lyrics.value.length > 0) {
    for (let i = lyrics.value.length - 1; i >= 0; i--) {
      if (currentTime.value >= lyrics.value[i].time) {
        currentLyricIndex.value = i
        
        // 滚动歌词到可视区域
        if (lyricsContainer.value) {
          const lyricLines = lyricsContainer.value.querySelectorAll('.lyric-line')
          if (lyricLines[i]) {
            lyricLines[i].scrollIntoView({ behavior: 'smooth', block: 'center' })
          }
        }
        break
      }
    }
  }
}

const handleCoverError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="300" height="300" fill="url(%23grad)" rx="20"/><text x="150" y="160" font-family="Arial" font-size="24" fill="white" text-anchor="middle" font-weight="bold">M</text></svg>'
}

// 监听播放状态变化
const handlePlayerStateChange = () => {
  const savedMusic = localStorage.getItem('currentMusic')
  if (savedMusic) {
    try {
      currentMusic.value = JSON.parse(savedMusic)
      loadLyrics()
      checkFavoriteStatus()
    } catch (e) {
      console.error('解析当前音乐失败:', e)
    }
  }
}

// 监听播放状态
const handlePlayStateChange = (event) => {
  isPlaying.value = event.detail?.isPlaying || false
  if (event.detail?.currentTime !== undefined) {
    currentTime.value = event.detail.currentTime
  }
  if (event.detail?.duration !== undefined) {
    duration.value = event.detail.duration
  }
}

onMounted(() => {
  handlePlayerStateChange()
  loadFavorites()
  
  // 监听播放器状态变化
  window.addEventListener('player-state-change', handlePlayStateChange)
  window.addEventListener('music-play', handlePlayerStateChange)
  
  // 获取当前播放状态
  window.dispatchEvent(new CustomEvent('get-player-state'))
})

onUnmounted(() => {
  window.removeEventListener('player-state-change', handlePlayStateChange)
  window.removeEventListener('music-play', handlePlayerStateChange)
})

// 监听路由变化，重新加载数据
watch(() => router.currentRoute.value, () => {
  handlePlayerStateChange()
})
</script>

<style scoped>
.player-view {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.close-btn {
  position: fixed;
  top: 20px;
  right: 120px;
  width: 48px;
  height: 48px;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.2s ease;
  z-index: 1001;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.player-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  width: 100%;
  max-width: 1400px;
  padding: 80px 120px 40px 120px;
  height: 100%;
}

.cover-section {
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-container {
  position: relative;
  width: 400px;
  height: 400px;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  transition: all 0.3s ease;
}

.cover-container.rotating {
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.album-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 50%;
  border: 4px solid rgba(255, 255, 255, 0.1);
}

.info-section {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.music-info {
  text-align: center;
  margin-bottom: 40px;
}

.music-title {
  font-size: 48px;
  font-weight: 700;
  color: white;
  margin: 0 0 16px 0;
  line-height: 1.3;
}

.music-artist {
  font-size: 24px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0 0 8px 0;
}

.music-album {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.lyrics-section {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  margin-bottom: 40px;
}

.lyrics-container {
  width: 100%;
  max-height: 300px;
  overflow-y: auto;
  text-align: center;
  padding: 20px 0;
  scroll-behavior: smooth;
}

.lyrics-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.lyric-line {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.4);
  transition: all 0.3s ease;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  line-height: 1.6;
}

.lyric-line:hover {
  color: rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.05);
}

.lyric-line.active {
  font-size: 28px;
  font-weight: 600;
  color: white;
  text-shadow: 0 0 20px rgba(102, 126, 234, 0.6);
}

.no-lyrics {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 18px;
  height: 200px;
}

.player-controls {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.progress-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.time {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  min-width: 50px;
  text-align: center;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s ease;
}

.progress-bar:hover {
  height: 8px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  position: relative;
  transition: width 0.1s linear;
}

.progress-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transition: all 0.2s ease;
}

.progress-bar:hover .progress-thumb {
  width: 18px;
  height: 18px;
}

.control-buttons {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
}

.control-btn {
  width: 48px;
  height: 48px;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.2s ease;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.1);
}

.play-btn {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.favorite-btn.active {
  color: #ff4545;
  background: rgba(255, 69, 69, 0.2);
}

.no-music {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  color: rgba(255, 255, 255, 0.6);
}

.btn-back {
  padding: 12px 32px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 24px;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-back:hover {
  background: rgba(255, 255, 255, 0.2);
}

/* 滚动条样式 */
.lyrics-container::-webkit-scrollbar {
  width: 6px;
}

.lyrics-container::-webkit-scrollbar-track {
  background: transparent;
}

.lyrics-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.lyrics-container::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>