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
            <button class="search-btn" @click="handleSearch" title="搜索">
              <svg viewBox="0 0 24 24" width="18" height="18">
                <path fill="currentColor" d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
              </svg>
            </button>
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

    <!-- Toast 通知 -->
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div 
          v-for="toast in toasts" 
          :key="toast.id"
          :class="['toast', `toast-${toast.type}`]"
        >
          <div class="toast-icon">
            <svg v-if="toast.type === 'success'" viewBox="0 0 24 24" width="20" height="20">
              <path fill="currentColor" d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
            </svg>
            <svg v-else-if="toast.type === 'error'" viewBox="0 0 24 24" width="20" height="20">
              <path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="20" height="20">
              <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
          </div>
          <div class="toast-content">
            <div class="toast-message">{{ toast.message }}</div>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <Transition name="modal">
      <div v-if="showLoginModal" class="modal-overlay" @click="showLoginModal = false">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <div class="modal-logo">
              <img src="/icon.png" alt="Logo" />
            </div>
            <Transition name="title-fade" mode="out-in">
              <h2 :key="authTab" class="modal-title">{{ authTab === 'login' ? '欢迎回来' : '创建账号' }}</h2>
            </Transition>
            <Transition name="subtitle-fade" mode="out-in">
              <p :key="authTab" class="modal-subtitle">{{ authTab === 'login' ? '登录以继续使用Neko云音乐' : '创建新账号开始您的音乐之旅' }}</p>
            </Transition>
          </div>
          
          <div class="modal-tabs">
            <button 
              :class="['tab-btn', { active: authTab === 'login' }]"
              @click="authTab = 'login'"
            >
              登录
            </button>
            <button 
              :class="['tab-btn', { active: authTab === 'register' }]"
              @click="authTab = 'register'"
            >
              注册
            </button>
          </div>
          
          <Transition name="form-slide" mode="out-in">
            <div :key="authTab" class="auth-form">
              <input 
                v-model="formData.username"
                type="text" 
                placeholder="用户名"
                class="auth-input"
              />
              <input 
                v-model="formData.password"
                type="password" 
                placeholder="密码"
                class="auth-input"
              />
              <Transition name="field-fade">
                <input 
                  v-if="authTab === 'register'"
                  v-model="formData.email"
                  type="email" 
                  placeholder="邮箱"
                  class="auth-input"
                />
              </Transition>
            </div>
          </Transition>

          <Transition name="button-fade" mode="out-in">
            <div :key="authTab" class="modal-buttons">
              <button class="modal-btn modal-btn-primary" @click="handleSubmit">
                {{ authTab === 'login' ? '立即登录' : '创建账号' }}
              </button>
            </div>
          </Transition>
          
          <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        </div>
      </div>
    </Transition>
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
const currentUser = ref(null)
const showLoginModal = ref(false)
const authTab = ref('login')
const errorMessage = ref('')
const formData = ref({
  username: '',
  password: '',
  email: ''
})
const toasts = ref([])
let toastId = 0

const navItems = [
  { key: 'home', label: '首页', icon: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z' }
]

const isLoggedIn = computed(() => {
  return currentUser.value !== null
})

const userAvatar = computed(() => {
  if (currentUser.value && currentUser.value.id) {
    return `https://music.cnmsb.xin/api/user/avatar/${currentUser.value.id}`
  }
  return getDefaultAvatar()
})

const getDefaultAvatar = () => {
  return 'https://music.cnmsb.xin/api/user/avatar/default'
}

const showToast = (message, type = 'info') => {
  const id = toastId++
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 3000)
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
    showLoginModal.value = true
  }
}

const handleSubmit = async () => {
  errorMessage.value = ''
  
  if (!formData.value.username || !formData.value.password) {
    errorMessage.value = '请填写用户名和密码'
    return
  }

  if (authTab.value === 'register' && !formData.value.email) {
    errorMessage.value = '请填写邮箱'
    return
  }

  try {
    if (authTab.value === 'login') {
      const response = await fetch('/api/user/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: formData.value.username,
          password: formData.value.password
        })
      })
      
      if (!response.ok) {
        throw new Error('登录失败')
      }
      
      const result = await response.json()
      if (result.success && result.data && result.data.user) {
        const user = result.data.user
        const token = result.data.token
        
        localStorage.setItem('user', JSON.stringify(user))
        localStorage.setItem('token', token)
        currentUser.value = user
        username.value = user.username
        showLoginModal.value = false
        formData.value = { username: '', password: '', email: '' }
        showToast('登录成功，欢迎回来！', 'success')
      } else {
        throw new Error(result.message || '登录失败')
      }
    } else {
      const response = await fetch('/api/user/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: formData.value.username,
          password: formData.value.password,
          email: formData.value.email
        })
      })
      
      if (!response.ok) {
        throw new Error('注册失败')
      }
      
      const result = await response.json()
      if (result.success) {
        authTab.value = 'login'
        errorMessage.value = ''
        showToast('注册成功，请登录', 'success')
      } else {
        throw new Error(result.message || '注册失败')
      }
    }
  } catch (error) {
    errorMessage.value = error.message || `${authTab.value === 'login' ? '登录' : '注册'}失败，请重试`
    showToast(errorMessage.value, 'error')
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
      currentUser.value = user
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
  max-width: 480px;
  height: 44px;
  background: white;
  border-radius: var(--radius-lg);
  padding: 0 4px 0 16px;
  box-shadow: var(--shadow-md);
  -webkit-app-region: no-drag;
  transition: all var(--transition-normal);
  border: 2px solid transparent;
  position: relative;
  overflow: hidden;
}

