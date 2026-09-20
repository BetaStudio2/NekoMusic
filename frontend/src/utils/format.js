/**
 * 通用格式化工具（纯函数）
 * ------------------------------------------------------------
 * UI 展示相关的格式化集中在此，避免各页面重复实现。
 */

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
 * 数字 → 紧凑展示（如 12345 → 1.2万 / 1234567 → 123.5万）
 * @param {number} n
 * @returns {string}
 */
export function formatCount(n) {
  if (!Number.isFinite(n)) return '0'
  const abs = Math.abs(n)
  if (abs >= 1e8) return (n / 1e8).toFixed(1).replace(/\.0$/, '') + '亿'
  if (abs >= 1e4) return (n / 1e4).toFixed(1).replace(/\.0$/, '') + '万'
  return String(n)
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
