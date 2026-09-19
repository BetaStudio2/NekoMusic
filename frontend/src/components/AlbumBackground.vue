<script setup>
/**
 * AlbumBackground —— AMLL 专辑流动背景
 * ------------------------------------------------------------
 * 使用官方 @applemusic-like-lyrics/vue 的 BackgroundRender：
 * 以专辑封面为素材生成 WebGL 流动背景（Apple Music 观感）。
 *
 * 音频联动：
 *   通过 useAudioAnalyser 读取 80–120Hz 低频能量，喂给 AMLL 的
 *   lowFreqVolume，使背景随鼓点起伏。分析器未就绪时传 undefined，
 *   AMLL 会退回默认值 1.0（静态流动）。
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { BackgroundRender } from '@applemusic-like-lyrics/vue'
import { useAudioAnalyser } from '@/composables/useAudioAnalyser'

const props = defineProps({
  /** 专辑封面地址 */
  album: { type: String, default: '' },
  /** 是否播放中（影响流动活跃度） */
  playing: { type: Boolean, default: false },
  /** 是否有歌词（部分渲染器据此调整效果） */
  hasLyric: { type: Boolean, default: false },
})

const { ready, readBand } = useAudioAnalyser()

/** 低频音量 0..1；undefined = 交给 AMLL 默认值 */
const lowFreqVolume = ref(undefined)

let rafId = 0
let visible = true
let reducedMotion = false
let lastEmitted = -1

function tick() {
  rafId = 0
  if (!visible || document.hidden || !ready.value) {
    lowFreqVolume.value = undefined
    return
  }

  const v = readBand(80, 120)
  // 阈值过滤：避免每帧都触发下游更新
  if (Math.abs(v - lastEmitted) > 0.02) {
    lastEmitted = v
    lowFreqVolume.value = v
  }

  if (!reducedMotion) rafId = requestAnimationFrame(tick)
}

function start() {
  if (rafId || reducedMotion) return
  rafId = requestAnimationFrame(tick)
}

function stop() {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
}

function onVisibility() {
  if (document.hidden) stop()
  else start()
}

onMounted(() => {
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false

  if (typeof IntersectionObserver !== 'undefined') {
    // BackgroundRender 挂在 fixed 容器上，这里以组件宿主判定可见性
    visible = true
  }
  document.addEventListener('visibilitychange', onVisibility)
  start()
})

onUnmounted(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibility)
})

// 暂停时不再喂低频，让背景回到平稳流动
watch(
  () => props.playing,
  (p) => {
    if (!p) {
      lowFreqVolume.value = undefined
      lastEmitted = -1
    }
  }
)
</script>

<template>
  <div class="bg" aria-hidden="true">
    <BackgroundRender
      v-if="album"
      class="bg__render"
      :album="album"
      :playing="playing"
      :has-lyric="hasLyric"
      :low-freq-volume="lowFreqVolume"
    />
  </div>
</template>

<style scoped>
.bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.bg__render {
  width: 100%;
  height: 100%;
}
</style>
