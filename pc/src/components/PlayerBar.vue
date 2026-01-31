<template>
  <div class="player-bar glass">
    <div class="player-info">
      <div class="player-cover-wrapper">
        <img :src="currentCover" alt="封面" class="player-cover" @error="handleCoverError" />
        <div class="cover-wave" v-if="isPlaying">
          <span></span><span></span><span></span><span></span>
        </div>
      </div>
      <div class="player-details">
        <span class="player-title">{{ currentMusic?.title || '未播放' }}</span>
        <span class="player-artist">{{ currentMusic?.artist || '-' }}</span>
      </div>
    </div>
    
    <div class="player-controls-main">
      <div class="control-buttons">
        <button class="control-btn" @click="togglePlayMode" :title="playModeTitle" :class="{ active: playMode !== 'off' }">
          <img v-if="playMode === 'list'" src="/icon-list-loop.png" alt="列表循环" width="18" height="18" />
          <img v-else-if="playMode === 'single'" src="/icon-single-loop.png" alt="单曲循环" width="18" height="18" />
          <img v-else src="/icon-shuffle.png" alt="随机播放" width="18" height="18" />
        </button>
        <button class="control-btn" @click="previous" title="上一首">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
          </svg>
        </button>
        <button class="control-btn play-btn" @click="togglePlay" :title="isPlaying ? '暂停' : '播放'">
          <svg v-if="!isPlaying" viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M8 5v14l11-7z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
          </svg>
        </button>
        <button class="control-btn" @click="next" title="下一首">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
          </svg>
        </button>
      </div>
      
      <div class="player-progress">
        <span class="time">{{ formatTime(currentTime) }}</span>
        <div class="progress-bar" 
             @mousedown="handleProgressMouseDown" 
             @mousemove="handleProgressMouseMove" 
             @mouseup="handleProgressMouseUp"
             @mouseleave="handleProgressLeave"
             @click="seekTo">
          <div class="progress-fill" :style="{ width: progress + '%' }">
            <div class="progress-glow"></div>
          </div>
          <div class="progress-thumb" :style="{ left: progress + '%' }">
            <div class="thumb-glow"></div>
          </div>
        </div>
        <span class="time">{{ formatTime(duration) }}</span>
      </div>
    </div>
    
    <div class="player-controls-right">
      <div class="volume-wrapper">
        <button class="control-btn" @click="toggleMute" :title="isMuted ? '取消静音' : '静音'">
          <svg v-if="!isMuted && volume > 50" viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
          </svg>
          <svg v-else-if="!isMuted && volume > 0" viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="18" height="18">
            <path fill="currentColor" d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/>
          </svg>
        </button>
        
        <div class="volume-panel">
          <div class="volume-slider-vertical" 
               @mousedown="handleVolumeMouseDown" 
               @mousemove="handleVolumeMouseMove">
            <div class="volume-track">
              <div class="volume-fill" :style="{ height: volume + '%' }"></div>
            </div>
            <div class="volume-thumb" :style="{ bottom: volume + '%' }"></div>
          </div>
          <div class="volume-value">{{ volume }}%</div>
        </div>
      </div>
      
      <button class="control-btn" @click="togglePlaylist" title="播放列表">
        <svg viewBox="0 0 24 24" width="18" height="18">
          <path fill="currentColor" d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const currentMusic = ref(null)
const isPlaying = ref(false)
const isMuted = ref(false)
const playMode = ref('list') // list, single, shuffle
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(parseInt(localStorage.getItem('volume')) || 100)
const audioElement = ref(null)
const desktopLyricsEnabled = ref(false)
const fadeInterval = ref(null)
const FADE_DURATION = 500 // 淡入淡出时长（毫秒）
const FADE_STEPS = 20 // 淡入淡出步数

const playModeTitle = computed(() => {
  const titles = {
    'list': '列表循环',
    'single': '单曲循环',
    'shuffle': '随机播放'
  }
  return titles[playMode.value] || '列表循环'
})

