/**
 * usePwa —— PWA 安装 / 离线 / 更新
 * ------------------------------------------------------------
 * 职责：
 *  - 判断当前是否「已安装」、是否「可安装」；
 *  - Chromium：捕获 `beforeinstallprompt`，提供 promptInstall()；
 *  - iOS Safari：没有该事件，标记为「需手动添加到主屏幕」并提供指引；
 *  - 注册 Service Worker（仅生产），检测到新版本进入 waiting 时置 needRefresh，
 *    用户确认后 applyUpdate() 触发 SKIP_WAITING，controllerchange 时自动刷新。
 *
 * 全部状态为模块级单例：任意组件调用 usePwa() 读到的是同一份。
 * 注意：Service Worker 只在生产构建注册（dev 下热更新与 SW 缓存会互相打架）。
 */
import { ref, computed, readonly } from 'vue'

const isBrowser = typeof window !== 'undefined' && typeof navigator !== 'undefined'
const swSupported = isBrowser && 'serviceWorker' in navigator

/** 已以「独立窗口 / 应用」方式运行（安装态） */
const isInstalled = ref(false)
/** Chromium 已给出 beforeinstallprompt，可直接弹出安装 */
const canInstall = ref(false)
/** iOS Safari：需手动「添加到主屏幕」 */
const iosManualInstall = ref(false)
/** 有新版本 Service Worker 在等待激活 */
const needRefresh = ref(false)

let deferredPrompt = null
let registration = null
let initialized = false
/** 用户已确认更新：此时 controllerchange 才触发刷新（首次安装不刷新） */
let applyUpdateRequested = false
let reloading = false
let lastUpdateCheck = 0

/** 是否显示「安装到桌面」入口 */
const showInstall = computed(
  () => !isInstalled.value && (canInstall.value || iosManualInstall.value),
)

function detectInstalled() {
  if (!isBrowser) return
  let standalone = false
  try {
    const mm = window.matchMedia
    standalone =
      (!!mm &&
        (mm('(display-mode: standalone)').matches ||
          mm('(display-mode: fullscreen)').matches ||
          mm('(display-mode: minimal-ui)').matches)) ||
      // iOS Safari 专有
      window.navigator.standalone === true
  } catch {
    standalone = false
  }
  isInstalled.value = standalone
  if (standalone) {
    canInstall.value = false
    iosManualInstall.value = false
  }
}

function detectIosManualInstall() {
  if (!isBrowser) return
  const ua = navigator.userAgent || ''
  const platform = navigator.platform || ''
  const isIpadOs = platform === 'MacIntel' && navigator.maxTouchPoints > 1
  const isIosDevice = /iPad|iPhone|iPod/i.test(ua) || isIpadOs
  // iOS 上只有 Safari 提供「添加到主屏幕」；CriOS/FxiOS/EdgiOS 等为 WebKit 套壳
  const isSafari = !/CriOS|FxiOS|EdgiOS|OPiOS|mercury/i.test(ua)
  iosManualInstall.value = isIosDevice && isSafari && !isInstalled.value
}

/** 触发原生安装弹窗（必须在用户手势中调用） */
export async function promptInstall() {
  if (!deferredPrompt) return false
  try {
    deferredPrompt.prompt()
    const choice = await deferredPrompt.userChoice
    const accepted = choice?.outcome === 'accepted'
    if (accepted) {
      isInstalled.value = true
      canInstall.value = false
    }
    deferredPrompt = null
    return accepted
  } catch (error) {
    console.warn('[pwa] 安装提示失败', error)
    deferredPrompt = null
    return false
  }
}

/** 应用新版本：让 waiting 中的 SW 立即接管，controllerchange 时自动刷新 */
export function applyUpdate() {
  needRefresh.value = false
  applyUpdateRequested = true
  const waiting = registration?.waiting
  if (waiting) {
    waiting.postMessage({ type: 'SKIP_WAITING' })
  } else {
    window.location.reload()
  }
}

/** 手动检查更新（带 30 分钟节流，避免频繁请求） */
export function checkForUpdate(force = false) {
  if (!registration || typeof registration.update !== 'function') return
  const now = Date.now()
  if (!force && now - lastUpdateCheck < 30 * 60 * 1000) return
  lastUpdateCheck = now
  registration.update().catch(() => {
    /* 网络异常等无需打扰用户 */
  })
}

async function registerServiceWorker() {
  try {
    registration = await navigator.serviceWorker.register('/sw.js', { scope: '/' })

    // 已有 waiting（例如上次没点刷新就关了页面）
    if (registration.waiting && navigator.serviceWorker.controller) {
      needRefresh.value = true
    }

    registration.addEventListener('updatefound', () => {
      const installing = registration.installing
      if (!installing) return
      installing.addEventListener('statechange', () => {
        if (installing.state === 'installed' && navigator.serviceWorker.controller) {
          needRefresh.value = true
        }
      })
    })

    // 回到前台时顺带检查一次更新（节流）
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') checkForUpdate()
    })
  } catch (error) {
    console.warn('[pwa] Service Worker 注册失败', error)
  }
}

/** 初始化：幂等，建议在应用启动时调用一次 */
export function initPwa() {
  if (!isBrowser || initialized) return
  initialized = true

  detectInstalled()
  detectIosManualInstall()

  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault()
    deferredPrompt = event
    canInstall.value = true
  })

  window.addEventListener('appinstalled', () => {
    isInstalled.value = true
    canInstall.value = false
    deferredPrompt = null
  })

  try {
    window.matchMedia?.('(display-mode: standalone)')?.addEventListener?.('change', detectInstalled)
  } catch {
    /* 老浏览器不支持 addEventListener on MediaQueryList，忽略 */
  }

  if (!swSupported) return

  // 仅在「用户确认更新」后，SW 接管时刷新一次让新资源生效；
  // 首次安装 new SW 通过 clients.claim() 也会触发 controllerchange，
  // 这里必须忽略，否则首访会被强行刷新一次。
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!applyUpdateRequested || reloading) return
    reloading = true
    window.location.reload()
  })

  // 仅生产注册：dev 下热更新与 SW 缓存会互相干扰
  if (import.meta.env.PROD) {
    if (document.readyState === 'complete') {
      registerServiceWorker()
    } else {
      window.addEventListener('load', registerServiceWorker, { once: true })
    }
  }
}

export function usePwa() {
  return {
    isInstalled: readonly(isInstalled),
    canInstall: readonly(canInstall),
    iosManualInstall: readonly(iosManualInstall),
    needRefresh: readonly(needRefresh),
    showInstall,
    promptInstall,
    applyUpdate,
    checkForUpdate,
  }
}
