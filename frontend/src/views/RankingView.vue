<template>
  <div class="ranking-view">
    <div class="content">
      <div class="ranking-header">
        <div class="header-content">
          <div class="header-text">
            <h1 class="page-title">热门音乐排行榜</h1>
            <p class="page-description">基于播放次数排序的热门音乐</p>
          </div>
          <button v-if="rankingList && rankingList.length > 0" @click="playAll" class="play-all-btn">
            播放全部
          </button>
        </div>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
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
      <div v-else class="no-ranking">
        <p>暂无排行榜数据</p>
      </div>
    </div>
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
      console.log('排行榜加载成功:', data.data.length, '首')
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
const downloadMusic = (music) => {
  const downloadUrl = `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`
  const link = document.createElement('a')
  link.href = downloadUrl
  link.download = `${music.title}-${music.artist}.mp3`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  toast.success(`开始下载: ${music.title}`)
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
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 20px;
}

.ranking-header {
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
  color: #6a5acd;
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

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.ranking-item {
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

.ranking-item:hover {
  background: rgba(255, 255, 255, 0.4);
  transform: translateX(5px);
  box-shadow: 0 6px 20px rgba(31, 38, 135, 0.25);
}

.ranking-number {
  font-size: 2rem;
  font-weight: bold;
  width: 50px;
  text-align: center;
  margin-right: 20px;
  color: #887bb0;
}

.rank-first {
  color: #ffd700;
  font-size: 2.5rem;
}

.rank-second {
  color: #c0c0c0;
  font-size: 2.3rem;
}

.rank-third {
  color: #cd7f32;
  font-size: 2.1rem;
}

.ranking-cover {
  width: 80px;
  height: 80px;
  border-radius: 12px;
  object-fit: cover;
  margin-right: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.ranking-info {
  flex: 1;
  min-width: 0;
}

.ranking-title-text {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-artist {
  font-size: 1rem;
  color: #666;
  margin-bottom: 8px;
}

.ranking-play-count {
  font-size: 0.9rem;
  color: #887bb0;
  font-weight: 500;
}

.ranking-actions {
  display: flex;
  gap: 12px;
  margin-left: 20px;
}

.ranking-actions .play-btn,
.ranking-actions .download-btn {
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

.ranking-actions .play-btn:hover,
.ranking-actions .download-btn:hover {
  background: rgba(106, 90, 205, 0.4);
  transform: scale(1.05);
}

.no-ranking {
  text-align: center;
  color: #887bb0;
  padding: 60px;
  font-size: 1.2rem;
}
</style>