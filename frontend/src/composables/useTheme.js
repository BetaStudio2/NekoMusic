/**
 * useTheme —— 主题管理
 * ------------------------------------------------------------
 * 当前仅提供深色主题（与旧版一致），但结构上预留给未来的浅色主题。
 * 通过 <html data-theme="..."> 切换，令牌在 tokens.css 中按属性覆盖。
 */
import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'neko:theme'
const THEMES = ['dark', 'light']

/** 模块级单例状态：所有组件共享同一份主题 */
const theme = ref(readInitialTheme())

function readInitialTheme() {
  if (typeof localStorage !== 'undefined') {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && THEMES.includes(saved)) return saved
  }
  return 'dark'
}

function applyTheme(value) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', value)
}

// 立即应用一次（模块被引入时）
applyTheme(theme.value)

watch(theme, (val) => {
  applyTheme(val)
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, val)
  }
})

export function useTheme() {
  function setTheme(value) {
    if (THEMES.includes(value)) theme.value = value
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  return {
    theme,
    isDark: computed(() => theme.value === 'dark'),
    setTheme,
    toggleTheme,
    themes: THEMES,
  }
}