const currentCover = computed(() => {
  if (!currentMusic.value) {
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 56 56"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="56" height="56" fill="url(%23grad)" rx="8"/><text x="28" y="35" font-family="Arial" font-size="24" fill="white" text-anchor="middle" font-weight="bold">M</text></svg>'
  }
  return `https://music.cnmsb.xin/api/music/cover/${currentMusic.value.id}`
})

const progress = computed(() => {
  if (duration.value === 0) return 0
  return (currentTime.value / duration.value) * 100
})

const formatTime = (seconds) => {
  if (!seconds) return '0:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const handleCoverError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 56 56"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="56" height="56" fill="url(%23grad)" rx="8"/><text x="28" y="35" font-family="Arial" font-size="24" fill="white" text-anchor="middle" font-weight="bold">M</text></svg>'
}

const togglePlay = () => {
  if (!audioElement.value) return
  
  // 立即更新 UI 状态
  isPlaying.value = !isPlaying.value
  updateMediaInfo()
  notifyPlayerState()
  
  if (window.electronAPI) {
    window.electronAPI.notifyPlayState(isPlaying.value)
  }
  
  // 执行淡入淡出效果
  if (isPlaying.value) {
    // 淡入播放
    audioElement.value.play().catch(err => {
      console.error('播放失败:', err)
      isPlaying.value = false
      updateMediaInfo()
    })
    fadeIn()
  } else {
    // 淡出暂停
    fadeOut(() => {
      audioElement.value.pause()
    })
  }
}

// 淡入效果
const fadeIn = () => {
  if (fadeInterval.value) {
    clearInterval(fadeInterval.value)
  }
  
  audioElement.value.volume = 0
  const targetVolume = volume.value / 100
  const step = targetVolume / FADE_STEPS
  let currentStep = 0
  
  fadeInterval.value = setInterval(() => {
    currentStep++
    audioElement.value.volume = Math.min(currentStep * step, targetVolume)
    
    if (currentStep >= FADE_STEPS) {
      clearInterval(fadeInterval.value)
      fadeInterval.value = null
    }
  }, FADE_DURATION / FADE_STEPS)
}

// 淡出效果
const fadeOut = (callback) => {
  if (fadeInterval.value) {
    clearInterval(fadeInterval.value)
  }
  
  const currentVolume = audioElement.value.volume
  const step = currentVolume / FADE_STEPS
  let currentStep = 0
  
  fadeInterval.value = setInterval(() => {
    currentStep++
    audioElement.value.volume = Math.max(currentVolume - currentStep * step, 0)
    
    if (currentStep >= FADE_STEPS) {
      clearInterval(fadeInterval.value)
      fadeInterval.value = null
      if (callback) callback()
    }
  }, FADE_DURATION / FADE_STEPS)
}

const previous = () => {
  console.log('上一首')
}

const next = () => {
  console.log('下一首')
}

const togglePlayMode = () => {
  const modes = ['list', 'single', 'shuffle']
  const currentIndex = modes.indexOf(playMode.value)
  const nextIndex = (currentIndex + 1) % modes.length
  playMode.value = modes[nextIndex]
  notifyPlayerState()
}

const toggleDesktopLyrics = (enabled) => {
  desktopLyricsEnabled.value = enabled
  // 这里可以添加桌面歌词窗口的显示/隐藏逻辑
  console.log('桌面歌词:', enabled ? '开启' : '关闭')
}

// 通知主进程播放状态变化
const notifyPlayerState = () => {
  if (window.electronAPI) {
    window.electronAPI.notifyPlayerState({
      isPlaying: isPlaying.value,
      playMode: playMode.value,
      volume: volume.value,
      desktopLyricsEnabled: desktopLyricsEnabled.value
    })
  }
}

const isDragging = ref(false)
const isVolumeDragging = ref(false)

const seekTo = (event) => {
  if (!audioElement.value || !duration.value) return
  
  const rect = event.currentTarget.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percentage = Math.max(0, Math.min(1, x / rect.width))
  audioElement.value.currentTime = percentage * duration.value
}

const handleProgressMouseDown = (event) => {
  isDragging.value = true
  seekTo(event)
  event.preventDefault()
}

