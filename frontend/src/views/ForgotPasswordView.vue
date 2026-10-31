<script setup>
/**
 * 找回密码入口。
 *
 * <p>密码和邮箱验证码均由 NekoAuth 保存与校验，本页只负责跳转到统一账号中心，
 * 不再调用 NekoMusic 的旧验证码 / 重置密码接口。</p>
 */
import { onMounted } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard } from '@/ui'
import { AmbientBackdrop, PageShell } from '@/layouts'

const target = '/sso/nekocore/forgot-password'

onMounted(() => {
  window.location.replace(target)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <NCard pad="lg" class="forgot-redirect">
      <span class="forgot-redirect__icon"><NIcon name="mail" :size="28" /></span>
      <h1 class="forgot-redirect__title">正在前往 NekoAuth</h1>
      <p class="forgot-redirect__desc">密码重置已统一交给 NekoAuth 处理。</p>
      <NButton variant="primary" icon="arrow-right" :href="target">立即前往</NButton>
    </NCard>
  </PageShell>
</template>

<style scoped>
.forgot-redirect {
  width: min(520px, 100%);
  padding: clamp(36px, 6vw, 52px) clamp(24px, 5vw, 40px);
  text-align: center;
}

.forgot-redirect__icon {
  display: inline-grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: var(--n-space-4);
  border-radius: 50%;
  color: var(--n-accent-strong);
  background: color-mix(in srgb, var(--n-accent) 12%, transparent);
}

.forgot-redirect__title {
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.25rem, 3vw, 1.6rem);
}

.forgot-redirect__desc {
  margin: 0 0 var(--n-space-6);
  color: var(--n-text-muted);
}
</style>
