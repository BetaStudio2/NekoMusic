/**
 * useAudioAnalyser —— 音频频谱分析（Web Audio API）
 * ------------------------------------------------------------
 * 架构说明：
 *   全站唯一承载音频的 <audio> 元素在 GlobalPlayer 内。Web Audio 的
 *   AnalyserNode 必须挂在同一个元素上，因此这里做成「模块级单例」：
 *   GlobalPlayer 挂载后调用 attachAudioElement(el) 注册，
 *   其它组件通过 useAudioAnalyser() 读取频谱数据（不重复创建 AudioContext）。
 *
 * 注意：
 *  - createMediaElementSource 对同一元素只能调用一次，重复调用会抛错，故只连一次。
 *  - AudioContext 需在用户手势后才能 resume()，这里挂了「首次交互自动恢复」。
 *  - 若浏览器不支持 / 初始化失败，failed 置为 true，调用方应降级为静态展示。
 */
import { ref } from 'vue'

const state = {
  ctx: null,
  source: null,
  analyser: null,
  freq: null,
  attachedEl: null,
  gestureBound: false,
  ready: false,
  failed: false,
}

/** 是否已就绪（可读到频谱） */
export const analyserReady = ref(false)
/** 是否初始化失败（不支持 / 被阻止） */
export const analyserFailed = ref(false)

function resumeContext() {
  if (state.ctx && state.ctx.state === 'suspended') {
    state.ctx.resume().catch(() => {})
  }
}

/** 由 GlobalPlayer 在挂载后调用，注册承载音频的元素 */
export function attachAudioElement(el) {
  if (typeof window === 'undefined' || !el) return
  if (state.attachedEl === el && state.ready) return

  const AudioCtx = window.AudioContext || window.webkitAudioContext
  if (!AudioCtx) {
    state.failed = true
    analyserFailed.value = true
    return
  }

  try {
    if (!state.ctx) state.ctx = new AudioCtx()

    // 每个元素只能建一次 source
    if (!state.source) {
      state.source = state.ctx.createMediaElementSource(el)
    }

    if (!state.analyser) {
      state.analyser = state.ctx.createAnalyser()
      state.analyser.fftSize = 1024
      state.analyser.smoothingTimeConstant = 0.78
      state.freq = new Uint8Array(state.analyser.frequencyBinCount)
    }

    // 串联：source → analyser → destination（否则听不到声音）
    state.source.connect(state.analyser)
    state.analyser.connect(state.ctx.destination)

    state.attachedEl = el
    state.ready = true
    analyserReady.value = true

    // 浏览器要求用户手势后才能 resume；挂一次性监听自动恢复
    if (!state.gestureBound) {
      state.gestureBound = true
      const onGesture = () => resumeContext()
      window.addEventListener('pointerdown', onGesture, { once: true, passive: true })
      window.addEventListener('keydown', onGesture, { once: true, passive: true })
    }
  } catch (err) {
    console.error('[Neko] 音频频谱初始化失败：', err)
    state.failed = true
    analyserFailed.value = true
  }
}

export function useAudioAnalyser() {
  return {
    ready: analyserReady,
    failed: analyserFailed,
    /** 采样点数（频率 bin 数） */
    get binCount() {
      return state.freq ? state.freq.length : 0
    },
    /** 手动恢复（用于 play 等手势路径） */
    resume: resumeContext,
    /**
     * 把当前频谱写入 target（长度可小于 binCount，会自动截取低频段）。
     * @param {Uint8Array} target
     * @returns {boolean} 是否成功读取
     */
    read(target) {
      if (!state.analyser || !state.freq || !target) return false
      state.analyser.getByteFrequencyData(state.freq)
      const n = Math.min(target.length, state.freq.length)
      for (let i = 0; i < n; i++) target[i] = state.freq[i]
      return true
    },
    /**
     * 读取指定频率区间的平均能量（0..1）。
     * 常用于「低频鼓点」驱动背景/封面起伏（如 AMLL 的 lowFreqVolume 需要 80–120Hz）。
     * @param {number} minHz
     * @param {number} maxHz
     * @returns {number} 0..1；未就绪时返回 0
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
