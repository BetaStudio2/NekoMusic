import API_CONFIG from '@/config/apiConfig.js'

/** 全站会员相关时间统一按东八区展示与编辑 */
export const VIP_TIMEZONE = 'Asia/Shanghai'

/** 与 SearchHeader / Profile 等同步：合并 VIP 后派发 */
export const USER_VIP_SYNC_EVENT = 'neko-user-vip-sync'

/**
 * 将 GET /api/user/playlists 根级返回的 isVip、vipExpiresAt 写入 localStorage.user
 */
export function applyVipFromPlaylistsResponse(data) {
  if (!data || typeof data.isVip !== 'boolean') return
  const raw = localStorage.getItem('user')
  if (!raw) return
  try {
    const u = JSON.parse(raw)
    u.isVip = data.isVip
    u.vipExpiresAt = data.vipExpiresAt ?? null
    localStorage.setItem('user', JSON.stringify(u))
    window.dispatchEvent(new Event(USER_VIP_SYNC_EVENT))
  } catch {
    /* ignore */
  }
}

/** 已登录时拉歌单接口，用于刷新 VIP（与「我的歌单」页复用同一 API） */
export async function syncUserVipFromPlaylistsApi() {
  const token = localStorage.getItem('userToken')
  if (!token) return
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlists`, {
      method: 'GET',
      headers: { Authorization: token }
    })
    const data = await response.json()
    if (data.success && typeof data.isVip === 'boolean') {
      applyVipFromPlaylistsResponse(data)
    }
  } catch {
    /* ignore */
  }
}

/** 东八区格式化展示 */
export function formatVipExpiresAt(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  return d.toLocaleString('zh-CN', {
    timeZone: VIP_TIMEZONE,
    dateStyle: 'medium',
    timeStyle: 'short'
  })
}

/**
 * 将绝对时间转为在东八区日历下的 datetime-local 字符串（供 input 使用）
 */
export function isoToDatetimeLocalValue(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: VIP_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  })
  const parts = fmt.formatToParts(d)
  const get = (t) => parts.find((p) => p.type === t)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`
}

/**
 * datetime-local 的字符串视为东八区墙上时间，转为带 +08:00 的 ISO 给后端
 * @param {string} localStr 如 2026-06-01T15:30
 */
export function datetimeLocalShanghaiToIso(localStr) {
  if (!localStr || !String(localStr).trim()) return null
  const s = String(localStr).trim()
  const m = s.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/)
  if (!m) return null
  const [, date, hh, mm, ss] = m
  const sec = ss ?? '00'
  return `${date}T${hh}:${mm}:${sec}+08:00`
}
