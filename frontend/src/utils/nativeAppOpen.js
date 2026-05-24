/**
 * 移动端进入详情 / 歌单页时尝试拉起原生 App。
 *
 * Android：只发起 **一次** VIEW intent（含 S.browser_fallback_url）。
 * 不再做「首次触摸重试」或延迟 nekomusic://，避免同一进入连弹多个系统框。
 * 若 Chrome 在无手势下完全静默失败，用户可刷新或从下载页 / App 内打开。
 *
 * 无 App 时 intent 的 fallback 回到当前页并带 nekoweb=1，避免死循环（见 stripSkipQueryFromUrl）。
 *
 * 微信 / QQ 内置浏览器常拦截，需在系统浏览器中打开。
 */
const APP_LINK_HOST = 'music.cnmsb.xin'
const ANDROID_PKG = 'com.neko.music'
const SKIP_QUERY = 'nekoweb'

/** 同一条资源在几秒内只尝试拉起一次（含 Vue Strict Mode 双挂载、路由重复触发） */
const OPEN_DEDUPE_MS = 12000

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

/** 单次导航：程序化 `<a>` 点击；失败再 `assign` */
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
    try {
      window.location.assign(url)
    } catch {
      /* ignore */
    }
  }
}

function shouldFireNativeOpen(kind, id) {
  try {
    const key = `neko-native-open:${kind}:${id}`
    const now = Date.now()
    const prev = sessionStorage.getItem(key)
    if (prev && now - Number(prev) < OPEN_DEDUPE_MS) return false
    sessionStorage.setItem(key, String(now))
    return true
  } catch {
    return true
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

  if (!shouldFireNativeOpen(kind, id)) return

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

  navigateLikeUserClick(intent)
}

export function tryOpenMusicDetailInApp(musicId) {
  tryOpenNative('detail', String(musicId), `/detail/${musicId}`, `nekomusic://player/${musicId}`)
}

export function tryOpenPlaylistInApp(playlistId) {
  tryOpenNative('playlist', String(playlistId), `/playlist/${playlistId}`, `nekomusic://playlist/${playlistId}`)
}
