<template>
  <div class="global-player" :class="{ 'global-player--chrome-dark': chromeDark }">
    <div class="player-content">
      <!-- 音乐封面 -->
      <div class="cover-container" @click="goToDetails">
        <img 
          v-if="currentMusic"
          :src="getCoverUrl(currentMusic.id)" 
          :alt="currentMusic.title"
          class="music-cover"
          @error="handleImageError"
        />
        <div v-else class="music-cover placeholder-cover">🎵</div>
      </div>
      
      <!-- 音乐信息 -->
      <div class="music-info" @click.stop>
        <div v-if="currentMusic" class="music-title">{{ currentMusic.title }}</div>
        <div v-else class="music-title placeholder-text">请选择音乐播放</div>
        <div v-if="currentMusic" class="music-artist">{{ currentMusic.artist }}</div>
        <div v-else class="music-artist placeholder-text">-</div>
      </div>
      
      <!-- 播放控制 -->
      <div class="player-controls">
        <audio 
          v-if="currentMusic"
          ref="audioPlayer" 
          :src="`${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.id}`" 
          @ended="onAudioEnded"
          @timeupdate="onTimeUpdate"
          @loadedmetadata="onLoadedMetadata"
        />
        
        <div class="progress-container" @click.stop>
          <span class="time">{{ formatTime(currentTime) }}</span>
          <input
              v-if="currentMusic"
              type="range"
              class="progress-bar"
              :value="progress"
              @input="onProgressChange"
              :max="duration"
              aria-label="播放进度"
          />
          <span class="time">{{ formatTime(duration) }}</span>
        </div>
        
        <div class="control-buttons" @click.stop>
          <!-- 上一曲按钮 -->
          <button @click="playPrevious" class="prev-btn" title="上一曲" :disabled="!currentMusic">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
            </svg>
          </button>
          
          <!-- 播放模式按钮 -->
          <button @click="togglePlaybackMode" class="mode-btn" :title="getPlaybackModeTitle()">
            <svg
                v-if="playbackMode === 'list_repeat'"
                class="btn-icon"
                viewBox="0 0 1024 1024"
                xmlns="http://www.w3.org/2000/svg">
              <path
                  d="M361.5 727.8c-119.1 0-215.9-96.9-215.9-215.9 0-119.1 96.9-215.9 215.9-215.9 2.3 0 4.6-0.2 6.8-0.6v58.3c0 12.3 14 19.4 23.9 12.1l132.6-97.6c8.1-6 8.1-18.2 0-24.2l-132.6-97.6c-9.9-7.3-23.9-0.2-23.9 12.1v58.1c-2.2-0.4-4.5-0.6-6.8-0.6-39.8 0-78.5 7.9-115 23.4-35.2 15-66.8 36.3-94 63.5s-48.6 58.8-63.5 94c-15.5 36.5-23.4 75.2-23.4 115s7.9 78.5 23.4 115c15 35.2 36.3 66.8 63.5 94s58.8 48.6 94 63.5c36.5 15.5 75.2 23.4 115 23.4 22.1 0 40-17.9 40-40s-17.9-40-40-40zM938.2 396.9c-15-35.2-36.3-66.8-63.5-94s-58.8-48.6-94-63.5c-36.5-15.5-75.2-23.4-115-23.4-22.1 0-40 17.9-40 40s17.9 40 40 40c119.1 0 215.9 96.9 215.9 215.9 0 119.1-96.9 215.9-215.9 215.9-4.1 0-8.1 0.6-11.8 1.8v-60.8c0-12.3-14-19.4-23.9-12.1l-132.6 97.6c-8.1 6-8.1 18.2 0 24.2L629.9 876c9.9 7.3 23.9 0.2 23.9-12.1V806c3.7 1.2 7.7 1.8 11.8 1.8 39.8 0 78.5-7.9 115-23.4 35.2-15 66.8-36.3 94-63.5s48.6-58.8 63.5-94c15.5-36.5 23.4-75.2 23.4-115s-7.8-78.5-23.3-115z"
                  fill="#ffffff"
              />
            </svg>

            <svg v-else-if="playbackMode === 'single_repeat'" class="btn-icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="20390" width="128" height="128">
              <path d="M507.008 122.752a42.666667 42.666667 0 0 0-30.165333 72.832l17.749333 17.749333H383.317333A298.666667 298.666667 0 0 0 232.533333 769.834667a42.666667 42.666667 0 1 0 44.672-72.149334q-23.808-13.909333-44.714666-34.816Q169.984 600.32 169.984 512q0-88.362667 62.506667-150.869333Q294.954667 298.666667 383.317333 298.666667H597.333333a42.666667 42.666667 0 0 0 30.336-12.586667 42.666667 42.666667 0 0 0 0-60.330667l-12.373333-12.373333h25.301333L639.317333 213.333333h-24.064l-78.08-78.08a42.666667 42.666667 0 0 0-30.165333-12.501333zM937.984 512c0-110.506667-59.946667-206.933333-149.12-258.56a42.666667 42.666667 0 1 0-39.424 75.264q21.589333 13.269333 40.746667 32.426667Q852.650667 423.68 852.650667 512q0 88.362667-62.464 150.869333Q727.68 725.333333 639.317333 725.333333h-209.066666a42.666667 42.666667 0 0 0-33.621334 12.373334l-0.512 0.512a42.666667 42.666667 0 0 0 3.370667 62.677333l87.637333 87.637333a42.666667 42.666667 0 0 0 60.373334-60.330666l-17.536-17.493334h109.354666a298.666667 298.666667 0 0 0 298.666667-298.709333z" p-id="20391"></path>
              <path d="M469.333333 597.333333v-170.666666a42.666667 42.666667 0 1 1 85.333334 0v170.666666a42.666667 42.666667 0 0 1-85.333334 0z" p-id="20392"></path>
            </svg>
            <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
              <path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/>
            </svg>
          </button>
          
          <!-- 播放/暂停按钮 -->
          <button
              @click="togglePlayPause"
              class="play-pause-btn"
              :disabled="!currentMusic"
              :aria-label="isPlaying && currentMusic ? '暂停' : '播放'"
              :aria-pressed="isPlaying && currentMusic"
          >
            <svg
                v-if="isPlaying && currentMusic"
                class="btn-icon"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
            >
              <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
            </svg>
            <svg
                v-else
                class="btn-icon"
                viewBox="0 0 24 24"
                fill="currentColor"
                aria-hidden="true"
            >
              <path d="M8 5v14l11-7z"/>
            </svg>
          </button>
          
          <!-- 下一曲按钮 -->
          <button @click="playNext" class="next-btn" title="下一曲" :disabled="!currentMusic">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
              <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
            </svg>
          </button>
          
          <!-- 播放列表按钮 -->
          <button @click="togglePlaylist" class="playlist-btn" title="播放列表">
            <svg class="btn-icon" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
              <path d="M981.333333 533.333333a21.333333 21.333333 0 0 1-21.333333 21.333334H448a21.333333 21.333333 0 0 1 0-42.666667h512a21.333333 21.333333 0 0 1 21.333333 21.333333zM533.333333 170.666667h426.666667a21.333333 21.333333 0 0 0 0-42.666667H533.333333a21.333333 21.333333 0 0 0 0 42.666667z m426.666667 725.333333H64a21.333333 21.333333 0 0 0 0 42.666667h896a21.333333 21.333333 0 0 0 0-42.666667zM89.66 696.753333C117.333333 715.186667 153.646667 725.333333 192 725.333333s74.7-10.146667 102.34-28.58c14.253333-9.5 25.56-20.746667 33.613333-33.44C336.833333 649.333333 341.333333 634.3 341.333333 618.666667V182a140.893333 140.893333 0 0 0 30.966667 27.82A21.18 21.18 0 0 0 376.666667 212c8.713333 3.2 16.773333 8.606667 23.953333 16.086667 16.733333 17.42 23.806667 41.146667 26.533333 53.733333a21.333333 21.333333 0 0 0 41.706667-9.026667c-4.5-20.773333-14.666667-50.513333-37.466667-74.266666-11.04-11.493333-23.64-20.093333-37.493333-25.606667-10.306667-7.126667-19.44-16.58-27.153333-28.133333-19.64-29.393333-24.373333-64.04-25.446667-82.08A21.333333 21.333333 0 0 0 298.666667 64v479.586667c-1.413333-1.02-2.846667-2-4.326667-3.006667C266.7 522.146667 230.353333 512 192 512s-74.666667 10.146667-102.34 28.58C75.406667 550.08 64.1 561.333333 56.046667 574 47.166667 588 42.666667 603.033333 42.666667 618.666667s4.5 30.666667 13.38 44.666666c8.053333 12.666667 19.36 23.92 33.613333 33.42z" fill="currentColor"/>
            </svg>
          </button>
          
          <!-- 收藏按钮 -->
          <button @click="toggleFavorite" class="favorite-btn" :title="isFavorite ? '取消收藏' : '收藏'" :disabled="!currentMusic">
            <svg v-if="isFavorite" class="btn-icon favorite-active" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
            </svg>
            <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="currentColor">
              <path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z" fill="#888888"/>
            </svg>
          </button>
        </div>
      </div>
      
      <!-- 播放列表弹窗 -->
      <div v-if="showPlaylist" class="playlist-container">
        <div class="playlist-header">
          <h3>播放列表</h3>
          <div class="playlist-header-actions">
            <button @click="clearPlaylist" class="clear-playlist-btn" title="清空列表" :disabled="playlist.length === 0">
              清空
            </button>
            <button @click="togglePlaylist" class="close-playlist">✕</button>
          </div>
        </div>
        <div class="playlist-items">
          <div 
            v-for="(item, index) in playlist" 
            :key="item.id" 
            class="playlist-item" 
            :class="{ 'current': currentMusic && item.id === currentMusic.id }"
            @click="playFromPlaylist(index)"
          >
            <div class="playlist-item-info">
              <span class="playlist-item-title">{{ item.title }}</span>
              <span class="playlist-item-artist">{{ item.artist }}</span>
            </div>
            <span v-if="currentMusic && item.id === currentMusic.id" class="current-indicator">▶</span>
          </div>
        </div>
      </div>
      
      <!-- 歌词显示区域 -->
      <div class="lyrics-container" @click.stop>
        <div class="lyrics-content">
          <div class="lyric-line" :class="{ 'active': isCurrentLyric(0), 'active-enter': isCurrentLyric(0) && currentAnimationIndex === 0 }">
            <div class="lyric-text">{{ getLyricLine(0) }}</div>
            <div class="lyric-translation" v-if="getLyricTranslation(0)">{{ getLyricTranslation(0) }}</div>
          </div>
          <div class="lyric-line" :class="{ 'active': isCurrentLyric(1), 'active-enter': isCurrentLyric(1) && currentAnimationIndex === 1 }">
            <div class="lyric-text">{{ getLyricLine(1) }}</div>
            <div class="lyric-translation" v-if="getLyricTranslation(1)">{{ getLyricTranslation(1) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
  
  <!-- 确认清空播放列表模态框 -->
  <div v-if="showClearConfirm" class="confirm-modal-overlay" @click.self="showClearConfirm = false">
    <div class="confirm-modal">
      <div class="confirm-modal-header">
        <h3>确认清空</h3>
      </div>
      <div class="confirm-modal-body">
        <p>确定要清空播放列表吗？</p>
      </div>
      <div class="confirm-modal-footer">
        <button @click="showClearConfirm = false" class="confirm-btn cancel">取消</button>
        <button @click="confirmClearPlaylist" class="confirm-btn confirm">确定</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

defineProps({
  chromeDark: {
    type: Boolean,
    default: false
  }
})

const toast = useToast()

const router = useRouter()

// 从localStorage获取当前播放的音乐信息
const currentMusic = ref(JSON.parse(localStorage.getItem('currentPlayingMusic')) || null)
const audioPlayer = ref(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const progress = ref(0)
const lyrics = ref('')
const parsedLyrics = ref([])
const lyricsContent = ref(null)
const currentAnimationIndex = ref(-1)

// 播放模式相关状态
const playbackMode = ref('list_repeat') // 'list_repeat', 'single_repeat', 'shuffle'
const playlist = ref([])
const isFavorite = ref(false) // 当前音乐是否已收藏
const showClearConfirm = ref(false) // 是否显示清空确认模态框

// 记录上一个歌词索引
let previousLyricIndex = -1

// 获取用户token
const getToken = () => {
  return localStorage.getItem('userToken')
}

// 切换收藏状态
const toggleFavorite = async () => {
  if (!currentMusic.value) return
  
  const token = getToken()
  if (!token) {
    toast.error('请先登录')
    return
  }
  
  if (isFavorite.value) {
    // 取消收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${currentMusic.value.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': token
        }
      })
      
      const data = await response.json()
      if (data.success) {
        isFavorite.value = false
        toast.success('取消收藏成功')
      } else {
        toast.error('取消收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('取消收藏失败:', error)
      toast.error('取消收藏失败')
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
        body: JSON.stringify({ musicId: currentMusic.value.id })
      })
      
      const data = await response.json()
      if (data.success) {
        isFavorite.value = true
        toast.success('收藏成功')
      } else {
        toast.error('收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('收藏失败:', error)
      toast.error('收藏失败')
    }
  }
}

// 检查当前音乐是否已收藏
const checkFavoriteStatus = async () => {
  if (!currentMusic.value) return
  
  const token = getToken()
  if (!token) {
    isFavorite.value = false
    return
  }
  
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success && data.favorites) {
      isFavorite.value = data.favorites.some(m => m.id === currentMusic.value.id)
    }
  } catch (error) {
    console.error('获取收藏状态失败:', error)
  }
}

