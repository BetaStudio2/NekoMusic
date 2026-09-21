/**
 * userStore —— 内存中的用户资料
 * ------------------------------------------------------------
 * 本地只持久化 Token（localStorage.userToken）；昵称、邮箱、会员等资料
 * 不再写进 localStorage，登录/刷新时用 GET /api/user/info 拉取，
 * 避免本地缓存过期（改了昵称但页面还显示旧的）。
 *
 * 资料变化时会派发 key 为 'user' 的 StorageEvent，
 * 沿用各页面既有的 storage 事件刷新逻辑，无需额外改动。
 */
import { ref } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'

/** 用户资料变化的额外事件（与 storage 事件互补，便于新代码使用） */
export const USER_INFO_CHANGED_EVENT = 'neko-user-info-changed'

/** 响应式用户资料（仅内存），未登录或未拉到时为 null */
export const user = ref(null)

let loaded = false

/** 已登录时返回持久化的 Token，未登录返回 null */
export function getToken() {
  return typeof window === 'undefined' ? null : localStorage.getItem('userToken')
}

/** 同步读取内存中的用户资料 */
export function getUser() {
  return user.value
}

/** 响应式读取：`const user = useUser()` */
export function useUser() {
  return user
}

function notify(previousValue) {
  if (typeof window === 'undefined') return
  const nextValue = user.value ? JSON.stringify(user.value) : null
  window.dispatchEvent(
    new StorageEvent('storage', { key: 'user', oldValue: previousValue, newValue: nextValue })
  )
  window.dispatchEvent(new CustomEvent(USER_INFO_CHANGED_EVENT, { detail: user.value }))
}

/** 覆盖内存中的用户资料（不落盘） */
export function setUser(next) {
  const previous = user.value ? JSON.stringify(user.value) : null
  user.value = next || null
  loaded = !!next
  notify(previous)
}

/** 局部更新内存中的用户资料（不落盘），如改昵称、VIP 合并 */
export function patchUser(patch) {
  if (!user.value || !patch) return
  const previous = JSON.stringify(user.value)
  user.value = { ...user.value, ...patch }
  notify(previous)
}

/** 清空内存中的用户资料（登出） */
export function clearUser() {
  const previous = user.value ? JSON.stringify(user.value) : null
  user.value = null
  loaded = false
  notify(previous)
}

/**
 * 用 Token 拉取最新用户资料。
 * 网络异常时保留内存中已有数据，401 时不做处理（保留 Token，由页面自行提示）。
 * @param {{ force?: boolean }} options force=true 时忽略「本次已加载」标记重新拉取
 */
export async function loadUserInfo({ force = false } = {}) {
  const token = getToken()
  if (!token) {
    clearUser()
    return null
  }
  if (loaded && !force) return user.value
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/info`, {
      method: 'GET',
      headers: { Authorization: token }
    })
    const data = await response.json().catch(() => ({}))
    if (response.ok && data?.success && data?.data?.user) {
      setUser(data.data.user)
    }
  } catch {
    /* 网络异常：保留内存中已有资料 */
  }
  return user.value
}
