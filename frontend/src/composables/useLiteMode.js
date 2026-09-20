/**
 * useLiteMode —— 是否该走「轻量渲染」
 * ------------------------------------------------------------
 * 手机的 GPU 与电量吃不起 AMLL 的全屏 WebGL 流动背景（Pixi 着色器每帧
 * 重绘），何况我们还有一条自己的 rAF 在读频谱。以下任一成立即判定为
 * 轻量模式，调用方应换用静态降级：
 *   · 窄屏（手机 / 小窗）
 *   · 系统「减弱动效」
 *   · 触摸设备且内存 / 核心数偏少
 *
 * 返回值是响应式的：窗口缩放、外接显示器、系统偏好变化都会实时更新。
 */
import { ref, onMounted, onUnmounted } from 'vue'

/** 命中任一即视为窄屏 / 需降级 */
const SIZE_QUERIES = ['(max-width: 900px)', '(prefers-reduced-motion: reduce)']

/** 触摸设备才看的硬件规格（桌面 4 核很常见，不该因此降级） */
const TOUCH_QUERY = '(hover: none) and (pointer: coarse)'

function weakHardware() {
  const nav = typeof navigator === 'undefined' ? null : navigator
  if (!nav) return false
  const cores = nav.hardwareConcurrency
  const mem = nav.deviceMemory
  if (typeof cores === 'number' && cores > 0 && cores <= 4) return true
  if (typeof mem === 'number' && mem > 0 && mem <= 4) return true
  return false
}

/**
 * WebGL 能力探测（只做一次，结果缓存）。
 *
 * AMLL 的 BackgroundRender 依赖【浮点渲染目标】：WebGL2 下需要
 * EXT_color_buffer_float。缺这个扩展时 Pixi 会打出
 *   EXT_color_buffer_float not supported
 * 并退回低保真路径（画面发灰、丢效果）。与其让它半残地跑，
 * 不如直接判定为轻量模式、换我们的静态模糊封面 —— 观感反而更稳。
 */
let webglCapable = null

function supportsAmllBackground() {
  if (webglCapable !== null) return webglCapable
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl2')
    // 拿不到 WebGL2 就不代 AMLL 做判断（它可能走 WebGL1 路径）
    if (!gl) {
      webglCapable = true
      return webglCapable
    }
    webglCapable = !!gl.getExtension('EXT_color_buffer_float')
    // 立刻释放：浏览器同时可存活的 WebGL 上下文有限，别占配额
    gl.getExtension('WEBGL_lose_context')?.loseContext()
  } catch {
    webglCapable = false
  }
  return webglCapable
}

export function useLiteMode() {
  const lite = ref(false)

  function evaluate() {
    if (typeof window === 'undefined') return
    let value = SIZE_QUERIES.some((q) => window.matchMedia?.(q)?.matches)
    if (!value && window.matchMedia?.(TOUCH_QUERY)?.matches) {
      value = weakHardware()
    }
    // 设备跑不动 AMLL 的 WebGL 背景（缺浮点渲染目标）→ 也用静态降级
    if (!value) value = !supportsAmllBackground()
    lite.value = value
  }

  /**
   * 同步先算一次：本函数在 setup 期间调用，因此首帧就能拿到正确值。
   * 若只在 onMounted 里算，首帧会先按「非轻量」渲染一次 —— 手机上
   * 表现为白白创建一次 WebGL 上下文再拆掉（闪烁 + 报错）。
   */
  evaluate()

  /** 已订阅的 MediaQueryList，卸载时解绑 */
  const bound = []

  onMounted(() => {
    evaluate()
    for (const q of [...SIZE_QUERIES, TOUCH_QUERY]) {
      const mql = window.matchMedia?.(q)
      if (!mql?.addEventListener) continue
      mql.addEventListener('change', evaluate)
      bound.push(mql)
    }
    // devicePixelRatio / 尺寸之外的场景（如外接屏）兜底
    window.addEventListener('resize', evaluate)
  })

  onUnmounted(() => {
    for (const mql of bound) mql.removeEventListener?.('change', evaluate)
    bound.length = 0
    window.removeEventListener('resize', evaluate)
  })

  return { lite }
}
