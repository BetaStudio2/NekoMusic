<script setup>
/**
 * SpectrumCanvas —— 实时频谱条
 * ------------------------------------------------------------
 * 数据来自 useAudioAnalyser（挂在 GlobalPlayer 的 <audio> 上）。
 * 无数据（未播放 / 分析器不可用）时绘制一条柔和的静止基线作为降级。
 *
 * 特性：
 *  - rAF 驱动；标签页隐藏或组件离开视口时暂停，省电
 *  - 攻击/释放平滑（快起慢落），视觉更接近真实频谱
 *  - DPR 自适应；颜色取自设计令牌（--n-accent / --n-accent-strong）
 *  - 尊重 prefers-reduced-motion：只画静态帧
 */
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useAudioAnalyser } from '@/composables/useAudioAnalyser'

const props = defineProps({
  /** 频谱条数量 */
  bars: { type: Number, default: 48 },
  /** 画布高度（px） */
  height: { type: Number, default: 72 },
  /** 是否动画（通常传 isPlaying） */
  active: { type: Boolean, default: true },
})

const canvasRef = ref(null)
const { readFloat } = useAudioAnalyser()

/**
 * 频谱映射的 dB 动态范围。
 * 浏览器默认 maxDecibels = -30，音乐在副歌/主歌时绝大多数频点都超过它，
 * getByteFrequencyData 会把它们统统截成 255 —— 表现就是整排条子满量程。
 * 这里改用原始 dB 值，并给出 ~80dB 的动态范围 + 软限幅，既保留细节又永不触顶。
 */
const MIN_DB = -90
const MAX_DB = -10

/** dB → 0..1，并裁掉范围外的值 */
function dbToUnit(db) {
  if (!Number.isFinite(db)) return 0
  const v = (db - MIN_DB) / (MAX_DB - MIN_DB)
  return v <= 0 ? 0 : v >= 1 ? 1 : v
}

/**
 * 软限幅（soft-knee）：x≤knee 时近似线性，越接近上限压缩越强，
 * 永远到不了 ceil，因此不会出现「满量程」的齐平顶。
 */
function softLimit(x, ceil = 0.92, knee = 0.72) {
  if (x <= knee) return x
  return knee + (ceil - knee) * (1 - Math.exp(-(x - knee) / (ceil - knee)))
}

let rafId = 0
let resizeObserver = null
let visible = true
let reducedMotion = false

/** 每根条子的显示高度（0..1），用于攻击/释放平滑 */
let levels = []
let freqBuf = null

let accent = '#5fd0e0'
let accentStrong = '#9ceefb'
let lineColor = 'rgba(120, 205, 218, 0.16)'

