<template>
  <div class="title-bar">
    <div class="title-bar-left">
      <img src="/icon.png" alt="Logo" class="app-logo" />
      <span class="title">Neko云音乐</span>
    </div>
    
    <div class="title-bar-center">
      <div class="search-box">
        <svg class="search-icon" viewBox="0 0 20 20">
          <path fill="currentColor" d="M8 3a5 5 0 100 10A5 5 0 008 3zM0 8a8 8 0 1114.32 4.906l5.387 5.387a1 1 0 01-1.414 1.414l-5.387-5.387A8 8 0 010 8z"/>
        </svg>
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="搜索音乐、艺术家..." 
          @keyup.enter="handleSearch"
        />
        <svg class="mic-icon" viewBox="0 0 20 20">
          <path fill="currentColor" d="M7 4a3 3 0 016 0v6a3 3 0 11-6 0V4zm4 10.93A7.001 7.001 0 0017 8a1 1 0 10-2 0A5 5 0 015 8a1 1 0 00-2 0 7.001 7.001 0 006 6.93V17H6a1 1 0 100 2h8a1 1 0 100-2h-3v-2.07z"/>
        </svg>
      </div>
    </div>
    
    <div class="title-bar-right">
      <button class="action-btn" title="消息">
        <svg viewBox="0 0 20 20" width="20" height="20">
          <path fill="currentColor" d="M2.003 5.884L10 9.882l7.997-3.998A2 2 0 0016 4H4a2 2 0 00-1.997 1.884z"/>
          <path fill="currentColor" d="M18 8.118l-8 4-8-4V14a2 2 0 002 2h12a2 2 0 002-2V8.118z"/>
        </svg>
        <span class="badge"></span>
      </button>
      
      <div class="user-section">
        <img :src="userAvatar" alt="用户头像" class="user-avatar" @error="handleAvatarError" />
        <span class="username">{{ username || '未登录' }}</span>
        <svg class="dropdown-icon" viewBox="0 0 20 20" width="12" height="12">
          <path fill="currentColor" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"/>
        </svg>
      </div>
      
      <div class="window-controls">
        <button class="window-btn" @click="minimize" title="最小化">
          <svg viewBox="0 0 12 12" width="12" height="12">
            <path fill="currentColor" d="M2 5h8v1H2V5z"/>
          </svg>
        </button>
        <button class="window-btn" @click="maximize" title="最大化">
          <svg viewBox="0 0 12 12" width="12" height="12">
            <path fill="none" stroke="currentColor" stroke-width="1" d="M2 2h8v8H2z"/>
          </svg>
        </button>
        <button class="window-btn close-btn" @click="close" title="关闭">
          <svg viewBox="0 0 12 12" width="12" height="12">
            <path fill="currentColor" d="M2 2l8 8M10 2l-8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const searchQuery = ref('')
const username = ref('')

const userAvatar = computed(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return `http://localhost:9999/api/user/avatar/${user.id}`
    } catch (e) {
      return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" fill="%236a5acd"/><text x="16" y="20" font-family="Arial" font-size="14" fill="white" text-anchor="middle">U</text></svg>'
    }
  }
  return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" fill="%236a5acd"/><text x="16" y="20" font-family="Arial" font-size="14" fill="white" text-anchor="middle">U</text></svg>'
})

const handleAvatarError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><rect width="32" height="32" fill="%236a5acd"/><text x="16" y="20" font-family="Arial" font-size="14" fill="white" text-anchor="middle">U</text></svg>'
}

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push(`/search?q=${encodeURIComponent(searchQuery.value)}`)
  }
}

const minimize = () => {
  if (window.electronAPI?.minimize) {
    window.electronAPI.minimize()
  }
}

const maximize = () => {
  if (window.electronAPI?.maximize) {
    window.electronAPI.maximize()
  }
}

const close = () => {
  if (window.electronAPI?.close) {
    window.electronAPI.close()
  }
}

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      username.value = user.username
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
})
</script>

<style scoped>
.title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 48px;
  padding: 0 16px;
  -webkit-app-region: drag;
  user-select: none;
  flex-shrink: 0;
}

.title-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.app-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
}

.title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.title-bar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  max-width: 500px;
}

.search-box {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 400px;
  height: 32px;
  background: #f5f5f7;
  border-radius: 16px;
  padding: 0 12px;
  -webkit-app-region: no-drag;
}

.search-icon {
  width: 16px;
  height: 16px;
  color: #999;
  flex-shrink: 0;
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0 8px;
  font-size: 13px;
  color: #333;
  outline: none;
}

.search-box input::placeholder {
  color: #999;
}

.mic-icon {
  width: 16px;
  height: 16px;
  color: #d0d0e0;
  flex-shrink: 0;
  cursor: pointer;
}

.mic-icon:hover {
  color: #999;
}

.title-bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.action-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.2s;
  -webkit-app-region: no-drag;
}

.action-btn:hover {
  background: #f5f5f7;
  color: #333;
}

.badge {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff4545;
}

.user-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 20px;
  cursor: pointer;
  transition: background-color 0.2s;
  -webkit-app-region: no-drag;
}

.user-section:hover {
  background: rgba(0, 0, 0, 0.05);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.username {
  font-size: 14px;
  color: #333;
  max-width: 100px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dropdown-icon {
  color: #999;
  flex-shrink: 0;
}

.window-controls {
  display: flex;
  align-items: center;
  gap: 2px;
  -webkit-app-region: no-drag;
}

.window-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.2s;
}

.window-btn:hover {
  background: #f5f5f7;
  color: #333;
}

.close-btn:hover {
  background: #e81123;
  color: white;
}
</style>