.search-box::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--gradient-primary);
  opacity: 0;
  transition: opacity var(--transition-normal);
  border-radius: var(--radius-lg);
}

.search-box:focus-within {
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.25);
  border-color: var(--primary);
  transform: translateY(-1px);
}

.search-box:focus-within::before {
  opacity: 0.05;
}

.search-icon {
  width: 20px;
  height: 20px;
  color: var(--text-muted);
  flex-shrink: 0;
  transition: color var(--transition-fast);
  position: relative;
  z-index: 1;
}

.search-box:focus-within .search-icon {
  color: var(--primary);
}

.search-box input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0 12px;
  font-size: 15px;
  color: var(--text-primary);
  outline: none;
  position: relative;
  z-index: 1;
}

.search-box input::placeholder {
  color: var(--text-muted);
  transition: opacity var(--transition-fast);
}

.search-box:focus-within input::placeholder {
  opacity: 0.6;
}

.search-btn {
  width: 40px;
  height: 36px;
  border: none;
  background: var(--gradient-primary);
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all var(--transition-normal);
  margin-right: 4px;
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.search-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.search-btn:active {
  transform: scale(0.95);
}

.search-btn svg {
  width: 18px;
  height: 18px;
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

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99999;
  padding: 20px;
}

.modal-content {
  background: linear-gradient(135deg, rgba(42, 42, 42, 0.95) 0%, rgba(30, 30, 30, 0.95) 100%);
  border-radius: 20px;
  padding: 32px;
  width: 100%;
  max-width: 380px;
  text-align: center;
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  position: relative;
  overflow: hidden;
}

.modal-content::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.1) 0%, transparent 50%);
  pointer-events: none;
}

.modal-header {
  text-align: center;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.modal-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  position: relative;
}

.modal-logo img {
  width: 100%;
  height: 100%;
  border-radius: 16px;
  box-shadow: 
    0 8px 24px rgba(102, 126, 234, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.modal-title {
  font-size: 24px;
  font-weight: 700;
  color: white;
  margin-bottom: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.modal-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.modal-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 28px;
  background: rgba(0, 0, 0, 0.3);
  padding: 6px;
  border-radius: 14px;
  position: relative;
  z-index: 1;
}

.tab-btn {
  flex: 1;
  padding: 12px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 1;
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 
    0 4px 12px rgba(102, 126, 234, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  transform: scale(1.02);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.auth-input {
  padding: 14px 18px;
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.3);
  color: white;
  font-size: 14px;
  outline: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.auth-input:focus {
  border-color: #667eea;
  background: rgba(0, 0, 0, 0.4);
  box-shadow: 
    0 0 0 4px rgba(102, 126, 234, 0.1),
    0 4px 12px rgba(102, 126, 234, 0.2);
  transform: translateY(-1px);
}

.auth-input:hover:not(:focus) {
  border-color: rgba(255, 255, 255, 0.15);
}

.modal-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.modal-btn {
  padding: 14px 28px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
  position: relative;
  overflow: hidden;
}

.modal-btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 
    0 4px 16px rgba(102, 126, 234, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.modal-btn-primary::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.modal-btn-primary:hover::before {
  left: 100%;
}

.modal-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 8px 24px rgba(102, 126, 234, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.modal-btn-primary:active {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.error-message {
  color: #ff6b6b;
  font-size: 13px;
  margin-top: 14px;
  padding: 10px 14px;
  background: rgba(255, 107, 107, 0.1);
  border-radius: 8px;
  border: 1px solid rgba(255, 107, 107, 0.2);
  position: relative;
  z-index: 1;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-active .modal-content,
.modal-leave-active .modal-content {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-enter-from .modal-content,
.modal-leave-to .modal-content {
  opacity: 0;
  transform: scale(0.9) translateY(10px);
}

.title-fade-enter-active,
.title-fade-leave-active {
  transition: all 0.3s ease;
}

.title-fade-enter-from,
.title-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.subtitle-fade-enter-active,
.subtitle-fade-leave-active {
  transition: all 0.3s ease 0.1s;
}

.subtitle-fade-enter-from,
.subtitle-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.form-slide-enter-active,
.form-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.form-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.form-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.field-fade-enter-active {
  transition: all 0.3s ease;
}

.field-fade-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.field-fade-leave-active {
  transition: all 0.2s ease;
}

.field-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.button-fade-enter-active,
.button-fade-leave-active {
  transition: all 0.3s ease;
}

.button-fade-enter-from,
.button-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.toast-container {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 999999;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.5);
  min-width: 300px;
  pointer-events: auto;
  position: relative;
  overflow: hidden;
}

.toast::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

.toast-success::before {
  background: linear-gradient(180deg, #4ade80 0%, #22c55e 100%);
}

.toast-error::before {
  background: linear-gradient(180deg, #f87171 0%, #ef4444 100%);
}

.toast-info::before {
  background: linear-gradient(180deg, #60a5fa 0%, #3b82f6 100%);
}

.toast-icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-success .toast-icon {
  color: #22c55e;
}

.toast-error .toast-icon {
  color: #ef4444;
}

.toast-info .toast-icon {
  color: #3b82f6;
}

.toast-content {
  flex: 1;
}

.toast-message {
  font-size: 14px;
  color: #1f2937;
  font-weight: 500;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100px);
}

.toast-move {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
</style>