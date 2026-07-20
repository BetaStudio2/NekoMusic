<template>
  <div class="admin-login-container">
    <div class="admin-login-card">
      <div class="login-header">
        <h2>管理员登录</h2>
        <p>请输入您的管理员凭据</p>
      </div>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="form-input"
            required
          />
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="form-input"
            required
          />
        </div>
        
        <button type="submit" class="login-button" :disabled="isLoading">
          <span v-if="isLoading">登录中...</span>
          <span v-else>登录</span>
        </button>
        
        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'

const router = useRouter()
const username = ref('')
const password = ref('')
const isLoading = ref(false)
const errorMessage = ref('')

const handleLogin = async () => {
  if (!username.value.trim() || !password.value.trim()) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  
  try {
    isLoading.value = true
    errorMessage.value = ''
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: username.value,
        password: password.value
      })
    })
    
    const data = await response.json()
    
    if (response.ok && data.success) {
      // 登录成功，保存令牌到本地存储
      localStorage.setItem('adminToken', data.token)  // 存储会话令牌，而不是管理员信息
      localStorage.setItem('isAdminLoggedIn', 'true')
      localStorage.setItem('adminInfo', JSON.stringify(data.admin))  // 保留管理员信息用于显示
      
      // 跳转到管理员面板
      router.push('/admin')
    } else {
      errorMessage.value = data.message || '登录失败，请检查用户名和密码'
    }
  } catch (error) {
    console.error('登录请求失败:', error)
    errorMessage.value = '网络错误，请稍后重试'
  } finally {
    isLoading.value = false
  }
}
</script>

<style scoped>
.admin-login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 20px;
}

.admin-login-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  width: 100%;
  max-width: 400px;
  text-align: center;
}

.login-header {
  margin-bottom: 30px;
}

.login-header h2 {
  color: #69c8df;
  margin-bottom: 10px;
  font-size: 1.8rem;
}

.login-header p {
  color: #887bb0;
  font-size: 0.9rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  text-align: left;
}

.form-group label {
  color: #5c4b7b;
  margin-bottom: 5px;
  font-weight: 500;
}

.form-input {
  padding: 12px 15px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.form-input:focus {
  outline: none;
  border: 1px solid rgba(105, 200, 223, 0.5);
  box-shadow: 0 0 0 2px rgba(105, 200, 223, 0.2);
  background: rgba(255, 255, 255, 0.35);
}

.login-button {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.8), rgba(105, 200, 223, 0.8));
  color: white;
  border: none;
  border-radius: 10px;
  padding: 12px 20px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(105, 200, 223, 0.3);
}

.login-button:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(105, 200, 223, 0.5);
}

.login-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-message {
  color: #e74c3c;
  background: rgba(231, 76, 60, 0.1);
  padding: 10px;
  border-radius: 8px;
  border: 1px solid rgba(231, 76, 60, 0.3);
  text-align: center;
  margin-top: 10px;
  animation: shake 0.5s;
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}

@media (max-width: 480px) {
  .admin-login-card {
    padding: 30px 20px;
    margin: 10px;
  }
}

.admin-login-container {
  min-height: 100dvh;
  background:
    radial-gradient(780px 520px at 18% -10%, rgba(105, 200, 223, 0.16), transparent 58%),
    linear-gradient(180deg, #070b10, #0b1118 48%, #06090d 100%);
}

.admin-login-card {
  background: rgba(14, 22, 31, 0.88);
  border: 1px solid rgba(143, 174, 198, 0.14);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.36);
  border-radius: 22px;
}

.login-header h2 {
  color: var(--neko-text);
  letter-spacing: -0.03em;
}

.login-header p,
.form-group label {
  color: var(--neko-muted);
}

.form-input {
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(143, 174, 198, 0.16);
  color: var(--neko-text);
  border-radius: 14px;
}

.form-input::placeholder {
  color: var(--neko-faint);
}

.form-input:focus {
  border-color: rgba(105, 200, 223, 0.45);
  box-shadow: 0 0 0 3px rgba(105, 200, 223, 0.12);
  background: rgba(255, 255, 255, 0.065);
}

.login-button {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.22), rgba(105, 200, 223, 0.1));
  color: var(--neko-text);
  border: 1px solid rgba(105, 200, 223, 0.24);
  border-radius: 999px;
  box-shadow: none;
}

.login-button:hover:not(:disabled) {
  background: rgba(105, 200, 223, 0.14);
  box-shadow: none;
}

.error-message {
  color: #ffd7d7;
  background: rgba(255, 107, 107, 0.1);
  border-color: rgba(255, 107, 107, 0.22);
}
</style>
