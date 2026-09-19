<script setup>
/**
 * ForgotPasswordView —— 两步式找回密码
 * ------------------------------------------------------------
 * 契约（保持与旧实现一致）：
 *  - POST /api/user/send-reset-code（发送/重发）
 *  - POST /api/user/reset-password
 *  - 成功后 1.5s 跳转登录
 */
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'

const toast = useToast()
const router = useRouter()

const step = ref(1)
const email = ref('')
const verificationCode = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)
const resendLoading = ref(false)
const countdown = ref(0)
let countdownTimer = null

function startCountdown() {
  countdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

async function handleSendCode() {
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-reset-code`, {
      email: email.value,
    })
    if (response.data.success) {
      toast.success(response.data.message || '验证码已发送')
      step.value = 2
      startCountdown()
    } else {
      toast.error(response.data.message || '发送失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    toast.error(error.response?.data?.message || (error.response ? '发送失败' : '网络错误，请检查服务器连接'))
  } finally {
    loading.value = false
  }
}

async function handleResendCode() {
  resendLoading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-reset-code`, {
      email: email.value,
    })
    if (response.data.success) {
      toast.success(response.data.message || '验证码已重新发送')
      startCountdown()
    } else {
      toast.error(response.data.message || '发送失败')
    }
  } catch (error) {
    console.error('重新发送验证码失败:', error)
    toast.error(error.response?.data?.message || (error.response ? '发送失败' : '网络错误，请检查服务器连接'))
  } finally {
    resendLoading.value = false
  }
}

async function handleResetPassword() {
  if (newPassword.value !== confirmPassword.value) {
    toast.error('两次输入的密码不一致')
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 30) {
    toast.error('密码长度必须在6-30位之间')
    return
  }

  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/reset-password`, {
      email: email.value,
      code: verificationCode.value,
      newPassword: newPassword.value,
    })
    if (response.data.success) {
      toast.success(response.data.message || '密码重置成功')
      stopCountdown()
      countdown.value = 0
      setTimeout(() => router.push('/login'), 1500)
    } else {
      toast.error(response.data.message || '重置失败')
    }
  } catch (error) {
    console.error('重置密码失败:', error)
    toast.error(error.response?.data?.message || (error.response ? '重置失败' : '网络错误，请检查服务器连接'))
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  stopCountdown()
  countdown.value = 0
  router.push('/login')
}

onUnmounted(stopCountdown)
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <NCard pad="lg" class="forgot">
      <h1 class="forgot__title">忘记密码</h1>

      <!-- 步骤指示 -->
      <ol class="steps">
        <li class="step" :class="{ 'step--active': step === 1, 'step--done': step > 1 }">
          <span class="step__num">
            <NIcon v-if="step > 1" name="check" :size="16" />
            <template v-else>1</template>
          </span>
          <span class="step__label">验证邮箱</span>
        </li>
        <li class="steps__divider" aria-hidden="true" />
        <li class="step" :class="{ 'step--active': step === 2 }">
          <span class="step__num">2</span>
          <span class="step__label">重置密码</span>
        </li>
      </ol>

      <!-- 第一步 -->
      <form v-if="step === 1" class="form" @submit.prevent="handleSendCode">
        <NInput
          v-model="email"
          type="email"
          icon="mail"
          placeholder="请输入注册邮箱"
          required
        />
        <NButton type="submit" variant="primary" size="lg" block :loading="loading">
          发送验证码
        </NButton>
        <p class="form__back">
          <a href="#" @click.prevent="goToLogin">返回登录</a>
        </p>
      </form>

      <!-- 第二步 -->
      <form v-else class="form" @submit.prevent="handleResetPassword">
        <NInput v-model="email" type="email" icon="mail" placeholder="注册邮箱" disabled />

        <div class="code-row">
          <NInput
            v-model="verificationCode"
            icon="shield-check"
            placeholder="请输入验证码"
            maxlength="6"
            required
          />
          <NButton
            variant="secondary"
            :disabled="countdown > 0 || resendLoading"
            @click="handleResendCode"
          >
            <template v-if="countdown > 0">{{ countdown }}s 后重发</template>
            <template v-else-if="resendLoading">发送中…</template>
            <template v-else>重新发送</template>
          </NButton>
        </div>

        <NInput
          v-model="newPassword"
          type="password"
          icon="lock"
          placeholder="请输入新密码（6-30位）"
          minlength="6"
          maxlength="30"
          required
        />
        <NInput
          v-model="confirmPassword"
          type="password"
          icon="lock"
          placeholder="请确认新密码"
          minlength="6"
          maxlength="30"
          required
        />

        <NButton type="submit" variant="primary" size="lg" block :loading="loading">
          重置密码
        </NButton>
        <p class="form__back">
          <a href="#" @click.prevent="goToLogin">返回登录</a>
        </p>
      </form>
    </NCard>
  </PageShell>
</template>

<style scoped>
.forgot {
  width: 100%;
  max-width: 460px;
}

.forgot__title {
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

/* ===== 步骤 ===== */
.steps {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-6);
  padding: 0;
  list-style: none;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-2);
}

.step__num {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--n-radius-sm);
  background: var(--n-surface-soft);
  border: 1px solid var(--n-line);
  color: var(--n-text-muted);
  font-weight: var(--n-weight-semibold);
  font-size: var(--n-text-sm);
  transition:
    background var(--n-duration) var(--n-ease),
    border-color var(--n-duration) var(--n-ease),
    color var(--n-duration) var(--n-ease);
}

.step--active .step__num {
  background: var(--n-accent-soft);
  border-color: var(--n-accent-line);
  color: var(--n-accent-strong);
}

.step--done .step__num {
  background: var(--n-accent);
  border-color: transparent;
  color: var(--n-text-inverse);
}

.step__label {
  font-size: var(--n-text-xs);
  color: var(--n-text-faint);
}

.step--active .step__label {
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-semibold);
}

.steps__divider {
  width: 48px;
  height: 1px;
  margin-top: 17px;
  background: var(--n-line);
}

/* ===== 表单 ===== */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-4);
}

.code-row {
  display: flex;
  gap: var(--n-space-3);
}

.code-row :deep(.n-input) {
  flex: 1;
  min-width: 0;
}

.form__back {
  margin: var(--n-space-1) 0 0;
  text-align: center;
  font-size: var(--n-text-sm);
}

.form__back a {
  font-weight: var(--n-weight-semibold);
}

@media (max-width: 560px) {
  .code-row {
    flex-direction: column;
  }
}
</style>
