<template>
  <div class="latest-view">
    <div class="content">
      <div class="latest-header">
        <div class="header-content">
          <div class="header-text">
            <h1 class="page-title">最新音乐</h1>
            <p class="page-description">刚刚上传的最新歌曲</p>
          </div>
          <button v-if="latestList && latestList.length > 0" @click="playAll" class="play-all-btn">
            播放全部
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
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
      <div v-else class="no-latest">
        <p>暂无最新音乐数据</p>
      </div>
    </div>
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
      console.log('最新音乐加载成功:', data.data.length, '首')
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
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 20px;
}

.latest-header {
  margin-bottom: 40px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(31, 38, 135, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.header-text {
  flex: 1;
}

.page-title {
  color: #667eea;
  font-size: 2.5rem;
  font-weight: bold;
  margin: 0 0 10px 0;
}

.page-description {
  color: #887bb0;
  font-size: 1.1rem;
  margin: 0;
}

.play-all-btn {
  padding: 12px 30px;
  border: none;
  border-radius: 25px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
  white-space: nowrap;
}

.play-all-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.loading {
  text-align: center;
  color: #887bb0;
  padding: 40px;
  font-size: 1.1rem;
}

.latest-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.latest-item {
  display: flex;
  align-items: center;
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: all 0.3s ease;
}

.latest-item:hover {
  background: rgba(255, 255, 255, 0.4);
  transform: translateX(5px);
  box-shadow: 0 6px 20px rgba(31, 38, 135, 0.25);
}

.latest-number {
  font-size: 2rem;
  font-weight: bold;
  width: 50px;
  text-align: center;
  margin-right: 20px;
  color: #887bb0;
}

.latest-cover {
  width: 80px;
  height: 80px;
  border-radius: 12px;
  object-fit: cover;
  margin-right: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.latest-info {
  flex: 1;
  min-width: 0;
}

.latest-title-text {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latest-artist {
  font-size: 1rem;
  color: #666;
  margin-bottom: 8px;
}

.latest-time {
  font-size: 0.9rem;
  color: #887bb0;
  font-weight: 500;
}

.latest-actions {
  display: flex;
  gap: 12px;
  margin-left: 20px;
}

.latest-actions .play-btn,
.latest-actions .download-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 25px;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(106, 90, 205, 0.2);
  color: #6a5acd;
  font-weight: 500;
}

.latest-actions .play-btn:hover,
.latest-actions .download-btn:hover {
  background: rgba(106, 90, 205, 0.4);
  transform: scale(1.05);
}

.no-latest {
  text-align: center;
  color: #887bb0;
  padding: 60px;
  font-size: 1.2rem;
}
</style>