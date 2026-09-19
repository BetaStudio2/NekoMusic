<script setup>
/**
 * UserLoginView —— 用户登录
 * ------------------------------------------------------------
 * 布局与特效参考 WebWord 的登录卡片：Logo + 标题/副标题 + 标签字段 +
 * 错误区（占位避免跳动）+ 全宽主按钮 + 分隔线 + 次级入口。
 * 特效：卡片入场、Logo 光晕、输入聚焦光晕（NInput）、按钮按压（NButton）。
 *
 * 契约（保持与旧实现一致）：
 *  - POST /api/user/login
 *  - 成功后写 userToken / user，并广播 storage（供 GlobalPlayer / 顶栏同步）
 *  - 跳转首页
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NInput } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const router = useRouter()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  if (loading.value) return
  error.value = ''

  if (!username.value.trim()) {
    error.value = '请输入邮箱'
    return
  }
  if (!password.value) {
    error.value = '请输入密码'
    return
  }

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
      error.value = response.data.message || '登录失败'
      toast.error(error.value)
    }
  } catch (err) {
    console.error('登录失败:', err)
    error.value = err.response
      ? err.response.data.message || '登录失败'
      : '网络错误，请检查服务器连接'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <div class="auth">
      <span class="auth__glow" aria-hidden="true" />

      <div class="auth__card">
        <!-- 头部 -->
        <header class="auth__head">
          <div class="auth__logo">
            <NIcon name="cat" :size="28" />
          </div>
          <h1 class="auth__title">登录 Neko歌姬计划</h1>
          <p class="auth__subtitle">请输入您的凭据</p>
        </header>

        <!-- 表单 -->
        <form class="auth__body" @submit.prevent="handleLogin">
          <div class="auth__field">
            <label class="auth__label" for="loginEmail">邮箱</label>
            <NInput
              id="loginEmail"
              v-model="username"
              icon="mail"
              placeholder="输入邮箱"
              autocomplete="username"
            />
          </div>

          <div class="auth__field">
            <label class="auth__label" for="loginPassword">密码</label>
            <NInput
              id="loginPassword"
              v-model="password"
              type="password"
              icon="lock"
              placeholder="输入密码"
              autocomplete="current-password"
            />
          </div>

          <p class="auth__error" role="alert">{{ error || '\u00A0' }}</p>

          <NButton type="submit" variant="primary" size="lg" block :loading="loading">
            登录
          </NButton>
        </form>

        <div class="auth__divider"><span>或</span></div>

        <!-- 次级入口 -->
        <footer class="auth__foot">
          <NButton variant="secondary" size="lg" block icon="user-plus" to="/register">
            注册新账户
          </NButton>
          <RouterLink to="/forgot-password" class="auth__link">
            <NIcon name="key" :size="14" />
            忘记密码？
          </RouterLink>
        </footer>
      </div>
    </div>
  </PageShell>
</template>

<style scoped>
.auth {
  position: relative;
  width: 100%;
  max-width: 400px;
}

/* 卡片背后的柔光 */
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

/* ==================== 卡片 ==================== */
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

/* 顶部一道极淡的青光 */
.auth__card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--n-accent-line), transparent);
}

/* ==================== 头部 ==================== */
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
  animation: authLogoIn 0.6s var(--n-ease) 0.08s both;
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

/* ==================== 表单 ==================== */
.auth__body {
  padding: var(--n-space-6) var(--n-space-8) var(--n-space-5);
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

/* 固定高度，避免出现错误时卡片跳动 */
.auth__error {
  min-height: 18px;
  margin: 0 0 var(--n-space-3);
  color: var(--n-danger);
  font-size: var(--n-text-xs);
  text-align: center;
}

/* ==================== 分隔线 ==================== */
.auth__divider {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  padding: 0 var(--n-space-8);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.auth__divider::before,
.auth__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--n-line);
}

/* ==================== 次级入口 ==================== */
.auth__foot {
  padding: var(--n-space-5) var(--n-space-8) var(--n-space-8);
}

.auth__link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: var(--n-space-4);
  width: 100%;
  justify-content: center;
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