// 播放/暂停控制
const togglePlayPause = () => {
  if (audioPlayer.value && currentMusic.value) {
    if (isPlaying.value) {
      // 暂停：直接暂停，避免重音
      isPlaying.value = false
      updateGlobalPlayerState()
      // 广播播放状态变化
      broadcastPlayerStateChange()
      // 立即暂停音频并静音
      if (audioPlayer.value) {
        audioPlayer.value.volume = 0;
        audioPlayer.value.pause();
      }
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    } else {
      // 播放：立即更新状态，然后淡入播放
      isPlaying.value = true
      updateGlobalPlayerState()
      // 广播播放状态变化
      broadcastPlayerStateChange()
      fadeIn(audioPlayer.value)
      audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    }
  }
}

// 音量淡出效果
const fadeOut = (audioElement) => {
  if (!audioElement) return

  // 为了防止暂停时出现重音，先快速将音量降低到0，然后暂停
  // 使用更快速的淡出效果
  const originalVolume = audioElement.volume;
  
  // 立即设置音量为0以避免重音，然后暂停音频
  audioElement.volume = 0;
  audioElement.pause();
}

// 音量淡入效果
const fadeIn = (audioElement) => {
  if (!audioElement) return
  
  let v = 0
  audioElement.volume = 0

  const tick = () => {
    v += 0.03
    audioElement.volume = Math.min(v, 1)
    if (v < 1) requestAnimationFrame(tick)
  }
  tick()
}

