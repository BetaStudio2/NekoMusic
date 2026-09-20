/**
 * useLiteMode —— 是否该走「轻量渲染」
 * ------------------------------------------------------------
 * 手机的 GPU 与电量吃不起 AMLL 的全屏 WebGL 流动背景（Pixi 着色器每帧
 * 重绘），何况我们还有一条自己的 rAF 在读频谱。以下任一成立即判定为
 * 轻量模式，调用方应换用静态降级：
 *   · 窄屏（手机 / 小窗）
 *   · 系统「减弱动效」
 *   · 触摸设备且内存 / 核心数偏少
 *   · 当前环境根本起不来 AMLL 的 WebGL 背景
 *
 * 这是一个【全应用共享的单例】：播放页外壳（PlayerView）与背景组件
 * （AlbumBackground）各调一次，但拿到的是同一个 lite 引用。这样当 AMLL
 * 背景真的跑不起来时，包装层把 lite 置真，两边会同时切到静态降级，
 * 不会出现「背景已经降级、压暗层还按 WebGL 调」的错配。
 */
import { ref } from 'vue'
import { MeshGradientRenderer } from '@applemusic-like-lyrics/core'

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
 * 关键：AMLL 的 MeshGradientRenderer 用的是【WebGL1】—— 它内部执行的是
 *   canvas.getContext('webgl')
 * 而不是 WebGL2。所以这里必须探测同一种上下文，否则探测结论与实际
 * 渲染结果对不上：
 *
 *   · 机器没有 WebGL2 时，早先的实现直接返回「有能力」，于是照样挂
 *     AMLL，而 AMLL 自己 getContext('webgl') 也可能拿不到上下文，
 *     最后抛 "WebGL not supported"，播放页只剩一片空背景；
 *   · 机器能拿到 WebGL2 时结论又依赖 EXT_color_buffer_float 这类
 *     WebGL2 专有扩展，而这些扩展和 WebGL1 的 AMLL 无关，判断同样失真。
 *
 * 结论：用 WebGL1 探；拿不到就判定为轻量模式，换静态模糊封面。
 * （AMLL 在 WebGL1 下会去找 EXT_color_buffer_float / OES_texture_float
 *   等 WebGL2 专有扩展名并打警告，那是上游的误报，无害，已在 main.js
 *   过滤，不作为降级依据。）
 */
let webglCapable = null

function supportsAmllBackground() {
  if (webglCapable !== null) return webglCapable
  try {
    const canvas = document.createElement('canvas')
    const gl = canvas.getContext('webgl', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      powerPreference: 'low-power',
    })
    webglCapable = !!gl
    // 立刻释放：浏览器同时可存活的 WebGL 上下文有限，别占配额
    gl?.getExtension('WEBGL_lose_context')?.loseContext()
  } catch {
    webglCapable = false
  }
  return webglCapable
}

/**
 * AMLL 0.6 两个缺陷的一次性原型补丁（该库未暴露扩展点，只能从原型兜）。
 *
 *  1) dispose() 只 delete GL 资源、把 canvas 移出文档，并不释放 WebGL
 *     上下文。反复进出播放页会耗尽浏览器上下文配额，先出现
 *     「Too many active WebGL contexts」，随后背景整体起不来。
 *  2) 构造函数拿不到上下文会抛错，但此前已挂上的 ResizeObserver / rAF
 *     仍会继续回调到 this.gl 尚未赋值的半成品对象，每帧抛
 *     "Cannot read properties of undefined (reading 'bindFramebuffer')"。
 */
let rendererPatched = false

function patchAmllRenderer() {
  if (rendererPatched) return
  rendererPatched = true
  const proto = MeshGradientRenderer.prototype

  const originalDispose = proto.dispose
  proto.dispose = function patchedDispose(...args) {
    const result = originalDispose.apply(this, args)
    try {
      this.gl?.getExtension('WEBGL_lose_context')?.loseContext()
    } catch {
      /* 释放失败不影响卸载流程 */
    }
    return result
  }

  const originalOnTick = proto.onTick
  proto.onTick = function patchedOnTick(...args) {
    if (!this.gl) return
    return originalOnTick.apply(this, args)
  }
}

/** 共享的轻量模式开关 */
const lite = ref(false)
/** AMLL 背景被证实跑不起来后，本页会话内不再重试 */
let amllFailed = false
let initialized = false

function evaluate() {
  if (typeof window === 'undefined') return
  let value = SIZE_QUERIES.some((q) => window.matchMedia?.(q)?.matches)
  if (!value && window.matchMedia?.(TOUCH_QUERY)?.matches) {
    value = weakHardware()
  }
  // 设备跑不动 AMLL 的 WebGL 背景（或已经证实起不来）→ 用静态降级
  if (!value) value = amllFailed || !supportsAmllBackground()
  lite.value = value
}

function init() {
  if (initialized) return
  initialized = true
  patchAmllRenderer()
  /**
   * 同步先算一次：本函数在 setup 期间调用，因此首帧就能拿到正确值。
   * 若只在 onMounted 里算，首帧会先按「非轻量」渲染一次 —— 手机上
   * 表现为白白创建一次 WebGL 上下文再拆掉（闪烁 + 报错）。
   */
  evaluate()
  if (typeof window === 'undefined') return
  // 全局只需要一套监听；应用生命周期与页面一致，无需解绑。
  for (const q of [...SIZE_QUERIES, TOUCH_QUERY]) {
    const mql = window.matchMedia?.(q)
    if (!mql?.addEventListener) continue
    mql.addEventListener('change', evaluate)
  }
  window.addEventListener('resize', evaluate)
}

/** AMLL 背景初始化失败时由背景组件调用：切静态降级，且不再重试 */
export function markAmllUnavailable() {
  amllFailed = true
  lite.value = true
}

export function useLiteMode() {
  init()
  return { lite, markAmllUnavailable }
}
