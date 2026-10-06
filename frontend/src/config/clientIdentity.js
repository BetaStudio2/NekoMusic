// 客户端标识：所有出站请求统一带上
//   - X-Neko-Client: web+<版本>
//   - User-Agent:    NekoMusic-web/<版本>
// 便于后端按来源端与版本做统计、灰度或最低版本拦截。
//
// 版本号在构建时由 Vite 注入（见 vite.config.js 的 __NEKO_WEB_VERSION__）：
// 优先取 NEKO_WEB_VERSION / VITE_APP_VERSION 环境变量，其次 git describe 结果。
import axios from 'axios'
import API_CONFIG from './apiConfig.js'

/** 标头名；原生客户端（pc/android）必须与此保持一致 */
export const NEKO_CLIENT_HEADER = 'X-Neko-Client'

/** User-Agent 标头名；浏览器可能按自身策略忽略脚本设置 */
export const NEKO_USER_AGENT_HEADER = 'User-Agent'

/** 平台标识；三端分别是 web / pc / android */
export const NEKO_CLIENT_NAME = 'web'

export const NEKO_CLIENT_VERSION =
  typeof __NEKO_WEB_VERSION__ === 'string' ? __NEKO_WEB_VERSION__ : 'dev'

export const NEKO_CLIENT_VALUE = `${NEKO_CLIENT_NAME}+${NEKO_CLIENT_VERSION}`

/** User-Agent 取值：`NekoMusic-web/<版本>` */
export const NEKO_USER_AGENT_VALUE = `NekoMusic-${NEKO_CLIENT_NAME}/${NEKO_CLIENT_VERSION}`

/**
 * 需要注入的请求标头。
 *
 * 关于 User-Agent：它已不在 Fetch 规范的 forbidden header 列表内，Firefox 等浏览器
 * 会按脚本设置发送；但 Chrome/Chromium 会静默丢弃脚本设置的值、仍发送自身 UA，
 * 这是浏览器行为限制，网页端无法绕过。
 */
const NEKO_REQUEST_HEADERS = {
  [NEKO_CLIENT_HEADER]: NEKO_CLIENT_VALUE,
  [NEKO_USER_AGENT_HEADER]: NEKO_USER_AGENT_VALUE,
}

/**
 * 仅对发往自家后端的请求附加标头。
 * 跨域请求附带自定义标头会触发 CORS 预检，第三方接口不一定放行。
 */
function shouldAttachHeader(url) {
  if (!url || typeof window === 'undefined') return false
  let target
  try {
    target = new URL(String(url), window.location.href)
  } catch {
    return false
  }
  if (target.origin === window.location.origin) return true
  const base = API_CONFIG.BASE_URL
  if (!base) return false
  try {
    return target.origin === new URL(base, window.location.href).origin
  } catch {
    return false
  }
}

function withFetchHeader(input, init) {
  const url =
    typeof input === 'string' || input instanceof URL
      ? String(input)
      : input instanceof Request
        ? input.url
        : ''
  if (!shouldAttachHeader(url)) return null

  if (input instanceof Request) {
    const headers = new Headers(input.headers)
    for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) headers.set(name, value)
    return new Request(input, { ...(init || {}), headers })
  }
  const headers = new Headers(init?.headers || {})
  for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) headers.set(name, value)
  return { ...(init || {}), headers }
}

function patchFetch() {
  if (typeof window === 'undefined' || typeof window.fetch !== 'function') return
  const originalFetch = window.fetch.bind(window)
  const patchedFetch = (input, init) => {
    const nextInit = withFetchHeader(input, init)
    if (!nextInit) return originalFetch(input, init)
    return input instanceof Request
      ? originalFetch(nextInit, init)
      : originalFetch(input, nextInit)
  }
  patchedFetch.__nekoPatched = true
  window.fetch = patchedFetch
}

function patchXhr() {
  if (typeof window === 'undefined' || typeof XMLHttpRequest === 'undefined') return
  const proto = XMLHttpRequest.prototype
  const originalOpen = proto.open
  const originalSetRequestHeader = proto.setRequestHeader
  const originalSend = proto.send

  proto.open = function (method, url, ...rest) {
    this.__nekoRequestUrl = url
    return originalOpen.call(this, method, url, ...rest)
  }
  proto.setRequestHeader = function (name, value) {
    this.__nekoHeaderSet = this.__nekoHeaderSet || new Set()
    this.__nekoHeaderSet.add(String(name).toLowerCase())
    return originalSetRequestHeader.call(this, name, value)
  }
  proto.send = function (...args) {
    // axios 等库会先自行设置标头，这里只在缺失时补一次，避免重复值
    if (shouldAttachHeader(this.__nekoRequestUrl)) {
      const sent = this.__nekoHeaderSet || new Set()
      for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) {
        if (!sent.has(name.toLowerCase())) {
          sent.add(name.toLowerCase())
          originalSetRequestHeader.call(this, name, value)
        }
      }
    }
    return originalSend.apply(this, args)
  }
}

/**
 * 安装客户端标识：在应用启动最早期调用一次即可。
 * 覆盖原生 fetch、XMLHttpRequest 与 axios（axios 浏览器端默认走 XHR）。
 */
export function installNekoClientHeader() {
  for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) {
    axios.defaults.headers.common[name] = value
  }
  patchFetch()
  patchXhr()
}
