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
    :title="$t('pwa.installTitle')"
    :aria-label="$t('pwa.installTitle')"
    :loading="busy"
    @click="onInstall"
  />

  <NModal v-model="iosHelpOpen" :title="$t('pwa.iosTitle')" size="sm">
    <div class="pwa-ios">
      <p class="pwa-ios__lead">
        {{ $t('pwa.iosLead') }}
      </p>
      <ol class="pwa-ios__steps">
        <li>
          <span class="pwa-ios__ico"><NIcon name="share" :size="15" /></span>
          <span>{{ $t('pwa.iosStep1Lead') }} <strong>{{ $t('pwa.iosStep1Strong') }}</strong> {{ $t('pwa.iosStep1Tail') }}</span>
        </li>
        <li>
          <span class="pwa-ios__ico"><NIcon name="plus" :size="15" /></span>
          <span>{{ $t('pwa.iosStep2Lead') }} <strong>{{ $t('pwa.iosStep2Strong') }}</strong>{{ $t('pwa.iosStep2Tail') }}</span>
        </li>
        <li>
          <span class="pwa-ios__ico"><NIcon name="check" :size="15" /></span>
          <span>{{ $t('pwa.iosStep3Lead') }} <strong>{{ $t('pwa.iosStep3Strong') }}</strong> {{ $t('pwa.iosStep3Tail') }}</span>
        </li>
      </ol>
      <p class="pwa-ios__tip">{{ $t('pwa.iosTip') }}</p>
    </div>
    <template #footer>
      <NButton variant="ghost" @click="iosHelpOpen = false">{{ $t('pwa.gotIt') }}</NButton>
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
