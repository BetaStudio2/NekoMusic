/* ============================================================================
 * Neko歌姬计划 —— Service Worker（PWA）
 * ----------------------------------------------------------------------------
 * 设计原则（宁保守、不越界）：
 *   1. 只缓存「应用外壳」：预缓存首页 + 图标/清单；运行时缓存带哈希的 /assets/*。
 *   2. 绝不缓存任何接口与媒体：
 *      - /api/*、/loser/*、/update/*、/version、/.well-known/* 直接放行；
 *      - 音频 / 封面 / 视频（含 Range 请求）直接放行，避免拖拽进度、鉴权、
 *        大文件缓存带来的错乱与磁盘膨胀。
 *   3. 导航「网络优先」：在线始终拿最新服务端 HTML；离线回退到已缓存的首页外壳。
 *   4. 带哈希静态资源「缓存优先」（文件名即版本，天然安全）。
 *   5. 其它同源静态资源 stale-while-revalidate，保证图标/清单能自更新。
 *
 * 更新策略：新 SW 安装后进入 waiting，页面侧弹出「发现新版本」，
 * 用户确认后 postMessage('SKIP_WAITING') → activate → 页面自动刷新。
 *
 * 注意：修改缓存策略或预缓存清单时，请递增 CACHE_VERSION。
 * ========================================================================== */

/* 构建时由 vite.config 的 neko-pwa-precache 插件替换（见 vite.config.js）：
 * 资源清单的短哈希，产物变化即换缓存名，activate 时自动清理上一版。 */
const CACHE_VERSION = /* __CACHE_VERSION__ */ 'v1'
const CORE_CACHE = `neko-core-${CACHE_VERSION}`
const RUNTIME_CACHE = `neko-runtime-${CACHE_VERSION}`

/** 构建时注入：本次产物的带哈希资源（/assets/xxx），用于离线首屏 */
const BUILD_ASSETS = /* __PRECACHE_MANIFEST__ */ []

/** 运行时缓存条目上限：只保留最近若干份哈希资源，避免跨版本无限堆积 */
const RUNTIME_MAX_ENTRIES = 80

/** 预缓存清单：应用外壳 + 安装所需资源 + 本次构建的哈希资源 */
const CORE_ASSETS = [
  '/',
  '/manifest.webmanifest',
  '/favicon.ico',
  '/logo.svg',
  '/apple-touch-icon.png',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/icon-maskable-192.png',
  '/icons/icon-maskable-512.png',
].concat(BUILD_ASSETS)

/** 永不拦截的路径前缀 / 精确路径 */
const BYPASS_PREFIXES = ['/api/', '/loser/', '/update/', '/.well-known/']
const BYPASS_PATHS = ['/version', '/sw.js']

const OFFLINE_HTML = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<meta name="theme-color" content="#04090b">
<title>离线 · Neko歌姬计划</title>
<style>
  html,body{margin:0;height:100%;background:#04090b;color:rgba(255,255,255,.9);
    font-family:'PingFang SC','Microsoft YaHei',system-ui,sans-serif}
  .wrap{height:100%;display:grid;place-items:center;text-align:center;padding:24px;box-sizing:border-box}
  .logo{width:96px;height:96px;border-radius:22px}
  h1{font-size:1.15rem;margin:16px 0 6px}
  p{margin:0;color:rgba(255,255,255,.55);font-size:.9rem;line-height:1.6}
</style></head>
<body><div class="wrap"><div>
  <img class="logo" src="/icons/icon-192.png" alt="">
  <h1>当前处于离线状态</h1>
  <p>网络恢复后即可继续搜索与播放。<br>已缓存的页面仍可访问。</p>
