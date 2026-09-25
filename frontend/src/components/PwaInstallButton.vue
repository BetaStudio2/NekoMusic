<script setup>
/**
 * PwaInstallButton —— 「安装到桌面」入口
 * ------------------------------------------------------------
 * 仅在可安装（Chromium 的 beforeinstallprompt）或 iOS Safari 需手动添加时出现；
 * 已安装 / 不支持时完全不渲染，不占用顶栏空间。
 */
import { ref } from 'vue'
import { NButton, NModal } from '@/ui'
import NIcon from '@/icons/NIcon.vue'
import { usePwa } from '@/composables/usePwa'

const { showInstall, canInstall, iosManualInstall, promptInstall } = usePwa()

const iosHelpOpen = ref(false)
const busy = ref(false)

async function onInstall() {
  // Chromium：直接弹原生安装框
  if (canInstall.value) {
    busy.value = true
    try {
      await promptInstall()
    } finally {
      busy.value = false
    }
    return
  }
  // iOS Safari：没有原生安装 API，改为展示手动步骤
  if (iosManualInstall.value) iosHelpOpen.value = true
}
</script>

<template>
  <NButton
    v-if="showInstall"
    variant="ghost"
    size="sm"
    icon="smartphone"
    title="安装到桌面"
    aria-label="安装到桌面"
    :loading="busy"
    @click="onInstall"
  />

  <NModal v-model="iosHelpOpen" title="安装到主屏幕" size="sm">
    <div class="pwa-ios">
      <p class="pwa-ios__lead">
        iOS 版 Safari 不支持一键安装，按以下步骤即可添加：
      </p>
      <ol class="pwa-ios__steps">
        <li>
          <span class="pwa-ios__ico"><NIcon name="share" :size="15" /></span>
          <span>点击底部工具栏的 <strong>分享</strong> 按钮</span>
        </li>
        <li>
          <span class="pwa-ios__ico"><NIcon name="plus" :size="15" /></span>
          <span>在菜单中选择 <strong>添加到主屏幕</strong></span>
        </li>
        <li>
          <span class="pwa-ios__ico"><NIcon name="check" :size="15" /></span>
          <span>点击右上角 <strong>添加</strong> 完成</span>
        </li>
      </ol>
      <p class="pwa-ios__tip">添加后即可像 App 一样全屏打开 Neko 音乐。</p>
    </div>
    <template #footer>
      <NButton variant="ghost" @click="iosHelpOpen = false">知道了</NButton>
    </template>
  </NModal>
</template>

<style scoped>
.pwa-ios__lead {
  margin: 0 0 var(--n-space-4);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: 1.6;
}

.pwa-ios__steps {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: var(--n-space-3);
}

.pwa-ios__steps li {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  font-size: var(--n-text-sm);
  line-height: 1.5;
}

.pwa-ios__steps strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.pwa-ios__ico {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--n-radius-sm, 10px);
  background: var(--n-surface-active);
  color: var(--n-accent-strong);
}

.pwa-ios__tip {
  margin: var(--n-space-4) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs, 0.75rem);
  line-height: 1.6;
}
</style>
