<template>
  <div class="auth" :class="{ 'auth--ready': animReady }">
    <span class="auth__glow" aria-hidden="true" />

    <div class="auth__card">
      <div
        class="auth__panels"
        :class="{ 'auth__panels--transitioning': transitioning }"
        :style="{ height: panelHeight }"
      >
        <!-- 登录面板 -->
        <div v-if="activeTab === 'login'" ref="loginPanel" class="auth__panel">
          <header class="auth__head">
            <div class="auth__head-top">
              <div class="auth__logo">
                <NIcon name="cat" :size="26" />
              </div>
              <button type="button" class="auth__mode" @click="switchTab('register')">
                <NIcon name="user-plus" :size="14" />
                注册
              </button>
            </div>
            <h1 class="auth__title">登录 Neko歌姬计划</h1>
            <p class="auth__subtitle">请输入您的凭据</p>
          </header>

          <div class="auth__body">
            <form class="auth__form" @submit.prevent="handleLogin">
              <div class="auth__field">
                <label class="auth__label" for="login-email">邮箱</label>
                <NInput
                  id="login-email"
                  v-model="loginEmail"
                  icon="mail"
                  placeholder="输入邮箱"
                  autocomplete="username"
                />
              </div>
              <div class="auth__field">
                <label class="auth__label" for="login-password">密码</label>
                <NInput
                  id="login-password"
                  v-model="loginPassword"
                  type="password"
                  icon="lock"
                  placeholder="输入密码"
                  autocomplete="current-password"
                />
              </div>

              <p class="auth__error" role="alert">{{ loginError || '\u00A0' }}</p>

              <NButton type="submit" variant="primary" size="lg" block :loading="loginLoading">
                登录
              </NButton>
            </form>
          </div>

          <p class="auth__link-row">
            <RouterLink to="/forgot-password" class="auth__link">
              <NIcon name="key" :size="14" />
              忘记密码？
            </RouterLink>
          </p>
        </div>

        <!-- 注册面板 -->
        <div v-else ref="registerPanel" class="auth__panel">
          <header class="auth__head">
            <div class="auth__head-top">
              <div class="auth__logo">
                <NIcon name="user-plus" :size="26" />
              </div>
              <button type="button" class="auth__mode" @click="switchTab('login')">
                <NIcon name="login" :size="14" />
                登录
              </button>
            </div>
            <h1 class="auth__title">注册 Neko歌姬计划</h1>
            <p class="auth__subtitle">创建账户，开始收藏与整理你的音乐</p>
          </header>

          <div class="auth__body">
            <form class="auth__form" @submit.prevent="handleRegister">
              <div class="auth__field">
                <label class="auth__label" for="reg-username">用户名</label>
                <NInput
                  id="reg-username"
                  v-model="username"
                  icon="user"
                  placeholder="用户名"
                  autocomplete="username"
                />
              </div>
              <div class="auth__field">
                <label class="auth__label" for="reg-email">邮箱</label>
                <NInput
                  id="reg-email"
                  v-model="email"
                  type="email"
                  icon="mail"
                  placeholder="邮箱"
                  autocomplete="email"
                />
              </div>
              <div class="auth__field">
                <label class="auth__label" for="reg-code">邮箱验证码</label>
                <div class="auth__code">
                  <NInput
                    id="reg-code"
                    v-model="verificationCode"
                    icon="shield-check"
                    placeholder="验证码"
                    maxlength="6"
                  />
                  <NButton
                    variant="secondary"
                    :disabled="codeSending || countdown > 0 || captchaModalOpen"
                    @click="sendVerificationCode"
                  >
                    {{ codeBtnText }}
                  </NButton>
                </div>
              </div>
              <div class="auth__field">
                <label class="auth__label" for="reg-password">密码</label>
                <NInput
                  id="reg-password"
                  v-model="password"
                  type="password"
                  icon="lock"
                  placeholder="至少 6 位"
                  autocomplete="new-password"
                />
              </div>
              <div class="auth__field">
                <label class="auth__label" for="reg-confirm">确认密码</label>
                <NInput
                  id="reg-confirm"
                  v-model="confirmPassword"
                  type="password"
                  icon="lock"
                  placeholder="再次输入密码"
                  autocomplete="new-password"
                />
              </div>

              <p class="auth__error" role="alert">{{ regError || '\u00A0' }}</p>

              <NButton type="submit" variant="primary" size="lg" block :loading="loading">
                注册
              </NButton>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 滑块验证码（仅注册流程使用） -->
  <Teleport to="body">
    <Transition name="captcha-modal">
      <div
        v-if="captchaModalOpen"
        class="captcha-modal-backdrop"
        @click.self="closeCaptchaModal"
      >
        <div class="captcha-modal-card" role="dialog" aria-modal="true" aria-labelledby="captcha-modal-title" @click.stop>
          <button type="button" class="captcha-modal-close" aria-label="关闭" @click="closeCaptchaModal">
            <NIcon name="close" :size="18" />
          </button>
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
                <div class="slider-captcha-wrap" :class="{ 'slider-captcha-wrap--shake': shakeActive }">
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
                      :style="{ width: sliderW + 'px', left: sliderX + 'px', top: puzzleY + 'px' }"
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
</template>

