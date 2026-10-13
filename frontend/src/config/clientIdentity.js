// 客户端标识：所有发往自家后端的请求统一带上
//   - X-Neko-Client: web+<版本>
//   - Authorization: Bearer <用户令牌>（已登录时；后台页面自己设的管理员令牌优先）
// 便于后端按来源端与版本做统计、灰度或最低版本拦截，并把访问日志落到具体账号。
//
// 严禁用脚本改写 User-Agent：它虽已不在 Fetch 规范的 forbidden header 列表内，但各浏览器处理
// 并不一致——WebKit（iOS Safari 及 iOS 上所有浏览器）与 Firefox 会按脚本设置原样发送，后端在
// 浏览器完整性校验里看到的就是一个不含 `Mozilla/` 与内核标记的 UA，会把整个 Web 前端当成伪造
// 客户端而整体 403（连 `/api/replay/challenge` 都拿不到，表现为「无法通过 PoW」）；Chrome/Chromium
// 则静默丢弃脚本设置、仍发自身 UA。不能依赖这种浏览器差异：Web 端身份一律由 X-Neko-Client 自报，
// User-Agent 必须保持浏览器自身取值，浏览器完整性校验才有真实信号可判。
//
// 版本号在构建时由 Vite 注入（见 vite.config.js 的 __NEKO_WEB_VERSION__）：
// 优先取 NEKO_WEB_VERSION / VITE_APP_VERSION 环境变量，其次 git describe 结果。
import axios from 'axios'
import API_CONFIG from './apiConfig.js'

/** 标头名；原生客户端（pc/android）必须与此保持一致 */
export const NEKO_CLIENT_HEADER = 'X-Neko-Client'

/** 平台标识；三端分别是 web / pc / android */
export const NEKO_CLIENT_NAME = 'web'

export const NEKO_CLIENT_VERSION =
  typeof __NEKO_WEB_VERSION__ === 'string' ? __NEKO_WEB_VERSION__ : 'dev'

export const NEKO_CLIENT_VALUE = `${NEKO_CLIENT_NAME}+${NEKO_CLIENT_VERSION}`

/**
 * 需要注入的请求标头：只注入 X-Neko-Client。
 *
 * User-Agent 保持浏览器自身取值，**不要**加进来：改写它会让 WebKit / Firefox 真正发出一个不带
 * 内核标记的 UA，被后端浏览器完整性校验判为伪造客户端而整体 403（详见文件头说明）。
 */
const NEKO_REQUEST_HEADERS = {
  [NEKO_CLIENT_HEADER]: NEKO_CLIENT_VALUE,
}

/** 用户令牌标头；与后端 `Authorization: Bearer <token>` 约定一致 */
export const AUTHORIZATION_HEADER = 'Authorization'

/** 用户令牌的本地存储键；与 utils/userStore.js 的 getToken() 同源 */
const USER_TOKEN_STORAGE_KEY = 'userToken'

/**
 * 已登录用户令牌的标头取值；未登录 / 取不到返回空串。
 *
 * 每次请求现读 localStorage，不缓存到内存：登出、换号后下一条请求立刻不再带旧令牌
 * （缓存一份就要额外维护失效，漏一处就是把上一个人的身份发出去）。
 */
function userAuthorizationValue() {
  if (typeof window === 'undefined' || !window.localStorage) return ''
  try {
    const token = window.localStorage.getItem(USER_TOKEN_STORAGE_KEY)
    return token ? `Bearer ${token}` : ''
  } catch {
    // 隐私模式下 localStorage 可能直接抛异常：没有令牌照常发请求
    return ''
  }
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

  const headers =
    input instanceof Request ? new Headers(input.headers) : new Headers(init?.headers || {})
  for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) headers.set(name, value)
  // 用户令牌只补空缺：后台页面用管理员令牌，调用方显式设过就一律不动
  const authorization = userAuthorizationValue()
  if (authorization && !headers.has(AUTHORIZATION_HEADER)) {
    headers.set(AUTHORIZATION_HEADER, authorization)
  }
  return input instanceof Request
    ? new Request(input, { ...(init || {}), headers })
    : { ...(init || {}), headers }
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
      const headers = { ...NEKO_REQUEST_HEADERS }
      const authorization = userAuthorizationValue()
      if (authorization) headers[AUTHORIZATION_HEADER] = authorization
      for (const [name, value] of Object.entries(headers)) {
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
 * 安装客户端标识与用户令牌：在应用启动最早期调用一次即可。
 * 覆盖原生 fetch、XMLHttpRequest 与 axios（axios 浏览器端默认走 XHR）。
 *
 * 用户令牌不进 axios 默认值：它是随登录态变化的，写死一份会在登出后继续发出去。
 */
export function installNekoClientHeader() {
  for (const [name, value] of Object.entries(NEKO_REQUEST_HEADERS)) {
    axios.defaults.headers.common[name] = value
  }
  patchFetch()
  patchXhr()
}
