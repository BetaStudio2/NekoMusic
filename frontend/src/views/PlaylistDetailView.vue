<template>
  <div class="pl-detail-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__grid" />
    </div>

    <div v-if="isMobile && showBanner" class="mobile-download-banner">
      <div class="banner-content">
        <span class="banner-text">下载 App 获得更好体验</span>
        <a href="/download" class="banner-link">立即下载</a>
        <button type="button" class="banner-close" aria-label="关闭" @click="closeBanner">×</button>
      </div>
    </div>

    <main class="shell" :class="{ 'shell--banner': isMobile && showBanner }">
      <section class="panel hero-panel">
        <button v-if="!isMobile" type="button" class="btn-back" @click="goBack">返回</button>
        <div class="hero-main">
          <div class="hero-cover">
            <img :src="getPlaylistCover()" alt="" @error="handlePlaylistCoverError" />
          </div>
          <div class="hero-text">
            <h1 class="hero-title">{{ playlist?.name || '歌单' }}</h1>
            <p v-if="playlist?.description" class="hero-desc">{{ playlist.description }}</p>
            <p class="hero-meta">{{ playlist?.musicCount ?? 0 }} 首</p>
          </div>
        </div>
        <div class="hero-actions">
          <button
            v-if="musicList.length > 0"
            type="button"
            class="btn-play-all"
            title="播放全部"
            @click="playAll"
          >
            <svg class="play-all-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M8 5v14l11-7z" />
            </svg>
            播放全部
          </button>
          <button v-if="isOwner" type="button" class="btn-ghost" @click="showAddMusicDialog">添加音乐</button>
        </div>
      </section>

      <section v-if="loading" class="panel state-panel">
        <div class="state state--loading">
          <div class="state__spinner" aria-hidden="true" />
          <p class="state__text">加载中…</p>
        </div>
      </section>

      <section v-else-if="musicList.length > 0" class="panel list-panel">
        <div
          v-for="(music, index) in musicList"
          :key="music.id"
          class="music-row"
        >
          <span class="music-idx">{{ index + 1 }}</span>
          <button type="button" class="music-cover-btn" @click="playMusic(music)">
            <img :src="getCoverUrl(music.id)" :alt="music.title" @error="handleCoverError" />
          </button>
          <button type="button" class="music-text-btn" @click="playMusic(music)">
            <span class="music-title">{{ music.title }}</span>
            <span class="music-artist">{{ music.artist }}</span>
          </button>
          <span class="music-dur">{{ formatDuration(music.duration) }}</span>
          <button
            v-if="isOwner"
            type="button"
            class="btn-remove"
            title="从歌单移除"
            aria-label="移除"
            @click="removeMusic(music.id)"
          >
            移除
          </button>
        </div>
      </section>

      <section v-else class="panel state-panel state-empty">
        <h2 class="state__title">歌单暂无音乐</h2>
        <p v-if="isOwner" class="state__text">点击「添加音乐」搜索并加入曲目。</p>
        <p v-else class="state__text">该歌单还没有添加曲目。</p>
        <button v-if="isOwner" type="button" class="btn-play-all" @click="showAddMusicDialog">添加音乐</button>
      </section>
    </main>

    <div v-if="showAddMusic" class="modal-overlay" @click.self="closeAddMusicDialog">
      <div class="modal panel" role="dialog" aria-labelledby="add-music-title" @click.stop>
        <button type="button" class="modal-close" aria-label="关闭" @click="closeAddMusicDialog">×</button>
        <h3 id="add-music-title" class="modal-title">添加音乐</h3>
        <input
          v-model="searchQuery"
          class="modal-search"
          type="search"
          placeholder="搜索曲名或艺人…"
          autocomplete="off"
          @input="handleSearch"
        />
        <div v-if="searchResults.length > 0" class="modal-results">
          <button
            v-for="music in searchResults"
            :key="music.id"
            type="button"
            class="modal-result-row"
            @click="addMusicToPlaylist(music)"
          >
            <img class="modal-result-cover" :src="getCoverUrl(music.id)" :alt="''" @error="handleCoverError" />
            <span class="modal-result-text">
              <span class="modal-result-title">{{ music.title }}</span>
              <span class="modal-result-artist">{{ music.artist }}</span>
            </span>
            <span class="modal-result-add">加入</span>
          </button>
        </div>
        <p v-else-if="searchQuery.trim()" class="modal-empty">未找到相关音乐</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
import { tryOpenPlaylistInApp } from '@/utils/nativeAppOpen.js'

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
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
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
    if (data.success && Array.isArray(data.results)) {
      searchResults.value = data.results
    } else {
      searchResults.value = []
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
  if (typeof localStorage !== 'undefined' && localStorage.getItem('mobileDownloadBannerClosed') === 'true') {
    showBanner.value = false
  }
  if (isMobile.value && playlistId.value) {
    tryOpenPlaylistInApp(playlistId.value)
  }
  fetchPlaylistDetail()
})
</script>

<style scoped>
.pl-detail-page {
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --line: rgba(255, 255, 255, 0.1);
  --accent2: #69c8df;
  --radius: 14px;
  --radius-lg: 20px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  --shadow: 0 24px 80px rgba(0, 0, 0, 0.45);

  position: relative;
  min-height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  color: var(--text);
  background: transparent;
}

.ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.ambient__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.45;
}

.ambient__blob--a {
  width: 400px;
  height: 400px;
  background: rgba(105, 200, 223, 0.36);
  top: -120px;
  right: -80px;
}