// 音频结束事件
// 音频结束事件 - 现在根据播放模式处理
const onAudioEnded = () => {
  if (playbackMode.value === 'single_repeat') {
    // 单曲循环：重新播放当前歌曲
    if (audioPlayer.value && currentMusic.value) {
      audioPlayer.value.currentTime = 0.2
      audioPlayer.value.play()
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    }
  } else if (playbackMode.value === 'shuffle' && playlist.value.length > 1) {
    // 随机播放：播放列表中的随机歌曲
    playNextInShuffle(true) // 标记：来自 ended
  } else {
    // 列表循环：播放下一首
    playNext(true) // 标记：来自 ended
  }
  
  updateGlobalPlayerState()
  // 广播播放状态变化
  broadcastPlayerStateChange()
}

// 时间更新事件
const onTimeUpdate = () => {
  if (audioPlayer.value) {
    currentTime.value = audioPlayer.value.currentTime
    progress.value = currentTime.value
    updateGlobalPlayerState()
    
    // 更新媒体会话播放位置
    updateMediaSessionPositionState()
    
    // 检测当前歌词是否发生变化，如果是，则触发动画
    if (parsedLyrics.value.length > 0) {
      let currentLyricIndex = -1
      for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
        const lyric = parsedLyrics.value[i]
        if (currentTime.value >= lyric.time) {
          currentLyricIndex = i
          break
        }
      }
      
      // 如果当前歌词索引发生变化，则触发动画
      if (previousLyricIndex !== currentLyricIndex && currentLyricIndex !== -1) {
        currentAnimationIndex.value = 0 // 为当前歌词行触发动画
        previousLyricIndex = currentLyricIndex
        // 动画结束后清除动画索引
        setTimeout(() => {
          currentAnimationIndex.value = -1
        }, 600)
      }
    }
  }
}

// 音频元数据加载完成
const onLoadedMetadata = () => {
  if (audioPlayer.value) {
    duration.value = audioPlayer.value.duration
    updateGlobalPlayerState()
    // 广播播放状态变化
    broadcastPlayerStateChange()
    
    // 重置歌词索引
    previousLyricIndex = -1
    
    // 加载歌词
    if (currentMusic.value) {
      loadLyrics(currentMusic.value.id)
    }
    
    // 更新媒体会话播放位置
    updateMediaSessionPositionState()
  }
}

// 进度条变化
const onProgressChange = (event) => {
  const newTime = parseFloat(event.target.value)
  if (audioPlayer.value) {
    audioPlayer.value.currentTime = newTime
    currentTime.value = newTime
    updateGlobalPlayerState()
    // 广播播放状态变化
    broadcastPlayerStateChange()
    // 更新媒体会话播放位置
    updateMediaSessionPositionState()
  }
}

// 更新全局播放器状态
const updateGlobalPlayerState = () => {
  const state = {
    isPlaying: isPlaying.value,
    currentTime: currentTime.value,
    duration: duration.value,
    playbackMode: playbackMode.value
  };
  localStorage.setItem('globalPlayerState', JSON.stringify(state));
}

// 广播播放器状态变化
const broadcastPlayerStateChange = () => {
  // 创建自定义事件来通知播放状态变化
  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: isPlaying.value,
      currentTime: currentTime.value,
      duration: duration.value,
      currentMusic: currentMusic.value
    }
  });
  window.dispatchEvent(event);
}

// 格式化时间（秒转分:秒）
const formatTime = (seconds) => {
  if (isNaN(seconds) || seconds < 0) return '0:00'
  
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return `${min}:${sec < 10 ? '0' : ''}${sec}`
}

// 跳转到音乐详情页面
const goToDetails = () => {
  if (currentMusic.value) {
    // 跳转到音乐详情页面
    router.push(`/detail/${currentMusic.value.id}`)
  }
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 获取歌词
const getLyricsUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/lyrics/${musicId}`
}

// 加载歌词
const loadLyrics = async (musicId) => {
  try {
    const response = await fetch(getLyricsUrl(musicId))
    if (response.ok) {
      const data = await response.json()
      if (data.success) {
        lyrics.value = data.data
        parseLrcLyrics(data.data)
      } else {
        lyrics.value = ''
        parsedLyrics.value = []
      }
    } else {
      lyrics.value = ''
      parsedLyrics.value = []
    }
  } catch (error) {
    console.error('加载歌词失败:', error)
    lyrics.value = ''
    parsedLyrics.value = []
  }
}

// 解析LRC歌词格式
const parseLrcLyrics = (lrcText) => {
  if (!lrcText) {
    parsedLyrics.value = []
    return
  }
  
  const lines = lrcText.split('\n')
  const parsed = []
  let currentLyric = null
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    
    // 跳过空行
    if (!line) {
      continue
    }
    
    // 匹配时间戳歌词行 [mm:ss.xx] 或 [mm:ss.xxx]
    const timeRegex = /\[(\d{2}):(\d{2})\.(\d{2,3})\]/
    const timeMatch = line.match(timeRegex)
    
    if (timeMatch) {
      // 这是歌词行，提取时间和文本
      const minutes = parseInt(timeMatch[1])
      const seconds = parseInt(timeMatch[2])
      const milliseconds = parseInt(timeMatch[3])
      
      // 根据毫秒部分的位数正确计算秒数
      let millisecondsDivisor
      if (milliseconds.toString().length === 2) {
        millisecondsDivisor = 100 // 两位毫秒，如 .25
      } else {
        millisecondsDivisor = 1000 // 三位毫秒，如 .250
      }
      const timeInSeconds = minutes * 60 + seconds + (milliseconds / millisecondsDivisor)
      const text = line.replace(timeRegex, '').trim()
      
      // 查找下一行是否有翻译
      let translation = ''
      if (i + 1 < lines.length) {
        const nextLine = lines[i + 1].trim()
        // 检查是否是JSON格式的翻译行
        const jsonMatch = nextLine.match(/^\{["\'](.+)["\']\}$/)
        if (jsonMatch) {
          translation = jsonMatch[1]
        }
      }
      
      parsed.push({
        time: timeInSeconds,
        text: text,
        translation: translation
      })
    }
  }
  
  // 按时间排序
  parsed.sort((a, b) => a.time - b.time)
  parsedLyrics.value = parsed
}

// 获取指定索引的歌词行文本
const getLyricLine = (offset) => {
  if (!currentMusic.value || parsedLyrics.value.length === 0) {
    return currentMusic.value ? (offset === 0 ? '暂无歌词' : '') : '请选择音乐播放'
  }
  
  // 查找当前时间点对应的歌词索引
  let currentLyricIndex = -1
  for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
    const lyric = parsedLyrics.value[i]
    if (currentTime.value >= lyric.time) {
      currentLyricIndex = i
      break
    }
  }
  
  // 根据偏移量返回对应的歌词
  const targetIndex = currentLyricIndex + offset
  if (targetIndex >= 0 && targetIndex < parsedLyrics.value.length) {
    return parsedLyrics.value[targetIndex].text || ''
  }
  
  // 如果超出范围但不是第一行，返回空字符串
  if (offset > 0) {
    return ''
  }
  
  // 如果还没到第一句歌词的时间，显示提示信息
  if (currentLyricIndex === -1 && parsedLyrics.value.length > 0) {
    return '即将开始...'
  }
  
  return '...'
}

// 获取指定索引的歌词翻译
const getLyricTranslation = (offset) => {
  if (!currentMusic.value || parsedLyrics.value.length === 0) {
    return ''
  }
  
  // 查找当前时间点对应的歌词索引
  let currentLyricIndex = -1
  for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
    const lyric = parsedLyrics.value[i]
    if (currentTime.value >= lyric.time) {
      currentLyricIndex = i
      break
    }
  }
  
  // 根据偏移量返回对应的歌词翻译
  const targetIndex = currentLyricIndex + offset
  if (targetIndex >= 0 && targetIndex < parsedLyrics.value.length) {
    return parsedLyrics.value[targetIndex].translation || ''
  }
  
  return ''
}

// 判断指定偏移量的歌词行是否是当前歌词
const isCurrentLyric = (offset) => {
  if (!currentMusic.value || parsedLyrics.value.length === 0) {
    return offset === 0 && !currentMusic.value // 只有在没有选择音乐时，第一行才"活跃"
  }
  
  // 查找当前时间点对应的歌词索引
  let currentLyricIndex = -1
  for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
    const lyric = parsedLyrics.value[i]
    if (currentTime.value >= lyric.time) {
      currentLyricIndex = i
      break
    }
  }
  
  // 检查指定偏移量的行是否是当前行
  return (currentLyricIndex + offset) >= 0 && (currentLyricIndex + offset) < parsedLyrics.value.length
}

// 获取当前应该显示的歌词文本 (保留此函数以备后续可能需要)
const getCurrentLyricText = () => {
  if (parsedLyrics.value.length === 0) {
    return '暂无歌词'
  }
  
  // 查找当前时间点对应的歌词
  for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
    const lyric = parsedLyrics.value[i]
    if (currentTime.value >= lyric.time) {
      return lyric.text || '...'
    }
  }
  
  // 如果还没到第一句歌词的时间，显示提示信息
  return '即将开始...'
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`
}

