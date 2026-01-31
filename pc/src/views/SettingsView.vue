<template>
  <div class="settings-view">
    <div class="settings-container">
      <h2>设置</h2>
      
      <div class="settings-section">
        <h3>账号信息</h3>
        <div v-if="currentUser" class="account-info">
          <div class="account-avatar">
            <img :src="userAvatar" alt="用户头像" />
          </div>
          <div class="account-details">
            <div class="account-item">
              <span class="label">用户名</span>
              <span class="value">{{ currentUser.username }}</span>
            </div>
            <div class="account-item">
              <span class="label">邮箱</span>
              <span class="value">{{ currentUser.email || '未设置' }}</span>
            </div>
            <div class="account-item">
              <span class="label">注册时间</span>
              <span class="value">{{ formatDate(currentUser.createdAt) }}</span>
            </div>
          </div>
          <button class="logout-btn" @click="handleLogout">
            <svg viewBox="0 0 24 24" width="16" height="16">
              <path fill="currentColor" d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
            </svg>
            退出登录
          </button>
        </div>
        <div v-else class="no-login">
          <p>未登录</p>
          <button class="login-btn" @click="showLoginModal = true">去登录</button>
        </div>
      </div>
      
      <div class="settings-section">
        <h3>关于</h3>
        <p>NekoMusic PC 版本</p>
        <p>版本号: 1.0.0</p>
      </div>
    </div>

    <Transition name="modal">
      <div v-if="showLoginModal" class="modal-overlay" @click="showLoginModal = false">
        <div class="modal-content" @click.stop>
          <div class="modal-header">
            <img src="/icon.png" alt="Logo" class="modal-logo" />
            <h3 class="modal-title">欢迎回来</h3>
            <p class="modal-subtitle">登录您的账号继续使用</p>
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
              <button class="submit-btn" @click="handleSubmit">
                {{ authTab === 'login' ? '登录' : '注册' }}
              </button>
            </div>
          </Transition>
          
          <Transition name="error-fade">
            <div v-if="errorMessage" class="error-message">
              {{ errorMessage }}
            </div>
          </Transition>
        </div>
      </div>
    </Transition>

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
              <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
            </svg>
            <svg v-else viewBox="0 0 24 24" width="20" height="20">
              <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
          </div>
          <span class="toast-message">{{ toast.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

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

const userAvatar = computed(() => {
  if (currentUser.value && currentUser.value.id) {
    return `https://music.cnmsb.xin/api/user/avatar/${currentUser.value.id}`
  }
  return 'https://music.cnmsb.xin/api/user/avatar/default'
})

const formatDate = (dateStr) => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

const showToast = (message, type = 'info') => {
  const id = ++toastId
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 3000)
}

const handleLogout = () => {
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  currentUser.value = null
  window.dispatchEvent(new CustomEvent('user-logout'))
  showToast('已退出登录', 'success')
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

onMounted(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      currentUser.value = user
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
})
</script>

<style scoped>
.settings-view {
  height: 100%;
  padding: 20px;
  overflow-y: auto;
}

.settings-container {
  max-width: 800px;
  margin: 0 auto;
}

.settings-container h2 {
  margin-bottom: 20px;
  color: #333;
  font-size: 24px;
  font-weight: 600;
}

.settings-section {
  background: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  margin-bottom: 20px;
}

.settings-section h3 {
  margin-bottom: 16px;
  color: #333;
  font-size: 16px;
  font-weight: 600;
}

.settings-section p {
  color: #666;
  margin: 8px 0;
}

.account-info {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.account-avatar {
  flex-shrink: 0;
}

.account-avatar img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid #f0f0f0;
}

.account-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.account-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.account-item:last-child {
  border-bottom: none;
}

.account-item .label {
  color: #999;
  font-size: 14px;
}

.account-item .value {
  color: #333;
  font-size: 14px;
  font-weight: 500;
}

.logout-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #f5f5f5;
  border: none;
  border-radius: 8px;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  align-self: flex-start;
  margin-top: 10px;
}

.logout-btn:hover {
  background: #e8e8e8;
  color: #333;
}

.no-login {
  text-align: center;
  padding: 40px 20px;
}

.no-login p {
  color: #999;
  margin-bottom: 16px;
}

.login-btn {
  padding: 10px 30px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
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
  backdrop-filter: blur(4px);
}

.modal-content {
  background: rgba(30, 30, 30, 0.95);
  border-radius: 16px;
  padding: 32px;
  width: 100%;
  max-width: 400px;
  text-align: center;
  box-shadow: 
    0 20px 60px rgba(0, 0, 0, 0.5),
    0 0 0 1px rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
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
  background: radial-gradient(circle at 30% 30%, rgba(102, 126, 234, 0.15) 0%, transparent 50%),
                radial-gradient(circle at 70% 70%, rgba(118, 75, 162, 0.15) 0%, transparent 50%);
  pointer-events: none;
}

.modal-header {
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.modal-logo {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  margin-bottom: 16px;
}

.modal-title {
  font-size: 22px;
  color: white;
  margin-bottom: 6px;
  font-weight: 600;
}

.modal-subtitle {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 24px;
}

.modal-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  background: rgba(255, 255, 255, 0.05);
  padding: 4px;
  border-radius: 10px;
  position: relative;
  z-index: 1;
}

.tab-btn {
  flex: 1;
  padding: 10px;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab-btn:hover {
  color: white;
}

.tab-btn.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: relative;
  z-index: 1;
}

.auth-input {
  padding: 14px 16px;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  color: white;
  font-size: 14px;
  outline: none;
  transition: all 0.2s;
}

.auth-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.auth-input:hover {
  border-color: rgba(255, 255, 255, 0.2);
}

.auth-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
}

.submit-btn {
  padding: 14px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
  margin-top: 8px;
}

.submit-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
}

.submit-btn:hover::before {
  left: 100%;
}

.submit-btn:active {
  transform: translateY(0);
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
  transition: transform 0.3s, opacity 0.3s;
}

.modal-enter-from .modal-content,
.modal-leave-to .modal-content {
  opacity: 0;
  transform: scale(0.9);
}

.form-slide-enter-active,
.form-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.form-slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.form-slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

.field-fade-enter-active,
.field-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.field-fade-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.field-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.error-fade-enter-active,
.error-fade-leave-active {
  transition: all 0.2s ease;
}

.error-fade-enter-from,
.error-fade-leave-to {
  opacity: 0;
  transform: translateY(-5px);
}

.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 999999;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.toast {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 10px;
  color: white;
  font-size: 14px;
  min-width: 280px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

.toast-success {
  background: rgba(76, 175, 80, 0.9);
  border-left: 4px solid #4caf50;
}

.toast-error {
  background: rgba(244, 67, 54, 0.9);
  border-left: 4px solid #f44336;
}

.toast-info {
  background: rgba(33, 150, 243, 0.9);
  border-left: 4px solid #2196f3;
}

.toast-icon {
  flex-shrink: 0;
}

.toast-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100px);
}

.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(100px);
}
</style>