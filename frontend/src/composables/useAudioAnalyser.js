/**
 * useAudioAnalyser —— 音频频谱分析（Web Audio API）
 * ------------------------------------------------------------
 * 架构说明：
 *   全站唯一承载音频的 <audio> 元素在 GlobalPlayer 内。Web Audio 的
 *   AnalyserNode 必须挂在同一个元素上，因此这里做成「模块级单例」：
 *   GlobalPlayer 注册元素，其它组件通过 useAudioAnalyser() 读取频谱。
 *
 * ★ 关键：必须等用户手势后才建立 AudioContext ★
 *   1) 浏览器禁止在无用户手势时启动 AudioContext（会警告并保持 suspended）；
 *   2) 更严重的是——一旦 createMediaElementSource 把元素接进「挂起的」
 *      AudioContext，音频会被路由进这个不输出的图，表现为【整条静音】。
 *   因此这里把「建 context / 接 source」整体延迟到首次用户手势（或显式
 *   unlock()，例如播放按钮回调）之后再执行。未就绪时 read() 返回 false，
 *   调用方降级为静态展示。
 *
 * 其它注意事项：
 *  - createMediaElementSource 对同一元素只能调用一次；元素被重建（v-if）
 *    时必须断开旧链路并重建。
 *  - 跨域音频需要 <audio crossorigin="anonymous"> 且服务端返回
 *    Access-Control-Allow-Origin，否则同样会被判为「污染」而静音。
 */
import { ref } from 'vue'

const state = {
  ctx: null,
  source: null,
  analyser: null,
  freq: null,
  /** Float32 频域缓冲（dB 值），供频谱条使用，不受 min/maxDecibels 截断影响 */
  floatFreq: null,
  attachedEl: null,
  builtEl: null,
  unlocked: false,
  gestureBound: false,
  autoResumeBound: false,
  ready: false,
  failed: false,
}

/** 已就绪（可读到频谱） */
export const analyserReady = ref(false)
/** 初始化失败（不支持 / 被阻止） */
export const analyserFailed = ref(false)

function resumeContext() {
  if (state.ctx && state.ctx.state === 'suspended') {
    return state.ctx.resume().catch(() => {})
  }
  return Promise.resolve()
}

/** 建立 context / source / analyser 并接通链路 */
function build(el) {
  if (!el) return
  if (state.builtEl === el && state.ready) return

  const AudioCtx = window.AudioContext || window.webkitAudioContext
  if (!AudioCtx) {
    state.failed = true
    analyserFailed.value = true
    return
  }

  try {
    if (!state.ctx) state.ctx = new AudioCtx()

    // 元素被重建：旧链路无法复用，断开后重建
    if (state.source && state.builtEl !== el) {
      try {
        state.source.disconnect()
      } catch {
        /* ignore */
      }
      state.source = null
      state.analyser = null
      state.freq = null
      state.floatFreq = null
      state.ready = false
      analyserReady.value = false
    }

    if (!state.source) {
      state.source = state.ctx.createMediaElementSource(el)
      state.analyser = state.ctx.createAnalyser()
      state.analyser.fftSize = 1024
      state.analyser.smoothingTimeConstant = 0.78
      state.freq = new Uint8Array(state.analyser.frequencyBinCount)
    }

    // 串联：source → analyser → destination（不接 destination 会没声音）
    try {
      state.source.disconnect()
    } catch {
      /* 首次未连接时会抛，忽略 */
    }
    state.source.connect(state.analyser)
    state.analyser.connect(state.ctx.destination)

    state.builtEl = el
    state.attachedEl = el
    state.ready = true
    analyserReady.value = true
    bindContextAutoResume()
  } catch (err) {
    console.error('[Neko] 音频频谱初始化失败：', err)
    state.failed = true
    analyserFailed.value = true
  }
}

