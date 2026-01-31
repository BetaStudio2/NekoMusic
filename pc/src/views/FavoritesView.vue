<template>
  <div class="favorites-view">
    <div class="view-header">
      <h2 class="view-title">我喜欢的音乐</h2>
      <button class="refresh-btn" @click="refreshFavorites" :disabled="loading">
        <svg v-if="!loading" viewBox="0 0 24 24" width="16" height="16">
          <path fill="currentColor" d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
        </svg>
        <svg v-else class="spin-icon" viewBox="0 0 24 24" width="16" height="16">
          <path fill="currentColor" d="M12 4V2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8z"/>
        </svg>
        <span>{{ loading ? '刷新中...' : '刷新' }}</span>
      </button>
    </div>
    <MusicList 
      title="" 
      :show-search="false" 
      :show-favorite="true"
      :fetch-function="fetchFavorites"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import MusicList from '../components/MusicList.vue'
import apiConfig from '../config/apiConfig'

const loading = ref(false)

// 统一的 API 请求函数
async function apiRequest(url, options = {}) {
  const fullUrl = url.startsWith('http') ? url : `${apiConfig.BASE_URL}${url}`
  return fetch(fullUrl, options)
}

async function fetchFavorites() {
  const token = localStorage.getItem('userToken')
  if (!token) {
    console.log('未登录，跳过获取收藏列表')
    return []
  }

  try {
    // 先尝试从本地存储读取
    const localFavorites = localStorage.getItem('favorites')
    if (localFavorites) {
      try {
        const parsed = JSON.parse(localFavorites)
        if (parsed && parsed.length > 0) {
          console.log('fetchFavorites: 从本地读取收藏列表，数量:', parsed.length)
          return parsed
        }
      } catch (e) {
        console.error('解析本地收藏列表失败:', e)
      }
    }

    // 从服务器同步
    const response = await apiRequest(apiConfig.USER_FAVORITES, {
      headers: { 'Authorization': token }
    })

    if (!response.ok) {
      throw new Error('获取收藏列表失败')
    }

    const result = await response.json()
    if (result.success && result.data) {
      console.log('fetchFavorites: 从服务器获取收藏列表成功，数量:', result.data.length)
      // 保存到本地存储
      localStorage.setItem('favorites', JSON.stringify(result.data))
      return result.data
    }
    return []
  } catch (error) {
    console.error('获取收藏列表失败:', error)
    return []
  }
}

async function refreshFavorites() {
  loading.value = true
  try {
    // 清除本地缓存
    localStorage.removeItem('favorites')
    // 重新获取
    await fetchFavorites()
  } catch (error) {
    console.error('刷新收藏列表失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.favorites-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 0 24px;
  margin-bottom: 20px;
}

.view-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--primary);
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.2);
  border-color: rgba(102, 126, 234, 0.3);
  transform: translateY(-1px);
}

.refresh-btn:active:not(:disabled) {
  transform: translateY(0);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>