<script setup>
/**
 * MobileAppBanner —— 手机端「下载 APP」软引导横幅
 * ------------------------------------------------------------
 * 取代原先的路由级【硬重定向】：手机访问首页 / 搜索 / 收藏等会被直接
 * 踢到 /download，导致整站手机端不可用。现在改为可关闭的横幅 ——
 * 不阻断浏览，App 导流仍在（真正的「拉起 App」由 utils/nativeAppOpen 负责）。
 *
 * 关闭状态记在 localStorage，DISMISS_TTL_MS 之后会再次出现：
 * 永久不再显示等于放弃导流，每次刷新都弹又太烦，7 天是折中。
 */
import { ref } from 'vue'
import NIcon from '@/icons/NIcon.vue'

defineProps({
  /** bar = 页面顶部通栏；card = 全屏播放页内部的圆角卡片 */
  variant: {
    type: String,
    default: 'bar',
    validator: (v) => ['bar', 'card'].includes(v),
  },
})

const STORAGE_KEY = 'neko-mobile-app-banner-dismissed-at'
/** 关闭后多久重新出现 */
const DISMISS_TTL_MS = 7 * 24 * 60 * 60 * 1000

function isDismissed() {
  try {
    const ts = Number(localStorage.getItem(STORAGE_KEY))
    return !!ts && Date.now() - ts < DISMISS_TTL_MS
  } catch {
    return false
  }
}

const visible = ref(!isDismissed())

function dismiss() {
  visible.value = false
  try {
    localStorage.setItem(STORAGE_KEY, String(Date.now()))
  } catch {
    // 隐私模式等场景存不了：只在本次会话内隐藏
  }
}
</script>

<template>
  <Transition name="mab">
    <div
      v-if="visible"
      class="mab"
      :class="`mab--${variant}`"
      role="region"
      aria-label="下载 APP 提示"
    >
      <NIcon name="download" :size="16" class="mab__icon" />
      <span class="mab__text">下载 APP 体验更好</span>
      <RouterLink to="/download" class="mab__btn">立即下载</RouterLink>
      <button type="button" class="mab__close" aria-label="关闭提示" @click="dismiss">
        <NIcon name="close" :size="16" />
      </button>
    </div>
  </Transition>
</template>

<style scoped>
.mab {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  color: var(--n-text);
  font-size: var(--n-text-sm);
}

/* ===== 页面顶部通栏 ===== */
.mab--bar {
  padding: var(--n-space-2) var(--n-content-gutter);
  background: var(--n-accent-soft);
  border-bottom: 1px solid var(--n-accent-line);
}

/* ===== 全屏播放页内的卡片（那里是 fixed 覆盖层，通栏会被盖住） ===== */
.mab--card {
  margin: 0 var(--n-space-4);
  padding: var(--n-space-2) var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface);
  color: var(--n-text-muted);
}

.mab__icon {
  flex: none;
  color: var(--n-accent);
}

.mab__text {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mab__btn {
  flex: none;
  margin-left: auto;
  padding: 6px 14px;
  border-radius: var(--n-radius-control);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  font-weight: var(--n-weight-semibold);
  font-size: var(--n-text-sm);
  text-decoration: none;
}

.mab--card .mab__btn {
  background: transparent;
  color: var(--n-accent);
  padding: 6px 8px;
}

.mab__close {
  flex: none;
  display: grid;
  place-items: center;
  width: var(--n-tap-min);
  height: var(--n-tap-min);
  /* 视觉上紧凑，热区仍是 44px（触摸设备友好） */
  margin: -8px;
  border-radius: var(--n-radius-xs);
  color: var(--n-text-muted);
}

@media (hover: hover) {
  .mab__close:hover {
    color: var(--n-text);
    background: var(--n-surface-hover);
  }
}

.mab__close:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--n-accent-soft);
}

/* 进出场：轻微上浮淡入，避免页面加载时突然「跳」一下 */
.mab-enter-active,
.mab-leave-active {
  transition: opacity var(--n-duration) var(--n-ease),
    transform var(--n-duration) var(--n-ease);
}

.mab-enter-from,
.mab-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (prefers-reduced-motion: reduce) {
  .mab-enter-active,
  .mab-leave-active {
    transition: none;
  }
}
</style>
