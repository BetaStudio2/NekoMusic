<script setup>
/**
 * AppShell —— 应用外层骨架
 * ------------------------------------------------------------
 * 负责编排「环境光 + 顶栏 + 内容 + 播放器 + 底栏」的层次与占位。
 * 具体顶栏 / 播放器 / 底栏通过具名插槽注入，便于逐步迁移旧组件。
 *
 * 内容区默认使用 PageShell 的宽度规则；若页面需要全幅（如首页 Hero），
 * 传 flush 由页面自行控制内边距。
 */
import AmbientBackdrop from './AmbientBackdrop.vue'

defineProps({
  /** 内容区是否全幅（不套 PageShell 的内边距） */
  flush: { type: Boolean, default: false },
  /** 是否显示环境光背景 */
  ambient: { type: Boolean, default: true },
  /** 是否有播放器占位（用于底部留白） */
  hasPlayer: { type: Boolean, default: false },
  /** 是否显示底栏 */
  hasFooter: { type: Boolean, default: true },
})
</script>

<template>
  <div class="n-shell" :class="{ 'n-shell--has-player': hasPlayer }">
    <AmbientBackdrop v-if="ambient" />

    <header v-if="$slots.header" class="n-shell__header">
      <slot name="header" />
    </header>

    <main class="n-shell__main" :class="{ 'n-shell__main--flush': flush }">
      <div :class="flush ? 'n-shell__content n-shell__content--flush' : 'n-shell__content'">
        <slot />
      </div>
    </main>

    <div v-if="$slots.player" class="n-shell__player">
      <slot name="player" />
    </div>

    <footer v-if="hasFooter && $slots.footer" class="n-shell__footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<style scoped>
.n-shell {
  position: relative;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  color: var(--n-text);
}

.n-shell__header {
  position: sticky;
  top: 0;
  z-index: var(--n-z-header);
}

.n-shell__main {
  flex: 1;
  position: relative;
  z-index: var(--n-z-content);
  width: 100%;
}

.n-shell__content {
  width: 100%;
  max-width: var(--n-shell-max);
  margin: 0 auto;
  padding: clamp(22px, 3.5vw, 40px) var(--n-content-gutter) 72px;
}

.n-shell__content--flush {
  max-width: none;
  padding: 0;
}

/* 播放器悬浮于底部时，给内容留出空间 */
.n-shell--has-player .n-shell__content {
  padding-bottom: calc(var(--n-player-height) + var(--n-space-8));
}

.n-shell__player {
  position: sticky;
  bottom: 0;
  z-index: var(--n-z-player);
}

.n-shell__footer {
  position: relative;
  z-index: var(--n-z-content);
}
</style>
