/**
 * 通用格式化工具（纯函数）
 * ------------------------------------------------------------
 * UI 展示相关的格式化集中在此，避免各页面重复实现。
 * 需要随语言变化的格式化（如紧凑数字）读取 i18n 的当前语言。
 */
import { locale } from '@/i18n'

/**
 * 秒数 → mm:ss 或 h:mm:ss
 * @param {number} seconds
 * @returns {string}
 */
export function formatDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return '0:00'
  const total = Math.floor(seconds)
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m)
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

/**
 * 数字 → 紧凑展示（中文 12345 → 1.2万，英文 1234567 → 1.2M）
 * 单位随当前语言走，不写死中文的「万 / 亿」。
 * @param {number} n
 * @returns {string}
 */
export function formatCount(n) {
  if (!Number.isFinite(n)) return '0'
  try {
    return new Intl.NumberFormat(locale.value, {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(n)
  } catch {
    return String(n)
  }
}

/**
 * 字节数 → 可读体积
 * @param {number} bytes
 * @returns {string}
 */
export function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(i === 0 ? 0 : 1).replace(/\.0$/, '')} ${units[i]}`
}

/**
 * 时间戳 → YYYY-MM-DD
 * @param {number|string|Date} input
 * @returns {string}
 */
export function formatDate(input) {
  const d = input instanceof Date ? input : new Date(input)
  if (Number.isNaN(d.getTime())) return ''
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * 请求耗时（微秒）→ 可读文案。
 *
 * 低于 1ms 的请求如果按毫秒取整会全是 0 ms，看不出快慢；这里对亚毫秒改用 us 展示：
 * < 1000us 显示「123 us」，否则显示毫秒「12 ms」。
 * @param {number} micros 处理耗时（微秒）
 * @returns {string}
 */
export function formatLatency(micros) {
  if (!Number.isFinite(micros) || micros < 0) return '—'
  if (micros < 1000) return `${Math.round(micros)} us`
  return `${Math.round(micros / 1000)} ms`
}
