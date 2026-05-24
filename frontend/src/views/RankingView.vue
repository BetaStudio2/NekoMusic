<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell">
      <section class="panel page-hero">
        <div class="page-hero__row">
          <div class="page-hero__text">
            <h1 class="page-hero__title">热门音乐排行榜</h1>
            <p class="page-hero__desc">基于播放次数排序的热门音乐</p>
          </div>
          <button
            v-if="rankingList && rankingList.length > 0"
            type="button"
            class="btn-play-all"
            @click="playAll"
          >
            播放全部
          </button>
        </div>
      </section>

      <div v-if="loading" class="panel state-panel">
        <p class="state-text">加载中…</p>
      </div>
      <div v-else-if="rankingList && rankingList.length > 0" class="ranking-list">
        <div
          v-for="(item, index) in rankingList"
          :key="item.id"
          class="ranking-item"
        >
          <div class="ranking-number" :class="getRankClass(index)">{{ index + 1 }}</div>
          <img
            :src="item.coverUrl"
            :alt="item.title"
            class="ranking-cover"
            @error="handleImageError"
          />
          <div class="ranking-info">
            <div class="ranking-title-text">{{ item.title }}</div>
            <div class="ranking-artist">{{ item.artist }}</div>
            <div class="ranking-play-count">{{ item.playCount }} 次播放</div>
          </div>
          <div class="ranking-actions">
            <button @click.stop="playMusic(item)" class="play-btn" title="播放">播放</button>
            <button @click.stop="downloadMusic(item)" class="download-btn" title="下载">下载</button>
          </div>
        </div>
      </div>
      <div v-else class="panel state-panel state-panel--empty">
        <p class="state-text">暂无排行榜数据</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const rankingList = ref([])
const loading = ref(false)

// 获取排行榜数据
const fetchRanking = async () => {
  loading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/ranking`)
    const data = await response.json()

    if (data.success && data.data) {
      // 为每首音乐设置正确的封面URL
      rankingList.value = data.data.map(item => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`
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

// 播放音乐
const playMusic = (music) => {
  // 通过URL参数触发GlobalPlayer播放
  const musicData = encodeURIComponent(JSON.stringify({
    id: music.id,
    title: music.title,
    artist: music.artist,
    album: music.album,
    duration: music.duration
  }))
  window.location.hash = `#play=${musicData}`
  toast.success(`开始播放: ${music.title}`)
}

// 播放全部
const playAll = () => {
  // 将所有音乐转换为播放列表格式
  const playlist = rankingList.value.map(music => ({
    id: music.id,
    title: music.title,
    artist: music.artist,
    album: music.album,
    duration: music.duration
  }))

  // 通过URL参数触发GlobalPlayer播放列表
  const playlistData = encodeURIComponent(JSON.stringify(playlist))
  window.location.hash = `#playlist=${playlistData}&index=0`
  toast.success(`开始播放全部 ${playlist.length} 首音乐`)
}

// 下载音乐
const downloadMusic = async (music) => {
  try {
    toast.info(`正在准备下载: ${music.title}`)
    const downloadUrl = `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`

    // 使用fetch获取文件数据
    const response = await fetch(downloadUrl)
    if (!response.ok) {
      throw new Error('下载失败')
    }

    // 将响应转换为blob
    const blob = await response.blob()

    // 创建blob URL
    const blobUrl = URL.createObjectURL(blob)

    // 创建下载链接
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = `${music.title}-${music.artist}.mp3`
    document.body.appendChild(link)
    link.click()

    // 清理
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)

    toast.success(`开始下载: ${music.title}`)
  } catch (error) {
    console.error('下载失败:', error)
    toast.error('下载失败，请重试')
  }
}

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

// 获取排名样式类
const getRankClass = (index) => {
  if (index === 0) return 'rank-first'
  if (index === 1) return 'rank-second'
  if (index === 2) return 'rank-third'
  return 'rank-normal'
}

onMounted(() => {
  fetchRanking()
})
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.page-hero {
  padding: clamp(18px, 3vw, 26px) clamp(18px, 3vw, 24px);
  margin-bottom: 16px;
}

.page-hero__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-hero__text {
  flex: 1;
  min-width: 0;
}

.page-hero__title {
  margin: 0 0 8px;
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text);
}

.page-hero__desc {
  margin: 0;
  font-size: 0.92rem;
  color: var(--muted);
  line-height: 1.45;
}

.btn-play-all {
  font-family: inherit;
  padding: 11px 20px;
  border: none;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.3);
  white-space: nowrap;
}

.state-panel {
  padding: 36px 24px;
  text-align: center;
  margin-bottom: 16px;
}

.state-panel--empty {
  padding: 48px 24px;
}

.state-text {
  margin: 0;
  color: var(--muted);
  font-size: 0.95rem;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ranking-item {
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
  .ranking-item:hover {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(139, 92, 246, 0.28);
  }
}

.ranking-number {
  font-size: 1.5rem;
  font-weight: 800;
  width: 44px;
  text-align: center;
  flex-shrink: 0;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}

.rank-first {
  color: #fde047;
  font-size: 1.75rem;
}

.rank-second {
  color: #e5e7eb;
  font-size: 1.6rem;
}

.rank-third {
  color: #fdba74;
  font-size: 1.5rem;
}

.ranking-cover {
  width: 72px;
  height: 72px;
  border-radius: var(--radius);
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

.ranking-info {
  flex: 1;
  min-width: 0;
}

.ranking-title-text {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-artist {
  font-size: 0.86rem;
  color: var(--accent2);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-play-count {
  font-size: 0.78rem;
  color: var(--faint);
  font-weight: 500;
}

.ranking-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.ranking-actions .play-btn,
.ranking-actions .download-btn {
  font-family: inherit;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
  transition: background 0.15s var(--ease), border-color 0.15s var(--ease);
}

@media (hover: hover) {
  .ranking-actions .play-btn:hover,
  .ranking-actions .download-btn:hover {
    background: rgba(139, 92, 246, 0.22);
    border-color: rgba(139, 92, 246, 0.4);
  }
}

@media (max-width: 640px) {
  .ranking-item {
    flex-wrap: wrap;
  }

  .ranking-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>