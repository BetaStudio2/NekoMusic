<template>
  <div class="home-view">
    <div class="content">
<!-- 推荐卡片 -->
      <div class="recommendation-cards">
        <a href="/download" class="recommendation-card download-card">
          <div class="card-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
              <polyline points="7 10 12 15 17 10"></polyline>
              <line x1="12" y1="15" x2="12" y2="3"></line>
            </svg>
          </div>
          <div class="card-content">
            <h3 class="card-title">下载客户端</h3>
            <p class="card-description">支持 Windows、macOS 和 Linux，享受更好的播放体验</p>
          </div>
        </a>

        <a href="https://github.com/NyaNyagulugulu/NekoMusicDocs" target="_blank" rel="noopener noreferrer" class="recommendation-card docs-card">
          <div class="card-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
            </svg>
          </div>
          <div class="card-content">
            <h3 class="card-title">开发者文档</h3>
            <p class="card-description">查看完整的 API 文档，快速集成 Neko云音乐服务</p>
          </div>
        </a>
      </div>

      <!-- 推荐歌单 -->
      <div class="playlist-card">
        <div class="section-header">
          <h2 class="section-title">推荐歌单</h2>
          <a href="/playlists" class="view-more-link">查看更多</a>
        </div>
        <div v-if="playlistsLoading || loading" class="loading">加载中...</div>
        <div v-else-if="(playlistList && playlistList.length > 0) || (displayList && displayList.length > 0)" class="playlist-scroll">
          <!-- 热门音乐整体卡片 -->
          <div
            class="playlist-item hot-music-card"
            @click="goToRanking"
          >
            <div class="playlist-cover hot-music-cover">
              <div class="hot-music-mosaic">
                <img
                  v-for="(item, index) in displayList.slice(0, 4)"
                  :key="index"
                  :src="item.coverUrl"
                  :alt="item.title"
                  class="mosaic-img"
                  @error="handleImageError"
                />
              </div>
              <div class="playlist-count hot-music-count">{{ rankingList.length }}首</div>
            </div>
            <div class="playlist-info">
              <h3 class="playlist-name">热门音乐</h3>
              <p class="playlist-description">播放次数最高的热门歌曲</p>
            </div>
          </div>

          <!-- 歌单卡片 -->
          <div
            v-for="playlist in playlistList"
            :key="'playlist-' + playlist.id"
            class="playlist-item"
            @click="goToPlaylist(playlist.id)"
          >
            <div class="playlist-cover">
              <img
                :src="playlist.firstMusicCover"
                :alt="playlist.name"
                class="playlist-cover-img"
                @error="handlePlaylistCoverError"
              />
              <div class="playlist-count">{{ playlist.musicCount }}首</div>
            </div>
            <div class="playlist-info">
              <h3 class="playlist-name">{{ playlist.name }}</h3>
              <p class="playlist-description">{{ playlist.description || '暂无描述' }}</p>
            </div>
          </div>
        </div>
        <div v-else class="no-playlist">
          <p>暂无推荐内容</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()
const router = useRouter()

const rankingList = ref([])
const loading = ref(false)
const playlistList = ref([])
const playlistsLoading = ref(false)

// 显示列表，只显示前20首
const displayList = computed(() => {
  return rankingList.value.slice(0, 20)
})

