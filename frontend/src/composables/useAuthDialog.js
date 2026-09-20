/**
 * useAuthDialog —— 全局登录弹窗开关
 * ------------------------------------------------------------
 * 登录不再是独立页面，任何地方需要登录都调用 openAuthDialog()。
 */
import { ref, readonly } from 'vue'

const isOpen = ref(false)
const initialTab = ref('login')

/** 打开登录弹窗；tab 传 'register' 时直接落在注册标签 */
export function openAuthDialog(tab = 'login') {
  initialTab.value = tab === 'register' ? 'register' : 'login'
  isOpen.value = true
}

export function closeAuthDialog() {
  isOpen.value = false
}

export function useAuthDialog() {
  return {
    isOpen: readonly(isOpen),
    initialTab: readonly(initialTab),
    openAuthDialog,
    closeAuthDialog,
  }
}
