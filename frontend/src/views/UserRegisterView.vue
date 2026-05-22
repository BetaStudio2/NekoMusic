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
                :disabled="codeSending || countdown > 0 || captchaModalOpen"
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

        <button type="submit" class="register-btn" :disabled="loading">
          <span v-if="loading">注册中...</span>
          <span v-else>注册</span>
        </button>
      </form>

      <div class="register-footer">
        <p>已有账户？<a href="#" @click.prevent="goToLogin">立即登录</a></p>
      </div>
    </div>

    <Teleport to="body">
      <Transition name="captcha-modal">
        <div
          v-if="captchaModalOpen"
          class="captcha-modal-backdrop"
          @click.self="closeCaptchaModal"
        >
          <div class="captcha-modal-card" role="dialog" aria-modal="true" aria-labelledby="captcha-modal-title" @click.stop>
            <button type="button" class="captcha-modal-close" aria-label="关闭" @click="closeCaptchaModal">×</button>
            <h3 id="captcha-modal-title" class="captcha-modal-title">安全验证</h3>
            <p class="captcha-modal-desc">请拖动下方滑轨对齐拼图，验证通过后将向你的邮箱发送验证码。</p>
            <div class="form-group slider-block captcha-modal-slider">
              <p v-if="captchaLoading" class="slider-status">正在加载拼图…</p>
              <div v-else-if="captchaError" class="slider-status slider-error">
                {{ captchaError }}
                <button type="button" class="slider-retry" @click="loadSliderCaptcha">重试</button>
              </div>
              <template v-else>
                <div class="slider-challenge-panel">
                  <div
                    class="slider-captcha-wrap"
                    :class="{ 'slider-captcha-wrap--shake': shakeActive }"
                  >
                    <div
                      class="slider-stage"
                      :class="{ 'slider-stage--checking': slideState === 'checking' }"
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
                      :class="{ 'slider-rail--checking': slideState === 'checking' }"
                      :style="{ width: bgWidth + 'px' }"
                      @pointerdown="onRailTrackPointerDown"
                    >
                      <div class="slider-rail-inner" aria-hidden="true">
                        <div class="slider-rail-track-line" />
                      </div>
                      <div v-if="slideState === 'checking'" class="slider-rail-scan" aria-hidden="true" />
                      <button
                        type="button"
                        class="slider-rail-thumb"
                        :disabled="slideState === 'checking'"
                        :style="{ width: railThumbW + 'px', left: thumbDisplayX + 'px' }"
                        aria-label="拖动滑块完成验证"
                        @pointerdown.stop.prevent="onThumbPointerDown"
                      >
                        <span v-if="slideState === 'checking'" class="slider-thumb-spinner" aria-hidden="true" />
                        <span v-else class="slider-rail-thumb-arrows" aria-hidden="true">››</span>
                      </button>
                    </div>
                    <p class="slider-status-line" :class="'slider-status-line--' + slideState">
                      {{ slideStatusText }}
                    </p>
                  </div>
                  <div class="slider-toolbar">
                    <button
                      type="button"
                      class="slider-refresh"
                      :disabled="slideState === 'checking'"
                      @click="loadSliderCaptcha"
                    >
                      换一张
                    </button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onUnmounted } from 'vue'
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
const captchaModalOpen = ref(false)

/** 滑块：弹窗内 GET 挑战 → 松手 POST /api/captcha/slider/verify → captchaPassToken → 发送邮箱验证码时消费 */
const captchaLoading = ref(true)
const captchaError = ref('')
const captchaToken = ref('')
const captchaPassToken = ref('')
/** idle | dragging | checking | fail */
const slideState = ref('idle')
const shakeActive = ref(false)
const bgImageUrl = ref('')
const sliderImageUrl = ref('')
const puzzleY = ref(0)
const bgWidth = ref(300)
const bgHeight = ref(180)
const sliderW = ref(52)
const sliderH = ref(52)
const sliderX = ref(0)

const railThumbW = 48
const railRef = ref(null)

let dragPointerOffset = 0
let railDragging = false
let verifyAbort = null
/** 点击轨道后等待松手校验的一次性监听，关闭弹窗时必须移除 */
let railTrackReleaseHandler = null

