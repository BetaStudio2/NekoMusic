<template>
  <span
    v-if="label"
    class="quality-badge"
    :class="`quality-badge--${tier}`"
    :title="$t('quality.title', { label })"
  >
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { t } from '@/i18n'

const props = defineProps({
  /** 后端 maxQuality：standard / hq / sq / hires，空值不展示 */
  quality: {
    type: String,
    default: ''
  }
})

const TIERS = {
  standard: { tier: 'standard', label: t('quality.standard') },
  hq: { tier: 'hq', label: t('quality.high') },
  sq: { tier: 'sq', label: t('quality.lossless') },
  hires: { tier: 'hires', label: 'Hi-Res' }
}

const matched = computed(() => TIERS[String(props.quality || '').trim().toLowerCase()] || null)
const tier = computed(() => matched.value?.tier || '')
const label = computed(() => matched.value?.label || '')
</script>

<style scoped>
.quality-badge {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  height: 16px;
  padding: 0 5px;
  border: 1px solid currentColor;
  border-radius: 5px;
  font-size: 10px;
  font-weight: var(--n-weight-semibold);
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  opacity: 0.9;
}

.quality-badge--standard {
  color: #8b93a1;
}

.quality-badge--hq {
  color: #3fae7d;
}

.quality-badge--sq {
  color: #d99a2b;
}

.quality-badge--hires {
  color: #e0567a;
}
</style>