// 清空播放列表
const clearPlaylist = () => {
  if (playlist.value.length === 0) {
    toast.warning('播放列表已经是空的')
    return
  }
  
  // 显示确认模态框
  showClearConfirm.value = true
}

// 确认清空播放列表
const confirmClearPlaylist = () => {
  // 清空播放列表
  playlist.value = []
  // 清空localStorage中的播放列表
  localStorage.setItem('globalPlaylist', JSON.stringify([]))
  // 如果当前有正在播放的音乐，保留当前音乐在列表中
  if (currentMusic.value) {
    playlist.value = [currentMusic.value]
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value))
  }
  // 广播播放列表更新事件
  const playlistEvent = new CustomEvent('playlistUpdated', {
    detail: {
      playlist: playlist.value
    }
  })
  window.dispatchEvent(playlistEvent)
  toast.success('播放列表已清空')
  // 关闭模态框
  showClearConfirm.value = false
}

// 播放模式切换函数
const togglePlaybackMode = () => {
  if (playbackMode.value === 'list_repeat') {
    playbackMode.value = 'single_repeat'
  } else if (playbackMode.value === 'single_repeat') {
    playbackMode.value = 'shuffle'
  } else {
    playbackMode.value = 'list_repeat'
  }
  // 保存播放模式到localStorage
  localStorage.setItem('playbackMode', playbackMode.value)
}

// 获取播放模式标题
const getPlaybackModeTitle = () => {
  if (playbackMode.value === 'list_repeat') {
    return '列表循环'
  } else if (playbackMode.value === 'single_repeat') {
    return '单曲循环'
  } else {
    return '随机播放'
  }
}

// 播放列表显示状态
const showPlaylist = ref(false)

// 切换播放列表显示
const togglePlaylist = () => {
  showPlaylist.value = !showPlaylist.value
}

// 从播放列表播放
const playFromPlaylist = (index) => {
  if (playlist.value[index]) {
    // 先暂停当前音频
    if (audioPlayer.value && !audioPlayer.value.paused) {
      audioPlayer.value.pause();
    }
    
    // 重置播放时间并立即更新UI（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 设置为当前播放的音乐
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[index]))
    currentMusic.value = playlist.value[index]
    isPlaying.value = true
    
    // 重新加载歌词
    loadLyrics(playlist.value[index].id)
    
    // 确保音频元素重新加载资源
    if (audioPlayer.value) {
      // 先加载音频资源
      audioPlayer.value.load()
      
      // 在音频加载完成后设置时间为0.1
      const onLoadedData = () => {
        audioPlayer.value.currentTime = 0.1
        currentTime.value = 0.1
        progress.value = 0.1
        updateGlobalPlayerState()
        // 只在有有效 duration 时才更新媒体会话播放位置
        if (audioPlayer.value.duration > 0) {
          duration.value = audioPlayer.value.duration
          updateMediaSessionPositionState()
        }
        fadeIn(audioPlayer.value)
        audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
      }
      
      audioPlayer.value.addEventListener('loadeddata', onLoadedData)
    }
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[index])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
  // 关闭播放列表
  showPlaylist.value = false
}

// 从localStorage加载播放模式
const loadPlaybackMode = () => {
  const savedMode = localStorage.getItem('playbackMode')
  if (savedMode && ['list_repeat', 'single_repeat', 'shuffle'].includes(savedMode)) {
    playbackMode.value = savedMode
  } else {
    // 默认设置为列表循环
    playbackMode.value = 'list_repeat'
    localStorage.setItem('playbackMode', 'list_repeat')
  }
}

