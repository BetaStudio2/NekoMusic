/**
 * 移动端进入详情 / 歌单页时尝试拉起原生 App。
 *
 * Android 常见问题：
 * 1. Chrome 对「无用户手势」的 intent / location 跳转可能完全静默失败 → 用 <a> 点击 + 首次触摸再试。
 * 2. 若仍无反应，约 800ms 后试 nekomusic://（会有系统框，但比「什么都没发生」可诊断）。
 * 3. 若已切到后台（可能已进 App），不再试 scheme。
 * 4. 无 App 时 intent 的 S.browser_fallback_url 回到当前页并带 nekoweb=1，避免死循环。
 *
 * 微信 / QQ 内置浏览器常拦截，需在系统浏览器中打开。
 */
const APP_LINK_HOST = 'music.cnmsb.xin'
const ANDROID_PKG = 'com.neko.music'
const SKIP_QUERY = 'nekoweb'

function shouldTryMobile() {
  if (typeof window === 'undefined') return false
  const ua = navigator.userAgent || ''
  return /android|ipad|iphone|ipod/i.test(ua)
}

function stripSkipQueryFromUrl() {
  try {
    const u = new URL(window.location.href)
    if (u.searchParams.get(SKIP_QUERY) !== '1') return
    u.searchParams.delete(SKIP_QUERY)
    const next = u.pathname + (u.searchParams.toString() ? `?${u.searchParams}` : '') + u.hash
    window.history.replaceState({}, '', next)
  } catch {
    /* ignore */
  }
}

function buildAppHttpsUrl(webPath) {
  const path = webPath.startsWith('/') ? webPath : `/${webPath}`
  return `https://${APP_LINK_HOST}${path}`
}

/** 部分浏览器只响应「像用户点的」导航；失败再退回 location */
function navigateLikeUserClick(url) {
  try {
    const a = document.createElement('a')
    a.setAttribute('href', url)
    a.setAttribute('rel', 'noopener noreferrer')
    a.style.display = 'none'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } catch {
    /* ignore */
  }
  try {
    window.location.assign(url)
  } catch {
    /* ignore */
  }
}

/**
 * @param {'detail'|'playlist'} kind
 * @param {string} id
 * @param {string} webPath
 * @param {string} schemeUrl
 */
function tryOpenNative(kind, id, webPath, schemeUrl) {
  if (!id || !shouldTryMobile()) return

  try {
    const u = new URL(window.location.href)
    if (u.searchParams.get(SKIP_QUERY) === '1') {
      stripSkipQueryFromUrl()
      return
    }
  } catch {
    /* ignore */
  }

  const origin = window.location.origin
  const pathOnly = webPath.startsWith('/') ? webPath : `/${webPath}`
  const webBase = `${origin}${pathOnly}`
  const sep = webBase.includes('?') ? '&' : '?'
  const fallbackUrl = `${webBase}${sep}${SKIP_QUERY}=1`
  const appUrl = buildAppHttpsUrl(pathOnly)
  const ua = navigator.userAgent || ''

  if (!/Android/i.test(ua)) {
    window.location.assign(schemeUrl)
    return
  }

  const data = encodeURIComponent(appUrl)
  const fallback = encodeURIComponent(fallbackUrl)
  const intent =
    `intent:#Intent;action=android.intent.action.VIEW;data=${data};` +
    `package=${ANDROID_PKG};` +
    `S.browser_fallback_url=${fallback};end`

  let pageHidden = false
  const onVisibility = () => {
    if (document.visibilityState === 'hidden') pageHidden = true
  }
  document.addEventListener('visibilitychange', onVisibility)

  const fireIntent = () => {
    navigateLikeUserClick(intent)
  }

  fireIntent()

  const onFirstPointer = () => {
    document.removeEventListener('pointerdown', onFirstPointer, true)
    fireIntent()
  }
  document.addEventListener('pointerdown', onFirstPointer, { capture: true, once: true })

  let schemeFallbackSent = false
  window.setTimeout(() => {
    document.removeEventListener('visibilitychange', onVisibility)
    if (pageHidden || schemeFallbackSent) return
    schemeFallbackSent = true
    navigateLikeUserClick(schemeUrl)
  }, 800)
}

export function tryOpenMusicDetailInApp(musicId) {
  tryOpenNative('detail', String(musicId), `/detail/${musicId}`, `nekomusic://player/${musicId}`)
}

export function tryOpenPlaylistInApp(playlistId) {
  tryOpenNative('playlist', String(playlistId), `/playlist/${playlistId}`, `nekomusic://playlist/${playlistId}`)
}
