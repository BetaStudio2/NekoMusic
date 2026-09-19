<script setup>
/**
 * UserLoginView —— 用户登录
 * ------------------------------------------------------------
 * 契约（保持与旧实现一致）：
 *  - POST /api/user/login
 *  - 成功后写 userToken / user，并广播 storage（供 GlobalPlayer/顶栏同步）
 *  - 跳转首页
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function handleLogin() {
  if (loading.value) return
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/login`, {
      username: username.value,
      password: password.value,
    })

    if (response.data.success) {
      toast.success('登录成功')
      const previousToken = localStorage.getItem('userToken')

      localStorage.setItem('userToken', response.data.data.token)
      localStorage.setItem('user', JSON.stringify(response.data.data.user))

      if (!previousToken) {
        window.dispatchEvent(
          new StorageEvent('storage', {
            key: 'userToken',
            oldValue: null,
            newValue: response.data.data.token,
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
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <NCard pad="lg" class="auth">
      <h1 class="auth__title">用户登录</h1>

      <form class="auth__form" @submit.prevent="handleLogin">
        <NInput
          v-model="username"
          icon="mail"
          placeholder="邮箱"
          autocomplete="username"
          required
        />
        <NInput
          v-model="password"
          type="password"
          icon="lock"
          placeholder="密码"
          autocomplete="current-password"
          required
        />
        <NButton type="submit" variant="primary" size="lg" block :loading="loading">
          登录
        </NButton>
      </form>

      <div class="auth__footer">
        <p>
          还没有账户？
          <RouterLink to="/register">立即注册</RouterLink>
        </p>
        <p>
          <RouterLink to="/forgot-password">
            <NIcon name="key" :size="14" />
            忘记密码？
          </RouterLink>
        </p>
      </div>
    </NCard>
  </PageShell>
</template>

<style scoped>
.auth {
  width: 100%;
  max-width: 420px;
}

.auth__title {
  margin: 0 0 var(--n-space-6);
  text-align: center;
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-4);
}

.auth__footer {
  margin-top: var(--n-space-6);
  text-align: center;
  font-size: var(--n-text-sm);
  color: var(--n-text-muted);
}

.auth__footer p {
  margin: var(--n-space-2) 0;
}

.auth__footer a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: var(--n-weight-semibold);
}
</style>
