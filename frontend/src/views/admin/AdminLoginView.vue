<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <div class="auth">
      <span class="auth__glow" aria-hidden="true" />

      <div class="auth__card">
        <header class="auth__head">
          <div class="auth__logo">
            <NIcon name="shield-check" :size="26" />
          </div>
          <h1 class="auth__title">管理员登录</h1>
          <p class="auth__subtitle">请输入您的管理员凭据</p>
        </header>

        <form class="auth__body" @submit.prevent="handleLogin">
          <div class="auth__field">
            <label class="auth__label" for="admin-username">用户名</label>
            <NInput
              id="admin-username"
              v-model="username"
              icon="user"
              placeholder="请输入用户名"
              autocomplete="username"
            />
          </div>

          <div class="auth__field">
            <label class="auth__label" for="admin-password">密码</label>
            <NInput
              id="admin-password"
              v-model="password"
              type="password"
              icon="lock"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
          </div>

          <p class="auth__error" role="alert">{{ errorMessage || '\u00A0' }}</p>

          <NButton type="submit" variant="primary" size="lg" block :loading="isLoading">
            登录
          </NButton>
        </form>

        <p class="auth__foot">
          <RouterLink to="/" class="auth__link">
            <NIcon name="arrow-left" :size="14" />
            返回站点
          </RouterLink>
        </p>
      </div>
    </div>
  </PageShell>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

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
/* ===== 与前台登录卡片一致的容器与特效 ===== */
.auth {
  position: relative;
  width: 100%;
  max-width: 400px;
}

.auth__glow {
  position: absolute;
  inset: -14% -10%;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 40%, rgba(95, 208, 224, 0.22), transparent 62%);
  filter: blur(48px);
  pointer-events: none;
  z-index: -1;
  animation: authGlow 8s var(--n-ease-in-out) infinite;
}

@keyframes authGlow {
  0%,
  100% { opacity: 0.75; }
  50% { opacity: 1; }
}

.auth__card {
  position: relative;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface-strong);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
  box-shadow: var(--n-shadow-lg);
  overflow: hidden;
  animation: authCardIn 0.5s var(--n-ease) both;
}

@keyframes authCardIn {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.985);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.auth__card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--n-accent-line), transparent);
}

.auth__head {
  padding: var(--n-space-8) var(--n-space-8) 0;
  text-align: center;
}

.auth__logo {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto var(--n-space-4);
  border-radius: var(--n-radius);
  background: linear-gradient(135deg, var(--n-accent-strong), var(--n-accent));
  color: var(--n-text-inverse);
  box-shadow: 0 8px 26px rgba(95, 208, 224, 0.3);
  animation: authLogoIn 0.5s var(--n-ease) 0.08s both;
}

@keyframes authLogoIn {
  from {
    opacity: 0;
    transform: scale(0.82);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.auth__title {
  margin: 0 0 var(--n-space-1);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.auth__subtitle {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.auth__body {
  padding: var(--n-space-6) var(--n-space-8) 0;
}

.auth__field {
  margin-bottom: var(--n-space-4);
}

.auth__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
}

.auth__error {
  min-height: 18px;
  margin: 0 0 var(--n-space-3);
  color: var(--n-danger);
  font-size: var(--n-text-xs);
  text-align: center;
}

.auth__foot {
  margin: 0;
  padding: var(--n-space-5) var(--n-space-8) var(--n-space-8);
  text-align: center;
}

.auth__link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
}

@media (hover: hover) {
  .auth__link:hover {
    color: var(--n-accent-strong);
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth__card,
  .auth__logo,
  .auth__glow {
    animation: none;
  }
}
</style>
