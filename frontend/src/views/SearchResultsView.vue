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

      <div v-if="searchQuery && searchLoading" class="search-loading-wrap">
        <section class="panel search-loading" aria-live="polite">
          <div class="state state--loading">
            <div class="state__spinner" aria-hidden="true" />
            <p class="state__text">正在搜索单曲、歌单与艺人…</p>
          </div>
        </section>
      </div>

      <div v-else-if="searchQuery && !searchLoading && hasAnyResults" class="search-grid">
        <!-- 单曲栏 -->
        <section class="search-column search-column--tracks panel" aria-labelledby="sec-tracks">
          <header class="column-head">
            <h2 id="sec-tracks" class="column-title">单曲</h2>
            <span class="column-badge">{{ musicResults.length }} 首</span>
          </header>
          <div class="column-body">
            <template v-if="musicResults.length > 0">
              <ul class="results results--dense" aria-label="单曲列表">
                <li v-for="result in musicResults" :key="'m-' + result.id" class="result-row">
                  <img
                    :src="getCoverUrl(result.id)"
                    :alt="result.title"
                    class="result-cover"
                    width="48"
                    height="48"
                    loading="lazy"
                    @error="handleImageError"
                  />
                  <div
                    class="result-main"
                    role="button"
                    tabindex="0"
                    @click="goMusicDetail(result)"
                    @keydown.enter.prevent="goMusicDetail(result)"
                  >
                    <span class="result-title">
                      <span class="result-title__text">{{ result.title }}</span>
                      <LrcBadge :show="!!result.lrc" />
                    </span>
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
            </template>
            <p v-else class="col-empty">本关键词下暂无单曲</p>
          </div>
        </section>

        <!-- 歌单栏 -->
        <section class="search-column search-column--playlists panel" aria-labelledby="sec-playlists">
          <header class="column-head">
            <h2 id="sec-playlists" class="column-title">歌单</h2>
            <span class="column-badge">{{ playlistResults.length }} 个</span>
          </header>
          <div class="column-body">
            <template v-if="playlistResults.length > 0">
              <ul class="results results--dense" aria-label="歌单列表">
                <li
                  v-for="pl in playlistResults"
                  :key="'p-' + pl.id"
                  class="result-row result-row--playlist"
                >
                  <img
                    :src="playlistCoverUrl(pl)"
                    :alt="pl.name"
                    class="result-cover"
                    width="48"
                    height="48"
                    loading="lazy"
                    @error="handleImageError"
                  />
                  <div
                    class="result-main"
                    role="button"
                    tabindex="0"
                    @click="goPlaylist(pl)"
                    @keydown.enter.prevent="goPlaylist(pl)"
                  >
                    <span class="result-title">{{ pl.name }}</span>
                    <span class="result-meta">{{ pl.musicCount ?? 0 }} 首</span>
                    <span class="result-sub">{{ pl.description || '暂无简介' }}</span>
                  </div>
                  <div class="result-actions">
                    <button type="button" class="icon-btn icon-btn--play" title="打开歌单" aria-label="打开歌单" @click.stop="goPlaylist(pl)">
                      打开
                    </button>
                  </div>
                </li>
              </ul>
            </template>
            <p v-else class="col-empty">本关键词下暂无歌单</p>
          </div>
        </section>

        <!-- 艺人栏 -->
        <section class="search-column search-column--artist panel" aria-labelledby="sec-artist">
          <header class="column-head">
            <h2 id="sec-artist" class="column-title">艺人</h2>
            <span v-if="artistPayload.name" class="column-badge">{{ artistTrackCount }} 首</span>
            <span v-else class="column-badge">—</span>
          </header>
          <div class="column-body">
            <template v-if="artistPayload.name">
              <p class="column-artist-name">{{ artistPayload.name }}</p>
              <ul v-if="artistTracks.length" class="results results--dense" :aria-label="`${artistPayload.name} 的作品`">
                <li v-for="result in artistTracks" :key="'a-' + result.id" class="result-row">
                  <img
                    :src="trackCoverUrl(result)"
                    :alt="result.title"
                    class="result-cover"
                    width="48"
                    height="48"
                    loading="lazy"
                    @error="handleImageError"
                  />
                  <div
                    class="result-main"
                    role="button"
                    tabindex="0"
                    @click="goMusicDetail(result)"
                    @keydown.enter.prevent="goMusicDetail(result)"
                  >
                    <span class="result-title">{{ result.title }}</span>
                    <span class="result-meta">{{ result.artist }}</span>
                    <span class="result-sub">{{ result.album || '未知专辑' }}</span>
                  </div>
                  <div class="result-actions">
                    <button type="button" class="icon-btn icon-btn--play" title="播放" aria-label="播放" @click.stop="playMusic(normalizeTrack(result))">
                      播放
                    </button>
                    <button
                      type="button"
                      class="icon-btn"
                      :class="{ 'icon-btn--on': isFavorite(result.id) }"
                      :title="isFavorite(result.id) ? '取消收藏' : '收藏'"
                      :aria-label="isFavorite(result.id) ? '取消收藏' : '收藏'"
                      @click.stop="toggleFavorite(normalizeTrack(result))"
                    >
                      {{ isFavorite(result.id) ? '已藏' : '收藏' }}
                    </button>
                    <button type="button" class="icon-btn icon-btn--dl" title="下载" aria-label="下载" @click.stop="downloadMusic(normalizeTrack(result))">
                      下载
                    </button>
                  </div>
                </li>
              </ul>
              <p v-else class="col-empty col-empty--tight">暂无该艺人下的曲目列表</p>
            </template>
            <p v-else class="col-empty">本关键词下暂无匹配艺人</p>
          </div>
        </section>
      </div>

      <section v-else-if="searchQuery && !searchLoading && !hasAnyResults" class="panel state-panel state state--empty">
        <h2 class="state__title">未找到结果</h2>
        <p class="state__text">没有找到与「{{ searchQuery }}」匹配的单曲、歌单或艺人</p>
        <p class="state__hint">如需补全曲库可联系我们</p>
        <a class="state__link" href="mailto:support@cnmsb.xin">support@cnmsb.xin</a>
      </section>

      <section v-else class="panel state-panel state state--hint">
        <h2 class="state__title">开始搜索</h2>
        <p class="state__text">请先在顶部搜索框输入关键词并搜索</p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import LrcBadge from '@/components/LrcBadge.vue'