// 播放下一首
const playNext = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  let nextIndex
  
  if (playbackMode.value === 'shuffle') {
    nextIndex = getRandomIndex(currentIndex)
  } else {
    nextIndex = (currentIndex + 1) % playlist.value.length
  }
  
  if (nextIndex !== -1 && playlist.value[nextIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[nextIndex]))
    currentMusic.value = playlist.value[nextIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[nextIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[nextIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI（从0.1开始）
      currentTime.value = 0.1
      duration.value = 0
      progress.value = 0.1
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间（从0.1开始）
        audioPlayer.value.currentTime = 0.1
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0.1
          audioPlayer.value.currentTime = 0.1
          currentTime.value = 0.1
          progress.value = 0.1
          updateGlobalPlayerState()
          
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[nextIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}

// 播放上一首
const playPrevious = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  let prevIndex
  
  if (playbackMode.value === 'shuffle') {
    prevIndex = getRandomIndex(currentIndex)
  } else {
    prevIndex = (currentIndex - 1 + playlist.value.length) % playlist.value.length
  }
  
  if (prevIndex !== -1 && playlist.value[prevIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[prevIndex]))
    currentMusic.value = playlist.value[prevIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[prevIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[prevIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI（从0.1开始）
      currentTime.value = 0.1
      duration.value = 0
      progress.value = 0.1
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间（从0.1开始）
        audioPlayer.value.currentTime = 0.1
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0.1
          audioPlayer.value.currentTime = 0.1
          currentTime.value = 0.1
          progress.value = 0.1
          updateGlobalPlayerState()
          
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[prevIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}

// 获取随机索引（排除当前索引）
const getRandomIndex = (currentIndex) => {
  if (playlist.value.length <= 1) return currentIndex
  
  let randomIndex
  do {
    randomIndex = Math.floor(Math.random() * playlist.value.length)
  } while (randomIndex === currentIndex && playlist.value.length > 1)
  
  return randomIndex
}

// 播放下一首（随机模式）
const playNextInShuffle = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  const nextIndex = getRandomIndex(currentIndex)
  
  if (nextIndex !== -1 && playlist.value[nextIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[nextIndex]))
    currentMusic.value = playlist.value[nextIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[nextIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[nextIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI
      currentTime.value = 0
      duration.value = 0
      progress.value = 0
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间
        audioPlayer.value.currentTime = 0
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0
          audioPlayer.value.currentTime = 0
          currentTime.value = 0
          progress.value = 0
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[nextIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}



// 监听localStorage变化，响应播放音乐的改变
const handleStorageChange = (e) => {
  if (e.key === 'currentPlayingMusic') {
    // 只有在音乐实际改变时才重置播放器
    const newMusic = e.newValue ? JSON.parse(e.newValue) : null;
    if (newMusic && (!currentMusic.value || newMusic.id !== currentMusic.value.id)) {
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 音乐改变了，更新当前音乐并重置播放器
      currentMusic.value = newMusic;
      
      // 检查当前音乐是否在播放列表中，如果不在则添加进去
      if (newMusic && playlist.value) {
        const existingIndex = playlist.value.findIndex(item => item.id === newMusic.id);
        if (existingIndex === -1) {
          // 如果当前音乐不在播放列表中，则添加到列表中
          playlist.value.push(newMusic);
          // 同时保存到 localStorage
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
        }
      }
      
      // 重置播放时间并立即更新UI
      currentTime.value = 0
      duration.value = 0
      progress.value = 0
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      if (audioPlayer.value) {
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${newMusic.id}`;
        
        // 检查播放状态，如果应该播放则开始播放
        const storedState = localStorage.getItem('globalPlayerState');
        let shouldPlay = false;
        
        if (storedState) {
          const state = JSON.parse(storedState);
          shouldPlay = state.isPlaying;
        } else {
          // 如果没有播放状态信息，默认播放（因为用户点击了播放按钮）
          shouldPlay = true;
        }
        
        // 监听 canplay 事件，一旦音频可以播放就立即播放
        const onCanPlay = () => {
          audioPlayer.value.currentTime = 0.1; // 确保从0.1开始播放
          currentTime.value = 0.1;
          progress.value = 0.1;
          updateGlobalPlayerState();
          
          if (shouldPlay) {
            // 设置播放状态
            isPlaying.value = true;
            fadeIn(audioPlayer.value);
            audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            broadcastPlayerStateChange(); // 确保其他组件同步状态
          } else {
            // 如果不应该播放，确保播放状态为 false
            isPlaying.value = false;
            broadcastPlayerStateChange();
          }
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay);
        };
        
        // 添加 canplay 事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay);
        
        // 调用 load() 来加载新资源
        audioPlayer.value.load();
      }
      
      // 加载新音乐的歌词
      if (newMusic) {
        loadLyrics(newMusic.id)
        // 更新媒体会话元数据
        updateMediaSessionMetadata(newMusic)
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
        // 检查收藏状态
        checkFavoriteStatus()
      } else {
        lyrics.value = ''
        parsedLyrics.value = []
        isFavorite.value = false
        // 清除媒体会话元数据
        if ('mediaSession' in navigator) {
          navigator.mediaSession.metadata = null
        }
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
      }
    } else if (!e.newValue) {
      // 没有音乐了，暂停播放器
      currentMusic.value = null;
      if (audioPlayer.value) {
        audioPlayer.value.pause();
        // 重置播放时间
        currentTime.value = 0;
        progress.value = 0;
        isPlaying.value = false;
        duration.value = 0;
        updateGlobalPlayerState();
        // 广播播放状态变化
        broadcastPlayerStateChange();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
      }
      // 清空歌词
      lyrics.value = ''
      parsedLyrics.value = []
      // 清除媒体会话元数据
      if ('mediaSession' in navigator) {
        navigator.mediaSession.metadata = null
      }
    }
  } else if (e.key === 'globalPlayerState') {
    // 从播放页面接收状态更新
    if (e.newValue) {
      const state = JSON.parse(e.newValue);
      // 更新播放器状态，无论audio元素是否准备好
      const previousIsPlaying = isPlaying.value;
      isPlaying.value = state.isPlaying;
      currentTime.value = state.currentTime;
      duration.value = state.duration;
      progress.value = state.currentTime;
      
      // 如果有audio元素则同步操作
      if (audioPlayer.value && currentMusic.value) {
        // 等待音频加载完成再执行操作
        const updateWhenReady = () => {
          if (audioPlayer.value) {
            audioPlayer.value.currentTime = state.currentTime;
            if (state.isPlaying && !previousIsPlaying) {
              // 如果状态从暂停变为播放，则开始播放音频
              fadeIn(audioPlayer.value);
              audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            } else if (!state.isPlaying) {
              // 如果状态变为暂停，则暂停音频
              audioPlayer.value.volume = 0;
              audioPlayer.value.pause();
            }
          }
        };
        
        if (audioPlayer.value.readyState >= 2) { // HAVE_CURRENT_DATA
          updateWhenReady();
        } else {
          audioPlayer.value.addEventListener('loadeddata', updateWhenReady, { once: true });
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      } else if (currentMusic.value) {
        // 如果audio元素还没准备好，等待并执行操作
        const handleMetadata = () => {
          if (audioPlayer.value) {
            audioPlayer.value.currentTime = state.currentTime;
            if (state.isPlaying && !previousIsPlaying) {
              // 如果状态从暂停变为播放，则开始播放音频
              fadeIn(audioPlayer.value);
              audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            } else if (!state.isPlaying) {
              // 如果状态变为暂停，则暂停音频
              audioPlayer.value.volume = 0;
              audioPlayer.value.pause();
            }
          }
        };
        
        audioPlayer.value?.addEventListener('loadedmetadata', handleMetadata, { once: true });
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
    }
  } else if (e.key === 'globalPlaylist') {
    // 当播放列表更新时，同步更新本地播放列表
    if (e.newValue) {
      try {
        const newPlaylist = JSON.parse(e.newValue);
        playlist.value = newPlaylist;
      } catch (error) {
        console.error('解析播放列表失败:', error);
      }
    }
  }
}

// 强制播放处理函数
const handleForcePlay = () => {
  if (audioPlayer.value && currentMusic.value) {
    // 确保时间从0.1开始
    audioPlayer.value.currentTime = 0.1;
    currentTime.value = 0.1;
    progress.value = 0.1;
    updateGlobalPlayerState();
    
    // 确保播放状态为true
    isPlaying.value = true;
    
    // 确保音频源已经设置为当前音乐
    const expectedSrc = `${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.value.id}`;
    
    if (!audioPlayer.value.src || !audioPlayer.value.src.includes(currentMusic.value.id.toString())) {
      // 如果音频源不是当前音乐，则设置为当前音乐
      audioPlayer.value.src = expectedSrc;
      
      const onCanPlay = () => {
        audioPlayer.value.currentTime = 0.1; // 再次确保从0.1开始
        currentTime.value = 0.1;
        progress.value = 0.1;
        updateGlobalPlayerState();
        
        fadeIn(audioPlayer.value);
        audioPlayer.value.play().catch(e => {
          console.log('播放被阻止:', e);
          // 如果播放失败，重置播放状态
          isPlaying.value = false;
          updateGlobalPlayerState();
          broadcastPlayerStateChange();
        });
        audioPlayer.value.removeEventListener('canplay', onCanPlay);
        
        // 更新播放状态
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
        updateMediaSessionPlaybackState();
      };
      
      // 添加错误处理
      const onError = (e) => {
        console.error('音频加载失败:', e);
        isPlaying.value = false;
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
        audioPlayer.value.removeEventListener('error', onError);
      };
      
      audioPlayer.value.addEventListener('canplay', onCanPlay);
      audioPlayer.value.addEventListener('error', onError);
      audioPlayer.value.load();
    } else {
      // 音频源已经是当前音乐，直接播放
      fadeIn(audioPlayer.value);
      audioPlayer.value.play().catch(e => {
        console.log('播放被阻止:', e);
        // 如果播放失败，重置播放状态
        isPlaying.value = false;
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
      });
      
      // 更新播放状态
      updateGlobalPlayerState();
      broadcastPlayerStateChange();
      updateMediaSessionPlaybackState();
    }
  }
}

// 处理自定义播放状态变化事件
const handlePlayerStateChange = (e) => {
  const state = e.detail;
  
  // 检查是否正在切换到新音乐
  if (currentMusic.value && state.currentMusic && currentMusic.value.id !== state.currentMusic.id) {
    // 如果是切换到新音乐，更新当前音乐并切换音频源
    currentMusic.value = state.currentMusic;
    
    // 重置播放时间（从0.1开始）
    currentTime.value = 0.1;
    duration.value = 0;
    progress.value = 0.1;
    updateGlobalPlayerState();
    updateMediaSessionPositionState();
    
    // 切换音频源
    if (audioPlayer.value) {
      audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${state.currentMusic.id}`;
      
      const onCanPlay = () => {
        audioPlayer.value.currentTime = 0.1;
        currentTime.value = 0.1;
        progress.value = 0.1;
        updateGlobalPlayerState();
        
        if (state.isPlaying) {
          isPlaying.value = true;
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        }
        
        audioPlayer.value.removeEventListener('canplay', onCanPlay);
        updateMediaSessionPlaybackState();
      };
      
      audioPlayer.value.addEventListener('canplay', onCanPlay);
      audioPlayer.value.load();
    }
    
    // 加载新音乐的歌词
    loadLyrics(state.currentMusic.id);
    // 更新媒体会话元数据
    updateMediaSessionMetadata(state.currentMusic);
    
    return; // 处理完音乐切换后直接返回
  }
  
  // 否则是同一首音乐的时间更新
  currentTime.value = state.currentTime;
  duration.value = state.duration;
  progress.value = state.currentTime;

  // 如果有audio元素则同步操作
  if (audioPlayer.value && currentMusic.value && currentMusic.value.id === state.currentMusic?.id) {
    const previousIsPlaying = isPlaying.value;
    isPlaying.value = state.isPlaying;
    
    // 等待音频加载完成再执行操作
    const performStateChange = () => {
      if (audioPlayer.value) {
        audioPlayer.value.currentTime = state.currentTime;
        if (state.isPlaying && !previousIsPlaying) {
          // 如果状态从暂停变为播放，则开始播放音频
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        } else if (!state.isPlaying) {
          // 如果是暂停状态，立即暂停并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState();
    };

    if (audioPlayer.value.readyState >= 2) { // HAVE_CURRENT_DATA
      performStateChange();
    } else {
      audioPlayer.value.addEventListener('loadeddata', performStateChange, { once: true });
    }
  } else if (currentMusic.value && currentMusic.value.id === state.currentMusic?.id) {
    // 如果audio元素还没准备好，等待并执行操作
    const previousIsPlaying = isPlaying.value;
    isPlaying.value = state.isPlaying;
    
    const handleMetadata = () => {
      if (audioPlayer.value) {
        audioPlayer.value.currentTime = state.currentTime;
        if (state.isPlaying && !previousIsPlaying) {
          // 如果状态从暂停变为播放，则开始播放音频
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        } else if (!state.isPlaying) {
          // 如果是暂停状态，立即暂停并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState();
    };

    audioPlayer.value?.addEventListener('loadedmetadata', handleMetadata, { once: true });
  }
}

// 处理播放列表更新事件
const handlePlaylistUpdated = (e) => {
  if (e.detail && e.detail.playlist) {
    playlist.value = e.detail.playlist;
    // 同时保存到 localStorage 以确保持久化
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
  }
}

/** 详情页试听片段时暂停底部播放器，避免双路音频与重复请求 */
const handlePauseGlobalPlayer = () => {
  if (audioPlayer.value && !audioPlayer.value.paused) {
    audioPlayer.value.pause()
    isPlaying.value = false
    updateGlobalPlayerState()
    updateMediaSessionPlaybackState()
  }
}

onMounted(() => {
  // 加载播放列表
  loadPlaylist()

  // 监听storage事件，以响应其他标签页的播放变化
  window.addEventListener('storage', handleStorageChange)
  // 监听自定义事件，以响应播放页面的状态变化
  window.addEventListener('playerStateChange', handlePlayerStateChange)
  // 监听强制播放事件
  window.addEventListener('forcePlay', handleForcePlay)
  // 监听播放列表更新事件
  window.addEventListener('playlistUpdated', handlePlaylistUpdated)
  // 监听URL hash变化，处理播放请求
  window.addEventListener('hashchange', handleHashChange)
  window.addEventListener('pauseGlobalPlayer', handlePauseGlobalPlayer)
  // 初始检查hash
  handleHashChange()
  
  // 初始化当前播放音乐
  const storedMusic = localStorage.getItem('currentPlayingMusic')
  if (storedMusic) {
    currentMusic.value = JSON.parse(storedMusic)
    
    // 如果当前音乐不在播放列表中，则添加进去
    if (currentMusic.value && playlist.value) {
      const existingIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id);
      if (existingIndex === -1) {
        // 如果当前音乐不在播放列表中，则添加到列表中
        playlist.value.push(currentMusic.value);
        // 同时保存到 localStorage
        localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
      }
    }
    
    // 初始化时加载歌词
    if (currentMusic.value) {
      loadLyrics(currentMusic.value.id)
      // 初始化媒体会话
      initializeMediaSession(currentMusic.value)
      // 检查收藏状态
      checkFavoriteStatus()
    }
  }
  
  // 初始化时从localStorage获取播放状态
  const storedState = localStorage.getItem('globalPlayerState');
  if (storedState) {
    const state = JSON.parse(storedState);
    currentTime.value = state.currentTime;
    duration.value = state.duration;
    progress.value = state.currentTime;
    
    // 如果全局播放器应该正在播放，则同步播放状态
    isPlaying.value = state.isPlaying;
    
    // 如果当前音乐存在且播放状态为播放，则尝试播放
    if (currentMusic.value && isPlaying.value && audioPlayer.value) {
      setTimeout(() => {
        handleForcePlay();
      }, 100); // 稍微延迟确保组件完全加载
    }
  }
  
  // 加载播放模式
  loadPlaybackMode()
  
  // 初始化媒体会话API
  initializeMediaSession()
})

// 加载播放列表
// 处理URL hash变化，响应播放请求
const handleHashChange = () => {
  const hash = window.location.hash

  if (hash.startsWith('#play=')) {
    // 单曲播放
    try {
      const musicData = JSON.parse(decodeURIComponent(hash.substring(6)))
      currentMusic.value = musicData
      localStorage.setItem('currentPlayingMusic', JSON.stringify(musicData))

      // 确保音乐在播放列表中
      if (playlist.value) {
        const existingIndex = playlist.value.findIndex(item => item.id === musicData.id)
        if (existingIndex === -1) {
          playlist.value.push(musicData)
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value))
        }
      }

      // 加载歌词并开始播放
      loadLyrics(musicData.id)
      if (audioPlayer.value) {
        audioPlayer.value.load()
        audioPlayer.value.play()
      }
      isPlaying.value = true

      // 清除hash
      history.replaceState(null, null, ' ')
    } catch (error) {
      console.error('解析播放数据失败:', error)
    }
  } else if (hash.startsWith('#playlist=')) {
    // 播放列表播放
    try {
      const params = hash.substring(1).split('&')
      const playlistData = JSON.parse(decodeURIComponent(params[0].substring(9)))
      const startIndex = parseInt(params[1].substring(6)) || 0

      // 更新播放列表
      playlist.value = playlistData
      localStorage.setItem('globalPlaylist', JSON.stringify(playlistData))

      // 播放指定索引的音乐
      if (playlistData[startIndex]) {
        currentMusic.value = playlistData[startIndex]
        localStorage.setItem('currentPlayingMusic', JSON.stringify(playlistData[startIndex]))

        loadLyrics(playlistData[startIndex].id)
        if (audioPlayer.value) {
          audioPlayer.value.load()
          audioPlayer.value.play()
        }
        isPlaying.value = true
      }

      // 清除hash
      history.replaceState(null, null, ' ')
    } catch (error) {
      console.error('解析播放列表数据失败:', error)
    }
  }
}

const loadPlaylist = async () => {
  try {
    // 首先尝试从 localStorage 读取播放列表
    const storedPlaylist = localStorage.getItem('globalPlaylist');
    if (storedPlaylist) {
      playlist.value = JSON.parse(storedPlaylist);
    } else {
      // 如果 localStorage 中没有播放列表，则从后端获取
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: '' })
      })
      if (response.ok) {
        const data = await response.json()
        if (data.success) {
          playlist.value = data.results || []
          // 同时保存到 localStorage
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
        }
      }
    }
  } catch (error) {
    console.error('加载播放列表失败:', error)
    
    // 如果出错，尝试从 localStorage 获取播放列表作为备选
    try {
      const storedPlaylist = localStorage.getItem('globalPlaylist');
      if (storedPlaylist) {
        playlist.value = JSON.parse(storedPlaylist);
      }
    } catch (localStorageError) {
      console.error('从localStorage加载播放列表也失败:', localStorageError);
    }
  }
}

// 初始化媒体会话API
const initializeMediaSession = (music = null) => {
  if ('mediaSession' in navigator) {
    try {
      // 设置媒体操作处理程序
      navigator.mediaSession.setActionHandler('play', () => {
        if (audioPlayer.value && currentMusic.value) {
          isPlaying.value = true
          updateGlobalPlayerState()
          fadeIn(audioPlayer.value)
          // 调用play()来开始播放
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        }
      })
      
      navigator.mediaSession.setActionHandler('pause', () => {
        if (audioPlayer.value && currentMusic.value) {
          isPlaying.value = false
          updateGlobalPlayerState()
          // 立即暂停音频并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
      })
      
      navigator.mediaSession.setActionHandler('previoustrack', () => {
        playPrevious()
      })
      
      navigator.mediaSession.setActionHandler('nexttrack', () => {
        playNext() // 手动点击，使用完整流程
      })
      
      navigator.mediaSession.setActionHandler('seekbackward', () => {
        if (audioPlayer.value) {
          audioPlayer.value.currentTime = Math.max(audioPlayer.value.currentTime - 10, 0);
        }
      })
      
      navigator.mediaSession.setActionHandler('seekforward', () => {
        if (audioPlayer.value) {
          audioPlayer.value.currentTime = Math.min(audioPlayer.value.currentTime + 10, audioPlayer.value.duration);
        }
      })
      
      // 设置当前播放的音乐元数据
      if (music || currentMusic.value) {
        updateMediaSessionMetadata(music || currentMusic.value)
      }
    } catch (error) {
      console.log('媒体会话API初始化失败:', error)
    }
  }
}

// 更新媒体会话元数据
const updateMediaSessionMetadata = (music) => {
  if ('mediaSession' in navigator && music) {
    try {
      const artwork = [
        { src: getCoverUrl(music.id), sizes: '96x96', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '128x128', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '192x192', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '256x256', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '384x384', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '512x512', type: 'image/jpeg' }
      ]
      
      navigator.mediaSession.metadata = new MediaMetadata({
        title: music.title || '未知标题',
        artist: music.artist || '未知艺术家',
        album: music.album || '未知专辑',
        artwork: artwork
      })
      
      // 更新播放状态
      navigator.mediaSession.playbackState = isPlaying.value ? 'playing' : 'paused'
    } catch (error) {
      console.log('更新媒体会话元数据失败:', error)
    }
  }
}

// 更新媒体会话播放状态
const updateMediaSessionPlaybackState = () => {
  if ('mediaSession' in navigator) {
    try {
      navigator.mediaSession.playbackState = isPlaying.value ? 'playing' : 'paused'
    } catch (error) {
      console.log('更新媒体会话播放状态失败:', error)
    }
  }
}

// 更新媒体会话播放位置
const updateMediaSessionPositionState = () => {
  if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
    try {
      // 确保 currentTime 不大于 duration
      const safeCurrentTime = Math.min(currentTime.value, duration.value);
      navigator.mediaSession.setPositionState({
        duration: duration.value,
        playbackRate: 1.0,
        position: safeCurrentTime
      });
    } catch (error) {
      console.log('更新媒体会话播放位置失败:', error)
    }
  }
}

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('playerStateChange', handlePlayerStateChange)
  window.removeEventListener('forcePlay', handleForcePlay)
  window.removeEventListener('playlistUpdated', handlePlaylistUpdated)
  window.removeEventListener('hashchange', handleHashChange)
  window.removeEventListener('pauseGlobalPlayer', handlePauseGlobalPlayer)
  
  // 清除媒体会话
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = null
    navigator.mediaSession.playbackState = 'none'
  }
})
</script>

<style scoped>
.global-player {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 80px;
  width: 100%;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  padding: 10px 20px;
  box-shadow: 0 -2px 20px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  transition: transform 0.3s ease;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 15px;
}

.global-player--chrome-dark {
  background: rgba(7, 6, 13, 0.88);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 -12px 40px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.global-player--chrome-dark .music-title {
  color: rgba(255, 255, 255, 0.92);
}

.global-player--chrome-dark .music-artist {
  color: #a5b4fc;
}

.global-player--chrome-dark .placeholder-text {
  color: rgba(255, 255, 255, 0.45);
}

.global-player--chrome-dark .time {
  color: rgba(255, 255, 255, 0.65);
}

.global-player--chrome-dark .progress-bar {
  background: rgba(255, 255, 255, 0.12);
}

.global-player--chrome-dark .lyrics-container {
  /* 仅保留与控件区的左侧分隔，避免再套一层四边 border 造成「多一圈外框」 */
  background: transparent;
  border: none;
  border-left: 1px solid rgba(255, 255, 255, 0.12);
}

.global-player--chrome-dark .lyric-text {
  color: rgba(255, 255, 255, 0.55);
}

.global-player--chrome-dark .lyric-line.active .lyric-text {
  color: #67e8f9;
}

.global-player--chrome-dark .playlist-container {
  background: rgba(12, 11, 22, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.55);
}

.global-player--chrome-dark .playlist-item {
  border-color: rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
}

.global-player--chrome-dark .playlist-item:hover {
  background: rgba(139, 92, 246, 0.15);
}

.global-player--chrome-dark .playlist-item.current {
  background: rgba(139, 92, 246, 0.22);
  border-color: rgba(167, 139, 250, 0.5);
}

.global-player--chrome-dark .playlist-item-title {
  color: rgba(255, 255, 255, 0.9);
}

.global-player--chrome-dark .playlist-item-artist {
  color: rgba(255, 255, 255, 0.5);
}

.global-player--chrome-dark .current-indicator {
  color: #67e8f9;
}

.global-player--chrome-dark .confirm-modal {
  background: rgba(18, 17, 30, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.88);
}

.global-player--chrome-dark .confirm-modal-header h3,
.global-player--chrome-dark .confirm-modal-body p {
  color: rgba(255, 255, 255, 0.88);
}

.player-content {
  display: flex;
  flex-direction: row;
  align-items: center;
  height: 100%;
  width: 100%;
  gap: 15px;
}

.cover-container {
  flex-shrink: 0;
}

.music-cover {
  width: 60px;
  height: 60px;
  object-fit: cover;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.placeholder-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6a5acd, #9370db);
  color: white;
  font-size: 1.2rem;
}

.music-info {
  flex-grow: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 200px;
}

.music-title {
  font-weight: bold;
  color: #5c4b7b;
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.placeholder-text {
  color: #aaa;
}

.music-artist {
  color: #9370db;
  font-size: 0.75rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.player-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-grow: 1;
  min-width: 200px;
  max-width: 600px;
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-grow: 1;
  min-width: 150px;
}

.time {
  color: #5c4b7b;
  font-size: 0.8rem;
  font-weight: bold;
  min-width: 40px;
  text-align: center;
}

.progress-bar {
  flex: 1;
  height: 5px;
  border-radius: 3px;
  background: rgba(106, 90, 205, 0.2);
  outline: none;
  -webkit-appearance: none;
}

.progress-bar::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #6a5acd;
  cursor: pointer;
}

.control-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.prev-btn, .next-btn, .mode-btn, .playlist-btn, .favorite-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.9), rgba(138, 43, 226, 0.9));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 8px rgba(106, 90, 205, 0.4);
  transition: all 0.3s ease;
  padding: 4px;
}

.prev-btn:hover, .next-btn:hover, .mode-btn:hover, .playlist-btn:hover, .favorite-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 12px rgba(106, 90, 205, 0.6);
}

.favorite-btn.favorite-active {
  background: linear-gradient(135deg, rgba(255, 69, 58, 0.9), rgba(220, 38, 38, 0.9));
  box-shadow: 0 4px 8px rgba(255, 69, 58, 0.4);
}

.favorite-btn.favorite-active:hover {
  box-shadow: 0 6px 12px rgba(255, 69, 58, 0.6);
}

.prev-btn:disabled, .next-btn:disabled, .play-pause-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.play-pause-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.9), rgba(138, 43, 226, 0.9));
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(106, 90, 205, 0.4);
  transition: all 0.3s ease;
  padding: 6px;
}

