<template>
  <div class="pl-detail">
    <AmbientBackdrop />

    <PageShell width="default">
      <!-- 歌单头 -->
      <section class="hero">
        <NButton
          class="hero__back"
          variant="ghost"
          size="sm"
          icon="arrow-left"
          @click="goBack"
        >
          {{ $t('playlistDetail.back') }}
        </NButton>

        <div class="hero__main">
          <div class="hero__cover">
            <img
              :src="getPlaylistCover()"
              :srcset="coverSrcset(getPlaylistCover())"
              sizes="160px"
              width="160"
              height="160"
              alt=""
              @error="handlePlaylistCoverError"
            />
          </div>
          <div class="hero__text">
            <h1 class="hero__title">{{ playlist?.name || $t('playlistDetail.fallbackName') }}</h1>
            <p v-if="playlist?.description" class="hero__desc">{{ playlist.description }}</p>
            <p class="hero__meta">
              <NIcon name="music" :size="14" />
              {{ $t('track.songCount', { count: playlist?.musicCount ?? 0 }) }}
            </p>
          </div>
        </div>

        <div class="hero__actions">
          <NButton
            v-if="musicList.length > 0"
            variant="primary"
            icon="play"
            @click="playAll"
          >
            {{ $t('track.playAll') }}
          </NButton>
          <NButton v-if="isOwner" variant="secondary" icon="plus" @click="showAddMusicDialog">
            {{ $t('playlistDetail.addMusic') }}
          </NButton>
        </div>
      </section>

      <!-- 加载 -->
      <div v-if="loading" class="state">
        <NSpinner :size="28" />
      </div>

      <!-- 曲目列表 -->
      <div v-else-if="musicList.length > 0" class="list">
        <article v-for="(music, index) in musicList" :key="music.id" class="row">
          <span class="row__idx">{{ index + 1 }}</span>
          <button type="button" class="row__cover-btn" @click="playMusic(music)">
            <img
              :src="getCoverUrl(music.id)"
              :srcset="coverSrcset(getCoverUrl(music.id))"
              sizes="48px"
              width="48"
              height="48"
              :alt="music.title"
              loading="lazy"
              @error="handleCoverError"
            />
            <span class="row__cover-play"><NIcon name="play" :size="14" /></span>
          </button>
          <button type="button" class="row__info" @click="playMusic(music)">
            <span class="row__title">
              <span class="row__title-text">{{ music.title }}</span>
              <NowPlayingTag v-if="isCurrentMusic(music.id)" />
              <QualityBadge :quality="music.maxQuality" />
            </span>
            <span class="row__artist">{{ music.artist }}</span>
          </button>
          <span class="row__dur">{{ formatDuration(music.duration) }}</span>
          <NButton
            v-if="isOwner"
            size="sm"
            variant="danger"
            icon="trash-2"
            :title="$t('playlistDetail.removeFromPlaylist')"
            @click="removeMusic(music.id)"
          />
        </article>
      </div>

      <!-- 空状态 -->
      <NCard v-else pad="lg" class="empty">
        <span class="empty__icon"><NIcon name="list-music" :size="26" /></span>
        <h2 class="empty__title">{{ $t('playlistDetail.emptyTitle') }}</h2>
        <p class="empty__text">
          <template v-if="isOwner">{{ $t('playlistDetail.emptyOwner') }}</template>
          <template v-else>{{ $t('playlistDetail.emptyOther') }}</template>
        </p>
        <NButton v-if="isOwner" variant="primary" icon="plus" @click="showAddMusicDialog">
          {{ $t('playlistDetail.addMusic') }}
        </NButton>
      </NCard>
    </PageShell>

    <!-- 添加音乐 -->
    <NModal v-model="showAddMusic" :title="$t('playlistDetail.addMusic')" size="md" @close="closeAddMusicDialog">
      <NInput
        v-model="searchQuery"
        icon="search"
        :placeholder="$t('playlistDetail.searchPlaceholder')"
        autocomplete="off"
        clearable
        @update:model-value="handleSearch"
      />

      <div v-if="searchResults.length > 0" class="results">
        <button
          v-for="music in searchResults"
          :key="music.id"
          type="button"
          class="results__row"
          @click="addMusicToPlaylist(music)"
        >
          <img
            class="results__cover"
            :src="getCoverUrl(music.id)"
            :srcset="coverSrcset(getCoverUrl(music.id))"
            sizes="44px"
            width="44"
            height="44"
            alt=""
            loading="lazy"
            @error="handleCoverError"
          />
          <span class="results__text">
            <span class="results__title">
              <span class="results__title-text">{{ music.title }}</span>
              <QualityBadge :quality="music.maxQuality" />
            </span>
            <span class="results__artist">{{ music.artist }}</span>
          </span>
          <span class="results__add"><NIcon name="plus" :size="14" />{{ $t('playlistDetail.join') }}</span>
        </button>
      </div>
      <p v-else-if="searchQuery.trim()" class="results__empty">{{ $t('playlistDetail.noResults') }}</p>
      <p v-else class="results__empty">{{ $t('playlistDetail.searchHint') }}</p>
    </NModal>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import QualityBadge from '@/components/QualityBadge.vue'
