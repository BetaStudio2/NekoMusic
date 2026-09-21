/**
 * 封面响应式工具
 * ------------------------------------------------------------
 * 后端 `/api/music/cover/{id}` 支持 `?size=` 返回方形缩略图
 * （白名单 96/160/240/320/480/640）。这里生成 srcset，
 * 让浏览器按显示宽度与 DPR 选择最合适的尺寸，避免小卡片加载大图。
 */
const COVER_SIZES = [160, 320, 480]

function isCoverApi(url) {
  return typeof url === 'string' && url.includes('/api/music/cover/')
}

/** 生成 srcset；非封面接口 URL 返回 undefined（直接绑定即可，浏览器会忽略） */
export function coverSrcset(url) {
  if (!isCoverApi(url)) return undefined
  const sep = url.includes('?') ? '&' : '?'
  return COVER_SIZES.map((w) => `${url}${sep}size=${w} ${w}w`).join(', ')
}

/** 生成单张封面 URL（默认 320，用于 src 回退） */
export function coverSrc(url, width = 320) {
  if (!isCoverApi(url)) return url
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}size=${width}`
}
