/**
 * 收敛「play() 被新的 load 请求打断」的 AbortError
 * ------------------------------------------------------------
 * 这是 HTML 规范规定的正常行为：媒体元素一旦开始新的资源加载，之前挂起的
 * play() 返回的 Promise 就会以 AbortError 拒绝。它可能来自浏览器内部、
 * 第三方库，或某条来不及 catch 的路径，但并不代表功能故障，却会在控制台
 * 留下
 *   Uncaught (in promise) AbortError: The play() request was interrupted
 *   by a new load request
 * 干扰排查。
 *
 * 应用层的 play() 已全部经由 GlobalPlayer 的 safePlay 收敛（AbortError
 * 静默、NotAllowedError 回滚播放态），这里是最后一道兜底。
 *
 * 只拦【错误名 + 消息】都精确匹配的这一个：其它 Promise 拒绝一律照常抛出，
 * 不会被掩盖。
 */
const BENIGN_MESSAGE = 'play() request was interrupted'

/** 是否为那条「无害」的 AbortError */
export function isBenignPlayAbort(reason) {
  return (
    !!reason &&
    reason.name === 'AbortError' &&
    typeof reason.message === 'string' &&
    reason.message.includes(BENIGN_MESSAGE)
  )
}

/** 安装全局兜底（幂等） */
export function installBenignPlayAbortGuard() {
  if (typeof window === 'undefined') return
  if (window.__nekoPlayAbortGuardInstalled) return
  window.__nekoPlayAbortGuardInstalled = true

  window.addEventListener('unhandledrejection', (event) => {
    if (isBenignPlayAbort(event.reason)) {
      // 标记为已处理，浏览器便不再打印 "Uncaught (in promise)"
      event.preventDefault()
    }
  })
}
