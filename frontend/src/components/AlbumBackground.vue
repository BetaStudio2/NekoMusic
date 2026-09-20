<script setup>
/**
 * AlbumBackground —— 专辑背景
 * ------------------------------------------------------------
 * 桌面：使用自封装的 AmllBackgroundRender（底层是 AMLL 的
 *       MeshGradientRenderer），以专辑封面为素材生成 WebGL 流动背景。
 * 手机 / 减弱动效 / 弱设备 / 起不来 WebGL：换用【静态降级】——
 *       封面放大 + 高斯模糊，观感接近，但没有每帧全屏着色器的开销
 *       （见 useLiteMode）。
 *
 * 音频联动：
 *   通过 useAudioAnalyser 读取 80–120Hz 低频能量，喂给 AMLL 的
 *   lowFreqVolume，使背景随鼓点起伏。分析器未就绪时传 undefined，
 *   AMLL 会退回默认值 1.0（静态流动）。轻量模式下不跑这条 rAF。
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import AmllBackgroundRender from '@/components/AmllBackgroundRender.vue'
import { useAudioAnalyser } from '@/composables/useAudioAnalyser'
import { useLiteMode } from '@/composables/useLiteMode'

const props = defineProps({
  /** 专辑封面地址 */
  album: { type: String, default: '' },
  /** 是否播放中（影响流动活跃度） */
  playing: { type: Boolean, default: false },
  /** 是否有歌词（部分渲染器据此调整效果） */
  hasLyric: { type: Boolean, default: false },
  /**
   * 是否已过了播放页进场动效。true 之前先渲染静态模糊封面（便宜），
   * 之后再把 WebGL 渲染器挂起来 —— 避免在祖先带 transform 的进场
   * 动效期间创建 canvas，那正是「关一次再打开就没背景」的诱因。
   */
  active: { type: Boolean, default: true },
})

const { ready, readBand } = useAudioAnalyser()
const { lite } = useLiteMode()

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
  // 轻量模式没有 WebGL 背景可喂，不必空转读频谱
  if (rafId || reducedMotion || lite.value) return
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

// 降级切换（如窗口从窄拉宽）：按需起停频谱联动
watch(lite, (v) => {
  if (v) stop()
  else start()
})
</script>

<template>
  <div class="bg" aria-hidden="true">
    <!-- 桌面：WebGL 流动背景（进场动效结束后才挂载，见 active prop） -->
    <AmllBackgroundRender
      v-if="album && !lite && active"
      :album="album"
      :playing="playing"
      :has-lyric="hasLyric"
      :low-freq-volume="lowFreqVolume"
    />
    <!-- 轻量 / 进场动效期间：静态模糊封面（观感接近，几乎零成本） -->
    <div
      v-else-if="album"
      class="bg__still"
      :style="{ backgroundImage: `url('${album}')` }"
    />
    <div v-if="lite" class="bg__tint" />
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

/* 静态降级：放大 + 高斯模糊，避免边缘露出底色 */
.bg__still {
  position: absolute;
  inset: -10%;
  background-size: cover;
  background-position: center;
  /* 静态模糊很容易糊成一团灰：提高饱和与亮度把它「提起来」 */
  filter: blur(56px) saturate(1.7) brightness(1.08);
  transform: scale(1.08);
  opacity: 0.9;
}

/* 上下压暗、中间留亮：
   顶/底栏文字需要对比度，而中间要真的看得到背景。
   之前是一层均匀的深色渐变，等于把背景整片盖掉。 */
.bg__tint {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(1200px 760px at 20% 0%, rgba(95, 208, 224, 0.16), transparent 62%),
    linear-gradient(
      180deg,
      rgba(4, 9, 11, 0.52) 0%,
      rgba(4, 9, 11, 0.14) 24%,
      rgba(4, 9, 11, 0.14) 64%,
      rgba(4, 9, 11, 0.56) 100%
    );
}
</style>
