<script setup>
/**
 * NModal —— 模态对话框
 * ------------------------------------------------------------
 * 使用 Teleport 到 body，支持标题、页脚插槽、ESC 关闭、遮罩点击关闭。
 * 打开时锁定 body 滚动。
 */
import { computed, watch, onBeforeUnmount } from 'vue'
import NIcon from '@/icons/NIcon.vue'

const open = defineModel({ type: Boolean, default: false })

const props = defineProps({
  title: { type: String, default: '' },
  /** 宽度预设 */
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg', 'full'].includes(v),
  },
  /** 点击遮罩是否关闭 */
  maskClosable: { type: Boolean, default: true },
  /** ESC 是否关闭 */
  escClosable: { type: Boolean, default: true },
  /** 显示右上角关闭按钮 */
  showClose: { type: Boolean, default: true },
})

const emit = defineEmits(['close'])

function close() {
  open.value = false
  emit('close')
}

function onMaskClick() {
  if (props.maskClosable) close()
}

function onKeydown(e) {
  if (e.key === 'Escape' && props.escClosable) close()
}

let prevOverflow = ''

watch(
  open,
  (val) => {
    if (typeof document === 'undefined') return
    if (val) {
      prevOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown)
    } else {
      document.body.style.overflow = prevOverflow
      document.removeEventListener('keydown', onKeydown)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  if (typeof document === 'undefined') return
  document.body.style.overflow = prevOverflow
  document.removeEventListener('keydown', onKeydown)
})

const panelClass = computed(() => ['n-modal__panel', `n-modal__panel--${props.size}`])
</script>

<template>
  <Teleport to="body">
    <Transition name="n-modal">
      <div v-if="open" class="n-modal" role="dialog" aria-modal="true" :aria-label="title || undefined">
        <div class="n-modal__mask" @click="onMaskClick" />
        <div :class="panelClass">
          <header v-if="title || showClose || $slots.header" class="n-modal__header">
            <div class="n-modal__title">
              <slot name="header">{{ title }}</slot>
            </div>
            <button v-if="showClose" type="button" class="n-modal__close" aria-label="关闭" @click="close">
              <NIcon name="close" :size="18" />
            </button>
          </header>

          <div class="n-modal__body">
            <slot />
          </div>

          <footer v-if="$slots.footer" class="n-modal__footer">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.n-modal {
  position: fixed;
  inset: 0;
  z-index: var(--n-z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--n-space-4);
}

.n-modal__mask {
  position: absolute;
  inset: 0;
  background: rgba(3, 6, 10, 0.66);
  backdrop-filter: var(--n-blur-sm);
  -webkit-backdrop-filter: var(--n-blur-sm);
}

.n-modal__panel {
  position: relative;
  width: 100%;
  max-height: calc(100dvh - 2 * var(--n-space-8));
  display: flex;
  flex-direction: column;
  background: var(--n-surface-strong);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  box-shadow: var(--n-shadow-lg);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
}

.n-modal__panel--sm { max-width: 380px; }
.n-modal__panel--md { max-width: 520px; }
.n-modal__panel--lg { max-width: 760px; }
.n-modal__panel--full { max-width: 1100px; }

.n-modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-4);
  padding: var(--n-space-5) var(--n-space-6);
  border-bottom: 1px solid var(--n-line-subtle);
}

.n-modal__title {
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  min-width: 0;
}

.n-modal__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--n-radius-circle);
  color: var(--n-text-muted);
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}
@media (hover: hover) {
  .n-modal__close:hover {
    background: var(--n-surface-active);
    color: var(--n-text);
  }
}

.n-modal__body {
  padding: var(--n-space-6);
  overflow-y: auto;
  flex: 1;
}

.n-modal__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--n-space-3);
  padding: var(--n-space-4) var(--n-space-6);
  border-top: 1px solid var(--n-line-subtle);
}

/* ===== 过渡 ===== */
.n-modal-enter-active,
.n-modal-leave-active {
  transition: opacity var(--n-duration) var(--n-ease);
}
.n-modal-enter-active .n-modal__panel,
.n-modal-leave-active .n-modal__panel {
  transition: transform var(--n-duration) var(--n-ease), opacity var(--n-duration) var(--n-ease);
}
.n-modal-enter-from,
.n-modal-leave-to {
  opacity: 0;
}
.n-modal-enter-from .n-modal__panel,
.n-modal-leave-to .n-modal__panel {
  transform: translateY(12px) scale(0.98);
  opacity: 0;
}
</style>
