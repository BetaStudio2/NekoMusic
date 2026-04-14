<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">用户登录</h2>
      <form @submit.prevent="handleLogin" class="login-form">
        <div class="form-group">
          <input
            type="text"
            v-model="username"
            class="form-input"
            placeholder="邮箱"
            required
          />
        </div>
        
        <div class="form-group">
          <input
            type="password"
            v-model="password"
            class="form-input"
            placeholder="密码"
            required
          />
        </div>
        
        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="loading">登录中...</span>
          <span v-else>登录</span>
        </button>
      </form>
      
      <div class="login-footer">
        <p>还没有账户？<a href="#" @click.prevent="goToRegister">立即注册</a></p>
        <p><a href="#" @click.prevent="goToForgotPassword">忘记密码？</a></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useToast } from 'vue-toastification'
import API_CONFIG from "@/config/apiConfig.js";

const toast = useToast()
const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

// 处理登录逻辑
const handleLogin = async () => {
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/login`, {
      username: username.value,
      password: password.value
    })
    
    if (response.data.success) {
      toast.success('登录成功！')
      // 存储用户信息和token到localStorage
      const previousToken = localStorage.getItem('userToken')
      const previousUser = localStorage.getItem('user')
      
      localStorage.setItem('userToken', response.data.data.token)
      localStorage.setItem('user', JSON.stringify(response.data.data.user))
      
      // 触发storage事件，确保其他标签页或组件能够检测到状态变化
      if (!previousToken) {
        // 只有在之前没有登录的情况下才触发storage事件
        window.dispatchEvent(new StorageEvent('storage', {
          key: 'userToken',
          oldValue: null,
          newValue: response.data.data.token
        }));
      }
      
      router.push('/')
    } else {
      toast.error(response.data.message || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '登录失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
  } finally {
    loading.value = false
  }
}

// 跳转到注册页面
const goToRegister = () => {
  router.push('/register')
}

// 跳转到忘记密码页面
const goToForgotPassword = () => {
  router.push('/forgot-password')
}
</script>

<style scoped>
.login-container {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-card {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: -10px;
  right: -10px;
  bottom: -10px;
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff, #ff9ec0);
  background-size: 400%;
  border-radius: 25px;
  z-index: -1;
  filter: blur(20px);
  opacity: 0.6;
  animation: gradientShift 10s ease infinite;
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.login-title {
  text-align: center;
  margin-bottom: 30px;
  font-size: 1.8rem;
  color: #6a5acd;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  z-index: 1;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-input {
  padding: 14px 20px;
  border: none;
  border-radius: 30px;
  font-size: 1rem;
  outline: none;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: all 0.3s ease;
  color: #333;
  width: 100%;
}

.form-input::placeholder {
  color: rgba(92, 75, 123, 0.6);
}

.form-input:focus {
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 8px 32px rgba(106, 90, 205, 0.3);
  background: rgba(255, 255, 255, 0.35);
}

.login-btn {
  padding: 14px 20px;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(147, 112, 219, 0.8));
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 1.1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 8px 32px rgba(106, 90, 205, 0.3);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  width: 100%;
  margin-top: 10px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(106, 90, 205, 0.5);
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-footer {
  text-align: center;
  margin-top: 20px;
  color: #6a5acd;
}

.login-footer p {
  margin: 8px 0;
}

.login-footer a {
  color: #6a5acd;
  text-decoration: none;
  font-weight: 500;
}

.login-footer a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .login-card {
    padding: 30px 20px;
    margin: 0 10px;
  }
}
</style>