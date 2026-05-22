<template>
  <div class="register-container">
    <div class="register-card">
      <h2 class="register-title">用户注册</h2>
      <form @submit.prevent="handleRegister" class="register-form">
        <div class="form-group">
          <input
            type="text"
            v-model="username"
            class="form-input"
            placeholder="用户名"
            required
          />
        </div>

        <div class="form-group">
          <input
            type="email"
            v-model="email"
            class="form-input"
            placeholder="邮箱"
            required
          />
        </div>

        <div class="form-group">
          <div style="display: flex; gap: 10px;">
            <input
                type="text"
                v-model="verificationCode"
                class="form-input"
                placeholder="验证码"
                required
                style="flex: 1;"
            />
            <button
                type="button"
                class="verification-btn"
                @click="sendVerificationCode"
                :disabled="codeSending || countdown > 0"
            >
              {{ codeBtnText }}
            </button>
          </div>
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

        <div class="form-group">
          <input
            type="password"
            v-model="confirmPassword"
            class="form-input"
            placeholder="确认密码"
            required
          />
        </div>

        <div class="form-group slider-block">
          <span class="slider-label">安全验证（必填）</span>
          <p v-if="captchaLoading" class="slider-status">正在加载拼图…</p>
          <div v-else-if="captchaError" class="slider-status slider-error">
            {{ captchaError }}
            <button type="button" class="slider-retry" @click="loadSliderCaptcha">重试</button>
          </div>
          <template v-else>
            <div class="slider-captcha-wrap">
              <div
                class="slider-stage"
                :style="{ width: bgWidth + 'px', height: bgHeight + 'px' }"
              >
                <img :src="bgImageUrl" alt="" class="slider-bg-img" draggable="false" />
                <img
                  :src="sliderImageUrl"
                  alt=""
                  class="slider-piece-img"
                  draggable="false"
                  :style="{
                    width: sliderW + 'px',
                    left: sliderX + 'px',
                    top: puzzleY + 'px'
                  }"
                />
              </div>
              <div
                ref="railRef"
                class="slider-rail"
                :style="{ width: bgWidth + 'px' }"
                @pointerdown="onRailTrackPointerDown"
              >
                <div class="slider-rail-inner" aria-hidden="true">
                  <div class="slider-rail-track-line" />
                </div>
                <button
                  type="button"
                  class="slider-rail-thumb"
                  :style="{ width: railThumbW + 'px', left: thumbDisplayX + 'px' }"
                  aria-label="拖动滑块完成验证"
                  @pointerdown.stop.prevent="onThumbPointerDown"
                >
                  <span class="slider-rail-thumb-arrows" aria-hidden="true">››</span>
                </button>
              </div>
            </div>
            <div class="slider-toolbar">
              <button type="button" class="slider-refresh" @click="loadSliderCaptcha">换一张</button>
              <span class="slider-hint">拖动下方滑轨对齐拼图</span>
            </div>
          </template>
        </div>

        <button type="submit" class="register-btn" :disabled="loading">
          <span v-if="loading">注册中...</span>
          <span v-else>注册</span>
        </button>
      </form>

      <div class="register-footer">
        <p>已有账户？<a href="#" @click.prevent="goToLogin">立即登录</a></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useToast } from 'vue-toastification'
import API_CONFIG from "@/config/apiConfig.js";

const toast = useToast()
const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const verificationCode = ref('')
const loading = ref(false)
const codeSending = ref(false)
const countdown = ref(0)
const countdownInterval = ref(null)

/** 滑块拼图（与 POST /api/user/register 中 captchaToken、captchaOffsetX 对应） */
const captchaLoading = ref(true)
const captchaError = ref('')
const captchaToken = ref('')
const bgImageUrl = ref('')
const sliderImageUrl = ref('')
const puzzleY = ref(0)
const bgWidth = ref(300)
const bgHeight = ref(180)
const sliderW = ref(52)
const sliderH = ref(52)
const sliderX = ref(0)

/** 底部滑轨按钮宽度（与背景图同宽映射到拼图 X） */
const railThumbW = 48
const railRef = ref(null)

