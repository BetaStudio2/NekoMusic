<template>
  <div class="player-bar">
    <div class="player-info">
      <img :src="currentCover" alt="封面" class="player-cover" @error="handleCoverError" />
      <div class="player-details">
        <span class="player-title">{{ currentMusic?.title || '未播放' }}</span>
        <span class="player-artist">{{ currentMusic?.artist || '-' }}</span>
      </div>
    </div>
    
    <div class="player-controls">
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
      <div class="progress-bar" @click="seekTo">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
      <span class="time">{{ formatTime(duration) }}</span>
    </div>
    
    <div class="player-volume">
      <button class="control-btn" @click="toggleMute" :title="isMuted ? '取消静音' : '静音'">
        <svg v-if="!isMuted" viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" width="20" height="20">
          <path fill="currentColor" d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/>
        </svg>
      </button>
      <input 
        v-model="volume" 
        type="range" 
        min="0" 
        max="100" 
        class="volume-slider"
        @input="handleVolumeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const currentMusic = ref(null)
const isPlaying = ref(false)
const isMuted = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(80)
const audioElement = ref(null)

const currentCover = computed(() => {
  if (!currentMusic.value) {
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 56 56"><rect width="56" height="56" fill="%236a5acd"/><text x="28" y="35" font-family="Arial" font-size="24" fill="white" text-anchor="middle">M</text></svg>'
  }
  return `http://localhost:9999/api/music/cover/${currentMusic.value.id}`
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
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="56" height="56" viewBox="0 0 56 56"><rect width="56" height="56" fill="%236a5acd"/><text x="28" y="35" font-family="Arial" font-size="24" fill="white" text-anchor="middle">M</text></svg>'
}

const togglePlay = () => {
  if (!audioElement.value) return
  
  if (isPlaying.value) {
    audioElement.value.pause()
  } else {
    audioElement.value.play()
  }
  isPlaying.value = !isPlaying.value
}

const previous = () => {
  // 实现上一首逻辑
  console.log('上一首')
}

const next = () => {
  // 实现下一首逻辑
  console.log('下一首')
}

const seekTo = (event) => {
  if (!audioElement.value || !duration.value) return
  
  const rect = event.currentTarget.getBoundingClientRect()
  const x = event.clientX - rect.left
  const percentage = x / rect.width
  audioElement.value.currentTime = percentage * duration.value
}

const toggleMute = () => {
  isMuted.value = !isMuted.value
  if (audioElement.value) {
    audioElement.value.muted = isMuted.value
  }
}

const handleVolumeChange = () => {
  if (audioElement.value) {
    audioElement.value.volume = volume.value / 100
    isMuted.value = volume.value === 0
  }
}

const loadMusic = (music) => {
  if (!music) return
  
  currentMusic.value = music
  isPlaying.value = false
  currentTime.value = 0
  duration.value = music.duration || 0
  
  if (audioElement.value) {
    audioElement.value.src = `http://localhost:9999/api/music/file/${music.id}`
    audioElement.value.load()
  }
}

const handleTimeUpdate = () => {
  if (audioElement.value) {
    currentTime.value = audioElement.value.currentTime
  }
}

const handleLoadedMetadata = () => {
  if (audioElement.value) {
    duration.value = audioElement.value.duration
  }
}

const handleEnded = () => {
  isPlaying.value = false
  next()
}

const handleMusicPlay = (event) => {
  loadMusic(event.detail)
  if (audioElement.value) {
    audioElement.value.play()
    isPlaying.value = true
  }
}

onMounted(() => {
  audioElement.value = new Audio()
  audioElement.value.volume = volume.value / 100
  
  audioElement.value.addEventListener('timeupdate', handleTimeUpdate)
  audioElement.value.addEventListener('loadedmetadata', handleLoadedMetadata)
  audioElement.value.addEventListener('ended', handleEnded)
  
  window.addEventListener('music-play', handleMusicPlay)
  
  const savedMusic = localStorage.getItem('currentMusic')
  if (savedMusic) {
    try {
      const music = JSON.parse(savedMusic)
      loadMusic(music)
    } catch (e) {
      console.error('解析音乐失败:', e)
    }
  }
})

onUnmounted(() => {
  if (audioElement.value) {
    audioElement.value.removeEventListener('timeupdate', handleTimeUpdate)
    audioElement.value.removeEventListener('loadedmetadata', handleLoadedMetadata)
    audioElement.value.removeEventListener('ended', handleEnded)
    audioElement.value.pause()
  }
  window.removeEventListener('music-play', handleMusicPlay)
})
</script>

<style scoped>
.player-bar {
  height: 80px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 20px;
}

.player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 300px;
  flex-shrink: 0;
}

.player-cover {
  width: 56px;
  height: 56px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.player-details {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.player-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-artist {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.control-btn {
  width: 40px;
  height: 40px;
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

.control-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.play-btn {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.play-btn:hover {
  background: linear-gradient(135deg, #5568d3, #654090);
  transform: scale(1.05);
}

.player-progress {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
}

.time {
  font-size: 12px;
  color: #666;
  min-width: 40px;
  text-align: center;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.progress-bar:hover {
  height: 6px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  border-radius: 2px;
  transition: width 0.1s;
}

.player-volume {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 140px;
  flex-shrink: 0;
}

.volume-slider {
  flex: 1;
  -webkit-appearance: none;
  appearance: none;
  height: 4px;
  background: #e0e0e0;
  border-radius: 2px;
  outline: none;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 12px;
  height: 12px;
  background: #667eea;
  border-radius: 50%;
  cursor: pointer;
}

.volume-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  background: #667eea;
  border-radius: 50%;
  cursor: pointer;
  border: none;
}
</style>