<script setup>
/**
 * AuthPanel —— 登录 / 注册合一卡片
 * ------------------------------------------------------------
 * 交互参考 WebWord：同一张卡片内切换，容器高度过渡 + 面板淡入淡出。
 * 使用自己的设计语言（黑偏青 + 圆角矩形 + 令牌）。
 *
 * 路由：/login 与 /register 共用同一个视图组件，切换时只更新 URL 不重挂，
 * 因此过渡动画不会被路由切换打断。
 *
 * 契约（与拆分版一致）：
 *  登录 POST /api/user/login
 *  注册 POST /api/user/register
 *  验证码 GET /api/captcha/slider、POST /api/captcha/slider/verify、POST /api/user/send-verification
 */
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NInput } from '@/ui'
import { useToast } from '@/composables/useToast'

const props = defineProps({
  /** 初始面板：login | register（由路由决定） */
  initialTab: {
    type: String,
    default: 'login',
    validator: (v) => ['login', 'register'].includes(v),
  },
})

const toast = useToast()
const route = useRoute()
const router = useRouter()

/* ==================================================================
   面板切换（高度过渡 + 淡入淡出）
   ================================================================== */
const activeTab = ref(props.initialTab)
const transitioning = ref(false)
const animReady = ref(false)
const panelHeight = ref('auto')
const loginPanel = ref(null)
const registerPanel = ref(null)

function measurePanel() {
  nextTick(() => {
    const el = activeTab.value === 'login' ? loginPanel.value : registerPanel.value
    if (el) panelHeight.value = `${el.offsetHeight}px`
  })
}

function switchTab(tab) {
  if (tab === activeTab.value || transitioning.value) return
  loginError.value = ''
  regError.value = ''
  transitioning.value = true
  window.setTimeout(() => {
    activeTab.value = tab
    const target = tab === 'login' ? '/login' : '/register'
    if (route.path !== target) router.replace(target)
    measurePanel()
    window.setTimeout(() => {
      transitioning.value = false
    }, 60)
  }, 180)
}

// 浏览器前进/后退时同步面板
watch(
  () => props.initialTab,
  (tab) => {
    if (tab !== activeTab.value && !transitioning.value) {
      activeTab.value = tab
      measurePanel()
    }
  }
)

/* ==================================================================
   登录
   ================================================================== */
const loginEmail = ref('')
const loginPassword = ref('')
const loginLoading = ref(false)
const loginError = ref('')