import { NButton, NCard, NInput, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { playTracks, playTrackInList, isCurrentMusic } from '@/composables/usePlaybackBridge'
import NowPlayingTag from '@/components/NowPlayingTag.vue'
import { tryOpenPlaylistInApp } from '@/utils/nativeAppOpen.js'
import { getUser } from '@/utils/userStore.js'
import { coverSrcset } from '@/utils/coverImage'
import { t } from '@/i18n'
import { avatarUrl } from '@/utils/userAvatar.js'
import { applyPageSeo, DEFAULT_ROBOTS, PRIVATE_ROBOTS, SITE_ORIGIN } from '@/utils/seoMeta'

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

const currentUser = computed(() => getUser())

const isOwner = computed(() => {
  if (!currentUser.value || !playlist.value) return false
  return currentUser.value.id === playlist.value.userId
})

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
      if (playlist.value) applyPlaylistSeo()
    } else {
      toast.error(data.message || t('playlistDetail.loadFailed'))
    }
  } catch (error) {
    console.error('获取歌单详情失败:', error)
    toast.error(t('playlistDetail.loadFailed'))
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
      applyPlaylistSeo()
    } else {
      console.warn('未找到歌单:', playlistId.value)
      toast.error(t('playlistDetail.notFound'))
      applyPageSeo({
        title: '歌单不存在 - Neko歌姬计划',
        description: '该歌单不存在或已下架。',
        path: `/playlist/${playlistId.value}`,
        robots: PRIVATE_ROBOTS,
        jsonLd: null,
      })
    }
  } catch (error) {
    console.error('获取歌单信息失败:', error)
    // 不显示错误提示，因为可能只是未登录
  }
}

/** 把歌单里的曲目整理成全局播放器认识的曲目对象 */
const toTrack = (music) => ({
  id: music.id,
  title: music.title,
  artist: music.artist,
  album: music.album || '',
  duration: music.duration || 0,
})

const playMusic = (music) => {
  // 以歌单为播放队列，保证「下一首」能接着播放
  playTrackInList(toTrack(music), musicList.value.map(toTrack))
}

const playAll = () => {
  if (musicList.value.length === 0) {
    toast.warning(t('playlistDetail.emptyWarning'))
    return
  }

  playTracks(musicList.value.map(toTrack), 0)
  toast.success(t('playlistDetail.playAllStarted', { count: musicList.value.length }))
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
  return avatarUrl(userId);
}

