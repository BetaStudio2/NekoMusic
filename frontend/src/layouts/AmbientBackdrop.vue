<script setup>
/**
 * AmbientBackdrop —— 环境光背景层
 * ------------------------------------------------------------
 * 页面的装饰性背景（光斑 + 网格），纯装饰、不拦截交互。
 * 由 AppShell 统一提供，页面无需重复实现。
 */
</script>

<template>
  <div class="n-ambient" aria-hidden="true">
    <span class="n-ambient__blob n-ambient__blob--a" />
    <span class="n-ambient__blob n-ambient__blob--b" />
    <span class="n-ambient__blob n-ambient__blob--c" />
    <span class="n-ambient__grid" />
  </div>
</template>

<style scoped>
.n-ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: var(--n-z-ambient);
  overflow: hidden;
}

.n-ambient__blob {
  position: absolute;
  border-radius: var(--n-radius-circle);
  filter: blur(88px);
  opacity: 0.28;
  animation: n-ambient-float 22s var(--n-ease) infinite;
}

.n-ambient__blob--a {
  width: 400px;
  height: 400px;
  background: rgba(105, 200, 223, 0.34);
  top: -120px;
  right: -80px;
}

.n-ambient__blob--b {
  width: 340px;
  height: 340px;
  background: rgba(120, 145, 164, 0.22);
  bottom: -60px;
  left: -60px;
  animation-delay: -8s;
}

.n-ambient__blob--c {
  width: 260px;
  height: 260px;
  background: rgba(105, 200, 223, 0.16);
  top: 38%;
  left: 30%;
  animation-delay: -14s;
}

.n-ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.24;
  background-image:
    linear-gradient(rgba(143, 174, 198, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(143, 174, 198, 0.05) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: radial-gradient(ellipse 80% 55% at 50% 15%, black, transparent);
}

@keyframes n-ambient-float {
  0%,
  100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-16px, 12px) scale(1.04); }
}

@media (prefers-reduced-motion: reduce) {
  .n-ambient__blob { animation: none; }
}
</style>
