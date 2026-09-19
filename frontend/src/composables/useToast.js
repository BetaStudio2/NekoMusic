/**
 * useToast —— 统一消息提示
 * ------------------------------------------------------------
 * 包装 vue-toastification，页面不直接依赖其 API，便于将来替换实现。
 */
import { useToast as useToastification, POSITION } from 'vue-toastification'

export function useToast() {
  const toast = useToastification()

  return {
    success: (msg, opts) => toast.success(msg, opts),
    error: (msg, opts) => toast.error(msg, opts),
    info: (msg, opts) => toast.info(msg, opts),
    warning: (msg, opts) => toast.warning(msg, opts),
    /** 原生透传，供特殊场景使用 */
    raw: toast,
    POSITION,
  }
}