/* SVG图标样式 */
.btn-icon {
  width: 100%;
  height: 100%;
  object-fit: contain;
  fill: white; /* SVG图标填充颜色为白色 */
}

/* 歌词显示区域 */
.lyrics-container {
  flex: 1;
  min-width: 180px;
  max-width: 450px;
  height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding-left: 20px;
  border-left: 1px solid #eee;
  margin-left: 10px;
}

.lyrics-content {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: 2px;
  overflow: hidden;
  position: relative;
}

.lyric-line {
  color: #888;
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  width: 100%;
  text-align: left;
  line-height: 1.3;
  opacity: 0.5;
  position: relative;
  transition: opacity 0.3s ease;
  flex-shrink: 0;
}

.lyric-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
  font-size: 0.82rem;
  font-weight: 500;
}

.lyric-translation {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
  font-size: 0.72rem;
  color: #aaa;
  margin-top: 0;
}

.lyric-line.active {
  color: #6a5acd;
  font-weight: 600;
  opacity: 1;
}

.lyric-line.active .lyric-text {
  color: #6a5acd;
  font-size: 0.88rem;
  font-weight: 600;
}

.lyric-line.active .lyric-translation {
  color: #8888cc;
  opacity: 0.9;
}

/* 滚动进入动画 */
@keyframes scrollIn {
  0% {
    transform: translateX(20px);
    opacity: 0;
  }
  100% {
    transform: translateX(0);
    opacity: 1;
  }
}

