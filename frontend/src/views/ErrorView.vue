<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell shell--center">
      <section class="panel err-card">
        <div class="err-code" aria-hidden="true">404</div>
        <h1 class="err-title">页面未找到</h1>
        <p class="err-desc">抱歉，您访问的页面不存在或已被移除。</p>
        <div class="err-actions">
          <router-link to="/" class="btn btn-primary">返回首页</router-link>
          <button type="button" class="btn btn-ghost" @click="goBack">返回上一页</button>
        </div>
        <div class="err-notes" aria-hidden="true">
          <span class="n">♪</span>
          <span class="n n2">♫</span>
          <span class="n n3">♪</span>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.shell--center {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: min(70vh, 720px);
}

.err-card {
  width: min(520px, 100%);
  text-align: center;
  padding: clamp(36px, 5vw, 52px) clamp(22px, 4vw, 36px);
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(105, 200, 223, 0.14), rgba(255, 255, 255, 0.05));
  box-shadow: var(--shadow);
  animation: err-in 0.5s var(--ease) both;
}

@keyframes err-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.err-code {
  font-size: clamp(4rem, 14vw, 6.5rem);
  font-weight: 800;
  line-height: 1;
  margin-bottom: 12px;
  background: linear-gradient(120deg, #fbcfe8, #9beaff, #c8f7ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.err-title {
  margin: 0 0 10px;
  font-size: clamp(1.25rem, 3vw, 1.6rem);
  font-weight: 800;
  color: var(--text);
}

.err-desc {
  margin: 0 0 28px;
  font-size: 0.95rem;
  color: var(--muted);
  line-height: 1.55;
}

.err-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}

.btn {
  font-family: inherit;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 22px;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  text-decoration: none;
  border: none;
  transition: filter 0.15s var(--ease), background 0.15s var(--ease);
}

.btn-primary {
  color: #0c0a14;
  background: linear-gradient(135deg, #9beaff, var(--accent2));
  box-shadow: 0 8px 24px rgba(105, 200, 223, 0.3);
}

.btn-primary:hover {
  filter: brightness(1.05);
}

.btn-ghost {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.16);
}

.btn-ghost:hover {
  background: rgba(255, 255, 255, 0.12);
}

.err-notes {
  position: relative;
  height: 48px;
  margin-top: 28px;
  opacity: 0.45;
}

.err-notes .n {
  position: absolute;
  font-size: 1.5rem;
  animation: float 3s ease-in-out infinite;
}

.err-notes .n {
  left: 18%;
  color: #fbcfe8;
}

.err-notes .n2 {
  left: auto;
  right: 18%;
  top: 6px;
  color: #9beaff;
  animation-delay: 0.8s;
}

.err-notes .n3 {
  left: 50%;
  transform: translateX(-50%);
  top: 2px;
  color: #c8f7ff;
  animation-delay: 1.6s;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.err-notes .n3 {
  animation-name: float-mid;
}

@keyframes float-mid {
  0%,
  100% {
    transform: translateX(-50%) translateY(0);
  }
  50% {
    transform: translateX(-50%) translateY(-8px);
  }
}

@media (max-width: 520px) {
  .err-actions {
    flex-direction: column;
  }

  .btn {
    width: 100%;
  }
}
</style>
