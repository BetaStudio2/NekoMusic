import API_CONFIG from '@/config/apiConfig.js'

/**
 * 站内消息（收件箱） API
 * ------------------------------------------------------------
 *   - GET  /api/user/notifications?since=&before=&limit=  列表（新的在前，响应里带未读数）
 *   - GET  /api/user/notifications/stream                实时推送（SSE 长连接）
 *   - POST /api/user/notifications/read                   { ids: [] } 标记已读，空数组 = 全部已读
 *
 * 消息落库即视为送达：离线期间产生的消息，下次带上 `since`（上一次拿到的 latestId）
 * 补拉即可全部拿到。`/stream` 只是在线时的提前告知，断开与重连都不影响消息完整性。
 * 未读数没有轮询接口，它来自列表响应与 SSE 的 `ready` 帧。
 */
const ENDPOINT = '/api/user/notifications'

/** 未读数变化事件：消息中心标记已读后广播新的未读数，顶栏直接采用，不再发请求。 */
export const NOTIFICATION_SYNC_EVENT = 'neko:notifications-changed'

function authHeaders(extra = {}) {
  const token = typeof window === 'undefined' ? null : localStorage.getItem('userToken')
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra }
}

async function request(url, options = {}) {
  const response = await fetch(url, options)
  let body = null
  try {
    body = await response.json()
  } catch {
    body = null
  }
  if (!response.ok || (body && body.success === false)) {
    const error = new Error((body && body.message) || `请求失败（${response.status}）`)
    error.status = response.status
    throw error
  }
  return body?.data ?? null
}

/** 收件箱列表；`since` 补拉新消息，`before` 翻更早的历史。 */
export async function fetchNotifications({ since, before, limit } = {}) {
  const params = new URLSearchParams()
  if (since) params.set('since', String(since))
  if (before) params.set('before', String(before))
  if (limit) params.set('limit', String(limit))
  const query = params.toString()
  const data = await request(`${API_CONFIG.BASE_URL}${ENDPOINT}${query ? `?${query}` : ''}`, {
    headers: authHeaders(),
  })
  return data ?? { items: [], unread: 0, hasMore: false, latestId: 0 }
}

/** 标记已读；不传 ids 表示全部已读。 */
export async function markNotificationsRead(ids = []) {
  const data = await request(`${API_CONFIG.BASE_URL}${ENDPOINT}/read`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ ids }),
  })
  return data ?? { updated: 0, unread: 0 }
}

// ── 实时推送（SSE） ────────────────────────────────────────────
// 整个标签页只维持一条连接：顶栏与消息中心都挂到同一条总线上，避免各自开流
// 把连接数用光。浏览器对 EventSource 自带重连，服务端到点也会主动断开，
// 客户端不需要写重试逻辑。
const readyListeners = new Set()
const messageListeners = new Set()
let stream = null
let streamToken = null

function closeStream() {
  if (stream) {
    stream.close()
    stream = null
  }
  streamToken = null
}

/** 让连接与当前登录身份对齐：未登录关闭，换账号重建，已对齐则不动。 */
function alignStream() {
  const token = typeof window === 'undefined' ? null : localStorage.getItem('userToken')
  if (!token || typeof EventSource === 'undefined') {
    closeStream()
    return
  }
  if (stream && streamToken === token) return
  closeStream()
  streamToken = token
  // EventSource 无法自定义请求头，token 只能走查询串
  stream = new EventSource(
    `${API_CONFIG.BASE_URL}${ENDPOINT}/stream?token=${encodeURIComponent(token)}`,
  )
  stream.addEventListener('ready', (event) => {
    const data = parseEvent(event)
    if (data) readyListeners.forEach((listener) => listener(data))
  })
  stream.addEventListener('message', (event) => {
    const data = parseEvent(event)
    if (data) messageListeners.forEach((listener) => listener(data))
  })
}

/** 登录 / 登出 / 换账号后调用。 */
export function syncNotificationStream() {
  alignStream()
}

/** 回到前台时重建连接，顺便拿一帧最新的未读数与游标。 */
export function reconnectNotificationStream() {
  if (typeof document !== 'undefined' && document.hidden) return
  closeStream()
  alignStream()
}

/**
 * 订阅实时推送。
 *
 * - `onReady(data)`：连上（含自动重连）时触发，`data` 为 `{ unread, latestId }`；
 *   拿 `latestId` 和本地游标比一下，落后就自己走列表接口补拉——服务端不做重放。
 * - `onMessage(item)`：新消息，结构与列表里的单条一致。
 *
 * 返回的对象可直接 `.close()`；未登录或浏览器不支持时不会有连接，回调也不会触发。
 */
export function subscribeNotifications({ onReady, onMessage } = {}) {
  if (onReady) readyListeners.add(onReady)
  if (onMessage) messageListeners.add(onMessage)
  alignStream()
  return {
    close() {
      if (onReady) readyListeners.delete(onReady)
      if (onMessage) messageListeners.delete(onMessage)
      if (readyListeners.size === 0 && messageListeners.size === 0) closeStream()
    },
  }
}

function parseEvent(event) {
  try {
    return JSON.parse(event.data)
  } catch {
    return null
  }
}
