<script setup>
/**
 * NTag —— 标签 / 徽章
 * variant: default | accent | success | warning | danger | outline
 * 可选前置图标、可关闭。
 */
import { computed } from 'vue'
import NIcon from '@/icons/NIcon.vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'accent', 'success', 'warning', 'danger', 'outline'].includes(v),
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md'].includes(v),
  },
  icon: { type: String, default: '' },
  closable: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])

const classes = computed(() => ['n-tag', `n-tag--${props.variant}`, `n-tag--${props.size}`])
</script>

<template>
  <span :class="classes">
    <NIcon v-if="icon" :name="icon" :size="size === 'sm' ? 12 : 13" />
    <span v-if="$slots.default" class="n-tag__label"><slot /></span>
    <button v-if="closable" type="button" class="n-tag__close" aria-label="移除" @click="emit('close')">
      <NIcon name="close" :size="12" />
    </button>
  </span>
</template>

<style scoped>
.n-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-1);
  border-radius: var(--n-radius-pill);
  border: 1px solid transparent;
  font-weight: var(--n-weight-medium);
  line-height: 1;
  white-space: nowrap;
}

.n-tag--sm { height: 20px; padding: 0 var(--n-space-2); font-size: var(--n-text-xs); }
.n-tag--md { height: 26px; padding: 0 var(--n-space-3); font-size: var(--n-text-sm); }

.n-tag--default {
  background: var(--n-surface-soft);
  border-color: var(--n-line);
  color: var(--n-text-muted);
}
.n-tag--accent {
  background: var(--n-accent-soft);
  border-color: var(--n-accent-line);
  color: var(--n-accent-strong);
}
.n-tag--success {
  background: var(--n-success-soft);
  border-color: rgba(102, 217, 160, 0.28);
  color: var(--n-success);
}
.n-tag--warning {
  background: var(--n-warning-soft);
  border-color: rgba(247, 198, 106, 0.28);
  color: var(--n-warning);
}
.n-tag--danger {
  background: var(--n-danger-soft);
  border-color: rgba(255, 107, 107, 0.28);
  color: #ffb3b3;
}
.n-tag--outline {
  background: transparent;
  border-color: var(--n-line-strong);
  color: var(--n-text);
}

.n-tag__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: -2px;
  color: inherit;
  opacity: 0.6;
  transition: opacity var(--n-duration-fast) var(--n-ease);
}
@media (hover: hover) {
  .n-tag__close:hover { opacity: 1; }
}
</style>
