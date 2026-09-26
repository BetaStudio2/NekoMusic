<script setup>
/**
 * LatestView —— 最新音乐
 * ------------------------------------------------------------
 * 契约：GET /api/music/latest；播放经 usePlaybackBridge 统一入口。
 */
import { ref, onMounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'
import { playTracks, playTrackInList } from '@/composables/usePlaybackBridge'
import { coverSrcset } from '@/utils/coverImage'

const toast = useToast()

const latestList = ref([])
const loading = ref(true)

async function fetchLatest() {
  loading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/latest?limit=300`)
    const data = await response.json()
    if (data.success && data.data) {
      latestList.value = data.data.map((item) => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`,
      }))
    } else {
      console.error('获取最新音乐失败:', data.message)
    }
  } catch (error) {
    console.error('最新音乐请求失败:', error)
    toast.error('加载最新音乐失败')
  } finally {
    loading.value = false
  }
}

const toTrack = (music) => ({
  id: music.id,
  title: music.title,
  artist: music.artist,
  album: music.album,
  duration: music.duration,
})

function playMusic(music) {
  // 以当前列表为播放队列，保证「下一首」能接着播放
  playTrackInList(toTrack(music), latestList.value.map(toTrack))
  toast.success(`开始播放：${music.title}`)
}

function playAll() {
  if (!latestList.value.length) return
  playTracks(latestList.value.map(toTrack), 0)
  toast.success(`开始播放全部 ${latestList.value.length} 首`)
}

async function downloadMusic(music) {
  try {
    const downloadUrl = `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`
    const response = await fetch(downloadUrl)
    if (!response.ok) throw new Error('下载失败')
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = `${music.title}-${music.artist}.mp3`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
    toast.success(`开始下载：${music.title}`)
  } catch (error) {
    console.error('下载失败:', error)
    toast.error('下载失败，请重试')
  }
}

function handleImageError(event) {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

function formatTime(timestamp) {
  if (!timestamp) return '未知时间'
  const date = new Date(timestamp)
  const diff = Date.now() - date

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

onMounted(fetchLatest)
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <header class="head">
      <div>
        <h1 class="head__title">最新音乐</h1>
        <p class="head__sub">刚刚上传的最新歌曲</p>
      </div>
      <NButton
        v-if="latestList.length"
        variant="primary"
        icon="play"
        @click="playAll"
      >
        播放全部
      </NButton>
    </header>

    <div v-if="loading" class="state">
      <NSpinner :size="28" />
    </div>

    <NCard v-else-if="!latestList.length" pad="lg" class="empty">
      <span class="empty__icon"><NIcon name="sparkles" :size="26" /></span>
      <p class="empty__text">暂无最新音乐数据</p>
    </NCard>

    <div v-else class="list">
      <article v-for="(item, index) in latestList" :key="item.id" class="row">
        <span class="row__num">{{ index + 1 }}</span>
        <img
          :src="item.coverUrl"
          :srcset="coverSrcset(item.coverUrl)"
          sizes="56px"
          width="56"
          height="56"
          :alt="item.title"
          class="row__cover"
          loading="lazy"
          decoding="async"
          @error="handleImageError"
        />
        <button type="button" class="row__info" @click="playMusic(item)">
          <span class="row__title">{{ item.title }}</span>
          <span class="row__artist">{{ item.artist }}</span>
          <span class="row__sub">{{ formatTime(item.createdAt) }}</span>
        </button>
        <div class="row__actions">
          <NButton size="sm" variant="secondary" icon="play" title="播放" @click="playMusic(item)" />
          <NButton size="sm" variant="secondary" icon="download" title="下载" @click="downloadMusic(item)" />
        </div>
      </article>
    </div>
  </PageShell>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
}

.head__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
}

.head__sub {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.state {
  display: flex;
  justify-content: center;
  padding: var(--n-space-16) 0;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-12) var(--n-space-6);
  text-align: center;
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

.empty__text {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-3) var(--n-space-4);
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

.row__num {
  flex: none;
  width: 34px;
  text-align: center;
  color: var(--n-text-faint);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
}

.row__cover {
  width: 56px;
  height: 56px;
  object-fit: cover;
  border-radius: var(--n-radius-sm);
  border: 1px solid var(--n-line-subtle);
  flex: none;
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
  color: var(--n-text);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__artist {
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__sub {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.row__actions {
  display: flex;
  gap: var(--n-space-2);
  flex: none;
}

@media (max-width: 560px) {
  .row {
    flex-wrap: wrap;
  }

  .row__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
