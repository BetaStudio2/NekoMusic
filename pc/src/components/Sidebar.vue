<template>
  <div class="sidebar">
    <slot name="title-bar" />
    <nav class="sidebar-nav">
      <div 
        v-for="item in navItems" 
        :key="item.key"
        :class="['nav-item', { active: currentRoute === item.key }]"
        @click="navigateTo(item.key)"
      >
        <svg class="nav-icon" viewBox="0 0 24 24">
          <path :d="item.icon" fill="currentColor"/>
        </svg>
        <span>{{ item.label }}</span>
      </div>
    </nav>
    <div class="sidebar-footer">
      <div class="user-info" @click="handleUserClick" :class="{ 'is-login': isLoggedIn }">
        <img :src="userAvatar" alt="用户头像" class="user-avatar" />
        <div class="user-details">
          <span class="username">{{ username || '未登录' }}</span>
        </div>
      </div>
    </div>

    <Transition name="modal">
      <div v-if="showLoginModal" class="modal-overlay" @click="showLoginModal = false">
        <div class="modal-content" @click.stop>
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
          
          <div class="auth-form">
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
            <input 
              v-if="authTab === 'register'"
              v-model="formData.email"
              type="email" 
              placeholder="邮箱"
              class="auth-input"
            />
          </div>

          <div class="modal-buttons">
            <button class="modal-btn modal-btn-primary" @click="handleSubmit">
              {{ authTab === 'login' ? '登录' : '注册' }}
            </button>
          </div>
          
          <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const currentRoute = ref('home')
const username = ref('')
const showLoginModal = ref(false)
const isLoggedIn = ref(false)
const authTab = ref('login')
const errorMessage = ref('')
const formData = ref({
  username: '',
  password: '',
  email: ''
})

const navItems = [
  { key: 'home', label: '首页', icon: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z' },
  { key: 'search', label: '搜索', icon: 'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z' },
  { key: 'library', label: '音乐库', icon: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z' },
  { key: 'favorites', label: '收藏', icon: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z' },
  { key: 'playlists', label: '歌单', icon: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z' },
  { key: 'settings', label: '设置', icon: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z' }
]

const userAvatar = computed(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return `https://music.cnmsb.xin/api/user/avatar/${user.id}`
    } catch (e) {
      return 'https://music.cnmsb.xin/api/user/avatar/default'
    }
  }
  return 'https://music.cnmsb.xin/api/user/avatar/default'
})

const navigateTo = (route) => {
  currentRoute.value = route
  router.push(`/${route}`)
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
    const baseUrl = 'https://music.cnmsb.xin/api'
    
    if (authTab.value === 'login') {
      const response = await fetch(`${baseUrl}/user/login`, {
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
      
      const data = await response.json()
      localStorage.setItem('user', JSON.stringify(data))
      localStorage.setItem('token', data.token || '')
      username.value = data.username
      isLoggedIn.value = true
      showLoginModal.value = false
      formData.value = { username: '', password: '', email: '' }
    } else {
      const response = await fetch(`${baseUrl}/user/register`, {
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
      
      authTab.value = 'login'
      errorMessage.value = ''
    }
  } catch (error) {
    errorMessage.value = error.message || `${authTab.value === 'login' ? '登录' : '注册'}失败，请重试`
  }
}

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      username.value = user.username
      isLoggedIn.value = true
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
})
</script>

<style scoped>
.sidebar {
  width: 240px;
  height: 100%;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-nav {
  flex: 1;
  padding: 10px 0;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.15);
  color: white;
  border-left-color: white;
}

.nav-icon {
  width: 20px;
  height: 20px;
  margin-right: 12px;
}

.nav-item span {
  font-size: 14px;
}

.sidebar-footer {
  padding: 15px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info:not(.is-login) {
  cursor: pointer;
  transition: all 0.2s;
}

.user-info:not(.is-login):hover {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 8px;
  margin: -8px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.user-details {
  flex: 1;
  min-width: 0;
}

.username {
  font-size: 14px;
  color: white;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99999;
  padding: 20px;
}

.modal-content {
  background: #2a2a2a;
  border-radius: 12px;
  padding: 24px;
  width: 100%;
  max-width: 360px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}

.modal-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.05);
  padding: 4px;
  border-radius: 8px;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.tab-btn.active {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.auth-input {
  padding: 12px 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: white;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.auth-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.auth-input:focus {
  border-color: rgba(102, 126, 234, 0.6);
}

.modal-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.modal-btn {
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.modal-btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.modal-btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.error-message {
  color: #ff6b6b;
  font-size: 13px;
  margin-top: 12px;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-active .modal-content,
.modal-leave-active .modal-content {
  transition: transform 0.2s, opacity 0.2s;
}

.modal-enter-from .modal-content,
.modal-leave-to .modal-content {
  opacity: 0;
  transform: scale(0.95);
}
</style>