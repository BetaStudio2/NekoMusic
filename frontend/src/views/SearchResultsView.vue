<template>
  <div class="search-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <main class="shell">
      <header class="page-head">
        <h1 class="page-title">搜索</h1>
        <p v-if="searchQuery" class="page-lede">
          关键词 <span class="page-query">「{{ searchQuery }}」</span>
        </p>
        <p v-else class="page-lede muted">在顶栏输入关键词后跳转至此查看结果</p>
      </header>

      <section class="panel" aria-live="polite">
        <!-- 加载中：searchResults 仍为 null -->
        <div v-if="searchQuery && searchResults === null" class="state state--loading">
          <div class="state__spinner" aria-hidden="true" />
          <p class="state__text">正在搜索…</p>
        </div>

        <ul
          v-else-if="searchResults && searchResults.length > 0"
          class="results"
          :aria-label="`共 ${searchResults.length} 条`"
        >
          <li
            v-for="result in searchResults"
            :key="result.id"
            class="result-row"
          >
            <img
              :src="getCoverUrl(result.id)"
              :alt="result.title"
              class="result-cover"
              width="56"
              height="56"
              loading="lazy"
              @error="handleImageError"
            />
            <div class="result-main" role="button" tabindex="0" @click="selectResult(result)" @keydown.enter.prevent="selectResult(result)">
              <span class="result-title">{{ result.title }}</span>
              <span class="result-meta">{{ result.artist }}</span>
              <span class="result-sub">{{ result.album || '未知专辑' }}</span>
            </div>
            <div class="result-actions">
              <button type="button" class="icon-btn icon-btn--play" title="播放" aria-label="播放" @click.stop="playMusic(result)">
                播放
              </button>
              <button
                type="button"
                class="icon-btn"
                :class="{ 'icon-btn--on': isFavorite(result.id) }"
                :title="isFavorite(result.id) ? '取消收藏' : '收藏'"
                :aria-label="isFavorite(result.id) ? '取消收藏' : '收藏'"
                @click.stop="toggleFavorite(result)"
              >
                {{ isFavorite(result.id) ? '已藏' : '收藏' }}
              </button>
              <button type="button" class="icon-btn icon-btn--dl" title="下载" aria-label="下载" @click.stop="downloadMusic(result)">
                下载
              </button>
            </div>
          </li>
        </ul>

        <div v-else-if="searchQuery && searchResults && searchResults.length === 0" class="state state--empty">
          <h2 class="state__title">未找到结果</h2>
          <p class="state__text">没有找到与「{{ searchQuery }}」匹配的曲目</p>
          <p class="state__hint">如需补全曲库可联系我们</p>
          <a class="state__link" href="mailto:support@cnmsb.xin">support@cnmsb.xin</a>
        </div>

        <div v-else class="state state--hint">
          <h2 class="state__title">开始搜索</h2>
          <p class="state__text">请先在顶部搜索框输入关键词并搜索</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const route = useRoute()
const searchQuery = ref('')
const searchResults = ref(null)
const favoriteMusicIds = ref(new Set())
const router = useRouter()

if (route.params.query) {
  searchQuery.value = decodeURIComponent(route.params.query)
}

const searchMusic = async (query) => {
  if (!query.trim()) return

  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: query
      })
    })

    const data = await response.json()

    if (response.ok) {
      if (data.success && data.results) {
        searchResults.value = data.results
      } else {
        searchResults.value = data.results || []
      }
    } else {
      console.error('搜索失败:', data.message || '未知错误')
      searchResults.value = []
    }
  } catch (error) {
    console.error('搜索请求失败:', error)
    searchResults.value = []
  }
}

const selectResult = (result) => {
  router.push(`/detail/${result.id}`)
}