import { useToast } from 'vue-toastification'
const toast = useToast()

const route = useRoute()
const router = useRouter()
const searchQuery = ref('')
const searchLoading = ref(false)
const musicResults = ref([])
const playlistResults = ref([])
const artistPayload = ref({ name: '', musicCount: 0, musicList: [] })
const favoriteMusicIds = ref(new Set())

if (route.params.query) {
  searchQuery.value = decodeURIComponent(route.params.query)
}

const hasAnyResults = computed(
  () =>
    musicResults.value.length > 0 ||
    playlistResults.value.length > 0 ||
    !!(artistPayload.value.name && String(artistPayload.value.name).trim())
)

const artistTracks = computed(() => {
  const raw = artistPayload.value.musicList
  return Array.isArray(raw) ? raw : []
})

const artistTrackCount = computed(() => {
  const n = artistPayload.value.musicCount
  if (typeof n === 'number' && n >= 0) return n
  return artistTracks.value.length
})

const jsonHeaders = { 'Content-Type': 'application/json' }

const fetchMusicResults = async (query) => {
  const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ query })
  })
  const data = await response.json()
  if (response.ok && data.success && Array.isArray(data.results)) return data.results
  if (response.ok && Array.isArray(data.results)) return data.results
  return []
}

const fetchPlaylistResults = async (query) => {
  const response = await fetch(`${API_CONFIG.BASE_URL}/api/playlists/search`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ query })
  })
  const data = await response.json()
  if (response.ok && data.success && Array.isArray(data.results)) return data.results
  return []
}

const fetchArtistPayload = async (query) => {
  const response = await fetch(`${API_CONFIG.BASE_URL}/api/artists/search`, {
    method: 'POST',
    headers: jsonHeaders,
    body: JSON.stringify({ query })
  })
  const data = await response.json()
  if (!response.ok || !data.success || !data.artist) {
    return { name: '', musicCount: 0, musicList: [] }
  }
  const a = data.artist
  const name = typeof a.name === 'string' ? a.name.trim() : ''
  const musicList = Array.isArray(a.musicList) ? a.musicList : []
  const musicCount = typeof a.musicCount === 'number' ? a.musicCount : musicList.length
  return { name, musicCount, musicList }
}

const runSearch = async (query) => {
  const q = (query || '').trim()
  if (!q) return

  searchLoading.value = true
  try {
    const [tracks, playlists, artist] = await Promise.all([
      fetchMusicResults(q),
      fetchPlaylistResults(q),
      fetchArtistPayload(q)
    ])
    musicResults.value = tracks
    playlistResults.value = playlists
    artistPayload.value = artist
  } catch (e) {
    console.error('搜索失败:', e)
    toast.error('搜索失败，请稍后重试')
    musicResults.value = []
    playlistResults.value = []
    artistPayload.value = { name: '', musicCount: 0, musicList: [] }
  } finally {
    searchLoading.value = false
  }
}