/** 用户手势解锁：建立链路并恢复 context */
function unlock() {
  state.unlocked = true
  if (state.attachedEl) build(state.attachedEl)
  resumeContext()
}

/**
 * 浏览器会在标签页切到后台、或长时间静默后把 AudioContext 挂起，
 * 回到前台时若不主动 resume，会表现为「音乐还在放但频谱不动」。
 */
function bindContextAutoResume() {
  if (state.autoResumeBound || typeof document === 'undefined') return
  state.autoResumeBound = true
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) resumeContext()
  })
}

/** 绑定一次性手势解锁（capture 阶段，确保早于其它处理） */
function bindGestureUnlock() {
  if (state.gestureBound) return
  state.gestureBound = true
  const onGesture = () => {
    window.removeEventListener('pointerdown', onGesture, true)
    window.removeEventListener('keydown', onGesture, true)
    unlock()
  }
  window.addEventListener('pointerdown', onGesture, true)
  window.addEventListener('keydown', onGesture, true)
}

/** 由 GlobalPlayer 注册承载音频的元素 */
export function attachAudioElement(el) {
  if (typeof window === 'undefined' || !el) return

  state.attachedEl = el

  // 已解锁过（或已有运行中的 context）→ 立即建立
  if (state.unlocked || state.ctx?.state === 'running') {
    build(el)
    return
  }

  // 否则等用户手势；此时不创建 AudioContext，避免自动播放限制与静音
  bindGestureUnlock()
}

/** 供播放等明确的手势路径主动调用 */
export function unlockAudioAnalyser() {
  if (typeof window === 'undefined') return
  unlock()
}

export function useAudioAnalyser() {
  return {
    ready: analyserReady,
    failed: analyserFailed,
    /** 采样点数 */
    get binCount() {
      return state.freq ? state.freq.length : 0
    },
    /** 主动解锁（可在播放按钮回调里调用） */
    unlock: unlockAudioAnalyser,
    resume: resumeContext,
    /**
     * 把当前频谱写入 target。
     * @param {Uint8Array} target
     * @returns {boolean}
     */
    read(target) {
      if (!state.analyser || !state.freq || !target) return false
      state.analyser.getByteFrequencyData(state.freq)
      const n = Math.min(target.length, state.freq.length)
      for (let i = 0; i < n; i++) target[i] = state.freq[i]
      return true
    },
    /**
     * 读取原始频域数据（dB 值，Float32）。与 read() 不同，这里不受
     * AnalyserNode 的 min/maxDecibels 截断影响，调用方可自行选择合适的
     * 动态范围做映射 —— 频谱条用它来避免大音量时整排满量程。
     * @param {Float32Array} target
     * @returns {boolean}
     */
    readFloat(target) {
      if (!state.analyser || !target) return false
      const bins = state.analyser.frequencyBinCount
      if (!state.floatFreq || state.floatFreq.length !== bins) {
        state.floatFreq = new Float32Array(bins)
      }
      state.analyser.getFloatFrequencyData(state.floatFreq)
      const n = Math.min(target.length, state.floatFreq.length)
      for (let i = 0; i < n; i++) target[i] = state.floatFreq[i]
      return true
    },
    /**
     * 读取指定频率区间的平均能量（0..1）。
     * @param {number} minHz
     * @param {number} maxHz
     * @returns {number}
     */
    readBand(minHz, maxHz) {
      if (!state.analyser || !state.freq) return 0
      state.analyser.getByteFrequencyData(state.freq)
      const sampleRate = state.ctx?.sampleRate || 44100
      const binHz = sampleRate / state.analyser.fftSize
      const from = Math.max(0, Math.floor(minHz / binHz))
      const to = Math.min(state.freq.length - 1, Math.ceil(maxHz / binHz))
      if (to < from) return 0
      let sum = 0
      for (let i = from; i <= to; i++) sum += state.freq[i]
      return sum / (to - from + 1) / 255
    },
  }
}
