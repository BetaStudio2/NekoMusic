<script setup>
/**
 * AuthDialog —— 全局登录 / 注册弹窗
 * ------------------------------------------------------------
 * 登录不再是独立页面：任何入口调用 openAuthDialog() 即可弹出。
 * 卡片横向两栏（左扫码、右登录/注册），窄屏自动只留表单栏。
 */
import { watch, onUnmounted } from 'vue'
import { useAuthDialog } from '@/composables/useAuthDialog'
import AuthPanel from '@/layouts/AuthPanel.vue'
import NIcon from '@/icons/NIcon.vue'

const { isOpen, initialTab, closeAuthDialog } = useAuthDialog()

function onKeydown(event) {
  if (event.key === 'Escape') closeAuthDialog()
}

/** 打开时锁滚动 + 监听 Esc；关闭时全部还原 */
watch(isOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) {
    window.addEventListener('keydown', onKeydown)
  } else {
    window.removeEventListener('keydown', onKeydown)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="auth-dialog">
      <div v-if="isOpen" class="auth-dialog" @mousedown.self="closeAuthDialog">
        <div class="auth-dialog__card" role="dialog" aria-modal="true" aria-label="登录 Neko歌姬计划">
          <button type="button" class="auth-dialog__close" aria-label="关闭" @click="closeAuthDialog">
            <NIcon name="close" :size="18" />
          </button>
          <AuthPanel
            :initial-tab="initialTab"
            @authenticated="closeAuthDialog"
            @close="closeAuthDialog"
          />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.auth-dialog {
  position: fixed;
  inset: 0;
  z-index: 10040;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  overflow-y: auto;
  box-sizing: border-box;
  background: rgba(3, 8, 10, 0.62);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.auth-dialog__card {
  position: relative;
  width: 100%;
  max-width: 680px;
  min-width: 0;
  border-radius: var(--n-radius-xl);
  box-shadow: var(--n-shadow-lg);
}

.auth-dialog__close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: var(--n-radius-circle);
  background: transparent;
  color: var(--n-text-muted);
  cursor: pointer;
  transition:
    color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .auth-dialog__close:hover {
    color: var(--n-accent-strong);
    background: var(--n-accent-soft);
  }
}

.auth-dialog-enter-active,
.auth-dialog-leave-active {
  transition: opacity var(--n-duration) var(--n-ease);
}

.auth-dialog-enter-active .auth-dialog__card,
.auth-dialog-leave-active .auth-dialog__card {
  transition:
    transform var(--n-duration) var(--n-ease),
    opacity var(--n-duration) var(--n-ease);
}

.auth-dialog-enter-from,
.auth-dialog-leave-to {
  opacity: 0;
}

.auth-dialog-enter-from .auth-dialog__card,
.auth-dialog-leave-to .auth-dialog__card {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}

@media (prefers-reduced-motion: reduce) {
  .auth-dialog-enter-active,
  .auth-dialog-leave-active,
  .auth-dialog-enter-active .auth-dialog__card,
  .auth-dialog-leave-active .auth-dialog__card {
    transition: none;
  }
}
</style>