function readTokens() {
  const el = canvasRef.value
  if (!el) return
  const cs = getComputedStyle(el)
  const a = cs.getPropertyValue('--n-accent').trim()
  const s = cs.getPropertyValue('--n-accent-strong').trim()
  const l = cs.getPropertyValue('--n-line').trim()
  if (a) accent = a
  if (s) accentStrong = s
  if (l) lineColor = l
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const rect = canvas.getBoundingClientRect()
  const w = Math.max(1, Math.floor(rect.width))
  const h = Math.max(1, props.height)
  // 只写【绘图缓冲】尺寸；显示尺寸由外层与 CSS 决定，
  // 避免 canvas.width 反过来影响布局（见 <style> 里的说明）
  canvas.width = Math.floor(w * dpr)
  canvas.height = Math.floor(h * dpr)
  const ctx = canvas.getContext('2d')
  if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function ensureBuffers() {
  if (levels.length !== props.bars) {
    levels = new Array(props.bars).fill(0)
  }
  if (!freqBuf || freqBuf.length !== props.bars * 4) {
    freqBuf = new Float32Array(props.bars * 4)
  }
}

function drawIdle(ctx, w, h) {
  const y = h - 1.5
  ctx.clearRect(0, 0, w, h)
  ctx.fillStyle = lineColor
  ctx.fillRect(0, y, w, 1.5)
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return 0
  const ctx = canvas.getContext('2d')
  if (!ctx) return 0

  const rect = canvas.getBoundingClientRect()
  const w = Math.max(1, rect.width)
  const h = props.height

  ensureBuffers()

  const hasData = readFloat(freqBuf)
  let maxLevel = 0

  if (hasData) {
    const n = props.bars
    const usable = Math.floor(freqBuf.length * 0.72) // 高频段通常很弱，略过尾部
    const step = Math.max(1, Math.floor(usable / n))
    for (let i = 0; i < n; i++) {
      let sum = 0
      let count = 0
      const start = i * step
      for (let j = start; j < start + step && j < freqBuf.length; j++) {
        sum += dbToUnit(freqBuf[j])
        count++
      }
      const avg = count ? sum / count : 0
      // 低频/小能量增强，视觉更均衡；再做软限幅，保证永远不满量程
      const boosted = softLimit(Math.pow(avg, 0.82) * 1.05)
      // 快起慢落
      levels[i] = boosted > levels[i] ? boosted : levels[i] * 0.86 + boosted * 0.14
      if (levels[i] > maxLevel) maxLevel = levels[i]
    }
  } else {
    for (let i = 0; i < levels.length; i++) {
      levels[i] *= 0.9
      if (levels[i] > maxLevel) maxLevel = levels[i]
    }
  }

  // 完全静止 → 只画基线（省 CPU 且视觉干净）
  if (maxLevel < 0.008) {
    drawIdle(ctx, w, h)
    return maxLevel
  }

  ctx.clearRect(0, 0, w, h)

  const n = levels.length
  const gap = 2
  const barW = Math.max(1, (w - gap * (n - 1)) / n)
  const radius = Math.min(barW / 2, 3)

  const grad = ctx.createLinearGradient(0, h, 0, 0)
  grad.addColorStop(0, accent)
  grad.addColorStop(1, accentStrong)

  for (let i = 0; i < n; i++) {
    const lv = levels[i]
    const bh = Math.max(2, lv * (h - 4))
    const x = i * (barW + gap)
    const y = h - bh

    ctx.fillStyle = grad
    ctx.beginPath()
    // 圆顶矩形
    ctx.moveTo(x, y + radius)
    ctx.arcTo(x, y, x + radius, y, radius)
    ctx.lineTo(x + barW - radius, y)
    ctx.arcTo(x + barW, y, x + barW, y + radius, radius)
    ctx.lineTo(x + barW, h)
    ctx.lineTo(x, h)
    ctx.closePath()
    ctx.fill()
  }

  return maxLevel
}

/** 当前帧的最大能量，用于判断「是否还需要继续画」 */
let lastMax = 0

function loop() {
  rafId = 0
  if (!visible || document.hidden) return
  lastMax = draw()
  // 播放中 → 持续绘制；
  // 已暂停 → 继续画到所有条子衰减回基线（避免画面「冻住」），再停。
  if (!reducedMotion && (props.active || lastMax >= 0.008)) {
    rafId = requestAnimationFrame(loop)
  }
}

function start() {
  if (rafId) return
  // 纯静态帧：开了减动效，或已暂停且残留能量早已衰减干净
  if (reducedMotion || (!props.active && lastMax < 0.008)) {
    draw()
    return
  }
  rafId = requestAnimationFrame(loop)
}

function stop() {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
}

/**
 * ★ 关键：这个循环只会「自己停」，不会「自己醒」：
 *   active（绑 isPlaying）由 true → false 时，loop 把残留条子衰减回基线后
 *   就停止调度；若没人监听它重新变 true，暂停一次之后频谱将永远不再动
 *   （刷新后同理：自动播放被拦 → isPlaying 变 false → 循环停止，
 *   此时再点播放也不会重新绘制）。
 */
watch(
  () => props.active,
  (isActive) => {
    if (isActive) start()
    // true → false 无需处理：loop 会自行衰减收尾后停止
  }
)

onMounted(() => {
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false
  readTokens()
  resize()
  lastMax = draw()

  resizeObserver = new ResizeObserver(() => {
    resize()
    if (reducedMotion || !props.active) lastMax = draw()
  })
  if (canvasRef.value) resizeObserver.observe(canvasRef.value)

  // 视口可见性：离开视口停止绘制
  if (typeof IntersectionObserver !== 'undefined' && canvasRef.value) {
    const io = new IntersectionObserver(
      ([entry]) => {
        visible = entry.isIntersecting
        if (visible) start()
        else stop()
      },
      { threshold: 0 }
    )
    io.observe(canvasRef.value)
  }

  document.addEventListener('visibilitychange', onVisibility)
  start()
})

function onVisibility() {
  if (document.hidden) stop()
  else start()
}

onUnmounted(() => {
  stop()
  resizeObserver?.disconnect()
  resizeObserver = null
  document.removeEventListener('visibilitychange', onVisibility)
})

defineExpose({ redraw: draw })
</script>

<template>
  <div class="spectrum-box" :style="{ height: height + 'px' }">
    <canvas ref="canvasRef" class="spectrum-canvas" aria-hidden="true" />
  </div>
</template>

<style scoped>
/* 外层负责占位，canvas 用绝对定位【脱离布局】。
   若让 <canvas> 直接作为对外根元素，它的固有尺寸（默认 300px，且会被
   resize() 里写入的 canvas.width 改变）会经由 flex/grid 的内容尺寸反馈给
   父级：既挤压同排控件，又会和 resize() 形成「越量越大」的正反馈。
   调用方只需给外层一个确定宽度（如 width: 96px / min(100%, 38vh)）。 */
.spectrum-box {
  position: relative;
  width: 100%;
  overflow: hidden;
}

.spectrum-canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
}
</style>