const applyPlaylistSeo = () => {
  const current = playlist.value
  if (!current) return
  const creator = current.creator?.nickname || 'Neko 用户'
  const count = current.musicCount ?? musicList.value.length
  const coverPath = musicList.value[0]?.id ? `/media/cover/${musicList.value[0].id}` : '/og-image.jpg'
  const pageUrl = `${SITE_ORIGIN}/playlist/${current.id}`
  const imageUrl = `${SITE_ORIGIN}${coverPath}`
  const sample = musicList.value.slice(0, 5).map((music) => music.title).filter(Boolean).join('、')
  const description = `免费在线收听歌单《${current.name}》— ${creator} 创建，共 ${count} 首`
    + `${sample ? `，包含 ${sample} 等曲目` : ''}。Neko歌姬计划提供高品质流媒体、歌词与收藏，永久免费。`
  const title = `${current.name || '歌单'} - 歌单 Playlist | ${creator} | Neko歌姬计划`
  const tracks = musicList.value.slice(0, 100).map((music, index) => ({
    '@type': 'ListItem',
    position: index + 1,
    url: `${SITE_ORIGIN}/detail/${music.id}`,
    item: {
      '@type': 'MusicRecording',
      name: music.title || '',
      url: `${SITE_ORIGIN}/detail/${music.id}`,
      ...(music.artist ? { byArtist: { '@type': 'MusicGroup', name: music.artist } } : {}),
    },
  }))
  const playlistNode = {
    '@type': 'MusicPlaylist',
    '@id': `${pageUrl}#playlist`,
    name: current.name || '未命名歌单',
    url: pageUrl,
    image: imageUrl,
    description,
    creator: { '@type': 'Person', name: creator },
    numTracks: count,
    inLanguage: ['zh-CN', 'en'],
  }
  if (tracks.length > 0) {
    playlistNode.track = {
      '@type': 'ItemList',
      numberOfItems: count,
      itemListElement: tracks,
    }
  }

  applyPageSeo({
    title,
    description,
    keywords: `${current.name || ''},歌单,playlist,${creator},免费在线播放,在线听歌,Neko歌姬计划`,
    path: `/playlist/${current.id}`,
    image: coverPath,
    imageAlt: `${current.name || '歌单'} 封面`,
    type: 'music.playlist',
    robots: DEFAULT_ROBOTS,
    jsonLd: {
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'WebPage',
          '@id': `${pageUrl}#webpage`,
          url: pageUrl,
          name: title,
          description,
          primaryImageOfPage: imageUrl,
          mainEntity: { '@id': `${pageUrl}#playlist` },
          inLanguage: ['zh-CN', 'en'],
        },
        playlistNode,
      ],
    },
  })
}

const handleCoverError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

const handlePlaylistCoverError = (event) => {
  const userId = currentUser.value ? currentUser.value.id : 'default';
  event.target.src = avatarUrl(userId);
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
      toast.success(t('playlistDetail.addSuccess'))
      closeAddMusicDialog()
      await fetchPlaylistDetail()
    } else {
      toast.error(data.message || t('playlistDetail.addFailed'))
    }
  } catch (error) {
    console.error('添加音乐失败:', error)
    toast.error(t('playlistDetail.addFailed'))
  }
}

const removeMusic = async (musicId) => {
  if (!confirm(t('playlistDetail.removeConfirm'))) return
  
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
      toast.success(t('playlistDetail.removeSuccess'))
      await fetchPlaylistDetail()
    } else {
      toast.error(data.message || t('playlistDetail.removeFailed'))
    }
  } catch (error) {
    console.error('移除音乐失败:', error)
    toast.error(t('playlistDetail.removeFailed'))
  }
}

const editPlaylist = () => {
  router.push(`/playlists`)
}

const confirmDelete = () => {
  if (!confirm(t('playlistDetail.deleteConfirm', { name: playlist.value?.name }))) return
  
  router.push(`/playlists`)
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  // 手机端尝试拉起原生 App（无 App 时会带 nekoweb=1 回到本页，不死循环）
  if (playlistId.value) {
    tryOpenPlaylistInApp(playlistId.value)
  }
  fetchPlaylistDetail()
})
</script>