const handleProgressMouseMove = (event) => {
  if (isDragging.value && event.currentTarget) {
    const rect = event.currentTarget.getBoundingClientRect()
    const x = event.clientX - rect.left
    const percentage = Math.max(0, Math.min(1, x / rect.width))
    if (audioElement.value && duration.value) {
      audioElement.value.currentTime = percentage * duration.value
    }
  }
}

const handleGlobalMouseMove = (event) => {
  if (isDragging.value) {
    const progressBar = document.querySelector('.progress-bar')
    if (progressBar) {
      const rect = progressBar.getBoundingClientRect()
      const x = event.clientX - rect.left
      const percentage = Math.max(0, Math.min(1, x / rect.width))
      if (audioElement.value && duration.value) {
        audioElement.value.currentTime = percentage * duration.value
      }
    }
  }
}

const handleProgressMouseUp = () => {
  isDragging.value = false
}

const handleVolumeMouseDown = (event) => {
  isVolumeDragging.value = true
  handleVolumeClick(event)
  event.preventDefault()
}

const handleVolumeMouseMove = (event) => {
  if (isVolumeDragging.value && event.currentTarget) {
    const rect = event.currentTarget.getBoundingClientRect()
    const y = rect.bottom - event.clientY
    const percentage = Math.max(0, Math.min(100, (y / rect.height) * 100))
    volume.value = Math.round(percentage)
    if (audioElement.value) {
      audioElement.value.volume = volume.value / 100
    }
    localStorage.setItem('volume', volume.value.toString())
  }
}

const handleGlobalVolumeMouseMove = (event) => {
  if (isVolumeDragging.value) {
    const volumeSlider = document.querySelector('.volume-slider-vertical')
    if (volumeSlider) {
      const rect = volumeSlider.getBoundingClientRect()
      const y = rect.bottom - event.clientY
      const percentage = Math.max(0, Math.min(100, (y / rect.height) * 100))
      volume.value = Math.round(percentage)
      if (audioElement.value) {
        audioElement.value.volume = volume.value / 100
      }
      localStorage.setItem('volume', volume.value.toString())
    }
  }
}

const handleVolumeMouseUp = () => {
  isVolumeDragging.value = false
}

const handleProgressHover = () => {
  // 可以添加悬停预览功能
}

const handleProgressLeave = () => {
  // 不在这里清除拖动状态，允许拖出进度条
}

const toggleMute = () => {
  isMuted.value = !isMuted.value
  if (audioElement.value) {
    audioElement.value.muted = isMuted.value
  }
  
  // 保存音量设置到本地存储
  localStorage.setItem('volume', volume.value.toString())
}

// 更新媒体信息
const updateMediaInfo = () => {
  if (window.electronAPI && currentMusic.value) {
    window.electronAPI.updateMediaInfo({
      music: currentMusic.value,
      isPlaying: isPlaying.value,
      currentTime: currentTime.value,
      duration: duration.value
    })
  }
}

const handleVolumeChange = () => {
  if (fadeInterval.value) {
    clearInterval(fadeInterval.value)
    fadeInterval.value = null
  }
  
  if (audioElement.value) {
    audioElement.value.volume = volume.value / 100
    isMuted.value = volume.value === 0
  }
  
  // 保存音量设置到本地存储
  localStorage.setItem('volume', volume.value.toString())
}

const togglePlaylist = () => {
  console.log('播放列表')
}

const handleVolumeClick = (event) => {
  const rect = event.currentTarget.getBoundingClientRect()
  const y = rect.bottom - event.clientY
  const percentage = Math.min(100, Math.max(0, (y / rect.height) * 100))
  volume.value = Math.round(percentage)
  handleVolumeChange()
}

const loadMusic = (music) => {
  if (!music) return
  
  currentMusic.value = music
  isPlaying.value = false
  currentTime.value = 0
  duration.value = music.duration || 0
  
  // 保存到 localStorage
  localStorage.setItem('currentMusic', JSON.stringify(music))
  
  if (audioElement.value) {
    audioElement.value.src = `https://music.cnmsb.xin/api/music/file/${music.id}`
    audioElement.value.load()
  }
  
  updateMediaInfo()
  
  // 通知主进程
  if (window.electronAPI) {
    window.electronAPI.notifyMusicPlay(music)
  }
}

