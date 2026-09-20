<script setup>
/**
 * NCard —— 玻璃拟态容器
 * ------------------------------------------------------------
 * variant: glass（默认，带模糊）| solid（不透明）| plain（透明无边框）
 * 支持 hoverable（悬停上浮）、pad（内边距）、标题插槽。
 */
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'glass',
    validator: (v) => ['glass', 'solid', 'plain'].includes(v),
  },
  /** 内边距预设 */
  pad: {
    type: String,
    default: 'md',
    validator: (v) => ['none', 'sm', 'md', 'lg'].includes(v),
  },
  /** 悬停上浮交互 */
  hoverable: { type: Boolean, default: false },
  /** 作为可点击元素时的语义角色 */
  as: { type: String, default: 'div' },
})

const classes = computed(() => [
  'n-card',
  `n-card--${props.variant}`,
  `n-card--pad-${props.pad}`,
  { 'n-card--hoverable': props.hoverable },
])
</script>

<template>
  <component :is="as" :class="classes">
    <header v-if="$slots.header || $slots.actions" class="n-card__header">
      <div class="n-card__heading">
        <slot name="header" />
      </div>
      <div v-if="$slots.actions" class="n-card__actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </component>
</template>

<style scoped>
.n-card {
  border-radius: var(--n-radius-lg);
  border: 1px solid var(--n-line);
  position: relative;
  transition:
    transform var(--n-duration) var(--n-ease),
    border-color var(--n-duration) var(--n-ease),
    box-shadow var(--n-duration) var(--n-ease),
    background var(--n-duration) var(--n-ease);
}

/* ===== 变体 ===== */
.n-card--glass {
  background: var(--n-surface);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
  box-shadow: var(--n-shadow);
}

.n-card--solid {
  background: var(--n-surface-strong);
  box-shadow: var(--n-shadow);
}

.n-card--plain {
  background: transparent;
  border-color: var(--n-line-subtle);
  box-shadow: none;
}

/* ===== 内边距 ===== */
.n-card--pad-none { padding: 0; }
.n-card--pad-sm { padding: var(--n-space-4); }
.n-card--pad-md { padding: var(--n-space-6); }
.n-card--pad-lg { padding: var(--n-space-8); }

/* ===== 悬停 ===== */
@media (hover: hover) {
  .n-card--hoverable:hover {
    transform: translateY(-4px);
    border-color: var(--n-line-strong);
    box-shadow: var(--n-shadow-lg);
  }
}

/* ===== 头部 ===== */
.n-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-4);
}

.n-card--pad-none .n-card__header {
  padding: var(--n-space-6) var(--n-space-6) 0;
  margin-bottom: var(--n-space-4);
}

.n-card__heading {
  min-width: 0;
  font-weight: var(--n-weight-semibold);
}

.n-card__actions {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  flex: none;
}
</style>
