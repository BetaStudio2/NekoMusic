/**
 * 移动设备判定
 * ------------------------------------------------------------
 * 原先这段 UA 正则在 5 个地方各写了一遍（router 守卫、PlayerView、
 * PlaylistDetailView、CreatePlaylistView、nativeAppOpen），改一处容易漏。
 * 统一到这里。
 *
 * 注意：这是「设备形态」判定，不是视口宽度判定。
 * 布局断点请用 CSS 媒体查询（约定见 design/tokens.css）。
 */

/** 与原生 App 的「是否该尝试拉起」共用同一套判断 */
export function isMobileDevice() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent || navigator.vendor || ''
  return /android|ipad|iphone|ipod/i.test(ua)
}
