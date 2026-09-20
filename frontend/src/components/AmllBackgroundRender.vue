<script setup>
/**
 * AmllBackgroundRender —— AMLL 流体背景的薄封装
 * ------------------------------------------------------------
 * 为什么不用官方 `@applemusic-like-lyrics/vue` 的 BackgroundRender：
 *   1) 它的根节点是 `display: contents`，canvas 的尺寸靠“恰好能解析到
 *      祖先高度”的百分比，配合 `contain: strict`，在不同浏览器 / 进出场
 *      动效期间（祖先带 transform）容易量到 0 尺寸且不再恢复；
 *   2) 它把创建/销毁放在 mounted / unmounted，且 dispose 不会释放 WebGL
 *      上下文 —— 关闭一次播放页再打开，上下文状态就可能不再干净，
 *      背景整块消失（刷新一次才恢复）。
 *
 * 这里参照 SPlayer-Next 的做法自管生命周期：显式 create / dispose，
 * canvas 挂到一个有确定尺寸的绝对定位容器里，并在 onBeforeUnmount
 * 同步释放上下文。构造失败时通知 useLiteMode 降级为静态模糊。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  BackgroundRender as CoreBackgroundRender,
  MeshGradientRenderer,
} from '@applemusic-like-lyrics/core'
import { markAmllUnavailable } from '@/composables/useLiteMode'

const props = defineProps({
  /** 专辑封面地址 */
  album: { type: String, default: '' },
  /** 是否播放中（决定流动活跃度） */
  playing: { type: Boolean, default: false },
  /** 是否有歌词 */
  hasLyric: { type: Boolean, default: false },
  /** 低频音量 0..1 */
  lowFreqVolume: { type: Number, default: undefined },
})

const hostRef = ref(null)
/** 底层渲染器实例（非响应式，避免 Vue 代理 WebGL 对象） */
let renderer = null

function syncMotion() {
  if (!renderer) return
  // 暂停时不冻结画面，只把流动速度降到 0，避免“定格”显得像坏掉
  renderer.setFlowSpeed(props.playing ? 1 : 0)
  renderer.resume()
}

onMounted(() => {
  if (!hostRef.value) return
  try {
    renderer = CoreBackgroundRender.new(MeshGradientRenderer)
  } catch {
    // 拿不到 WebGL 上下文：交给上层切静态模糊，别再抛给全局
    renderer = null
    markAmllUnavailable()
    return
  }

  const el = renderer.getElement()
  el.style.width = '100%'
  el.style.height = '100%'
  el.style.display = 'block'
  hostRef.value.appendChild(el)

  if (props.album) renderer.setAlbum(props.album, false)
  renderer.setHasLyric(props.hasLyric)
  syncMotion()
})

onBeforeUnmount(() => {
  const r = renderer
  renderer = null
  if (!r) return
  try {
    r.pause()
    r.dispose()
  } catch {
    /* 卸载阶段尽力释放，失败不影响流程 */
  }
})

watch(
  () => props.album,
  (val) => {
    if (val && renderer) renderer.setAlbum(val, false)
  }
)

watch(
  () => props.playing,
  () => syncMotion()
)

watch(
  () => props.hasLyric,
  (val) => renderer?.setHasLyric(val)
)

watch(
  () => props.lowFreqVolume,
  (val) => {
    if (val !== undefined) renderer?.setLowFreqVolume(val)
  }
)
</script>

<template>
  <div ref="hostRef" class="amll-bg" aria-hidden="true" />
</template>

<style scoped>
/* 绝对定位、有确定尺寸：canvas 的 100% 宽高有可解析的参照 */
.amll-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}
</style>