let dragPointerOffset = 0
let railDragging = false

const maxSliderX = computed(() =>
  Math.max(0, bgWidth.value - sliderW.value)
)

const thumbMaxTravel = computed(() =>
  Math.max(0, bgWidth.value - railThumbW)
)

/** 滑轨手柄水平位置（px），与 sliderX 线性对应 */
const thumbDisplayX = computed(() => {
  if (maxSliderX.value <= 0) return 0
  return Math.round((sliderX.value / maxSliderX.value) * thumbMaxTravel.value)
})

function setSliderXFromThumbLeft(leftPx) {
  const tm = thumbMaxTravel.value
  const mx = maxSliderX.value
  if (mx <= 0) {
    sliderX.value = 0
    return
  }
  const clamped = Math.max(0, Math.min(tm, leftPx))
  sliderX.value = Math.round((clamped / tm) * mx)
}

const onThumbPointerMove = (e) => {
  if (!railDragging) return
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  const leftPx = e.clientX - rail.left - dragPointerOffset
  setSliderXFromThumbLeft(leftPx)
}

const onThumbPointerUp = () => {
  if (!railDragging) return
  railDragging = false
  window.removeEventListener('pointermove', onThumbPointerMove)
  window.removeEventListener('pointerup', onThumbPointerUp)
  window.removeEventListener('pointercancel', onThumbPointerUp)
}

const onThumbPointerDown = (e) => {
  if (e.button != null && e.button !== 0) return
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  railDragging = true
  dragPointerOffset = e.clientX - rail.left - thumbDisplayX.value
  try {
    e.currentTarget?.setPointerCapture?.(e.pointerId)
  } catch {
    /* ignore */
  }
  window.addEventListener('pointermove', onThumbPointerMove)
  window.addEventListener('pointerup', onThumbPointerUp)
  window.addEventListener('pointercancel', onThumbPointerUp)
}

/** 点击滑轨空白处跳转手柄（不点在按钮上时） */
const onRailTrackPointerDown = (e) => {
  if (e.target.closest('.slider-rail-thumb')) return
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  const x = e.clientX - rail.left
  const leftPx = x - railThumbW / 2
  setSliderXFromThumbLeft(leftPx)
}

const loadSliderCaptcha = async () => {
  captchaLoading.value = true
  captchaError.value = ''
  sliderX.value = 0
  captchaToken.value = ''
  try {
    const { data } = await axios.get(`${API_CONFIG.BASE_URL}/api/captcha/slider`)
    if (!data.success || !data.data) {
      captchaError.value = data.message || '加载失败'
      return
    }
    const d = data.data
    captchaToken.value = d.captchaToken || ''
    puzzleY.value = Number(d.puzzleY) || 0
    bgWidth.value = Number(d.bgWidth) || 300
    bgHeight.value = Number(d.bgHeight) || 180
    sliderW.value = Number(d.sliderWidth) || 52
    sliderH.value = Number(d.sliderHeight) || 52
    bgImageUrl.value = d.bgImage || ''
    sliderImageUrl.value = d.sliderImage || ''
    if (!captchaToken.value || !bgImageUrl.value || !sliderImageUrl.value) {
      captchaError.value = '验证码数据不完整'
    }
  } catch (err) {
    console.error(err)
    captchaError.value = '网络错误，无法加载安全验证'
  } finally {
    captchaLoading.value = false
  }
}

onMounted(() => {
  loadSliderCaptcha()
})

onUnmounted(() => {
  onThumbPointerUp()
})

// 验证码按钮文字
const codeBtnText = computed(() => {
  return countdown.value > 0 ? `${countdown.value}秒后重发` : '获取验证码'
})