<style scoped>
.pl-detail {
  position: relative;
  min-height: 100dvh;
}

/* ==================== 移动下载横幅 ==================== */



/* ==================== 歌单头 ==================== */
.hero {
  margin-bottom: var(--n-space-6);
}

.hero__back {
  margin-bottom: var(--n-space-4);
}

.hero__main {
  display: flex;
  align-items: center;
  gap: clamp(18px, 3vw, 32px);
}

.hero__cover {
  flex: none;
  width: clamp(120px, 20vw, 180px);
  aspect-ratio: 1;
  border-radius: var(--n-radius-lg);
  overflow: hidden;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
  box-shadow: var(--n-shadow-lg);
}

.hero__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hero__text {
  min-width: 0;
}

.hero__title {
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.5rem, 3.4vw, 2.2rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
  line-height: 1.12;
  overflow-wrap: anywhere;
}

.hero__desc {
  margin: 0 0 var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-base);
  line-height: var(--n-leading-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hero__meta {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-5);
}

/* ==================== 状态 ==================== */
.state {
  display: flex;
  justify-content: center;
  padding: var(--n-space-16) 0;
}

/* ==================== 曲目列表 ==================== */
.list {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-2) var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
  transition: border-color var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .row:hover {
    border-color: var(--n-line-strong);
    background: var(--n-surface-hover);
  }
}

.row__idx {
  flex: none;
  width: 28px;
  text-align: center;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  font-variant-numeric: tabular-nums;
}

.row__cover-btn {
  position: relative;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: var(--n-radius-sm);
  overflow: hidden;
  border: 1px solid var(--n-line-subtle);
}

.row__cover-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.row__cover-play {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(4, 9, 11, 0.55);
  color: var(--n-accent-strong);
  opacity: 0;
  transition: opacity var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .row__cover-btn:hover .row__cover-play {
    opacity: 1;
  }
}

/* 触摸端没有 hover，遮罩常显一角，提示「这里可以播放」 */
@media (hover: none) {
  .row__cover-play {
    opacity: 1;
    background: rgba(4, 9, 11, 0.28);
  }
}

.row__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  text-align: left;
}

.row__title {
  display: flex;
  align-items: center;
  gap: var(--n-space-1);
  min-width: 0;
  color: var(--n-text);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-medium);
}

.row__title-text {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__artist {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__dur {
  flex: none;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  font-variant-numeric: tabular-nums;
}

/* ==================== 空状态 ==================== */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--n-space-3);
  padding: var(--n-space-12) var(--n-space-6);
}

.empty__icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.empty__title {
  margin: 0;
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.empty__text {
  margin: 0 0 var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 添加音乐弹窗 ==================== */
.results {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-1);
  margin-top: var(--n-space-4);
  max-height: min(52vh, 420px);
  overflow-y: auto;
}

.results__row {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-2);
  border-radius: var(--n-radius-sm);
  text-align: left;
  transition: background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .results__row:hover {
    background: var(--n-surface-hover);
  }
}

.results__cover {
  width: 44px;
  height: 44px;
  border-radius: var(--n-radius-xs);
  object-fit: cover;
  flex: none;
  border: 1px solid var(--n-line-subtle);
}

.results__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.results__title {
  display: flex;
  align-items: center;
  gap: var(--n-space-1);
  min-width: 0;
  color: var(--n-text);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-medium);
}

.results__title-text {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.results__artist {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.results__add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 5px 10px;
  border-radius: var(--n-radius-xs);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
}

.results__empty {
  margin: var(--n-space-6) 0 0;
  text-align: center;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

/* ==================== 响应式 ==================== */
@media (max-width: 560px) {
  .hero__main {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--n-space-4);
  }

  .hero__cover {
    width: 140px;
  }

  .row__dur {
    display: none;
  }
}
</style>
