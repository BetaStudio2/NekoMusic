<template>
  <div class="forgot-password-container">
    <div class="forgot-password-card">
      <h2 class="forgot-password-title">忘记密码</h2>
      
      <!-- 步骤指示器 -->
      <div class="steps-indicator">
        <div :class="['step', { active: step === 1 }, { completed: step > 1 }]">
          <span class="step-number">1</span>
          <span class="step-label">验证邮箱</span>
        </div>
        <div class="step-divider"></div>
        <div :class="['step', { active: step === 2 }, { completed: step > 2 }]">
          <span class="step-number">2</span>
          <span class="step-label">重置密码</span>
        </div>
      </div>

      <!-- 步骤1：发送验证码 -->
      <div v-if="step === 1" class="step-content">
        <form @submit.prevent="handleSendCode" class="forgot-password-form">
          <div class="form-group">
            <input
              type="email"
              v-model="email"
              class="form-input"
              placeholder="请输入注册邮箱"
              required
            />
          </div>
          
          <button type="submit" class="submit-btn" :disabled="loading">
            <span v-if="loading">发送中...</span>
            <span v-else>发送验证码</span>
          </button>
        </form>
        
        <div class="back-link">
          <a href="#" @click.prevent="goToLogin">返回登录</a>
        </div>
      </div>

      <!-- 步骤2：验证码和重置密码 -->
      <div v-if="step === 2" class="step-content">
        <form @submit.prevent="handleResetPassword" class="forgot-password-form">
          <div class="form-group">
            <input
              type="email"
              v-model="email"
              class="form-input"
              placeholder="注册邮箱"
              disabled
            />
          </div>
          
          <div class="form-group">
            <div class="code-input-group">
              <input
                type="text"
                v-model="verificationCode"
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
                <span v-else-if="resendLoading">发送中...</span>
                <span v-else>重新发送</span>
              </button>
            </div>
          </div>
          
          <div class="form-group">
            <input
              type="password"
              v-model="newPassword"
              class="form-input"
              placeholder="请输入新密码（6-30位）"
              required
              minlength="6"
              maxlength="30"
            />
          </div>
          
          <div class="form-group">
            <input
              type="password"
              v-model="confirmPassword"
              class="form-input"
              placeholder="请确认新密码"
              required
              minlength="6"
              maxlength="30"
            />
          </div>
          
          <button type="submit" class="submit-btn" :disabled="loading">
            <span v-if="loading">重置中...</span>
            <span v-else>重置密码</span>
          </button>
        </form>
        
        <div class="back-link">
          <a href="#" @click.prevent="goToLogin">返回登录</a>
        </div>
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

// 组件卸载时清除倒计时
import { onUnmounted } from 'vue'
onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})
</script>

<style scoped>
.forgot-password-container {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.forgot-password-card {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  width: 100%;
  max-width: 450px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.forgot-password-card::before {
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

.forgot-password-title {
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

.steps-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 30px;
  position: relative;
  z-index: 1;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
}

.step-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  border: 2px solid rgba(106, 90, 205, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  color: #6a5acd;
  transition: all 0.3s ease;
}

.step.active .step-number {
  background: linear-gradient(135deg, #6a5acd, #84ffff);
  border-color: #6a5acd;
  color: white;
  transform: scale(1.1);
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
}

.step.completed .step-number {
  background: #4caf50;
  border-color: #4caf50;
  color: white;
}

.step-label {
  font-size: 0.85rem;
  color: rgba(106, 90, 205, 0.7);
}

.step.active .step-label {
  color: #6a5acd;
  font-weight: 500;
}

.step-divider {
  width: 60px;
  height: 2px;
  background: rgba(106, 90, 205, 0.2);
  margin: 0 10px 16px;
}

.step-content {
  position: relative;
  z-index: 1;
}

.forgot-password-form {
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

.form-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: rgba(255, 255, 255, 0.15);
}

.code-input-group {
  display: flex;
  gap: 10px;
}

.code-input {
  flex: 1;
}

.resend-btn {
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: #6a5acd;
  border: 1px solid rgba(106, 90, 205, 0.3);
  border-radius: 30px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  min-width: 100px;
}

.resend-btn:hover:not(:disabled) {
  background: rgba(106, 90, 205, 0.1);
  border-color: rgba(106, 90, 205, 0.5);
  transform: translateY(-1px);
}

.resend-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.submit-btn {
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

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(106, 90, 205, 0.5);
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.back-link {
  text-align: center;
  margin-top: 20px;
  color: #6a5acd;
}

.back-link a {
  color: #6a5acd;
  text-decoration: none;
  font-weight: 500;
}

.back-link a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .forgot-password-card {
    padding: 30px 20px;
    margin: 0 10px;
  }
  
  .code-input-group {
    flex-direction: column;
  }
  
  .resend-btn {
    width: 100%;
  }
}
</style>