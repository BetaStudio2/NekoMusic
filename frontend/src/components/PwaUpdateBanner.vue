<script setup>
/**
 * PwaUpdateBanner —— 新版本提示
 * ------------------------------------------------------------
 * 当 Service Worker 检测到新版本进入 waiting 时，在播放条上方浮出一条
 * 轻量提示；点「立即刷新」激活新 SW，SW 接管后自动刷新页面。
 * 用户可以「稍后」，本次会话内不再打扰。
 */
import { ref } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { usePwa } from '@/composables/usePwa'

const { needRefresh, applyUpdate } = usePwa()

const dismissed = ref(false)
</script>

<template>
  <Transition name="pwa-up">
    <div v-if="needRefresh && !dismissed" class="pwa-up" role="status" aria-live="polite">
      <span class="pwa-up__icon" aria-hidden="true"><NIcon name="refresh" :size="15" /></span>
      <span class="pwa-up__text">发现新版本</span>
      <button type="button" class="pwa-up__action" @click="applyUpdate">立即刷新</button>
      <button type="button" class="pwa-up__close" aria-label="稍后" @click="dismissed = true">
        <NIcon name="close" :size="14" />
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.pwa-up {
  position: fixed;
  left: 50%;
  /* 悬停在播放条上方；无播放条时仅是一个悬浮提示，不影响布局 */
  bottom: calc(var(--n-player-height) + var(--n-safe-bottom) + var(--n-space-3));
  transform: translateX(-50%);
  z-index: calc(var(--n-z-player) + 1);
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  max-width: calc(100vw - 2 * var(--n-space-4));
  padding: var(--n-space-2) var(--n-space-2) var(--n-space-2) var(--n-space-4);
  border: 1px solid var(--n-line-strong);
  border-radius: var(--n-radius-pill, 999px);
  background: var(--n-surface-strong);
  color: var(--n-text);
  box-shadow: var(--n-shadow-lg);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
}

.pwa-up__icon {
  display: inline-flex;
  color: var(--n-accent-strong);
}

.pwa-up__text {
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
}

.pwa-up__action {
  flex: none;
  padding: 4px 12px;
  border-radius: var(--n-radius-pill, 999px);
  background: var(--n-accent-soft, rgba(120, 220, 232, 0.16));
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  transition: background var(--n-duration-fast) var(--n-ease);
}
.pwa-up__action:hover {
  background: var(--n-surface-active);
}

.pwa-up__close {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--n-radius-circle, 50%);
  color: var(--n-text-muted);
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}
.pwa-up__close:hover {
  background: var(--n-surface-active);
  color: var(--n-text);
}

/* 过渡：自下方淡入 */
.pwa-up-enter-active,
.pwa-up-leave-active {
  transition: opacity var(--n-duration) var(--n-ease), transform var(--n-duration) var(--n-ease);
}
.pwa-up-enter-from,
.pwa-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}

@media (prefers-reduced-motion: reduce) {
  .pwa-up-enter-active,
  .pwa-up-leave-active {
    transition: opacity var(--n-duration-fast) linear;
  }
  .pwa-up-enter-from,
  .pwa-up-leave-to {
    transform: translateX(-50%);
  }
}
</style>
