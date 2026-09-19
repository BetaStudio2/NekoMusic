<script setup>
/**
 * ErrorView —— 404 页面
 * ------------------------------------------------------------
 * 黑偏青 + 圆角矩形；无高亮条。
 */
import { useRouter } from 'vue-router'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="narrow" centered>
    <NCard pad="lg" class="err">
      <p class="err__code" aria-hidden="true">404</p>
      <h1 class="err__title">页面未找到</h1>
      <p class="err__desc">抱歉，您访问的页面不存在或已被移除。</p>

      <div class="err__actions">
        <NButton variant="primary" icon="home" to="/">返回首页</NButton>
        <NButton variant="secondary" icon="arrow-left" @click="goBack">返回上一页</NButton>
      </div>

      <div class="err__notes" aria-hidden="true">
        <NIcon name="music" :size="20" class="err__note err__note--a" />
        <NIcon name="music-2" :size="16" class="err__note err__note--b" />
        <NIcon name="audio-lines" :size="18" class="err__note err__note--c" />
      </div>
    </NCard>
  </PageShell>
</template>

<style scoped>
.err {
  width: min(520px, 100%);
  text-align: center;
  padding: clamp(36px, 5vw, 52px) clamp(22px, 4vw, 36px);
}

.err__code {
  margin: 0 0 var(--n-space-3);
  font-size: clamp(4rem, 14vw, 6.5rem);
  font-weight: var(--n-weight-bold);
  line-height: 1;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.err__title {
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.25rem, 3vw, 1.6rem);
  font-weight: var(--n-weight-bold);
}

.err__desc {
  margin: 0 0 var(--n-space-8);
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.err__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  justify-content: center;
}

.err__notes {
  position: relative;
  height: 40px;
  margin-top: var(--n-space-8);
  color: var(--n-accent);
  opacity: 0.4;
}

.err__note {
  position: absolute;
  animation: err-float 3s var(--n-ease-in-out) infinite;
}

.err__note--a {
  left: 20%;
  top: 4px;
}
.err__note--b {
  right: 20%;
  top: 10px;
  animation-delay: 0.8s;
}
.err__note--c {
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  animation-delay: 1.6s;
}

@keyframes err-float {
  0%,
  100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.err__note--c {
  animation-name: err-float-mid;
}

@keyframes err-float-mid {
  0%,
  100% { transform: translateX(-50%) translateY(0); }
  50% { transform: translateX(-50%) translateY(-8px); }
}

@media (prefers-reduced-motion: reduce) {
  .err__note { animation: none; }
}

@media (max-width: 520px) {
  .err__actions :deep(.n-btn) {
    width: 100%;
    justify-content: center;
  }
}
</style>