async function handleLogin() {
  if (loginLoading.value) return
  loginError.value = ''

  if (!loginEmail.value.trim()) {
    loginError.value = '请输入邮箱'
    return
  }
  if (!loginPassword.value) {
    loginError.value = '请输入密码'
    return
  }

  loginLoading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/login`, {
      nickname: loginEmail.value,
      password: loginPassword.value,
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
      loginError.value = response.data.message || '登录失败'
      toast.error(loginError.value)
    }
  } catch (err) {
    console.error('登录失败:', err)
    loginError.value = err.response ? err.response.data.message || '登录失败' : '网络错误，请检查服务器连接'
    toast.error(loginError.value)
  } finally {
    loginLoading.value = false
  }
}

/* ==================================================================
   注册
   ================================================================== */
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
const regError = ref('')

/* --- 滑块验证码 --- */
const captchaLoading = ref(true)
const captchaError = ref('')
const captchaToken = ref('')
const captchaPassToken = ref('')
const slideState = ref('idle') // idle | dragging | checking | fail
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
let railTrackReleaseHandler = null

const maxSliderX = computed(() => Math.max(0, bgWidth.value - sliderW.value))
const thumbMaxTravel = computed(() => Math.max(0, bgWidth.value - railThumbW))
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

const codeBtnText = computed(() => (countdown.value > 0 ? `${countdown.value}秒后重发` : '获取验证码'))

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
      { captchaToken: captchaToken.value, captchaOffsetX: sliderX.value },
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

function onThumbPointerMove(e) {
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

function onThumbPointerUp() {
  if (!railDragging) return
  detachThumbRailListeners()
  slideState.value = 'idle'
  scheduleSlideVerify()
}

function onThumbPointerDown(e) {
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

function onRailTrackPointerDown(e) {
  if (slideState.value === 'checking') return
  if (e.target.closest('.slider-rail-thumb')) return
  invalidateSlidePass()
  const rail = railRef.value?.getBoundingClientRect()
  if (!rail) return
  const x = e.clientX - rail.left
  setSliderXFromThumbLeft(x - railThumbW / 2)
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

async function loadSliderCaptcha() {
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
      nickname: username.value || '用户',
      captchaPassToken: token,
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
    toast.error(error.response ? error.response.data?.message || '发送验证码失败' : '网络错误，请检查服务器连接')
    await loadSliderCaptcha()
  } finally {
    codeSending.value = false
    if (captchaModalOpen.value) slideState.value = 'idle'
  }
}

function sendVerificationCode() {
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
  nextTick(loadSliderCaptcha)
}

function startCountdown() {
  countdown.value = 60
  countdownInterval.value = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(countdownInterval.value)
  }, 1000)
}

async function handleRegister() {
  regError.value = ''

  if (!username.value.trim()) {
    regError.value = '请输入用户名'
    return
  }
  if (!email.value.trim()) {
    regError.value = '请输入邮箱'
    return
  }
  if (!verificationCode.value.trim()) {
    regError.value = '请输入验证码'
    return
  }
  if (password.value.length < 6) {
    regError.value = '密码至少 6 位'
    return
  }
  if (password.value !== confirmPassword.value) {
    regError.value = '两次输入的密码不一致'
    toast.error(regError.value)
    return
  }

  loading.value = true
  try {
    const response = await axios.post(`${API_CONFIG.BASE_URL}/api/user/register`, {
      nickname: username.value,
      email: email.value,
      password: password.value,
      verificationCode: verificationCode.value,
    })

    if (response.data.success) {
      toast.success('注册成功！请登录您的账户。')
      loginEmail.value = email.value
      switchTab('login')
    } else {
      regError.value = response.data.message || '注册失败'
      toast.error(regError.value)
    }
  } catch (error) {
    console.error('注册失败:', error)
    regError.value = error.response ? error.response.data.message || '注册失败' : '网络错误，请检查服务器连接'
    toast.error(regError.value)
  } finally {
    loading.value = false
  }
}

/* ==================================================================
   生命周期
   ================================================================== */
onMounted(() => {
  measurePanel()
  window.setTimeout(() => {
    animReady.value = true
  }, 60)
  window.addEventListener('resize', measurePanel)
})

onUnmounted(() => {
  window.removeEventListener('resize', measurePanel)
  if (countdownInterval.value) clearInterval(countdownInterval.value)
  detachThumbRailListeners()
  if (railTrackReleaseHandler) {
    window.removeEventListener('pointerup', railTrackReleaseHandler)
    window.removeEventListener('pointercancel', railTrackReleaseHandler)
    railTrackReleaseHandler = null
  }
  verifyAbort?.abort()
})
</script>

<style scoped>
/* ==================== 容器 ==================== */
.auth {
  position: relative;
  width: 100%;
  max-width: 420px;
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

/* ==================== 面板容器（高度过渡 + 淡入淡出） ==================== */
.auth__panels {
  position: relative;
  overflow: hidden;
}

.auth--ready .auth__panels {
  transition: height 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.auth__panel {
  padding-bottom: var(--n-space-8);
  opacity: 1;
  transition: opacity 0.2s var(--n-ease);
}

/* 切换中：整个面板（含头部）淡出，切换后再淡入 */
.auth__panels--transitioning .auth__panel {
  opacity: 0;
}

/* ==================== 头部 ==================== */
.auth__head {
  padding: var(--n-space-8) var(--n-space-8) 0;
  text-align: center;
}

.auth__head-top {
  position: relative;
  display: flex;
  justify-content: center;
  margin-bottom: var(--n-space-4);
}

.auth__logo {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  background: linear-gradient(135deg, var(--n-accent-strong), var(--n-accent));
  color: var(--n-text-inverse);
  box-shadow: 0 8px 26px rgba(95, 208, 224, 0.3);
}

/* 右上角「切换模式」按钮 */
.auth__mode {
  position: absolute;
  right: 0;
  top: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--n-line-strong);
  border-radius: var(--n-radius-xs);
  background: transparent;
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
  white-space: nowrap;
  transition:
    color var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .auth__mode:hover {
    color: var(--n-accent-strong);
    border-color: var(--n-accent-line);
    background: var(--n-accent-soft);
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
  padding: var(--n-space-6) var(--n-space-8) 0;
}

.auth__form {
  display: flex;
  flex-direction: column;
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

/* 固定高度，避免出错时卡片高度跳动 */
.auth__error {
  min-height: 18px;
  margin: 0 0 var(--n-space-3);
  color: var(--n-danger);
  font-size: var(--n-text-xs);
  text-align: center;
}

.auth__code {
  display: flex;
  gap: var(--n-space-3);
}

.auth__code :deep(.n-input) {
  flex: 1;
  min-width: 0;
}

.auth__code :deep(.n-btn) {
  flex: none;
  white-space: nowrap;
}

.auth__link-row {
  margin: 0;
  padding: var(--n-space-5) var(--n-space-8) 0;
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

/* 验证码弹窗内的 form-group 仅作占位 */
.form-group {
  margin: 0;
}

@media (max-width: 560px) {
  .auth__code {
    flex-direction: column;
  }

  .auth__code :deep(.n-btn) {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth__card,
  .auth__glow {
    animation: none;
  }

  .auth--ready .auth__panels,
  .auth__panel {
    transition: none;
  }
}

/* ==================== 滑块验证码（沿用原实现，仅配色改为令牌版） ==================== */
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
  border: 1px solid rgba(95, 208, 224, 0.2);
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
  background: rgba(95, 208, 224, 0.12);
  transition: background 0.2s ease, color 0.2s ease;
}

.captcha-modal-close:hover {
  background: rgba(95, 208, 224, 0.22);
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
  border: 1px solid rgba(95, 208, 224, 0.5);
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
  border: 1px solid rgba(95, 208, 224, 0.22);
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
  background: linear-gradient(135deg, #5fd0e0, #9ceefb);
  box-shadow: 0 2px 8px rgba(95, 208, 224, 0.45);
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
    box-shadow: 0 4px 22px rgba(95, 208, 224, 0.45);
  }
}

.slider-rail--checking {
  border-color: rgba(95, 208, 224, 0.55);
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.06), 0 0 0 2px rgba(95, 208, 224, 0.18);
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
  background: linear-gradient(135deg, rgba(95, 208, 224, 0.85), rgba(95, 208, 224, 0.85));
}

.slider-hint {
  font-size: 0.8rem;
  color: rgba(92, 75, 123, 0.85);
}


/* ==================== 页面卡片（与登录页一致） ==================== */
</style>
