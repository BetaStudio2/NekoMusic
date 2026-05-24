<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell">
      <section class="panel fav-head">
        <div class="fav-head__main">
          <h1 class="fav-title">我的收藏</h1>
          <p v-if="favorites.length === 0" class="fav-empty-hint">还没有收藏任何音乐</p>
        </div>
        <button
          v-if="favorites.length > 0"
          type="button"
          class="btn-play-all"
          title="播放全部"
          @click="playAllFavorites"
        >
          <svg class="play-all-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M8 5v14l11-7z" />
          </svg>
          播放全部
        </button>
      </section>

      <div v-if="favorites.length > 0" class="favorites-list">
        <div 
          v-for="music in favorites" 
          :key="music.id" 
          class="favorite-item"
        >
          <img 
            :src="getCoverUrl(music.id)" 
            :alt="music.title"
            class="favorite-cover"
            @error="handleImageError"
          />
          <div class="favorite-info" @click="playMusic(music)">
            <div class="favorite-title">{{ music.title }}</div>
            <div class="favorite-artist">作曲：{{ music.artist }}</div>
            <div class="favorite-album">专辑：{{ music.album || '未知专辑' }}</div>
          </div>
          <div class="favorite-actions">
            <button type="button" class="text-btn text-btn--play" title="播放" @click.stop="playMusic(music)">
              播放
            </button>
            <button type="button" class="text-btn text-btn--remove" title="取消收藏" @click.stop="removeFavorite(music.id)">
              移除
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const router = useRouter()
const favorites = ref([])

// 获取用户token
const getToken = () => {
  return localStorage.getItem('userToken')
}

// 检查用户是否登录
const isLoggedIn = () => {
  return !!getToken()
}

// 获取收藏列表
const fetchFavorites = async () => {
  if (!isLoggedIn()) {
    router.push('/login')
    return
  }
  
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success) {
      favorites.value = data.favorites || []
    } else {
      console.error('获取收藏列表失败:', data.message)
      if (response.status === 401) {
        // Token无效，跳转到登录页
        localStorage.removeItem('userToken')
        localStorage.removeItem('userInfo')
        router.push('/login')
      }
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error)
  }
}

// 播放音乐
const playMusic = async (music) => {
  // 先获取当前播放列表，如果没有则从后端获取
  let playlist = JSON.parse(localStorage.getItem('globalPlaylist') || '[]')
  
  // 检查当前音乐是否已经在播放列表中
  const existingIndex = playlist.findIndex(item => item.id === music.id)
  if (existingIndex === -1) {
    // 如果当前音乐不在播放列表中，则添加到列表中
    playlist.push(music)
    // 保存更新后的播放列表
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist))
    
    // 立即广播播放列表更新事件，确保 GlobalPlayer 组件收到通知
    const playlistEvent = new CustomEvent('playlistUpdated', {
      detail: {
        playlist: playlist
      }
    })
    window.dispatchEvent(playlistEvent)
  }
  
  // 设置当前播放的音乐到localStorage，触发全局播放器
  localStorage.setItem('currentPlayingMusic', JSON.stringify(music))
  
  // 立即更新播放状态为播放，并清零时间（从0.1开始）
  const state = {
    isPlaying: true,
    currentTime: 0.1,
    duration: music.duration || 0
  }
  localStorage.setItem('globalPlayerState', JSON.stringify(state))
  
  // 立即广播播放状态变化
  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: state.isPlaying,
      currentTime: state.currentTime,
      duration: state.duration,
      currentMusic: music
    }
  })
  window.dispatchEvent(event)
  
  // 立即触发强制播放
  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'))
  }, 10)
  
  // 再次确保播放器状态同步
  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'))
  }, 100)
}

// 播放全部收藏
const playAllFavorites = () => {
  if (favorites.value.length === 0) {
    toast.warning('收藏列表为空')
    return
  }
  
  // 将整个收藏列表设置为播放列表
  localStorage.setItem('globalPlaylist', JSON.stringify(favorites.value))
  
  // 广播播放列表更新事件
  const playlistEvent = new CustomEvent('playlistUpdated', {
    detail: {
      playlist: favorites.value
    }
  })
  window.dispatchEvent(playlistEvent)
  
  // 播放第一首
  if (favorites.value.length > 0) {
    playMusic(favorites.value[0])
  }
  
  toast.success(`已开始播放全部 ${favorites.value.length} 首收藏音乐`)
}

// 取消收藏
const removeFavorite = async (musicId) => {
  if (!confirm('确定要取消收藏这首音乐吗？')) {
    return
  }
  
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${musicId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success) {
      // 从列表中移除
      favorites.value = favorites.value.filter(m => m.id !== musicId)
      toast.success('取消收藏成功')
    } else {
      console.error('取消收藏失败:', data.message)
      toast.error('取消收藏失败: ' + data.message)
    }
  } catch (error) {
    console.error('取消收藏失败:', error)
    toast.error('取消收藏失败')
  }
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.fav-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: clamp(18px, 3vw, 24px) clamp(18px, 3vw, 22px);
  margin-bottom: 16px;
}

.fav-head__main {
  flex: 1;
  min-width: 0;
}

.fav-title {
  margin: 0 0 6px;
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text);
}

.fav-empty-hint {
  margin: 0;
  font-size: 0.92rem;
  color: var(--muted);
}

.btn-play-all {
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.3);
}

.play-all-icon {
  width: 18px;
  height: 18px;
}

.favorites-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.favorite-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.08), rgba(255, 255, 255, 0.03));
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  transition: background 0.15s var(--ease), border-color 0.15s var(--ease);
}

@media (hover: hover) {
  .favorite-item:hover {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(139, 92, 246, 0.28);
  }
}

.favorite-cover {
  width: 52px;
  height: 52px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  flex-shrink: 0;
}

.favorite-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
  min-width: 0;
  gap: 2px;
  cursor: pointer;
}

.favorite-title {
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.95rem;
}

.favorite-artist {
  color: var(--accent2);
  font-size: 0.82rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.favorite-album {
  color: var(--faint);
  font-size: 0.76rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.favorite-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.text-btn {
  font-family: inherit;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
}

.text-btn--play:hover {
  border-color: rgba(139, 92, 246, 0.45);
  background: rgba(139, 92, 246, 0.2);
}

.text-btn--remove {
  border-color: rgba(251, 113, 133, 0.35);
  background: rgba(244, 63, 94, 0.12);
  color: #fecdd3;
}

@media (max-width: 560px) {
  .favorite-item {
    flex-wrap: wrap;
  }

  .favorite-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>