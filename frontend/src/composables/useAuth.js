/**
 * useAuth —— 登录态的响应式视图
 * ------------------------------------------------------------
 * token 以 localStorage 为准；同标签页写入后由 AuthPanel 广播 storage 事件，
 * 跨标签页由浏览器原生 storage 事件同步，这里统一转成响应式 token 供页面 watch。
 */
import { ref, computed } from 'vue'

const token = ref(typeof window === 'undefined' ? null : localStorage.getItem('userToken'))

function syncFromStorage() {
  token.value = localStorage.getItem('userToken')
}

if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (!event.key || event.key === 'userToken') syncFromStorage()
  })
}

export function useAuth() {
  return {
    token,
    isLoggedIn: computed(() => !!token.value),
    syncFromStorage,
  }
}