const maxSliderX = computed(() =>
  Math.max(0, bgWidth.value - sliderW.value)
)

const thumbMaxTravel = computed(() =>
  Math.max(0, bgWidth.value - railThumbW)
)

const thumbDisplayX = computed(() => {
  if (maxSliderX.value <= 0) return 0
  return Math.round((sliderX.value / maxSliderX.value) * thumbMaxTravel.value)
})

const slideStatusText = computed(() => {
  switch (slideState.value) {
    case 'checking':
      return codeSending.value ? '正在发送验证码…' : '正在校验，请稍候…'
    case 'fail':
      return '未对齐，已为你换新题'
    case 'dragging':
      return '松开手指完成校验'
    default:
      return '拖动下方滑轨对齐拼图，松开即可完成校验'
  }
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

function invalidateSlidePass() {
  captchaPassToken.value = ''
}

async function scheduleSlideVerify() {
  if (!captchaToken.value || captchaLoading.value) return
  if (slideState.value === 'checking') return
  verifyAbort?.abort()
  verifyAbort = new AbortController()
  const ac = verifyAbort
  slideState.value = 'checking'
  try {
    const { data } = await axios.post(
      `${API_CONFIG.BASE_URL}/api/captcha/slider/verify`,
      {
        captchaToken: captchaToken.value,
        captchaOffsetX: sliderX.value
      },
      { signal: ac.signal }
    )
    if (ac.signal.aborted) return
    if (data.success && data.data?.captchaPassToken) {
      captchaPassToken.value = data.data.captchaPassToken
      await sendVerificationWithCaptcha()
      return
    }
    captchaPassToken.value = ''
    slideState.value = 'fail'
    shakeActive.value = true
    toast.error(data.message || '验证未通过')
    setTimeout(() => {
      shakeActive.value = false
    }, 480)
    await new Promise((r) => setTimeout(r, 620))
    await loadSliderCaptcha()
  } catch (err) {
    if (axios.isCancel?.(err) || err.code === 'ERR_CANCELED' || err.name === 'CanceledError') {
      slideState.value = 'idle'
      return
    }
    console.error(err)
    captchaPassToken.value = ''
    slideState.value = 'fail'
    shakeActive.value = true
    toast.error('校验请求失败，请重试')
    setTimeout(() => {
      shakeActive.value = false
    }, 480)
    await new Promise((r) => setTimeout(r, 620))
    await loadSliderCaptcha()
  }
}

const onThumbPointerMove = (e) => {
  if (!railDragging) return
  if (slideState.value === 'checking') return
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  const leftPx = e.clientX - rail.left - dragPointerOffset
  setSliderXFromThumbLeft(leftPx)
}

function detachThumbRailListeners() {
  window.removeEventListener('pointermove', onThumbPointerMove)
  window.removeEventListener('pointerup', onThumbPointerUp)
  window.removeEventListener('pointercancel', onThumbPointerUp)
  railDragging = false
}

const onThumbPointerUp = () => {
  if (!railDragging) return
  detachThumbRailListeners()
  slideState.value = 'idle'
  scheduleSlideVerify()
}

const onThumbPointerDown = (e) => {
  if (slideState.value === 'checking') return
  if (e.button != null && e.button !== 0) return
  invalidateSlidePass()
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  railDragging = true
  slideState.value = 'dragging'
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

const onRailTrackPointerDown = (e) => {
  if (slideState.value === 'checking') return
  if (e.target.closest('.slider-rail-thumb')) return
  invalidateSlidePass()
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  const x = e.clientX - rail.left
  const leftPx = x - railThumbW / 2
  setSliderXFromThumbLeft(leftPx)
  if (railTrackReleaseHandler) {
    window.removeEventListener('pointerup', railTrackReleaseHandler)
    window.removeEventListener('pointercancel', railTrackReleaseHandler)
    railTrackReleaseHandler = null
  }
  railTrackReleaseHandler = () => {
    window.removeEventListener('pointerup', railTrackReleaseHandler)
    window.removeEventListener('pointercancel', railTrackReleaseHandler)
    railTrackReleaseHandler = null
    scheduleSlideVerify()
  }
  window.addEventListener('pointerup', railTrackReleaseHandler)
  window.addEventListener('pointercancel', railTrackReleaseHandler)
}

const loadSliderCaptcha = async () => {
  verifyAbort?.abort()
  verifyAbort = null
  captchaLoading.value = true
  captchaError.value = ''
  captchaPassToken.value = ''
  slideState.value = 'idle'
  shakeActive.value = false
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

function closeCaptchaModal() {
  detachThumbRailListeners()
  if (railTrackReleaseHandler) {
    window.removeEventListener('pointerup', railTrackReleaseHandler)
    window.removeEventListener('pointercancel', railTrackReleaseHandler)
    railTrackReleaseHandler = null
  }
  verifyAbort?.abort()
  verifyAbort = null
  captchaModalOpen.value = false
  captchaPassToken.value = ''
  slideState.value = 'idle'
  shakeActive.value = false
}

async function sendVerificationWithCaptcha() {
  const token = captchaPassToken.value
  if (!token) return
  codeSending.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/send-verification`, {
      email: email.value,
      username: username.value || '用户',
      captchaPassToken: token
    })
    if (response.data.success) {
      toast.success('验证码已发送至您的邮箱')
      closeCaptchaModal()
      startCountdown()
    } else {
      toast.error(response.data.message || '发送验证码失败')
      await loadSliderCaptcha()
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    if (error.response) {
      toast.error(error.response.data?.message || '发送验证码失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
    await loadSliderCaptcha()
  } finally {
    codeSending.value = false
    if (captchaModalOpen.value) {
      slideState.value = 'idle'
    }
  }
}

onUnmounted(() => {
  detachThumbRailListeners()
  if (railTrackReleaseHandler) {
    window.removeEventListener('pointerup', railTrackReleaseHandler)
    window.removeEventListener('pointercancel', railTrackReleaseHandler)
    railTrackReleaseHandler = null
  }
  verifyAbort?.abort()
})

// 验证码按钮文字
const codeBtnText = computed(() => {
  return countdown.value > 0 ? `${countdown.value}秒后重发` : '获取验证码'
})

// 发送验证码：先打开弹窗完成滑块，再请求邮件
const sendVerificationCode = () => {
  if (!email.value) {
    toast.error('请先输入邮箱地址')
    return
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(email.value)) {
    toast.error('请输入有效的邮箱地址')
    return
  }
  if (countdown.value > 0 || codeSending.value) return
  captchaModalOpen.value = true
  nextTick(() => {
    loadSliderCaptcha()
  })
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

// 处理注册逻辑
const handleRegister = async () => {
  if (password.value !== confirmPassword.value) {
    toast.error('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/register`, {
      username: username.value,
      email: email.value,
      password: password.value,
      verificationCode: verificationCode.value
    })

    if (response.data.success) {
      toast.success('注册成功！请登录您的账户。')
      router.push('/login')
    } else {
      toast.error(response.data.message || '注册失败')
    }
  } catch (error) {
    console.error('注册失败:', error)
    if (error.response) {
      toast.error(error.response.data.message || '注册失败')
    } else {
      toast.error('网络错误，请检查服务器连接')
    }
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

.captcha-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
  background: rgba(15, 15, 35, 0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.captcha-modal-card {
  position: relative;
  width: 100%;
  max-width: min(420px, calc(100vw - 32px));
  max-height: min(90vh, 640px);
  overflow-x: auto;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 22px 20px 20px;
  box-sizing: border-box;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 16px 48px rgba(31, 38, 135, 0.35);
  border: 1px solid rgba(106, 90, 205, 0.2);
}

.captcha-modal-close {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 36px;
  height: 36px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 50%;
  font-size: 1.35rem;
  line-height: 1;
  cursor: pointer;
  color: #5c4b7b;
  background: rgba(106, 90, 205, 0.12);
  transition: background 0.2s ease, color 0.2s ease;
}

.captcha-modal-close:hover {
  background: rgba(106, 90, 205, 0.22);
  color: #3d2f66;
}

.captcha-modal-title {
  margin: 0 40px 8px 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: #4a3d6b;
}

.captcha-modal-desc {
  margin: 0 0 14px;
  font-size: 0.85rem;
  line-height: 1.45;
  color: #6b5b8a;
}

.captcha-modal-slider {
  margin-top: 4px;
}

/* 弹窗进入 / 离开过渡 */
.captcha-modal-enter-active,
.captcha-modal-leave-active {
  transition: opacity 0.28s ease;
}

.captcha-modal-enter-active .captcha-modal-card,
.captcha-modal-leave-active .captcha-modal-card {
  transition:
    transform 0.34s cubic-bezier(0.34, 1.12, 0.64, 1),
    opacity 0.28s ease;
}

.captcha-modal-enter-from,
.captcha-modal-leave-to {
  opacity: 0;
}

.captcha-modal-enter-from .captcha-modal-card,
.captcha-modal-leave-to .captcha-modal-card {
  transform: translateY(20px) scale(0.94);
  opacity: 0;
}

.captcha-modal-enter-to,
.captcha-modal-leave-from {
  opacity: 1;
}

.captcha-modal-enter-to .captcha-modal-card,
.captcha-modal-leave-from .captcha-modal-card {
  transform: translateY(0) scale(1);
  opacity: 1;
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

.slider-captcha-wrap--shake {
  animation: captcha-shake-wrap 0.45s ease;
}

@keyframes captcha-shake-wrap {
  0%,
  100% {
    transform: translateX(0);
  }
  20% {
    transform: translateX(-7px);
  }
  40% {
    transform: translateX(7px);
  }
  60% {
    transform: translateX(-4px);
  }
  80% {
    transform: translateX(4px);
  }
}

.slider-stage--checking {
  animation: captcha-stage-pulse 1.05s ease-in-out infinite;
}

@keyframes captcha-stage-pulse {
  0%,
  100% {
    box-shadow: 0 4px 16px rgba(31, 38, 135, 0.25);
  }
  50% {
    box-shadow: 0 4px 22px rgba(106, 90, 205, 0.45);
  }
}

.slider-rail--checking {
  border-color: rgba(106, 90, 205, 0.55);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.06), 0 0 0 2px rgba(106, 90, 205, 0.18);
}

.slider-rail-scan {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  background: linear-gradient(
    105deg,
    transparent 0%,
    rgba(255, 255, 255, 0.65) 42%,
    transparent 78%
  );
  background-size: 220% 100%;
  animation: captcha-rail-scan 0.95s linear infinite;
}

@keyframes captcha-rail-scan {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.slider-rail-thumb:disabled {
  cursor: default;
  opacity: 1;
}

.captcha-compact-enter-active,
.captcha-compact-leave-active {
  transition: opacity 0.2s ease, transform 0.32s cubic-bezier(0.34, 1.15, 0.64, 1);
}

.captcha-compact-enter-from {
  opacity: 0;
  transform: scale(0.92) translateY(8px);
}

.captcha-compact-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

.slider-challenge-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
}

.slider-compact-pass {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 48px;
  padding: 10px 14px;
  box-sizing: border-box;
  border-radius: 10px;
  border: 1px solid rgba(34, 197, 94, 0.45);
  background: linear-gradient(135deg, rgba(236, 253, 245, 0.96), rgba(220, 252, 231, 0.9));
  box-shadow: 0 2px 12px rgba(34, 197, 94, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.slider-compact-pass-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(145deg, #22c55e, #16a34a);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 2px 6px rgba(22, 163, 74, 0.35);
}

.slider-compact-pass-svg {
  width: 16px;
  height: 16px;
}

.slider-compact-pass-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.slider-compact-pass-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: #166534;
  line-height: 1.2;
}

.slider-compact-pass-sub {
  font-size: 0.75rem;
  color: rgba(21, 128, 61, 0.78);
  line-height: 1.2;
}

.slider-thumb-spinner {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: captcha-spin 0.6s linear infinite;
}

@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

.slider-status-line {
  margin: 10px 0 0;
  font-size: 0.82rem;
  font-weight: 600;
  color: #5c4b7b;
  min-height: 1.25em;
  transition: color 0.22s ease;
}

.slider-status-line--checking {
  color: #5b4fc9;
}

.slider-status-line--fail {
  color: #b91c1c;
}

.slider-status-line--dragging {
  color: #5b4fc9;
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