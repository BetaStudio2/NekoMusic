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
            <h1 class="page-hero__title">最新音乐</h1>
            <p class="page-hero__desc">刚刚上传的最新歌曲</p>
          </div>
          <button
            v-if="latestList && latestList.length > 0"
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
      <div v-else-if="latestList && latestList.length > 0" class="latest-list">
        <div
          v-for="(item, index) in latestList"
          :key="item.id"
          class="latest-item"
        >
          <div class="latest-number">{{ index + 1 }}</div>
          <img
            :src="item.coverUrl"
            :alt="item.title"
            class="latest-cover"
            @error="handleImageError"
          />
          <div class="latest-info">
            <div class="latest-title-text">{{ item.title }}</div>
            <div class="latest-artist">{{ item.artist }}</div>
            <div class="latest-time">{{ formatTime(item.createdAt) }}</div>
          </div>
          <div class="latest-actions">
            <button @click.stop="playMusic(item)" class="play-btn" title="播放">播放</button>
            <button @click.stop="downloadMusic(item)" class="download-btn" title="下载">下载</button>
          </div>
        </div>
      </div>
      <div v-else class="panel state-panel state-panel--empty">
        <p class="state-text">暂无最新音乐数据</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const latestList = ref([])
const loading = ref(false)

// 获取最新音乐数据
const fetchLatest = async () => {
  loading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/latest?limit=300`)
    const data = await response.json()

    if (data.success && data.data) {
      // 为每首音乐设置正确的封面URL
      latestList.value = data.data.map(item => ({
        ...item,
        coverUrl: `${API_CONFIG.BASE_URL}/api/music/cover/${item.id}`
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
  const playlist = latestList.value.map(music => ({
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

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '未知时间'
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

onMounted(() => {
  fetchLatest()
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

.latest-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.latest-item {
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
  .latest-item:hover {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(139, 92, 246, 0.28);
  }
}

.latest-number {
  font-size: 1.35rem;
  font-weight: 800;
  width: 44px;
  text-align: center;
  flex-shrink: 0;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}

.latest-cover {
  width: 72px;
  height: 72px;
  border-radius: var(--radius);
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}

.latest-info {
  flex: 1;
  min-width: 0;
}

.latest-title-text {
  font-size: 1rem;
  font-weight: 700;
  color: var(--text);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-artist {
  font-size: 0.86rem;
  color: var(--accent2);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-time {
  font-size: 0.78rem;
  color: var(--faint);
  font-weight: 500;
}

.latest-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.latest-actions .play-btn,
.latest-actions .download-btn {
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
  .latest-actions .play-btn:hover,
  .latest-actions .download-btn:hover {
    background: rgba(139, 92, 246, 0.22);
    border-color: rgba(139, 92, 246, 0.4);
  }
}

@media (max-width: 640px) {
  .latest-item {
    flex-wrap: wrap;
  }

  .latest-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>