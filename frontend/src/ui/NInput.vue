<script setup>
/**
 * NInput —— 基础输入框
 * ------------------------------------------------------------
 * 支持前后置图标、清空、密码显隐、错误态、文本域(textarea)。
 * 使用 defineModel 双向绑定（Vue 3.4+）。
 */
import { computed, ref, useAttrs } from 'vue'
import NIcon from '@/icons/NIcon.vue'

defineOptions({ inheritAttrs: false })

const model = defineModel({ type: [String, Number], default: '' })

const props = defineProps({
  /** 输入类型 */
  type: { type: String, default: 'text' },
  /** 占位符 */
  placeholder: { type: String, default: '' },
  /** 前置图标名 */
  icon: { type: String, default: '' },
  /** 尺寸 */
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
  /** 可清空 */
  clearable: { type: Boolean, default: false },
  /** 错误态 */
  invalid: { type: Boolean, default: false },
  /** 禁用 */
  disabled: { type: Boolean, default: false },
  /** 使用文本域 */
  textarea: { type: Boolean, default: false },
  /** 文本域行数 */
  rows: { type: Number, default: 4 },
})

const emit = defineEmits(['clear', 'enter'])

const attrs = useAttrs()
const passwordVisible = ref(false)
const inputRef = ref(null)

/** 透出原生控件方法，便于父组件在编辑场景聚焦/全选 */
function focus() {
  inputRef.value?.focus?.()
}
function select() {
  inputRef.value?.select?.()
}
defineExpose({ focus, select })

const isPassword = computed(() => props.type === 'password')

const actualType = computed(() => {
  if (isPassword.value) return passwordVisible.value ? 'text' : 'password'
  return props.type
})

const showClear = computed(
  () => props.clearable && !props.disabled && model.value !== '' && model.value !== null && model.value !== undefined
)

// 非 class/style 的属性透传到内部控件
const innerAttrs = computed(() => {
  const { class: _c, style: _s, ...rest } = attrs
  return rest
})

function clear() {
  model.value = ''
  emit('clear')
}

function onEnter(e) {
  emit('enter', e)
}
</script>

<template>
  <div
    class="n-input"
    :class="[
      `n-input--${size}`,
      {
        'n-input--invalid': invalid,
        'n-input--disabled': disabled,
        'n-input--textarea': textarea,
      },
      attrs.class,
    ]"
    :style="attrs.style"
  >
    <NIcon v-if="icon" :name="icon" class="n-input__lead" :size="size === 'sm' ? 14 : 16" />

    <textarea
      v-if="textarea"
      ref="inputRef"
      v-model="model"
      class="n-input__control"
      :placeholder="placeholder"
      :disabled="disabled"
      :rows="rows"
      v-bind="innerAttrs"
    />
    <input
      v-else
      ref="inputRef"
      v-model="model"
      class="n-input__control"
      :type="actualType"
      :placeholder="placeholder"
      :disabled="disabled"
      v-bind="innerAttrs"
      @keyup.enter="onEnter"
    />

    <button
      v-if="showClear"
      type="button"
      class="n-input__affix n-input__clear"
      aria-label="清空"
      tabindex="-1"
      @click="clear"
    >
      <NIcon name="close" :size="14" />
    </button>

    <button
      v-if="isPassword"
      type="button"
      class="n-input__affix"
      :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
      tabindex="-1"
      @click="passwordVisible = !passwordVisible"
    >
      <NIcon :name="passwordVisible ? 'eye-off' : 'eye'" :size="16" />
    </button>
  </div>
</template>

<style scoped>
.n-input {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  background: var(--n-surface-soft);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  color: var(--n-text);
  transition:
    border-color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease),
    box-shadow var(--n-duration-fast) var(--n-ease);
}

.n-input:focus-within {
  border-color: var(--n-accent-line);
  background: var(--n-surface-hover);
  box-shadow: var(--n-shadow-glow);
}

.n-input--invalid {
  border-color: rgba(255, 107, 107, 0.42);
}

.n-input--invalid:focus-within {
  box-shadow: 0 0 0 4px var(--n-danger-soft);
}

.n-input--disabled {
  opacity: 0.55;
}

/* ===== 尺寸 ===== */
.n-input--sm { height: 32px; padding: 0 var(--n-space-3); }
.n-input--md { height: 40px; padding: 0 var(--n-space-4); }
.n-input--lg { height: 48px; padding: 0 var(--n-space-5); font-size: var(--n-text-md); }

.n-input--textarea {
  height: auto;
  align-items: flex-start;
  padding: var(--n-space-3) var(--n-space-4);
}

.n-input__control {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: inherit;
  font-size: inherit;
  line-height: var(--n-leading-normal);
}

.n-input--textarea .n-input__control {
  resize: vertical;
  min-height: 2lh;
}

.n-input__control::placeholder {
  color: var(--n-text-faint);
}

.n-input__lead,
.n-input__affix {
  flex: none;
  color: var(--n-text-faint);
}

.n-input__affix {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--n-radius-xs);
  position: relative;

/* 触摸设备：22px 太小，热区外扩到 --n-tap-min（视觉不变） */
@media (hover: none) and (pointer: coarse) {
  .n-input__affix::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: max(100%, var(--n-tap-min, 44px));
    height: max(100%, var(--n-tap-min, 44px));
    transform: translate(-50%, -50%);
  }
}
  transition: color var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .n-input__affix:hover {
    color: var(--n-text);
    background: var(--n-surface-active);
  }
}
</style>
