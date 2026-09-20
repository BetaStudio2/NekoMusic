/**
 * vue-router 历史状态安全替换
 * ------------------------------------------------------------
 * vue-router v4 会把 back / current / forward / position / scroll 写进
 * history.state，导航时依赖这些字段。任何 `history.replaceState(null, ...)`
 * 都会把它们抹掉，于是下一次导航 vue-router 会报：
 *
 *   [VUE_ROUTER_R0121] history.state seems to have been manually replaced
 *   without preserving the necessary values.
 *
 * 需要「只改地址栏不动路由」时（清除分享用的 hash、去掉 ?skip=1 等），
 * 一律走这里：保留原 state，并把其中的 current 同步成新 URL，
 * 免得 state.current 与地址栏不一致导致前进/后退错乱。
 */

/**
 * @param {string|null} url 新的 URL（与 history.replaceState 的第三参同义；
 *   传 null 表示只更新 state、不改地址）
 */
export function replaceUrlSafely(url) {
  try {
    const prev = history.state
    const next = prev && typeof prev === 'object' ? { ...prev } : prev

    if (next && typeof next === 'object' && 'current' in next) {
      const target = new URL(url ?? window.location.href, window.location.href)
      next.current = target.pathname + target.search + target.hash
    }

    history.replaceState(next, '', url)
  } catch {
    // 隐私模式 / 沙箱iframe 等不允许改历史时静默失败，不影响页面功能
  }
}

/**
 * 清除地址栏上的 hash（分享链接用完后不该留在 URL 里）。
 * 保留 pathname 与 search。
 */
export function clearUrlHash() {
  replaceUrlSafely(window.location.pathname + window.location.search)
}