</div></div></body></html>`

self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CORE_CACHE)
      // 逐个 add：任一资源失败（例如上线顺序问题）也不至于让整个 SW 安装失败
      await Promise.all(
        CORE_ASSETS.map(async (url) => {
          try {
            const response = await fetch(new Request(url, { cache: 'reload' }))
            if (response && response.ok) await cache.put(url, response)
          } catch {
            /* 忽略单个资源失败 */
          }
        }),
      )
    })(),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      await Promise.all(
        keys
          .filter((key) => key.startsWith('neko-') && key !== CORE_CACHE && key !== RUNTIME_CACHE)
          .map((key) => caches.delete(key)),
      )
      // 立即接管未被控制的页面（配合页面侧 SKIP_WAITING 实现无缝更新）
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('message', (event) => {
  const data = event.data
  if (data && data.type === 'SKIP_WAITING') self.skipWaiting()
})

/** 判断是否属于「应用外壳之外的请求」，这类请求一律交给网络 */
function shouldBypass(url, request) {
  if (url.origin !== self.location.origin) return true
  if (BYPASS_PATHS.includes(url.pathname)) return true
  if (BYPASS_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) return true
  // 音频/视频的 Range 请求必须直连，交给浏览器原生分段加载
  if (request.headers.has('range')) return true
  return false
}

async function networkFirstNavigation(request) {
  try {
    const response = await fetch(request)
    // 只缓存同源、成功、非跳转的 HTML，作为离线回退外壳
    if (response && response.ok && response.type === 'basic') {
      const cache = await caches.open(CORE_CACHE)
      cache.put('/', response.clone())
    }
    return response
  } catch {
    const cache = await caches.open(CORE_CACHE)
    const cached = await cache.match('/')
    if (cached) return cached
    return new Response(OFFLINE_HTML, {
      status: 200,
      headers: { 'Content-Type': 'text/html; charset=UTF-8' },
    })
  }
}

/** 条目超限时按插入顺序丢弃最旧的缓存（近似 LRU 的轻量实现） */
async function trimCache(cache) {
  try {
    const keys = await cache.keys()
    if (keys.length <= RUNTIME_MAX_ENTRIES) return
    const overflow = keys.length - RUNTIME_MAX_ENTRIES
    for (let i = 0; i < overflow; i += 1) await cache.delete(keys[i])
  } catch {
    /* 清理失败不影响请求 */
  }
}

/** 网络优先（用于需要及时更新的小文件，如 manifest） */
async function networkFirstAsset(request, cacheName) {
  const cache = await caches.open(cacheName)
  try {
    const response = await fetch(request)
    if (response && response.ok && response.type === 'basic') {
      cache.put(request, response.clone())
    }
    return response
  } catch {
    const cached = await cache.match(request)
    if (cached) return cached
    throw new Error('offline')
  }
}

async function cacheFirst(request) {
  // 全局匹配：优先命中安装时预缓存进 CORE 的构建产物
  const cached = await caches.match(request)
  if (cached) return cached
  const cache = await caches.open(RUNTIME_CACHE)
  const response = await fetch(request)
  if (response && response.ok && response.type === 'basic') {
    cache.put(request, response.clone())
    trimCache(cache)
  }
  return response
}

async function staleWhileRevalidate(request) {
  const cached = await caches.match(request)
  const cache = await caches.open(RUNTIME_CACHE)
  const network = fetch(request)
    .then((response) => {
      if (response && response.ok && response.type === 'basic') {
        cache.put(request, response.clone())
        trimCache(cache)
      }
      return response
    })
    .catch(() => null)
  return cached || (await network) || Response.error()
}

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return

  let url
  try {
    url = new URL(request.url)
  } catch {
    return
  }
  if (shouldBypass(url, request)) return

  if (request.mode === 'navigate') {
    event.respondWith(networkFirstNavigation(request))
    return
  }

  // Manifest 变动需要尽快生效（名称 / 主题色 / 图标）：网络优先
  if (url.pathname.endsWith('.webmanifest')) {
    event.respondWith(networkFirstAsset(request, CORE_CACHE))
    return
  }

  // 带内容哈希的构建产物：缓存优先
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(cacheFirst(request))
    return
  }

  // 其余同源静态资源（图标、字体等）后台更新
  event.respondWith(staleWhileRevalidate(request))
})
