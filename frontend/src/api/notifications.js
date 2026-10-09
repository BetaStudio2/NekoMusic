import API_CONFIG from '@/config/apiConfig.js'

/**
 * 站内消息（收件箱） API
 * ------------------------------------------------------------
 *   - GET  /api/user/notifications?since=&before=&limit=  列表（新的在前）
 *   - GET  /api/user/notifications/unread                 未读条数
 *   - POST /api/user/notifications/read                   { ids: [] } 标记已读，空数组 = 全部已读
 *
 * 消息落库即视为送达：离线期间产生的消息，下次带上 `since`（上一次拿到的 latestId）
 * 补拉即可全部拿到，不依赖长连接。
 */
const ENDPOINT = '/api/user/notifications'

/** 未读状态变化事件：顶栏红点与消息中心共用一个信号，任一侧变更后广播 */
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

/** 未读条数（顶栏红点用，单请求很轻）。 */
export async function fetchUnreadCount() {
  const data = await request(`${API_CONFIG.BASE_URL}${ENDPOINT}/unread`, {
    headers: authHeaders(),
  })
  return data?.unread ?? 0
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