const handleTimeUpdate = () => {
  if (audioElement.value) {
    currentTime.value = audioElement.value.currentTime
    updateMediaInfo()
  }
}

const handleLoadedMetadata = () => {
  if (audioElement.value) {
    duration.value = audioElement.value.duration
  }
}

const handleEnded = () => {
  isPlaying.value = false
  updateMediaInfo()
  next()
}

const handleMusicPlay = (event) => {
  loadMusic(event.detail)
  if (audioElement.value) {
    audioElement.value.play()
    isPlaying.value = true
    updateMediaInfo()
  }
}

onMounted(() => {
  audioElement.value = new Audio()
  audioElement.value.volume = volume.value / 100
  
  audioElement.value.addEventListener('timeupdate', handleTimeUpdate)
  audioElement.value.addEventListener('loadedmetadata', handleLoadedMetadata)
  audioElement.value.addEventListener('ended', handleEnded)
  
  window.addEventListener('music-play', handleMusicPlay)
  
  // 监听托盘事件
  window.addEventListener('tray-previous', previous)
  window.addEventListener('tray-play-pause', togglePlay)
  window.addEventListener('tray-next', next)
  window.addEventListener('tray-favorite', handleTrayFavorite)
  window.addEventListener('tray-set-play-mode', (event) => {
    playMode.value = event.detail
    notifyPlayerState()
  })
  window.addEventListener('tray-toggle-desktop-lyrics', (event) => {
    toggleDesktopLyrics(event.detail)
  })
  window.addEventListener('navigate-to-settings', handleNavigateToSettings)
  
  // 监听媒体控制事件
  window.addEventListener('media-play-pause', togglePlay)
  window.addEventListener('media-next', next)
  window.addEventListener('media-previous', previous)
  
  // 全局鼠标事件，处理拖动进度条和音量
  window.addEventListener('mouseup', (event) => {
    handleProgressMouseUp()
    handleVolumeMouseUp()
  })
  window.addEventListener('mousemove', (event) => {
    handleGlobalMouseMove(event)
    handleGlobalVolumeMouseMove(event)
  })
  
  // 恢复之前播放的音乐
  const savedMusic = localStorage.getItem('currentMusic')
  if (savedMusic) {
    try {
      const music = JSON.parse(savedMusic)
      loadMusic(music)
      isPlaying.value = false // 恢复时默认暂停
    } catch (e) {
      console.error('解析音乐失败:', e)
    }
  }
})

onUnmounted(() => {
  if (fadeInterval.value) {
    clearInterval(fadeInterval.value)
    fadeInterval.value = null
  }
  
  if (audioElement.value) {
    audioElement.value.removeEventListener('timeupdate', handleTimeUpdate)
    audioElement.value.removeEventListener('loadedmetadata', handleLoadedMetadata)
    audioElement.value.removeEventListener('ended', handleEnded)
    audioElement.value.pause()
  }
  window.removeEventListener('music-play', handleMusicPlay)
  window.removeEventListener('tray-previous', previous)
  window.removeEventListener('tray-play-pause', togglePlay)
  window.removeEventListener('tray-next', next)
  window.removeEventListener('tray-favorite', handleTrayFavorite)
  window.removeEventListener('tray-set-play-mode', togglePlayMode)
  window.removeEventListener('tray-toggle-desktop-lyrics', toggleDesktopLyrics)
  window.removeEventListener('navigate-to-settings', handleNavigateToSettings)
  window.removeEventListener('media-play-pause', togglePlay)
  window.removeEventListener('media-next', next)
  window.removeEventListener('media-previous', previous)
})

const handleTrayFavorite = () => {
  if (currentMusic.value) {
    toggleFavorite(currentMusic.value)
  }
}

const handleNavigateToSettings = () => {
  const router = useRouter()
  router.push('/settings')
}

