<script setup>
/**
 * NButton —— 基础按钮
 * ------------------------------------------------------------
 * 变体：primary | secondary | ghost | danger | outline
 * 尺寸：sm | md | lg
 * 支持：图标名（自动用 NIcon）、加载态、块级、圆形。
 * 导航：传 to（router-link）或 href（<a>）时渲染为链接，保留原生链接语义。
 */
import { computed, useSlots } from 'vue'
import { RouterLink } from 'vue-router'
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
  /** 原生 type（仅 button 模式生效） */
  type: { type: String, default: 'button' },
  /** 站内路由目标；提供时渲染为 router-link */
  to: { type: [String, Object], default: undefined },
  /** 外部链接；提供时渲染为 <a>（to 优先） */
  href: { type: String, default: '' },
})

const emit = defineEmits(['click'])

const isDisabled = computed(() => props.disabled || props.loading)

/** button | router-link | a */
const tag = computed(() => {
  if (props.to !== undefined && props.to !== null && props.to !== '') return RouterLink
  if (props.href) return 'a'
  return 'button'
})
const isButton = computed(() => tag.value === 'button')
const isRouterLink = computed(() => tag.value === RouterLink)

const iconSize = computed(() => ({ sm: 14, md: 16, lg: 18 }[props.size] ?? 16))

const hasLabel = computed(() => !!slots.default)

/** 只向根元素传对应模式需要的属性，避免给 RouterLink 传入 href 覆盖其计算值 */
const elementAttrs = computed(() => {
  if (isRouterLink.value) return { to: props.to }
  if (tag.value === 'a') return props.href ? { href: props.href } : {}
  return { type: props.type, disabled: isDisabled.value }
})

const classes = computed(() => [
  'n-btn',
  `n-btn--${props.variant}`,
  `n-btn--${props.size}`,
  {
    'n-btn--block': props.block,
    'n-btn--round': props.round,
    'n-btn--loading': props.loading,
    'n-btn--icon-only': !hasLabel.value && (props.icon || props.iconAfter),
    'n-btn--disabled': !isButton.value && isDisabled.value,
  },
])

function onClick(e) {
  if (isDisabled.value) {
    if (!isButton.value) e.preventDefault()
    return
  }
  emit('click', e)
}
</script>

<template>
  <component
    :is="tag"
    :class="classes"
    v-bind="elementAttrs"
    :aria-disabled="!isButton && isDisabled ? 'true' : undefined"
    :aria-busy="loading || undefined"
    @click="onClick"
  >
    <span v-if="loading" class="n-btn__spinner" aria-hidden="true">
      <NIcon name="loader-circle" :size="iconSize" />
    </span>
    <NIcon v-else-if="icon" :name="icon" :size="iconSize" class="n-btn__icon" />
    <span v-if="$slots.default" class="n-btn__label"><slot /></span>
    <NIcon v-if="iconAfter && !loading" :name="iconAfter" :size="iconSize" class="n-btn__icon" />
  </component>
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
  text-decoration: none;
  border: 1px solid transparent;
  transition:
    background var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease),
    color var(--n-duration-fast) var(--n-ease),
    transform var(--n-duration-instant) var(--n-ease),
    box-shadow var(--n-duration-fast) var(--n-ease);
}

.n-btn:not(:disabled):not(.n-btn--disabled):active {
  transform: translateY(1px) scale(0.99);
}

.n-btn:disabled,
.n-btn--disabled {
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
  .n-btn--primary:not(:disabled):not(.n-btn--disabled):hover {
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
  .n-btn--secondary:not(:disabled):not(.n-btn--disabled):hover {
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
  .n-btn--ghost:not(:disabled):not(.n-btn--disabled):hover {
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
  .n-btn--danger:not(:disabled):not(.n-btn--disabled):hover {
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
  .n-btn--outline:not(:disabled):not(.n-btn--disabled):hover {
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
