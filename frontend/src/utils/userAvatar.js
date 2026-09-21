import { ref } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'

/**
 * 用户头像工具
 * ------------------------------------------------------------
 * 头像地址固定为 `/api/user/avatar/{userId}`，更换后 URL 不变，
 * 浏览器会命中缓存显示旧图。这里给 URL 追加版本参数（`?v=`），
 * 上传成功后 bump 版本并广播事件，全站头像即时换成新图。
 *
 * 版本存 localStorage，同一标签页由自定义事件通知，跨标签页由原生
 * storage 事件同步（与 useAuth 的 token 处理方式一致）。
 */
export const USER_AVATAR_SYNC_EVENT = 'neko-user-avatar-sync'

const VERSION_KEY = 'userAvatarVersion'

function readStoredVersion() {
  if (typeof window === 'undefined') return ''
  return localStorage.getItem(VERSION_KEY) || ''
}

/** 响应式头像版本；computed 里读它即可在换头像后自动重算 URL */
const avatarVersion = ref(readStoredVersion())

/** 生成头像 URL；version 为空（从未上传过）时不带查询参数 */
export function avatarUrl(userId, version = avatarVersion.value) {
  const id = userId === null || userId === undefined ? 'default' : userId
  const base = `${API_CONFIG.BASE_URL}/api/user/avatar/${id}`
  return version ? `${base}?v=${encodeURIComponent(version)}` : base
}

/** 响应式头像版本，供组件 computed 追踪 */
export function useAvatarVersion() {
  return avatarVersion
}

/** 上传成功后调用：写入新版本并广播，让顶栏 / 个人中心等立刻刷新 */
export function bumpAvatarVersion() {
  const version = String(Date.now())
  if (typeof window !== 'undefined') {
    localStorage.setItem(VERSION_KEY, version)
    window.dispatchEvent(new Event(USER_AVATAR_SYNC_EVENT))
  }
  avatarVersion.value = version
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === VERSION_KEY) avatarVersion.value = event.newValue || ''
  })
  window.addEventListener(USER_AVATAR_SYNC_EVENT, () => {
    avatarVersion.value = readStoredVersion()
  })
}
