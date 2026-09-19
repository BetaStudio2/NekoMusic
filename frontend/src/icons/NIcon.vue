<script setup>
/**
 * NIcon —— 统一图标组件
 * ------------------------------------------------------------
 * 基于 morphicons 的 MorphIcon，页面只通过语义化 name 使用图标。
 *
 * 三种用法：
 *  1. 静态/自动变形（最常用）：name 变化时自动以弹簧动画变形
 *     <NIcon name="play" />
 *     <NIcon :name="isPlaying ? 'pause' : 'play'" />
 *  2. 受控变形（手势 / 滚动进度），用 from / to + progress(0~1)
 *     <NIcon from="menu" to="close" :progress="p" />
 *  3. 显式指定端点做一次性变形：可配合 spring 预设
 *
 * 图标数据来自 @/icons/registry，未登记的 name 会渲染为空并告警。
 */
import { computed, ref, watch } from 'vue'
import { MorphIcon } from 'morphicons/vue'
import { resolveIcon } from './registry'

const props = defineProps({
  /** 图标名（kebab-case），来自注册表；变化时自动变形 */
  name: { type: String, default: '' },
  /** 受控变形起点（图标名） */
  from: { type: String, default: '' },
  /** 受控变形终点（图标名） */
  to: { type: String, default: '' },
  /** 受控变形进度 0~1；提供时走受控模式 */
  progress: { type: Number, default: undefined },
  /** 弹簧预设：smooth | snappy | bouncy，或自定义 { k, c } */
  spring: { type: [String, Object], default: 'snappy' },
  /** 尺寸（px 或 CSS 值） */
  size: { type: [Number, String], default: 20 },
  /** 颜色，默认继承 currentColor */
  color: { type: String, default: 'currentColor' },
  /** 线宽 */
  strokeWidth: { type: [Number, String], default: 2 },
  /** 线宽不随 size 缩放（与 lucide 行为一致） */
  absoluteStrokeWidth: { type: Boolean, default: false },
  /** 无障碍标签；提供时 role="img"，否则 aria-hidden */
  label: { type: String, default: '' },
  /** 减弱动效策略：never（默认不理会）| user（跟随系统）| always */
  reducedMotion: { type: String, default: 'user' },
})

const emit = defineEmits(['missing'])

/** 受控模式：MorphIcon 自身按 from/to/progress 计算冻结形状，这里只负责解析数据 */
const isControlled = computed(() => props.progress !== undefined)

const resolvedName = computed(() => resolveIcon(props.name))
const resolvedFrom = computed(() => resolveIcon(props.from))
const resolvedTo = computed(() => resolveIcon(props.to))

/** 首次渲染前缺图标时，仅在开发环境提示 */
watch(
  () => [props.name, props.from, props.to],
  () => {
    if (import.meta.env.DEV) {
      if (props.name && !resolvedName.value) {
        console.warn(`[NIcon] 未登记的图标名: "${props.name}"（请检查 @/icons/registry）`)
        emit('missing', props.name)
      }
      if (props.from && !resolvedFrom.value) {
        console.warn(`[NIcon] 未登记的 from 图标名: "${props.from}"`)
      }
      if (props.to && !resolvedTo.value) {
        console.warn(`[NIcon] 未登记的 to 图标名: "${props.to}"`)
      }
    }
  },
  { immediate: true }
)

const morphRef = ref(null)
defineExpose({
  /** 命令式变形到指定图标名 */
  morphTo(name) {
    const data = resolveIcon(name)
    if (data) morphRef.value?.morphTo(data, props.spring)
  },
  /** 命令式立即切换（无动画） */
  set(name) {
    const data = resolveIcon(name)
    if (data) morphRef.value?.set(data)
  },
})
</script>

<template>
  <MorphIcon
    v-if="isControlled"
    ref="morphRef"
    :from="resolvedFrom"
    :to="resolvedTo"
    :progress="progress"
    :size="size"
    :color="color"
    :stroke-width="strokeWidth"
    :absolute-stroke-width="absoluteStrokeWidth"
    :reduced-motion="reducedMotion"
    :label="label || undefined"
    class="n-icon"
  />
  <MorphIcon
    v-else-if="resolvedName"
    ref="morphRef"
    :icon="resolvedName"
    :spring="spring"
    :size="size"
    :color="color"
    :stroke-width="strokeWidth"
    :absolute-stroke-width="absoluteStrokeWidth"
    :reduced-motion="reducedMotion"
    :label="label || undefined"
    class="n-icon"
  />
</template>

<style scoped>
.n-icon {
  display: inline-block;
  flex: none;
  vertical-align: middle;
}
</style>