const goMusicDetail = (result) => {
  router.push(`/detail/${result.id}`)
}

const goPlaylist = (pl) => {
  router.push(`/playlist/${pl.id}`)
}

/** 艺人搜索返回的曲目字段与单曲接口略有差异，统一成播放器/收藏可用的形状 */
const normalizeTrack = (m) => {
  const id = m.id
  const fileFormat = m.fileFormat ?? m.file_format ?? 'mp3'
  return {
    id,
    title: m.title,
    artist: m.artist,
    album: m.album ?? '',
    duration: m.duration ?? 0,
    fileFormat,
    filename: m.filename ?? `${m.title}.${fileFormat}`
  }
}

const playlistCoverUrl = (pl) => {
  const u = pl.firstMusicCover
  if (!u) return `${API_CONFIG.BASE_URL}/api/music/cover/0`
  if (typeof u === 'string' && u.startsWith('http')) return u
  const path = typeof u === 'string' && u.startsWith('/') ? u : `/${u}`
  return `${API_CONFIG.BASE_URL}${path}`
}

const trackCoverUrl = (m) => {
  const u = m.coverPath
  if (u && typeof u === 'string') {
    if (u.startsWith('http')) return u
    const path = u.startsWith('/') ? u : `/${u}`
    return `${API_CONFIG.BASE_URL}${path}`
  }
  return getCoverUrl(m.id)
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
      runSearch(searchQuery.value)
    } else {
      searchQuery.value = ''
      searchLoading.value = false
      musicResults.value = []
      playlistResults.value = []
      artistPayload.value = { name: '', musicCount: 0, musicList: [] }
    }
  }
)

onMounted(async () => {
  if (searchQuery.value && searchQuery.value !== 'undefined') {
    await runSearch(searchQuery.value)
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
  width: min(1320px, 100%);
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

.search-loading-wrap {
  max-width: 520px;
  margin: 0 auto;
}

.search-loading {
  padding: 28px 24px;
}

.search-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(14px, 2vw, 22px);
  align-items: stretch;
}

@media (max-width: 1024px) {
  .search-grid {
    grid-template-columns: 1fr;
  }

  .search-column {
    min-height: auto;
    max-height: none;
  }

  .search-column .column-body {
    max-height: min(52vh, 520px);
  }
}

.search-column {
  --col-accent: var(--accent);
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: min(62vh, 640px);
  max-height: min(78vh, 820px);
  overflow: hidden;
}

.search-column--tracks {
  --col-accent: #a78bfa;
}

.search-column--playlists {
  --col-accent: #22d3ee;
}

.search-column--artist {
  --col-accent: #34d399;
}

.column-head {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--line);
  border-left: 4px solid var(--col-accent);
  margin-left: 0;
  background: rgba(0, 0, 0, 0.12);
}

.column-title {
  margin: 0;
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.column-badge {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: rgba(139, 92, 246, 0.45) rgba(255, 255, 255, 0.06);
  padding-bottom: 8px;
}

.column-body::-webkit-scrollbar {
  width: 6px;
}

.column-body::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.04);
}

.column-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 999px;
}

.column-artist-name {
  margin: 12px 14px 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--accent2);
  border-radius: var(--radius);
  border: 1px solid rgba(34, 211, 238, 0.25);
  background: rgba(34, 211, 238, 0.08);
}

.col-empty {
  margin: 24px 16px;
  padding: 20px 14px;
  font-size: 0.86rem;
  line-height: 1.5;
  color: var(--faint);
  text-align: center;
  border-radius: var(--radius);
  border: 1px dashed rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.03);
}

.col-empty--tight {
  margin-top: 8px;
}

.state-panel {
  padding: 40px 24px;
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

.results--dense {
  padding: 6px 0 10px;
}

.search-column .result-row {
  margin: 0 8px 6px;
  padding: 10px 10px;
  gap: 10px;
}

.search-column .result-cover {
  width: 48px;
  height: 48px;
}

.search-column .result-actions {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.search-column .icon-btn {
  padding: 6px 8px;
  font-size: 0.72rem;
  width: 100%;
  box-sizing: border-box;
}

@media (min-width: 1025px) {
  .search-column .result-actions {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: flex-end;
    width: auto;
  }

  .search-column .icon-btn {
    width: auto;
  }
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text);
}

.result-title__text {
  min-width: 0;
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