const playMusic = async (result) => {
  let playlist = JSON.parse(localStorage.getItem('globalPlaylist') || '[]')

  const existingIndex = playlist.findIndex((item) => item.id === result.id)
  if (existingIndex === -1) {
    playlist.push(result)
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist))

    const playlistEvent = new CustomEvent('playlistUpdated', {
      detail: {
        playlist: playlist
      }
    })
    window.dispatchEvent(playlistEvent)
  }

  localStorage.setItem('currentPlayingMusic', JSON.stringify(result))

  const state = {
    isPlaying: true,
    currentTime: 0.1,
    duration: result.duration || 0
  }
  localStorage.setItem('globalPlayerState', JSON.stringify(state))

  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: state.isPlaying,
      currentTime: state.currentTime,
      duration: state.duration,
      currentMusic: result
    }
  })
  window.dispatchEvent(event)

  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'))
  }, 10)

  setTimeout(() => {
    window.dispatchEvent(new Event('forcePlay'))
  }, 100)
}

const downloadMusic = async (result) => {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/file/${result.id}`)
    const blob = await response.blob()

    const contentType = response.headers.get('Content-Type') || 'audio/mpeg'
    const extension = mapContentTypeToExtension(contentType)

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = result.filename || `${result.title}.${extension}`

    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('下载音乐失败:', error)

    const link = document.createElement('a')
    link.href = `${API_CONFIG.BASE_URL}/api/music/file/${result.id}`
    const extension = result.fileFormat || 'mp3'
    link.download = result.filename || `${result.title}.${extension}`
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }
}

const mapContentTypeToExtension = (contentType) => {
  const type = contentType.toLowerCase()
  if (type.includes('flac')) return 'flac'
  if (type.includes('wav')) return 'wav'
  if (type.includes('ogg')) return 'ogg'
  if (type.includes('aac')) return 'aac'
  if (type.includes('m4a') || type.includes('mp4')) return 'm4a'
  if (type.includes('wma')) return 'wma'
  if (type.includes('ape')) return 'ape'
  if (type.includes('mpeg') || type.includes('mp3')) return 'mp3'
  console.warn('未知的 Content-Type:', contentType, '使用 mp3')
  return 'mp3'
}

const getToken = () => {
  return localStorage.getItem('userToken')
}

const isLoggedIn = () => {
  return !!getToken()
}

const isFavorite = (musicId) => {
  return favoriteMusicIds.value.has(musicId)
}

const toggleFavorite = async (result) => {
  if (!isLoggedIn()) {
    toast.error('请先登录')
    return
  }

  const token = getToken()

  if (isFavorite(result.id)) {
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${result.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: token
        }
      })

      const data = await response.json()
      if (data.success) {
        favoriteMusicIds.value.delete(result.id)
        toast.success('取消收藏成功')
      } else {
        console.error('取消收藏失败:', data.message)
        toast.error('取消收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('取消收藏失败:', error)
      toast.error('取消收藏失败')
    }
  } else {
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
        method: 'POST',
        headers: {
          Authorization: token,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ musicId: result.id })
      })

      const data = await response.json()
      if (data.success) {
        favoriteMusicIds.value.add(result.id)
        toast.success('收藏成功')
      } else {
        console.error('收藏失败:', data.message)
        toast.error('收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('收藏失败:', error)
      toast.error('收藏失败')
    }
  }
}

const fetchFavorites = async () => {
  if (!isLoggedIn()) {
    return
  }

  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        Authorization: token
      }
    })

    const data = await response.json()
    if (data.success) {
      favoriteMusicIds.value = new Set(data.favorites.map((m) => m.id))
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error)
  }
}

const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

watch(
  () => route.params.query,
  (newQuery) => {
    if (newQuery) {
      searchQuery.value = decodeURIComponent(newQuery)
      searchResults.value = null
      searchMusic(searchQuery.value)
    } else {
      searchQuery.value = ''
      searchResults.value = null
    }
  }
)

onMounted(async () => {
  if (searchQuery.value && searchQuery.value !== 'undefined') {
    await searchMusic(searchQuery.value)
  }
  await fetchFavorites()
})
</script>

<style scoped>
.search-page {
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --line: rgba(255, 255, 255, 0.1);
  --accent: #8b5cf6;
  --accent2: #22d3ee;
  --accent3: #34d399;
  --radius: 16px;
  --radius-lg: 22px;
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
  opacity: 0.48;
  animation: blobFloat 22s var(--ease) infinite;
}

.ambient__blob--a {
  width: 400px;
  height: 400px;
  background: rgba(139, 92, 246, 0.4);
  top: -120px;
  right: -80px;
}

.ambient__blob--b {
  width: 340px;
  height: 340px;
  background: rgba(34, 211, 238, 0.22);
  bottom: -60px;
  left: -60px;
  animation-delay: -8s;
}

.ambient__blob--c {
  width: 260px;
  height: 260px;
  background: rgba(52, 211, 153, 0.16);
  top: 38%;
  left: 30%;
  animation-delay: -14s;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.3;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 55% at 50% 15%, black, transparent);
}

@keyframes blobFloat {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(-16px, 12px) scale(1.04);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient__blob {
    animation: none;
  }
}

.shell {
  position: relative;
  z-index: 1;
  width: min(880px, 100%);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 28px) clamp(14px, 3.5vw, 24px) 48px;
}

.page-head {
  margin-bottom: clamp(18px, 2.5vw, 24px);
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.page-title {
  margin: 0 0 6px;
  font-size: clamp(1.45rem, 3.2vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}

.page-lede {
  margin: 0;
  font-size: 0.92rem;
  color: var(--muted);
  line-height: 1.45;
}

.page-lede.muted {
  color: var(--faint);
}

.page-query {
  color: var(--accent2);
  font-weight: 700;
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  overflow: hidden;
}

.results {
  list-style: none;
  margin: 0;
  padding: 10px 0;
}

.result-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 18px;
  margin: 0 10px 8px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  transition:
    background 0.2s var(--ease),
    border-color 0.2s var(--ease),
    transform 0.2s var(--ease);
}

.result-row:last-child {
  margin-bottom: 12px;
}

@media (hover: hover) {
  .result-row:hover {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(139, 92, 246, 0.35);
    transform: translateY(-1px);
  }
}

.result-cover {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: 10px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.result-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
  text-align: left;
  border-radius: 10px;
  padding: 4px 6px;
  margin: -4px -6px;
  outline: none;
}

.result-main:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 2px;
}

.result-title {
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-meta {
  font-size: 0.84rem;
  color: var(--accent2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-sub {
  font-size: 0.78rem;
  color: var(--faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
  align-items: center;
}

.icon-btn {
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 0.76rem;
  font-weight: 600;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  transition:
    background 0.2s var(--ease),
    border-color 0.2s var(--ease),
    transform 0.2s var(--ease);
  white-space: nowrap;
}

@media (hover: hover) {
  .icon-btn:hover {
    background: rgba(255, 255, 255, 0.12);
    border-color: rgba(139, 92, 246, 0.4);
  }
}

.icon-btn--play {
  color: #0c0a14;
  border: none;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
}

.icon-btn--on {
  border-color: rgba(244, 114, 182, 0.45);
  color: #fbcfe8;
  background: rgba(244, 114, 182, 0.12);
}

.icon-btn--dl {
  border-color: rgba(52, 211, 153, 0.35);
  color: #a7f3d0;
}

.state {
  padding: 40px 24px;
  text-align: center;
}

.state--loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 48px 24px;
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

@media (prefers-reduced-motion: reduce) {
  .state__spinner {
    animation: none;
    border-color: rgba(34, 211, 238, 0.35);
  }
}

.state__title {
  margin: 0 0 10px;
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.state__text {
  margin: 0;
  font-size: 0.92rem;
  color: var(--muted);
  line-height: 1.55;
}

.state__hint {
  margin: 16px 0 6px;
  font-size: 0.82rem;
  color: var(--faint);
}

.state__link {
  color: var(--accent2);
  font-weight: 600;
  text-decoration: none;
}

.state__link:hover {
  text-decoration: underline;
}

.state--empty .state__title {
  color: rgba(252, 211, 77, 0.95);
}

.state--hint .state__title {
  color: var(--text);
}

@media (max-width: 560px) {
  .result-row {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .result-actions {
    width: 100%;
    justify-content: flex-end;
    padding-top: 4px;
  }

  .icon-btn {
    flex: 1;
    justify-content: center;
    min-width: 0;
  }
}
</style>
