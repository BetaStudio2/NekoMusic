<script setup>
/**
 * NButton —— 基础按钮
 * ------------------------------------------------------------
 * 变体：primary | secondary | ghost | danger | outline
 * 尺寸：sm | md | lg
 * 支持：图标名（自动用 NIcon）、加载态、块级、圆形。
 */
import { computed, useSlots } from 'vue'
import NIcon from '@/icons/NIcon.vue'

const slots = useSlots()

const props = defineProps({
  /** 视觉变体 */
  variant: {
    type: String,
    default: 'secondary',
    validator: (v) => ['primary', 'secondary', 'ghost', 'danger', 'outline'].includes(v),
  },
  /** 尺寸 */
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
  /** 前置图标名（来自图标注册表） */
  icon: { type: String, default: '' },
  /** 后置图标名 */
  iconAfter: { type: String, default: '' },
  /** 加载中：显示转圈并禁用交互 */
  loading: { type: Boolean, default: false },
  /** 禁用 */
  disabled: { type: Boolean, default: false },
  /** 块级宽度 */
  block: { type: Boolean, default: false },
  /** 圆形（常用于纯图标按钮） */
  round: { type: Boolean, default: false },
  /** 原生 type */
  type: { type: String, default: 'button' },
})

const emit = defineEmits(['click'])

const isDisabled = computed(() => props.disabled || props.loading)

const iconSize = computed(() => ({ sm: 14, md: 16, lg: 18 }[props.size] ?? 16))

const hasLabel = computed(() => !!slots.default)

const classes = computed(() => [
  'n-btn',
  `n-btn--${props.variant}`,
  `n-btn--${props.size}`,
  {
    'n-btn--block': props.block,
    'n-btn--round': props.round,
    'n-btn--loading': props.loading,
    'n-btn--icon-only': !hasLabel.value && (props.icon || props.iconAfter),
  },
])

function onClick(e) {
  if (isDisabled.value) return
  emit('click', e)
}
</script>

<template>
  <button
    :type="type"
    :class="classes"
    :disabled="isDisabled"
    :aria-busy="loading || undefined"
    @click="onClick"
  >
    <span v-if="loading" class="n-btn__spinner" aria-hidden="true">
      <NIcon name="loader-circle" :size="iconSize" />
    </span>
    <NIcon v-else-if="icon" :name="icon" :size="iconSize" class="n-btn__icon" />
    <span v-if="$slots.default" class="n-btn__label"><slot /></span>
    <NIcon v-if="iconAfter && !loading" :name="iconAfter" :size="iconSize" class="n-btn__icon" />
  </button>
</template>

<style scoped>
.n-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-2);
  border-radius: var(--n-radius-pill);
  font-weight: var(--n-weight-medium);
  line-height: 1;
  white-space: nowrap;
  border: 1px solid transparent;
  transition:
    background var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease),
    color var(--n-duration-fast) var(--n-ease),
    transform var(--n-duration-instant) var(--n-ease),
    box-shadow var(--n-duration-fast) var(--n-ease);
}

.n-btn:not(:disabled):active {
  transform: translateY(1px) scale(0.99);
}

.n-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 尺寸 ===== */
.n-btn--sm {
  height: 30px;
  padding: 0 var(--n-space-3);
  font-size: var(--n-text-sm);
}
.n-btn--md {
  height: 38px;
  padding: 0 var(--n-space-5);
  font-size: var(--n-text-base);
}
.n-btn--lg {
  height: 46px;
  padding: 0 var(--n-space-8);
  font-size: var(--n-text-md);
}

.n-btn--round {
  padding: 0;
  aspect-ratio: 1;
  border-radius: var(--n-radius-circle);
}
.n-btn--round.n-btn--sm { width: 30px; }
.n-btn--round.n-btn--md { width: 38px; }
.n-btn--round.n-btn--lg { width: 46px; }

.n-btn--block {
  display: flex;
  width: 100%;
}

/* ===== 变体 · primary ===== */
.n-btn--primary {
  background: var(--n-gradient-accent);
  border-color: var(--n-accent-line);
  color: var(--n-text);
  box-shadow: 0 8px 24px rgba(105, 200, 223, 0.16);
}
@media (hover: hover) {
  .n-btn--primary:not(:disabled):hover {
    background: linear-gradient(135deg, rgba(105, 200, 223, 0.36), rgba(105, 200, 223, 0.16));
    border-color: rgba(105, 200, 223, 0.42);
    box-shadow: 0 10px 30px rgba(105, 200, 223, 0.24);
  }
}

/* ===== 变体 · secondary ===== */
.n-btn--secondary {
  background: var(--n-surface-soft);
  border-color: var(--n-line);
  color: var(--n-text);
}
@media (hover: hover) {
  .n-btn--secondary:not(:disabled):hover {
    background: var(--n-surface-hover);
    border-color: var(--n-line-strong);
  }
}

/* ===== 变体 · ghost ===== */
.n-btn--ghost {
  background: transparent;
  border-color: transparent;
  color: var(--n-text-muted);
}
@media (hover: hover) {
  .n-btn--ghost:not(:disabled):hover {
    background: var(--n-surface-soft);
    color: var(--n-text);
  }
}

/* ===== 变体 · danger ===== */
.n-btn--danger {
  background: var(--n-danger-soft);
  border-color: rgba(255, 107, 107, 0.28);
  color: #ffb3b3;
}
@media (hover: hover) {
  .n-btn--danger:not(:disabled):hover {
    background: rgba(255, 107, 107, 0.24);
    border-color: rgba(255, 107, 107, 0.42);
    color: #ffd0d0;
  }
}

/* ===== 变体 · outline ===== */
.n-btn--outline {
  background: transparent;
  border-color: var(--n-accent-line);
  color: var(--n-accent-strong);
}
@media (hover: hover) {
  .n-btn--outline:not(:disabled):hover {
    background: var(--n-accent-soft);
    border-color: rgba(105, 200, 223, 0.42);
  }
}

.n-btn__icon {
  flex: none;
}

.n-btn__spinner {
  display: inline-flex;
  animation: n-btn-spin 0.9s linear infinite;
}

@keyframes n-btn-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .n-btn__spinner { animation-duration: 2s; }
}
</style>
