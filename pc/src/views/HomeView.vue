<template>
  <div class="home-view">
    <div class="welcome-section">
      <h1 class="welcome-title">
        <span class="greeting">{{ greeting }}</span>
        <span class="emoji">🎵</span>
      </h1>
      <p class="welcome-subtitle">发现好音乐，享受每一刻</p>
    </div>

    <div class="featured-section">
      <h2 class="section-title">
        <svg class="title-icon" viewBox="0 0 24 24" width="24" height="24">
          <path fill="currentColor" d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
        </svg>
        推荐音乐
      </h2>
      <MusicList title="" :show-search="false" :show-favorite="true" :fetch-function="fetchRecommendedMusic" />
    </div>

    <div class="stats-section">
      <div class="stat-card">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="32" height="32">
            <path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ favoriteCount }}</span>
          <span class="stat-label">收藏音乐</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="32" height="32">
            <path fill="currentColor" d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ playlistCount }}</span>
          <span class="stat-label">我的歌单</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="32" height="32">
            <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ listenedCount }}</span>
          <span class="stat-label">已播放</span>
        </div>
      </div>
    </div>

    <div class="quick-actions">
      <button class="action-card" @click="navigateTo('search')">
        <div class="action-icon" style="background: var(--gradient-primary)">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
          </svg>
        </div>
        <span class="action-label">搜索音乐</span>
      </button>
      <button class="action-card" @click="navigateTo('favorites')">
        <div class="action-icon" style="background: var(--gradient-secondary)">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
          </svg>
        </div>
        <span class="action-label">我的收藏</span>
      </button>
      <button class="action-card" @click="navigateTo('playlists')">
        <div class="action-icon" style="background: var(--gradient-accent)">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z"/>
          </svg>
        </div>
        <span class="action-label">歌单管理</span>
      </button>
      <button class="action-card" @click="navigateTo('settings')">
        <div class="action-icon" style="background: var(--gradient-warm)">
          <svg viewBox="0 0 24 24" width="24" height="24">
            <path fill="currentColor" d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
          </svg>
        </div>
        <span class="action-label">设置</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MusicList from '../components/MusicList.vue'

const router = useRouter()
const favoriteCount = ref(0)
const playlistCount = ref(0)
const listenedCount = ref(0)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const fetchRecommendedMusic = async () => {
  try {
    const response = await fetch('https://music.cnmsb.xin/api/music/recommended')
    if (!response.ok) {
      throw new Error('获取推荐音乐失败')
    }
    return await response.json()
  } catch (error) {
    console.error('获取推荐音乐失败:', error)
    return []
  }
}

const navigateTo = (route) => {
  router.push(`/${route}`)
}

const fetchStats = async () => {
  const token = localStorage.getItem('userToken')
  if (!token) return

  try {
    // 获取收藏数量
    const favResponse = await fetch('https://music.cnmsb.xin/api/user/favorites', {
      headers: { 'Authorization': token }
    })
    if (favResponse.ok) {
      const favorites = await favResponse.json()
      favoriteCount.value = favorites.length
    }

    // 获取歌单数量
    const playlistResponse = await fetch('https://music.cnmsb.xin/api/user/playlists', {
      headers: { 'Authorization': token }
    })
    if (playlistResponse.ok) {
      const playlists = await playlistResponse.json()
      playlistCount.value = playlists.length
    }

    // 已播放数量（从本地存储获取）
    const listened = localStorage.getItem('listenedCount')
    listenedCount.value = listened ? parseInt(listened) : 0
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
.home-view {
  height: 100%;
  overflow-y: auto;
  padding: 32px;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.welcome-section {
  text-align: center;
  margin-bottom: 40px;
  padding: 40px 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-radius: var(--radius-xl);
  position: relative;
  overflow: hidden;
}

.welcome-section::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.1) 0%, transparent 50%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.welcome-title {
  margin: 0;
  font-size: 42px;
  font-weight: 800;
  color: var(--text-primary);
  position: relative;
  z-index: 1;
}

.greeting {
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.emoji {
  margin-left: 8px;
  animation: bounce 2s ease-in-out infinite;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.welcome-subtitle {
  margin-top: 12px;
  font-size: 16px;
  color: var(--text-secondary);
  position: relative;
  z-index: 1;
}

.featured-section {
  margin-bottom: 32px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.title-icon {
  color: var(--primary);
}

.stats-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
  cursor: pointer;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 20px;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px 24px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 2px solid transparent;
  cursor: pointer;
  transition: all var(--transition-normal);
}

.action-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary);
}

.action-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  transition: all var(--transition-normal);
}

.action-card:hover .action-icon {
  transform: scale(1.1);
}

.action-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 滚动条样式 */
.home-view::-webkit-scrollbar {
  width: 8px;
}

.home-view::-webkit-scrollbar-track {
  background: transparent;
}

.home-view::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 4px;
}

.home-view::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}
</style>