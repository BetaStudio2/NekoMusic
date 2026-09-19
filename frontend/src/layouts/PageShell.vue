<script setup>
/**
 * PageShell —— 页面内容容器
 * ------------------------------------------------------------
 * 统一页面的标题区、内容宽度与上下留白。
 * 可作为普通页面骨架，也可只用于「内容居中」的页面。
 */
import { computed } from 'vue'

const props = defineProps({
  /** 页面标题（可选，渲染为 h1） */
  title: { type: String, default: '' },
  /** 副标题 */
  subtitle: { type: String, default: '' },
  /** 内容最大宽度：narrow | default | wide | full */
  width: {
    type: String,
    default: 'default',
    validator: (v) => ['narrow', 'default', 'wide', 'full'].includes(v),
  },
  /** 内容水平 / 垂直居中（适合登录、错误页） */
  centered: { type: Boolean, default: false },
  /** 去除顶部留白 */
  flushTop: { type: Boolean, default: false },
})

const classes = computed(() => [
  'n-page',
  `n-page--${props.width}`,
  {
    'n-page--centered': props.centered,
    'n-page--flush-top': props.flushTop,
  },
])
</script>

<template>
  <div :class="classes">
    <header v-if="title || subtitle || $slots.header || $slots.actions" class="n-page__header">
      <div class="n-page__heading">
        <slot name="header">
          <h1 v-if="title" class="n-page__title">{{ title }}</h1>
          <p v-if="subtitle" class="n-page__subtitle">{{ subtitle }}</p>
        </slot>
      </div>
      <div v-if="$slots.actions" class="n-page__actions">
        <slot name="actions" />
      </div>
    </header>

    <slot />
  </div>
</template>

<style scoped>
.n-page {
  position: relative;
  z-index: var(--n-z-content);
  width: 100%;
  margin: 0 auto;
  padding: clamp(22px, 3.5vw, 40px) var(--n-content-gutter) 72px;
}

.n-page--narrow { max-width: 560px; }
.n-page--default { max-width: var(--n-shell-max); }
.n-page--wide { max-width: 1440px; }
.n-page--full { max-width: none; }

.n-page--flush-top { padding-top: 0; }

.n-page--centered {
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  min-height: min(78dvh, 880px);
  padding-top: clamp(28px, 6vh, 64px);
}

.n-page--centered > :not(.n-page__header) {
  width: 100%;
}

.n-page__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-8);
}

.n-page__title {
  font-size: clamp(1.6rem, 3vw, 2.2rem);
  line-height: var(--n-leading-tight);
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.n-page__subtitle {
  margin-top: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-md);
}

.n-page__actions {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  flex: none;
}
</style>
