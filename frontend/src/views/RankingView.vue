<script setup>
/**
 * RankingView —— 热门音乐排行榜
 * ------------------------------------------------------------
 * 契约：GET /api/music/ranking；播放经 usePlaybackBridge 统一入口。
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

const rankingList = ref([])
const loading = ref(true)

async function fetchRanking() {
  loading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/ranking`)
    const data = await response.json()
    if (data.success && data.data) {
      rankingList.value = data.data.map((item) => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`,
      }))
    } else {
      console.error('获取排行榜失败:', data.message)
    }
  } catch (error) {
    console.error('排行榜请求失败:', error)
    toast.error('加载排行榜失败')
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
  playTrackInList(toTrack(music), rankingList.value.map(toTrack))
  toast.success(`开始播放：${music.title}`)
}

function playAll() {
  if (!rankingList.value.length) return
  playTracks(rankingList.value.map(toTrack), 0)
  toast.success(`开始播放全部 ${rankingList.value.length} 首`)
}

async function downloadMusic(music) {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/file/${music.id}`)
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

function getRankClass(index) {
  if (index === 0) return 'row__num--first'
  if (index === 1) return 'row__num--second'
  if (index === 2) return 'row__num--third'
  return ''
}

onMounted(fetchRanking)
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <header class="head">
      <div>
        <h1 class="head__title">热门音乐排行榜</h1>
        <p class="head__sub">基于播放次数排序的热门音乐</p>
      </div>
      <NButton
        v-if="rankingList.length"
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

    <NCard v-else-if="!rankingList.length" pad="lg" class="empty">
      <span class="empty__icon"><NIcon name="trophy" :size="26" /></span>
      <p class="empty__text">暂无排行榜数据</p>
    </NCard>

    <div v-else class="list">
      <article v-for="(item, index) in rankingList" :key="item.id" class="row">
        <span class="row__num" :class="getRankClass(index)">{{ index + 1 }}</span>
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
          <span class="row__sub">{{ item.playCount }} 次播放</span>
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
  width: 40px;
  text-align: center;
  color: var(--n-text-faint);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
}

/* 前三名奖牌色（语义色，不随主题变化） */
.row__num--first {
  color: #fde047;
  font-size: 1.75rem;
}
.row__num--second {
  color: #e5e7eb;
  font-size: 1.6rem;
}
.row__num--third {
  color: #fdba74;
  font-size: 1.5rem;
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
