/**
 * useToast —— 统一消息提示
 * ------------------------------------------------------------
 * 包装 vue-toastification，页面不直接依赖其 API，便于将来替换实现。
 *
 * 这里额外做了一层「限量」，因为 vue-toastification@2.0.0-rc.5 的默认行为
 * 在短时间内收到大量吐司时并不友好：同屏只渲染 maxToasts 条，且按插入顺序
 * 先显示旧消息，新消息要等旧消息一条条过期才轮到，消息一多整页就会表现为
 * 「点了没反应 / 提示迟迟不更新」。
 *
 * 处理方式：
 *  - 同屏最多 MAX_VISIBLE 条，超出时立即挤掉最旧的一条，保证最新消息永远
 *    第一时间可见；
 *  - 完全相同的消息（同类型 + 同文案）正在显示时不再叠加，避免刷屏。
 *
 * 只在这一层收口，页面仍照常 `useToast()`，不需要感知这些细节。
 */
import { useToast as useToastification, POSITION } from 'vue-toastification'

/** 同屏最多保留的吐司条数：超出即挤掉最旧的，保证最新消息可见。 */
const MAX_VISIBLE = 4

/** 当前正在显示的吐司（按创建先后排列），元素为 { id, key }。 */
const visible = []

/**
 * 缓存 vue-toastification 的实例。
 * 必须在组件 setup 里通过 useToastification() 拿到（注入实例），
 * 不能等到事件回调里再取；否则会退化成全局 eventBus 上的临时实例。
 */
let api = null

/** 相同消息的去重键；非字符串内容（组件等）不去重，返回 null。 */
function keyOf(kind, msg) {
  return typeof msg === 'string' ? `${kind}\u0000${msg}` : null
}

function forget(record) {
  const index = visible.indexOf(record)
  if (index >= 0) visible.splice(index, 1)
}

/** 统一入口：挤掉最旧 + 去重 + 维护关闭回调。 */
function show(kind, msg, options) {
  const toast = api
  if (!toast || typeof toast[kind] !== 'function') return undefined

  const key = keyOf(kind, msg)
  if (key !== null) {
    // 相同消息正在显示：直接复用，不再叠加一条
    const duplicate = visible.find((item) => item.key === key)
    if (duplicate) return duplicate.id
  }

  // 已满：先关掉最旧的一条，腾出位置给最新消息
  while (visible.length >= MAX_VISIBLE) {
    const oldest = visible[0]
    forget(oldest)
    toast.dismiss(oldest.id)
  }

  const record = { id: undefined, key }
  const userOnClose = options && typeof options.onClose === 'function' ? options.onClose : null
  const merged = {
    ...options,
    onClose: (instance) => {
      forget(record)
      if (userOnClose) userOnClose(instance)
    },
  }

  record.id = toast[kind](msg, merged)
  visible.push(record)
  return record.id
}

export function useToast() {
  const toast = useToastification()
  // 只认第一个（注入的）实例；所有组件拿到的是同一个全局 eventBus 接口
  if (!api) api = toast

  return {
    success: (msg, opts) => show('success', msg, opts),
    error: (msg, opts) => show('error', msg, opts),
    info: (msg, opts) => show('info', msg, opts),
    warning: (msg, opts) => show('warning', msg, opts),
    /** 原生透传，供特殊场景使用 */
    raw: toast,
    POSITION,
  }
}
