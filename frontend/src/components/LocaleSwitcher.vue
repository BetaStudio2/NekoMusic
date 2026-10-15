<script setup>
/**
 * LocaleSwitcher —— 语言切换器
 * ------------------------------------------------------------
 * 顶栏右侧的下拉：显示当前语言，展开可选站内全部语言（与后端注册表一致）。
 * 选择结果落盘到 localStorage（见 @/i18n），下次进站沿用。
 *
 * 交互约定：点外部关闭、Esc 关闭、键盘可用（menu / menuitemradio + aria-checked）。
 * 窄屏只留图标（语言名收进 aria-label），与顶栏其它入口的收缩规则一致。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from '@/i18n'
import NIcon from '@/icons/NIcon.vue'

const { locale, setLocale, supportedLocales, localeName } = useI18n()

const open = ref(false)
const root = ref(null)

const currentName = computed(() => localeName(locale.value))

function toggle() {
  open.value = !open.value
}

function choose(tag) {
  setLocale(tag)
  open.value = false
}

function onDocumentPointerDown(event) {
  if (!open.value) return
  if (root.value && !root.value.contains(event.target)) open.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape' && open.value) open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="root" class="locale-switcher">
    <button
      type="button"
      class="locale-switcher__trigger"
      :aria-label="$t('nav.language')"
      :title="currentName"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="toggle"
    >
      <NIcon name="languages" :size="18" />
    </button>

    <div v-if="open" class="locale-switcher__menu" role="menu">
      <button
        v-for="tag in supportedLocales"
        :key="tag"
        type="button"
        role="menuitemradio"
        class="locale-switcher__item"
        :class="{ 'locale-switcher__item--active': tag === locale }"
        :aria-checked="tag === locale"
        :lang="tag"
        @click="choose(tag)"
      >
        <span class="locale-switcher__name">{{ localeName(tag) }}</span>
        <NIcon v-if="tag === locale" name="check" :size="14" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.locale-switcher {
  position: relative;
  display: inline-flex;
}

/* 触发按钮：与顶栏其它图标入口同尺寸，触摸热区抬到 32px */
.locale-switcher__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text-muted);
  cursor: pointer;
  transition:
    color var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .locale-switcher__trigger:hover {
    color: var(--n-accent-strong);
    border-color: var(--n-accent);
  }
}

.locale-switcher__trigger:focus-visible {
  outline: 2px solid var(--n-accent);
  outline-offset: 2px;
}

.locale-switcher__menu {
  position: absolute;
  top: calc(100% + var(--n-space-2));
  right: 0;
  z-index: var(--n-z-overlay);
  min-width: 148px;
  padding: var(--n-space-1);
  display: flex;
  flex-direction: column;
  gap: 2px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-strong);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
  box-shadow: var(--n-shadow-lg);
}

.locale-switcher__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-3);
  padding: var(--n-space-2) var(--n-space-3);
  border: 0;
  border-radius: var(--n-radius-sm);
  background: transparent;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  text-align: left;
  cursor: pointer;
  transition: background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .locale-switcher__item:hover {
    background: var(--n-surface-hover);
    color: var(--n-text);
  }
}

.locale-switcher__item--active {
  color: var(--n-accent-strong);
}

.locale-switcher__name {
  white-space: nowrap;
}
</style>