// 获取排行榜数据
const fetchRanking = async () => {
  loading.value = true
  try {
    const timestamp = Date.now()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/ranking?t=${timestamp}`)
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

// 获取推荐歌单
const fetchPlaylists = async () => {
  playlistsLoading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/playlists/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: ''
      })
    })
    const data = await response.json()

    if (data.success && data.results) {
      // 为歌单封面添加正确的URL
      playlistList.value = data.results.slice(0, 10).map(playlist => ({
        ...playlist,
        firstMusicCover: playlist.firstMusicCover
          ? (playlist.firstMusicCover.startsWith('http') || playlist.firstMusicCover.startsWith('/api'))
            ? playlist.firstMusicCover
            : `${API_CONFIG.BASE_URL}${playlist.firstMusicCover}`
          : '/api/defaultIcon'
      }))
      console.log('歌单加载成功:', data.results.length, '个')
    } else {
      console.error('获取歌单失败:', data.message)
    }
  } catch (error) {
    console.error('歌单请求失败:', error)
    toast.error('加载歌单失败')
  } finally {
    playlistsLoading.value = false
  }
}

// 跳转到歌单详情
const goToPlaylist = (playlistId) => {
  router.push(`/playlist/${playlistId}`)
}

// 跳转到排行榜页面
const goToRanking = () => {
  router.push('/ranking')
}

// 播放音乐
const playMusic = (music) => {
  // 这里需要调用全局播放器的播放方法
  // 暂时使用路由跳转到播放页面
  window.location.href = `/player?musicId=${music.id}`
}

// 下载音乐
const downloadMusic = (music) => {
  const downloadUrl = `${API_CONFIG.BASE_URL}/api/music/file/${music.id}`
  window.open(downloadUrl, '_blank')
}

// 处理图片加载错误
const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

// 处理歌单封面加载错误
const handlePlaylistCoverError = (event) => {
  event.target.src = '/api/defaultIcon'
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
  fetchPlaylists()
})
</script>

<style scoped>
.content {
  max-width: 900px;
  margin: 0 auto;
  position: relative;
  padding-top: 40px;
}

/* 推荐卡片样式 */
.recommendation-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.recommendation-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 20px;
  padding: 25px;
  text-decoration: none;
  color: inherit;
  display: flex;
  align-items: flex-start;
  gap: 20px;
  box-shadow: 0 4px 20px rgba(31, 38, 135, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.recommendation-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 0;
}

.recommendation-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(31, 38, 135, 0.25);
}

.recommendation-card:hover::before {
  opacity: 1;
}

.download-card:hover::before {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2) 0%, rgba(118, 75, 162, 0.2) 100%);
}

.docs-card:hover::before {
  background: linear-gradient(135deg, rgba(240, 147, 251, 0.2) 0%, rgba(245, 87, 108, 0.2) 100%);
}

.card-icon {
  flex-shrink: 0;
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.music-card .card-icon {
  color: #ff6b6b;
}

.download-card .card-icon {
  color: #667eea;
}

.docs-card .card-icon {
  color: #f5576c;
}

.card-content {
  flex: 1;
  position: relative;
  z-index: 1;
}

.card-title {
  font-size: 1.3rem;
  font-weight: 700;
  color: #333;
  margin: 0 0 8px 0;
}

.card-description {
  font-size: 0.95rem;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

/* 排行榜样式 */
.ranking-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 25px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  margin: 30px auto;
  max-width: 90%;
  overflow: hidden;
}

.ranking-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.ranking-title {
  color: #6a5acd;
  font-size: 1.8rem;
  font-weight: bold;
  margin: 0;
}

.view-more-link {
  color: #887bb0;
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s ease;
  padding: 8px 16px;
  border-radius: 15px;
  background: rgba(136, 123, 176, 0.1);
}

.view-more-link:hover {
  color: #6a5acd;
  background: rgba(106, 90, 205, 0.15);
  transform: translateX(3px);
}

.loading {
  text-align: center;
  color: #887bb0;
  padding: 40px;
  font-size: 1.1rem;
}

.ranking-list {
  max-height: 800px;
  overflow-y: auto;
  padding-right: 10px;
}

.ranking-list::-webkit-scrollbar {
  width: 6px;
}

.ranking-list::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.ranking-list::-webkit-scrollbar-thumb {
  background: rgba(106, 90, 205, 0.5);
  border-radius: 3px;
}

.ranking-list::-webkit-scrollbar-thumb:hover {
  background: rgba(106, 90, 205, 0.7);
}

.ranking-item {
  display: flex;
  align-items: center;
  padding: 15px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 15px;
  margin-bottom: 10px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.ranking-item:hover {
  background: rgba(255, 255, 255, 0.35);
  transform: translateX(5px);
}

.ranking-number {
  font-size: 1.5rem;
  font-weight: bold;
  width: 40px;
  text-align: center;
  margin-right: 15px;
  color: #887bb0;
}

.rank-first {
  color: #ffd700;
  font-size: 1.8rem;
}

.rank-second {
  color: #c0c0c0;
  font-size: 1.7rem;
}

.rank-third {
  color: #cd7f32;
  font-size: 1.6rem;
}

.ranking-cover {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  object-fit: cover;
  margin-right: 15px;
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
}

.ranking-info {
  flex: 1;
  min-width: 0;
}

.ranking-title-text {
  font-size: 1.1rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-artist {
  font-size: 0.9rem;
  color: #666;
  margin-bottom: 5px;
}

.ranking-play-count {
  font-size: 0.85rem;
  color: #887bb0;
}

.ranking-actions {
  display: flex;
  gap: 10px;
  margin-left: 15px;
}

.ranking-actions .play-btn,
.ranking-actions .download-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 20px;
  font-size: 0.9rem;
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
  padding: 40px;
  font-size: 1.1rem;
}

/* 推荐歌单样式 */
.playlist-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 25px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  margin: 30px auto;
  max-width: 90%;
  overflow: hidden;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  color: #6a5acd;
  font-size: 1.8rem;
  font-weight: bold;
  margin: 0;
}

.playlist-scroll {
  display: flex;
  gap: 20px;
  overflow-x: auto;
  padding-bottom: 10px;
  scroll-behavior: smooth;
}

.playlist-scroll::-webkit-scrollbar {
  height: 6px;
}

.playlist-scroll::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
}

.playlist-scroll::-webkit-scrollbar-thumb {
  background: rgba(106, 90, 205, 0.5);
  border-radius: 3px;
}

.playlist-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(106, 90, 205, 0.7);
}

.playlist-item {
  flex-shrink: 0;
  width: 180px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.playlist-item:hover {
  transform: translateY(-5px);
}

.playlist-cover {
  position: relative;
  width: 180px;
  height: 180px;
  border-radius: 15px;
  overflow: hidden;
  margin-bottom: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.playlist-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.playlist-item:hover .playlist-cover-img {
  transform: scale(1.05);
}

/* 热门音乐卡片样式 */
.hot-music-card {
  background: linear-gradient(135deg, rgba(255, 107, 107, 0.1) 0%, rgba(255, 159, 67, 0.1) 100%);
}

.hot-music-cover {
  background: linear-gradient(135deg, #ff6b6b, #ff9f43);
  padding: 4px;
}

.hot-music-mosaic {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 4px;
  width: 100%;
  height: 100%;
}

.mosaic-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
}

.hot-music-count {
  background: rgba(255, 107, 107, 0.9);
  color: white;
  font-weight: 600;
}

.playlist-count {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
  color: white;
  padding: 20px 10px 8px;
  font-size: 0.85rem;
  font-weight: 500;
}

.playlist-info {
  padding: 0 5px;
}

.playlist-name {
  font-size: 1rem;
  font-weight: 600;
  color: #333;
  margin: 0 0 6px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.playlist-description {
  font-size: 0.85rem;
  color: #666;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-playlist {
  text-align: center;
  color: #887bb0;
  padding: 40px;
  font-size: 1.1rem;
}
</style>