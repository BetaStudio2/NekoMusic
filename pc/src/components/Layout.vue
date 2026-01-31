<template>
  <div class="layout">
    <div class="layout-sidebar glass-dark">
      <div class="sidebar-header">
        <div class="logo-wrapper">
          <img src="/icon.png" alt="Logo" class="app-logo animate-pulse" />
          <div class="logo-glow"></div>
        </div>
        <span class="app-name text-gradient">Neko云音乐</span>
      </div>
      <nav class="sidebar-nav">
        <div 
          v-for="item in navItems" 
          :key="item.key"
          :class="['nav-item', { active: currentRoute === item.key }]"
          @click="navigateTo(item.key)"
        >
          <div class="nav-icon-wrapper">
            <svg class="nav-icon" viewBox="0 0 24 24">
              <path :d="item.icon" fill="currentColor"/>
            </svg>
            <div class="nav-icon-glow" v-if="currentRoute === item.key"></div>
          </div>
          <span>{{ item.label }}</span>
          <div class="nav-indicator" v-if="currentRoute === item.key"></div>
        </div>
      </nav>
      <div class="sidebar-footer">
        <div class="user-card" @click="handleUserClick">
          <div class="user-avatar-wrapper">
            <img :src="userAvatar" alt="用户头像" class="user-avatar" />
            <div class="user-avatar-ring"></div>
          </div>
          <div class="user-details">
            <span class="username">{{ username || '未登录' }}</span>
            <span class="user-status">{{ isLoggedIn ? '在线' : '点击登录' }}</span>
          </div>
          <svg class="user-arrow" viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z"/>
          </svg>
        </div>
      </div>
    </div>
    <div class="layout-main">
      <div class="title-bar glass">
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
          </div>
        </div>
        <div class="title-bar-right">
          <button class="action-btn" @click="navigateTo('settings')" title="设置">
            <svg viewBox="0 0 24 24" width="20" height="20">
              <path fill="currentColor" d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
            </svg>
          </button>
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
      <div class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
      <PlayerBar />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PlayerBar from './PlayerBar.vue'

const router = useRouter()
const currentRoute = ref('home')
const searchQuery = ref('')
const username = ref('')

const navItems = [
  { key: 'home', label: '首页', icon: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z' }
]

const isLoggedIn = computed(() => {
  return localStorage.getItem('user') !== null
})

const userAvatar = computed(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return `https://music.cnmsb.xin/api/user/avatar/${user.id}`
    } catch (e) {
      return getDefaultAvatar()
    }
  }
  return getDefaultAvatar()
})

const getDefaultAvatar = () => {
  return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="40" height="40" fill="url(%23grad)" rx="20"/><text x="20" y="26" font-family="Arial" font-size="16" fill="white" text-anchor="middle" font-weight="bold">U</text></svg>'
}

const navigateTo = (route) => {
  currentRoute.value = route
  router.push(`/${route}`)
}

const handleSearch = () => {
  if (searchQuery.value.trim()) {
    router.push(`/search?q=${encodeURIComponent(searchQuery.value)}`)
  }
}

const handleUserClick = () => {
  if (!isLoggedIn.value) {
    router.push('/login')
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
.layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-main);
}

/* 侧边栏样式 */
.layout-sidebar {
  width: 260px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.layout-sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(102, 126, 234, 0.1) 0%, transparent 100%);
  pointer-events: none;
}

.sidebar-header {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.logo-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  position: relative;
  z-index: 2;
}

.logo-glow {
  position: absolute;
  width: 50px;
  height: 50px;
  background: var(--gradient-primary);
  border-radius: 50%;
  filter: blur(20px);
  opacity: 0.5;
  z-index: 1;
}

.app-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
  position: relative;
  z-index: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  color: var(--text-white-muted);
  cursor: pointer;
  transition: all var(--transition-normal);
  border-radius: var(--radius-md);
  position: relative;
  margin-bottom: 4px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
  transform: translateX(4px);
}

.nav-item.active {
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.nav-icon-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-right: 14px;
}

.nav-icon {
  width: 20px;
  height: 20px;
  position: relative;
  z-index: 2;
}

.nav-icon-glow {
  position: absolute;
  width: 30px;
  height: 30px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  filter: blur(8px);
  z-index: 1;
}

.nav-item span {
  font-size: 14px;
  font-weight: 500;
}

.nav-indicator {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 60%;
  background: white;
  border-radius: 2px 0 0 2px;
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.5);
}

.sidebar-footer {
  padding: 16px 12px;
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-normal);
  border: 1px solid transparent;
}

.user-card:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-2px);
}

.user-avatar-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  position: relative;
  z-index: 2;
}

.user-avatar-ring {
  position: absolute;
  width: 50px;
  height: 50px;
  border: 2px solid transparent;
  border-radius: 50%;
  background: var(--gradient-primary);
  -webkit-mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  animation: rotate 8s linear infinite;
}

.user-details {
  flex: 1;
  min-width: 0;
}

.username {
  font-size: 14px;
  color: white;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.user-status {
  font-size: 12px;
  color: var(--text-white-muted);
  margin-top: 2px;
  display: block;
}

.user-arrow {
  color: var(--text-white-muted);
  opacity: 0;
  transform: translateX(-8px);
  transition: all var(--transition-normal);
}

.user-card:hover .user-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* 主内容区域 */
.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

/* 标题栏 */
.title-bar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  -webkit-app-region: drag;
  user-select: none;
  position: relative;
  z-index: 10;
}

.title-bar::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 20px;
  right: 20px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 0, 0, 0.1), transparent);
}

.title-bar-center {
  flex: 1;
  display: flex;
  justify-content: center;
  max-width: 520px;
}

.search-box {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 420px;
  height: 38px;
  background: white;
  border-radius: var(--radius-full);
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
  -webkit-app-region: no-drag;
  transition: all var(--transition-normal);
  border: 2px solid transparent;
}

.search-box:focus-within {
  box-shadow: var(--shadow-md);
  border-color: var(--primary);
  transform: scale(1.02);
}

.search-icon {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0 12px;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.search-box input::placeholder {
  color: var(--text-muted);
}

.title-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all var(--transition-normal);
  -webkit-app-region: no-drag;
}

.action-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary);
  transform: translateY(-2px);
}

.window-controls {
  display: flex;
  align-items: center;
  gap: 4px;
  -webkit-app-region: no-drag;
}

.window-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.window-btn:hover {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-primary);
}

.close-btn:hover {
  background: #e81123;
  color: white;
}

/* 主内容 */
.main-content {
  flex: 1;
  overflow: auto;
  background: transparent;
  position: relative;
}

/* 页面过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>