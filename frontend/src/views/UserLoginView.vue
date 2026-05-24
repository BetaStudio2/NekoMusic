<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell auth-main">
      <div class="panel auth-card">
        <h2 class="auth-title">用户登录</h2>
        <form class="auth-form" @submit.prevent="handleLogin">
          <div class="form-group">
            <input
              v-model="username"
              type="text"
              class="form-input"
              placeholder="邮箱"
              required
              autocomplete="username"
            />
          </div>
          <div class="form-group">
            <input
              v-model="password"
              type="password"
              class="form-input"
              placeholder="密码"
              required
              autocomplete="current-password"
            />
          </div>
          <button type="submit" class="btn-submit" :disabled="loading">
            <span v-if="loading">登录中…</span>
            <span v-else>登录</span>
          </button>
        </form>
        <div class="auth-footer">
          <p>还没有账户？<a href="#" @click.prevent="goToRegister">立即注册</a></p>
          <p><a href="#" @click.prevent="goToForgotPassword">忘记密码？</a></p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useToast } from 'vue-toastification'
import API_CONFIG from '@/config/apiConfig.js'

const toast = useToast()
const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

const handleLogin = async () => {
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/login`, {
      username: username.value,
      password: password.value
    })

    if (response.data.success) {
      toast.success('登录成功！')
      const previousToken = localStorage.getItem('userToken')

      localStorage.setItem('userToken', response.data.data.token)
      localStorage.setItem('user', JSON.stringify(response.data.data.user))

      if (!previousToken) {
        window.dispatchEvent(
          new StorageEvent('storage', {
            key: 'userToken',
            oldValue: null,
            newValue: response.data.data.token
          })
        )
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

const goToRegister = () => {
  router.push('/register')
}

const goToForgotPassword = () => {
  router.push('/forgot-password')
}
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: clamp(28px, 4vw, 40px) clamp(22px, 4vw, 32px);
}

.auth-title {
  margin: 0 0 24px;
  text-align: center;
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #e9d5ff, #a5f3fc, #c4b5fd);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-group {
  margin: 0;
}

.form-input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  color: var(--text);
  font-size: 0.95rem;
  font-family: inherit;
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.form-input::placeholder {
  color: var(--faint);
}

.form-input:focus {
  outline: none;
  border-color: rgba(34, 211, 238, 0.45);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.12);
}

.btn-submit {
  font-family: inherit;
  margin-top: 6px;
  padding: 12px 20px;
  border: none;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.95rem;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.3);
}

.btn-submit:hover:not(:disabled) {
  filter: brightness(1.05);
}

.btn-submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.auth-footer {
  margin-top: 22px;
  text-align: center;
  font-size: 0.88rem;
  color: var(--muted);
}

.auth-footer p {
  margin: 8px 0;
}

.auth-footer a {
  color: var(--accent2);
  font-weight: 600;
  text-decoration: none;
}

.auth-footer a:hover {
  text-decoration: underline;
}
</style>
