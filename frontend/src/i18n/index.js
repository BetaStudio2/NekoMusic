/**
 * i18n —— 前端多语言
 * ------------------------------------------------------------
 * 为什么不用 vue-i18n：本站只需要「按 key 查表 + 回退链 + 切换语言」三件事，自己实现不到
 * 100 行，而新增一个运行期依赖要跟着升级、要处理它的插件注册与 SSR/打包细节。语言清单与
 * 回退链刻意与后端 `i18n/languages.properties` 保持一一对应，两端文案不会各说各话。
 *
 * 用法：
 *  - 模板里直接用 `$t('home.title')`（已在 main.js 注册为全局属性，组件无需 import）；
 *  - 脚本里 `import { t } from '@/i18n'`（吐司、router、请求层等）；
 *  - 需要「语言本身的响应式值」（如语言选择器）用 `useI18n()` 的 `locale`。
 *
 * 键名沿用后端命名空间（common/auth/music/playlist/...），按点分路径查嵌套文案对象。
 * 查不到的键**原样返回键名**：漏翻一条会在界面上直接暴露出来，而不是显示 undefined。
 */
import { ref } from 'vue'
import { DEFAULT_LOCALE, LOCALE_ENTRIES, MESSAGES } from './locales/index.js'

/** 语言偏好落盘键；与后端无关，只影响本浏览器的默认语言 */
const STORAGE_KEY = 'nekoLocale'

export { DEFAULT_LOCALE }

export const supportedLocales = LOCALE_ENTRIES.map((entry) => entry.tag)

/** 语言自称（选择器展示用） */
export function localeName(tag) {
  return LOCALE_ENTRIES.find((entry) => entry.tag === tag)?.name || tag
}

function fallbackOf(tag) {
  return LOCALE_ENTRIES.find((entry) => entry.tag === tag)?.fallback || DEFAULT_LOCALE
}

/**
 * 把一个 `Accept-Language` 子标签映到站内语言。
 *
 * 与后端 `Languages.match` 同口径：**最长前缀优先**，且结束端必须锚定（后面跟 `-` 或就是结尾），
 * 因此 `zh-Hant-TW` → zh-TW、`zh-Hans` → zh-CN、`en-GB` → en，而 `enge` / `zhx` 不会被误匹配。
 * 这样浏览器报 `zh-Hant-TW` 时前端不会掉进简体。
 */
export function matchLocale(candidate) {
  const lower = String(candidate || '').trim().toLowerCase()
  if (!lower) return null
  let matched = null
  let matchedLength = -1
  for (const entry of LOCALE_ENTRIES) {
    for (const prefix of entry.prefixes) {
      if ((lower === prefix || lower.startsWith(`${prefix}-`)) && prefix.length > matchedLength) {
        matched = entry.tag
        matchedLength = prefix.length
      }
    }
  }
  return matched
}

/** 初始语言：本地选择 > 浏览器语言列表 > 源语言 */
function detectLocale() {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved && supportedLocales.includes(saved)) return saved
  } catch {
    // 隐私模式下 localStorage 可能直接抛异常：照常走浏览器语言
  }
  const candidates =
    Array.isArray(window.navigator?.languages) && window.navigator.languages.length
      ? window.navigator.languages
      : [window.navigator?.language]
  for (const candidate of candidates) {
    const matched = matchLocale(candidate)
    if (matched) return matched
  }
  return DEFAULT_LOCALE
}

/** 当前语言（响应式：切换后所有用到 `t` 的视图自动重渲染） */
export const locale = ref(typeof window === 'undefined' ? DEFAULT_LOCALE : detectLocale())

function lookup(tag, key) {
  let node = MESSAGES[tag]
  if (!node) return null
  for (const part of key.split('.')) {
    if (!node || typeof node !== 'object') return null
    node = node[part]
  }
  return typeof node === 'string' ? node : null
}

/** `{name}` 占位符填充；缺参数填成空串，避免界面上露出 `{name}` */
function fill(text, params) {
  if (!params) return text
  return text.replace(/\{(\w+)\}/g, (match, name) =>
    params[name] === undefined || params[name] === null ? '' : String(params[name]),
  )
}

/**
 * 取文案。
 *
 * 查表顺序与后端一致：当前语言 → 该语言声明的回落语言 → 源语言 → 键名本身。
 * 每次调用都读一遍 `locale.value`，这是模板/计算属性能在切语言后自动刷新的原因。
 */
export function t(key, params) {
  if (!key) return ''
  const chain = []
  let cursor = locale.value
  while (cursor && !chain.includes(cursor)) {
    chain.push(cursor)
    cursor = fallbackOf(cursor)
  }
  if (!chain.includes(DEFAULT_LOCALE)) chain.push(DEFAULT_LOCALE)
  for (const tag of chain) {
    const text = lookup(tag, key)
    if (text !== null) return fill(text, params)
  }
  return key
}

/** 切换语言：更新 `lang` 属性并落盘，下次进站沿用 */
export function setLocale(tag) {
  if (!supportedLocales.includes(tag) || tag === locale.value) return
  locale.value = tag
  try {
    window.localStorage.setItem(STORAGE_KEY, tag)
  } catch {
    // 存不进去也不影响本次会话
  }
  applyDocumentLang()
}

function applyDocumentLang() {
  if (typeof document !== 'undefined') document.documentElement.lang = locale.value
}

/** 注册全局 `$t`，让模板不必逐个 import */
export function installI18n(app) {
  applyDocumentLang()
  app.config.globalProperties.$t = t
  app.config.globalProperties.$locale = locale
}

/** 组件内使用（语言选择器需要响应式的 locale 与 setLocale） */
export function useI18n() {
  return { t, locale, setLocale, supportedLocales, localeName }
}
