<script setup>
/**
 * NSpinner —— 加载指示器
 */
import { computed } from 'vue'
import NIcon from '@/icons/NIcon.vue'

const props = defineProps({
  size: { type: [Number, String], default: 20 },
  label: { type: String, default: '加载中' },
  /** 是否占满父容器居中 */
  center: { type: Boolean, default: false },
})

const sizeValue = computed(() => props.size)
</script>

<template>
  <div class="n-spinner" :class="{ 'n-spinner--center': center }" role="status">
    <NIcon name="loader-circle" :size="sizeValue" class="n-spinner__icon" />
    <span class="n-visually-hidden">{{ label }}</span>
  </div>
</template>

<style scoped>
.n-spinner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--n-accent);
}

.n-spinner--center {
  display: flex;
  width: 100%;
  padding: var(--n-space-10) 0;
}

.n-spinner__icon {
  animation: n-spinner-spin 0.9s linear infinite;
}

@keyframes n-spinner-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .n-spinner__icon { animation-duration: 2s; }
}
</style>