/* 当歌词变成活动状态时的滚动动画 */
.lyric-line.active-enter {
  animation: scrollIn 0.6s ease-out;
}

/* 播放列表 */
.playlist-container {
  position: fixed;
  bottom: 80px;
  right: 20px;
  width: 350px;
  height: 400px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  z-index: 1001;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.playlist-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.9), rgba(138, 43, 226, 0.9));
  color: white;
}

.playlist-header h3 {
  margin: 0;
  font-size: 1rem;
}

.playlist-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.clear-playlist-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  padding: 5px 12px;
  border-radius: 15px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.3s ease;
}

.clear-playlist-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.05);
}

.clear-playlist-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.close-playlist {
  background: none;
  border: none;
  color: white;
  font-size: 1.2rem;
  cursor: pointer;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
}

.close-playlist:hover {
  background: rgba(255, 255, 255, 0.2);
}

.playlist-items {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.playlist-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.3s;
  border: 1px solid #eee;
}

.playlist-item:hover {
  background: rgba(106, 90, 205, 0.1);
}

.playlist-item.current {
  background: rgba(106, 90, 205, 0.2);
  border-color: #6a5acd;
}

.playlist-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.playlist-item-title {
  font-weight: bold;
  color: #333;
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playlist-item-artist {
  font-size: 0.8rem;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.current-indicator {
  color: #6a5acd;
  font-weight: bold;
  margin-left: 10px;
}

/* 隐藏audio元素 */
audio {
  display: none;
}

/* 确认清空播放列表模态框 */
.confirm-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.confirm-modal {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  min-width: 320px;
  max-width: 400px;
  overflow: hidden;
  animation: slideUp 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.confirm-modal-header {
  padding: 20px 20px 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

.confirm-modal-header h3 {
  margin: 0;
  font-size: 1.2rem;
  color: #5c4b7b;
  font-weight: 600;
}

.confirm-modal-body {
  padding: 20px;
}

.confirm-modal-body p {
  margin: 0;
  color: #666;
  font-size: 1rem;
  line-height: 1.5;
}

.confirm-modal-footer {
  padding: 15px 20px 20px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-btn {
  padding: 10px 24px;
  border-radius: 20px;
  border: none;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 80px;
}

.confirm-btn.cancel {
  background: rgba(106, 90, 205, 0.1);
  color: #6a5acd;
  border: 1px solid rgba(106, 90, 205, 0.3);
}

.confirm-btn.cancel:hover {
  background: rgba(106, 90, 205, 0.2);
  transform: translateY(-1px);
}

.confirm-btn.confirm {
  background: linear-gradient(135deg, rgba(255, 69, 58, 0.9), rgba(220, 38, 38, 0.9));
  color: white;
  box-shadow: 0 4px 12px rgba(255, 69, 58, 0.4);
}

.confirm-btn.confirm:hover {
  background: linear-gradient(135deg, rgba(220, 38, 38, 0.95), rgba(185, 28, 28, 0.95));
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(255, 69, 58, 0.6);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .global-player {
    height: 120px;
    padding: 10px 12px;
    flex-direction: column;
    justify-content: center;
    gap: 10px;
  }

  .player-content {
    display: flex;
    flex-direction: column;
    gap: 8px;
    height: auto;
  }

  .cover-container {
    display: none; /* 隐藏封面以节省空间 */
  }

  .music-info {
    max-width: 100%;
    text-align: center;
    order: 1;
    flex: 0 0 auto;
  }

  .music-title {
    font-size: 0.95rem;
  }

  .music-artist {
    font-size: 0.8rem;
  }

  .player-controls {
    width: 100%;
    max-width: 100%;
    order: 2;
    flex-direction: column;
    gap: 8px;
    flex: 1;
  }

  .progress-container {
    width: 100%;
    order: 1;
    gap: 8px;
  }

  .time {
    font-size: 0.75rem;
    min-width: 38px;
  }

  .progress-bar {
    height: 6px;
    flex: 1;
  }

  .progress-bar::-webkit-slider-thumb {
    width: 18px;
    height: 18px;
  }

  .control-buttons {
    width: 100%;
    justify-content: center;
    order: 2;
    gap: 12px;
  }

  .lyrics-container {
    display: none; /* 在小屏幕上隐藏歌词 */
  }

  /* 隐藏上一曲、下一曲和播放模式按钮 */
  .prev-btn,
  .next-btn,
  .mode-btn {
    display: none !important;
  }

  .play-pause-btn {
    width: 50px;
    height: 50px;
  }

  .mode-btn {
    width: 36px;
    height: 36px;
  }

  /* 隐藏收藏和播放列表按钮 */
  .favorite-btn,
  .playlist-btn {
    display: none !important;
  }

  /* 播放列表弹窗适配 */
  .playlist-container {
    width: calc(100% - 40px);
    right: 20px;
    left: 20px;
    bottom: 120px;
    height: 300px;
  }
}
</style>