.ambient__blob--b {
  width: 320px;
  height: 320px;
  background: rgba(105, 200, 223, 0.18);
  bottom: -40px;
  left: -50px;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.26;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 55% at 50% 12%, black, transparent);
}

.mobile-download-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: linear-gradient(135deg, rgba(30, 27, 50, 0.96), rgba(15, 16, 32, 0.98));
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 11px 16px;
  max-width: 1100px;
  margin: 0 auto;
  flex-wrap: wrap;
}

.banner-text {
  color: rgba(255, 255, 255, 0.88);
  font-size: 0.86rem;
  font-weight: 600;
}

.banner-link {
  background: linear-gradient(135deg, #69c8df, #4aa9c0);
  color: #fff;
  text-decoration: none;
  padding: 7px 16px;
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: 700;
  white-space: nowrap;
}

.banner-close {
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.shell {
  position: relative;
  z-index: 1;
  width: min(920px, 100%);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 28px) clamp(14px, 3.5vw, 24px) 48px;
}

.shell--banner {
  padding-top: 56px;
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(105, 200, 223, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  margin-bottom: 16px;
}

.hero-panel {
  padding: 18px 20px 20px;
}

.btn-back {
  font-family: inherit;
  display: inline-flex;
  margin-bottom: 14px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
}

.hero-main {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  align-items: center;
}

.hero-cover {
  width: 120px;
  height: 120px;
  border-radius: var(--radius);
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

.hero-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hero-text {
  flex: 1;
  min-width: 0;
}

.hero-title {
  margin: 0 0 8px;
  font-size: clamp(1.35rem, 3vw, 1.75rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.hero-desc {
  margin: 0 0 8px;
  font-size: 0.88rem;
  color: var(--muted);
  line-height: 1.45;
}

.hero-meta {
  margin: 0;
  font-size: 0.86rem;
  color: var(--accent2);
  font-weight: 600;
}

.hero-actions {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.btn-play-all {
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 18px;
  border: none;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.88rem;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #9beaff, var(--accent2));
  box-shadow: 0 8px 24px rgba(105, 200, 223, 0.3);
}

.play-all-icon {
  width: 18px;
  height: 18px;
}

.btn-ghost {
  font-family: inherit;
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  font-weight: 600;
  font-size: 0.86rem;
  cursor: pointer;
}

.state-panel {
  padding: 36px 24px;
  text-align: center;
}

.state--loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.state__spinner {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--accent2);
  animation: spin 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state__text {
  margin: 0;
  color: var(--muted);
  font-size: 0.92rem;
}

.state-empty .state__title {
  margin: 0 0 10px;
  font-size: 1.15rem;
  font-weight: 800;
}

.state-empty .state__text {
  margin: 0 0 18px;
  line-height: 1.5;
}

.list-panel {
  padding: 6px 0;
  overflow: hidden;
}

.music-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  transition: background 0.15s var(--ease);
}

.music-row:last-child {
  border-bottom: none;
}

@media (hover: hover) {
  .music-row:hover {
    background: rgba(255, 255, 255, 0.05);
  }
}

.music-idx {
  width: 28px;
  text-align: center;
  font-size: 0.82rem;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.music-cover-btn {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  overflow: hidden;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.music-cover-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.music-text-btn {
  flex: 1;
  min-width: 0;
  text-align: left;
  border: none;
  background: none;
  cursor: pointer;
  padding: 4px 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.music-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.music-artist {
  font-size: 0.8rem;
  color: var(--accent2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.music-dur {
  font-size: 0.78rem;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.btn-remove {
  font-family: inherit;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(251, 113, 133, 0.35);
  background: rgba(244, 63, 94, 0.12);
  color: #fecdd3;
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(6, 5, 12, 0.72);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal {
  position: relative;
  width: min(480px, 100%);
  max-height: min(85vh, 640px);
  display: flex;
  flex-direction: column;
  padding: 22px 20px 18px;
  color: var(--text);
}

.modal-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: none;
  background: none;
  color: var(--faint);
  font-size: 1.45rem;
  line-height: 1;
  cursor: pointer;
}

.modal-title {
  margin: 0 0 14px;
  font-size: 1.1rem;
  font-weight: 800;
}

.modal-search {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 14px;
  margin-bottom: 12px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  color: var(--text);
  font-size: 0.92rem;
}

.modal-search::placeholder {
  color: var(--faint);
}

.modal-search:focus {
  outline: none;
  border-color: rgba(105, 200, 223, 0.45);
}

.modal-results {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.modal-result-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  cursor: pointer;
  text-align: left;
  color: inherit;
  font: inherit;
}

@media (hover: hover) {
  .modal-result-row:hover {
    border-color: rgba(105, 200, 223, 0.35);
    background: rgba(255, 255, 255, 0.07);
  }
}

.modal-result-cover {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.modal-result-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.modal-result-title {
  font-size: 0.88rem;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.modal-result-artist {
  font-size: 0.78rem;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.modal-result-add {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--accent2);
  flex-shrink: 0;
}

.modal-empty {
  margin: 16px 0 0;
  text-align: center;
  font-size: 0.86rem;
  color: var(--faint);
}

@media (max-width: 600px) {
  .hero-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-actions {
    flex-direction: column;
  }

  .btn-play-all,
  .btn-ghost {
    width: 100%;
    justify-content: center;
  }

  .music-row {
    flex-wrap: wrap;
  }

  .music-dur {
    margin-left: auto;
  }

  .btn-remove {
    width: 100%;
    margin-top: 4px;
  }
}
</style>