// 发送验证码
const sendVerificationCode = async () => {
  if (!email.value) {
    toast.error('请先输入邮箱地址')
    return
  }
  
  // 验证邮箱格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(email.value)) {
    toast.error('请输入有效的邮箱地址')
    return
  }
  
  codeSending.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-verification`, {
      email: email.value,
      usernme: username.value || '用户'
    })
    
    if (response.data.success) {
      toast.success('验证码已发送至您的邮箱')
      startCountdown()
    } else {
      toast.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '发送验证码失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
  } finally {
    codeSending.value = false
  }
}

// 开始倒计时
const startCountdown = () => {
  countdown.value = 60
  countdownInterval.value = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownInterval.value)
    }
  }, 1000)
}

// 处理注册逻辑（注册接口内会校验滑块，缺参或错误直接失败）
const handleRegister = async () => {
  if (password.value !== confirmPassword.value) {
    toast.error('两次输入的密码不一致')
    return
  }

  if (captchaLoading.value || captchaError.value || !captchaToken.value) {
    toast.error('请等待安全验证加载完成，或点击重试')
    return
  }
  
  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/register`, {
      username: username.value,
      email: email.value,
      password: password.value,
      verificationCode: verificationCode.value,
      captchaToken: captchaToken.value,
      captchaOffsetX: sliderX.value
    })
    
    if (response.data.success) {
      toast.success('注册成功！请登录您的账户。')
      router.push('/login')
    } else {
      toast.error(response.data.message || '注册失败')
      await loadSliderCaptcha()
    }
  } catch (error) {
    console.error('注册失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '注册失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
    await loadSliderCaptcha()
  } finally {
    loading.value = false
  }
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-container {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.slider-block {
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.slider-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: #5c4b7b;
}

.slider-status {
  margin: 0;
  font-size: 0.9rem;
  color: #5c4b7b;
}

.slider-error {
  color: #c0392b;
}

.slider-retry {
  margin-left: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  border: 1px solid rgba(106, 90, 205, 0.5);
  background: rgba(255, 255, 255, 0.6);
  cursor: pointer;
}

.slider-captcha-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.slider-stage {
  position: relative;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.25);
  line-height: 0;
}

.slider-bg-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
  user-select: none;
  pointer-events: none;
}

.slider-piece-img {
  position: absolute;
  height: auto;
  pointer-events: none;
  user-select: none;
}

.slider-rail {
  position: relative;
  flex-shrink: 0;
  height: 44px;
  margin-top: 10px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(106, 90, 205, 0.22);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.06);
  touch-action: none;
  cursor: pointer;
}

.slider-rail-inner {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  height: 8px;
  pointer-events: none;
}

.slider-rail-track-line {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #e0e0e8, #c8c8d8);
}

.slider-rail-thumb {
  position: absolute;
  top: 4px;
  height: 36px;
  padding: 0;
  margin: 0;
  border: none;
  border-radius: 10px;
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 1.1rem;
  line-height: 1;
  background: linear-gradient(135deg, #6a5acd, #9b7dd4);
  box-shadow: 0 2px 8px rgba(106, 90, 205, 0.45);
  touch-action: none;
  user-select: none;
}

.slider-rail-thumb:active {
  cursor: grabbing;
}

.slider-rail-thumb-arrows {
  letter-spacing: -2px;
  font-weight: 700;
  opacity: 0.95;
}

.slider-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.slider-refresh {
  padding: 8px 14px;
  border-radius: 10px;
  border: none;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.85), rgba(147, 112, 219, 0.85));
}

.slider-hint {
  font-size: 0.8rem;
  color: rgba(92, 75, 123, 0.85);
}

.register-card {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.register-card::before {
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

.register-title {
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

.register-form {
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

.verification-btn {
  padding: 14px 15px;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(147, 112, 219, 0.8));
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 0.9rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 8px 32px rgba(106, 90, 205, 0.3);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
  width: auto;
  white-space: nowrap;
}

.verification-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(106, 90, 205, 0.5);
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
}

.verification-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.register-btn {
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

.register-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(106, 90, 205, 0.5);
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
}

.register-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.register-footer {
  text-align: center;
  margin-top: 20px;
  color: #6a5acd;
}

.register-footer a {
  color: #6a5acd;
  text-decoration: none;
  font-weight: 500;
}

.register-footer a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .register-card {
    padding: 30px 20px;
    margin: 0 10px;
  }
}
</style>