const toggleFavorite = (music) => {
  if (!music) return
  
  // 获取收藏列表
  let favorites = []
  try {
    const saved = localStorage.getItem('favorites')
    if (saved) {
      favorites = JSON.parse(saved)
    }
  } catch (e) {
    console.error('解析收藏列表失败:', e)
  }
  
  // 检查是否已收藏
  const index = favorites.findIndex(f => f.id === music.id)
  
  if (index > -1) {
    // 取消收藏
    favorites.splice(index, 1)
    console.log('取消收藏:', music.title)
  } else {
    // 添加收藏
    favorites.push(music)
    console.log('收藏成功:', music.title)
  }
  
  // 保存收藏列表
  localStorage.setItem('favorites', JSON.stringify(favorites))
}
</script>

<style scoped>
.player-bar {
  height: 90px;
  border-top: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 24px;
  position: relative;
  z-index: 100;
}

.player-bar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(102, 126, 234, 0.3), transparent);
}

.player-info {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 280px;
  flex-shrink: 0;
}

.player-cover-wrapper {
  position: relative;
  width: 60px;
  height: 60px;
  flex-shrink: 0;
}

.player-cover {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  object-fit: cover;
  box-shadow: var(--shadow-md);
  transition: all var(--transition-normal);
}

.player-cover-wrapper:hover .player-cover {
  transform: scale(1.05);
  box-shadow: var(--shadow-lg);
}

.cover-wave {
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 3px;
  align-items: flex-end;
  height: 12px;
}

.cover-wave span {
  width: 3px;
  background: var(--gradient-primary);
  border-radius: 2px;
  animation: wave 1s ease-in-out infinite;
}

.cover-wave span:nth-child(1) { animation-delay: 0s; height: 6px; }
.cover-wave span:nth-child(2) { animation-delay: 0.1s; height: 10px; }
.cover-wave span:nth-child(3) { animation-delay: 0.2s; height: 8px; }
.cover-wave span:nth-child(4) { animation-delay: 0.3s; height: 12px; }

@keyframes wave {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(0.5); }
}

.player-details {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.player-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-artist {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.player-controls-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  min-width: 400px;
}

.control-buttons {
  display: flex;
  align-items: center;
  gap: 12px;
}

.control-btn {
  width: 38px;
  height: 38px;
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

.control-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary);
  transform: scale(1.1);
}

.control-btn.active {
  color: var(--primary);
  background: rgba(102, 126, 234, 0.1);
}

.play-btn {
  width: 48px;
  height: 48px;
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.play-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

.player-progress {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
}

.time {
  font-size: 12px;
  color: var(--text-muted);
  min-width: 42px;
  text-align: center;
  font-weight: 500;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: height var(--transition-fast);
  user-select: none;
}

.progress-bar:hover {
  height: 8px;
}

.progress-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 3px;
  position: relative;
  transition: width 0.1s;
}

.progress-glow {
  position: absolute;
  top: -4px;
  left: 0;
  right: 0;
  bottom: -4px;
  background: var(--gradient-primary);
  filter: blur(8px);
  opacity: 0.5;
}

.progress-thumb {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: var(--shadow-md);
  opacity: 0;
  transition: all var(--transition-fast);
}

.progress-bar:hover .progress-thumb {
  opacity: 1;
}

.thumb-glow {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  background: var(--gradient-primary);
  border-radius: 50%;
  filter: blur(6px);
  opacity: 0.6;
}

.player-controls-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.volume-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.volume-wrapper:hover .volume-panel {
  opacity: 1;
  visibility: visible;
}

.volume-panel {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  padding: 16px 12px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  align-items: center;
  z-index: 200;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease, visibility 0.2s ease;
}

.volume-slider-vertical {
  width: 8px;
  height: 120px;
  position: relative;
  cursor: pointer;
  user-select: none;
}

.volume-track {
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.volume-fill {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: #FF3B30;
  border-radius: 4px;
  transition: height 0.1s;
}

.volume-thumb {
  position: absolute;
  left: 50%;
  transform: translateX(-50%) translateY(50%);
  width: 20px;
  height: 20px;
  background: white;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  transition: bottom 0.1s;
}

.volume-value {
  margin-top: 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
}
</style>