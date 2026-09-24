<script setup>
import { isMobileDevice } from '@/utils/mobile.js'
import { usePwaInstall } from '@/utils/pwa.js'
import NIcon from '@/icons/NIcon.vue'

const { canInstall, install } = usePwaInstall()

async function installPwa() {
  await install()
}
</script>

<template>
  <div v-if="isMobileDevice() && canInstall" class="pwa-prompt">
    <NIcon name="download" :size="17" />
    <span class="pwa-prompt__text">安装到手机</span>
    <button type="button" class="pwa-prompt__install" @click="installPwa">安装</button>
  </div>
</template>

<style scoped>
.pwa-prompt {
  position: fixed;
  right: max(12px, env(safe-area-inset-right));
  bottom: max(12px, env(safe-area-inset-bottom));
  z-index: var(--n-z-toast, 1000);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface);
  color: var(--n-text);
  box-shadow: 0 10px 30px rgb(0 0 0 / 24%);
}

.pwa-prompt__text {
  font-size: var(--n-text-sm);
  white-space: nowrap;
}

.pwa-prompt__install {
  min-height: var(--n-tap-min);
  padding: 0 12px;
  border-radius: var(--n-radius-xs);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  font-weight: var(--n-weight-semibold);
}
</style>
