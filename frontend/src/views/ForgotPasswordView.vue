<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell auth-main">
      <div class="panel forgot-card">
        <h2 class="forgot-title">忘记密码</h2>

        <div class="steps-indicator">
          <div :class="['step', { active: step === 1 }, { completed: step > 1 }]">
            <span class="step-number">1</span>
            <span class="step-label">验证邮箱</span>
          </div>
          <div class="step-divider" />
          <div :class="['step', { active: step === 2 }, { completed: step > 2 }]">
            <span class="step-number">2</span>
            <span class="step-label">重置密码</span>
          </div>
        </div>

        <div v-if="step === 1" class="step-content">
          <form class="forgot-form" @submit.prevent="handleSendCode">
            <div class="form-group">
              <input
                v-model="email"
                type="email"
                class="form-input"
                placeholder="请输入注册邮箱"
                required
              />
            </div>
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading">发送中…</span>
              <span v-else>发送验证码</span>
            </button>
          </form>
          <div class="back-link">
            <a href="#" @click.prevent="goToLogin">返回登录</a>
          </div>
        </div>

        <div v-if="step === 2" class="step-content">
          <form class="forgot-form" @submit.prevent="handleResetPassword">
            <div class="form-group">
              <input v-model="email" type="email" class="form-input" placeholder="注册邮箱" disabled />
            </div>
            <div class="form-group">
              <div class="code-input-group">
                <input
                  v-model="verificationCode"
                  type="text"
                  class="form-input code-input"
                  placeholder="请输入验证码"
                  required
                  maxlength="6"
                />
                <button
                  type="button"
                  class="resend-btn"
                  :disabled="countdown > 0 || resendLoading"
                  @click="handleResendCode"
                >
                  <span v-if="countdown > 0">{{ countdown }}秒后重发</span>
                  <span v-else-if="resendLoading">发送中…</span>
                  <span v-else>重新发送</span>
                </button>
              </div>
            </div>
            <div class="form-group">
              <input
                v-model="newPassword"
                type="password"
                class="form-input"
                placeholder="请输入新密码（6-30位）"
                required
                minlength="6"
                maxlength="30"
              />
            </div>
            <div class="form-group">
              <input
                v-model="confirmPassword"
                type="password"
                class="form-input"
                placeholder="请确认新密码"
                required
                minlength="6"
                maxlength="30"
              />
            </div>
            <button type="submit" class="submit-btn" :disabled="loading">
              <span v-if="loading">重置中…</span>
              <span v-else>重置密码</span>
            </button>
          </form>
          <div class="back-link">
            <a href="#" @click.prevent="goToLogin">返回登录</a>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useToast } from 'vue-toastification'
import API_CONFIG from "@/config/apiConfig.js";

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

// 发送验证码
const handleSendCode = async () => {
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-reset-code`, {
      email: email.value
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
    if (error.response) {
      toast.error(error.response.data.message || '发送失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
  } finally {
    loading.value = false
  }
}

// 重新发送验证码
const handleResendCode = async () => {
  resendLoading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-reset-code`, {
      email: email.value
    })
    
    if (response.data.success) {
      toast.success(response.data.message || '验证码已重新发送')
      startCountdown()
    } else {
      toast.error(response.data.message || '发送失败')
    }
  } catch (error) {
    console.error('重新发送验证码失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '发送失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
  } finally {
    resendLoading.value = false
  }
}

// 开始倒计时
const startCountdown = () => {
  countdown.value = 60
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

// 重置密码
const handleResetPassword = async () => {
  // 验证密码是否一致
  if (newPassword.value !== confirmPassword.value) {
    toast.error('两次输入的密码不一致')
    return
  }
  
  // 验证密码长度
  if (newPassword.value.length < 6 || newPassword.value.length > 30) {
    toast.error('密码长度必须在6-30位之间')
    return
  }
  
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/reset-password`, {
      email: email.value,
      code: verificationCode.value,
      newPassword: newPassword.value
    })
    
    if (response.data.success) {
      toast.success(response.data.message || '密码重置成功')
      // 清除倒计时
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdown.value = 0
      }
      // 跳转到登录页面
      setTimeout(() => {
        router.push('/login')
      }, 1500)
    } else {
      toast.error(response.data.message || '重置失败')
    }
  } catch (error) {
    console.error('重置密码失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '重置失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
  } finally {
    loading.value = false
  }
}

// 返回登录
const goToLogin = () => {
  // 清除倒计时
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdown.value = 0
  }
  router.push('/login')
}

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.forgot-card {
  width: 100%;
  max-width: 460px;
  padding: clamp(26px, 4vw, 38px) clamp(20px, 4vw, 30px);
}

.forgot-title {
  margin: 0 0 22px;
  text-align: center;
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #e9d5ff, #a5f3fc, #c4b5fd);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.steps-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-number {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  border: 2px solid rgba(255, 255, 255, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.9rem;
  color: var(--muted);
  transition: border-color 0.2s var(--ease), background 0.2s var(--ease), transform 0.2s var(--ease);
}

.step.active .step-number {
  background: linear-gradient(135deg, #8b5cf6, #22d3ee);
  border-color: rgba(255, 255, 255, 0.2);
  color: #fff;
  transform: scale(1.06);
  box-shadow: 0 6px 20px rgba(139, 92, 246, 0.35);
}

.step.completed .step-number {
  background: rgba(52, 211, 153, 0.35);
  border-color: rgba(52, 211, 153, 0.55);
  color: #ecfdf5;
}

.step-label {
  font-size: 0.8rem;
  color: var(--faint);
}

.step.active .step-label {
  color: var(--accent2);
  font-weight: 600;
}

.step-divider {
  width: 56px;
  height: 2px;
  background: rgba(255, 255, 255, 0.1);
  margin: 0 10px 18px;
}

.forgot-form {
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

.form-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.code-input-group {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.code-input {
  flex: 1;
  min-width: 0;
}

.resend-btn {
  font-family: inherit;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  min-width: 108px;
  transition: background 0.15s var(--ease), border-color 0.15s var(--ease);
}

.resend-btn:hover:not(:disabled) {
  background: rgba(139, 92, 246, 0.22);
  border-color: rgba(139, 92, 246, 0.4);
}

.resend-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.submit-btn {
  font-family: inherit;
  margin-top: 4px;
  padding: 12px 20px;
  width: 100%;
  border: none;
  border-radius: 999px;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.3);
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.05);
}

.submit-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.back-link {
  text-align: center;
  margin-top: 18px;
  font-size: 0.88rem;
}

.back-link a {
  color: var(--accent2);
  font-weight: 600;
  text-decoration: none;
}

.back-link a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .code-input-group {
    flex-direction: column;
  }

  .resend-btn {
    width: 100%;
  }
}
</style>