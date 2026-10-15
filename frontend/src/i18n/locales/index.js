/**
 * 语言注册表与文案表。
 *
 * 与后端 `backend/src/main/resources/i18n/languages.properties` 一一对应：标签、匹配前缀、
 * 回落语言都保持一致，因此浏览器与接口返回的语言不会打架。加一门语言＝加一个文案表文件 +
 * 在下面注册一行，其余代码不用动。
 */
import zhCN from './zh-CN.js'
import zhTW from './zh-TW.js'
import en from './en.js'
import ja from './ja.js'
import ko from './ko.js'

/** 源语言：所有语言的最终回落目标，也是浏览器语言完全匹配不上时的默认值 */
export const DEFAULT_LOCALE = 'zh-CN'

/**
 * tag        语言标签（= 文案表文件名 = 后端注册表标签）
 * name       自称，语言选择器展示用
 * prefixes   navigator.language / Accept-Language 的匹配前缀（最长优先）
 * fallback   缺译文时的下一站；空表示直接回落源语言（zh-CN）
 */
export const LOCALE_ENTRIES = [
  {
    tag: 'zh-CN',
    name: '简体中文',
    prefixes: ['zh', 'zh-hans', 'zh-cn', 'zh-sg', 'zh-my'],
    fallback: '',
  },
  {
    tag: 'zh-TW',
    name: '繁體中文',
    prefixes: ['zh-hant', 'zh-tw', 'zh-hk', 'zh-mo'],
    fallback: 'zh-CN',
  },
  { tag: 'en', name: 'English', prefixes: ['en'], fallback: '' },
  { tag: 'ja', name: '日本語', prefixes: ['ja'], fallback: 'en' },
  { tag: 'ko', name: '한국어', prefixes: ['ko'], fallback: 'en' },
]

export const MESSAGES = { 'zh-CN': zhCN, 'zh-TW': zhTW, en, ja, ko }
