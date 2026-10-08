// 通用防重放客户端（web 端）
// ------------------------------------------------------------
// 后端 ReplayProtectionFilter 要求所有动态接口（/api/*、/loser/*）携带一次性 nonce
// （请求头 X-Neko-Nonce）；客户端版本检查 /version 也按同一约定带上（便于与服务端同步收紧）。
// 本模块负责：
//   1. 向 GET /api/replay/challenge 换一道挑战题、本地解出 proof，再带 challenge + proof
//      调 GET /api/replay/nonce 批量领取 nonce（读 / 写两类），放进内存池；
//      （挑战接口不存在时——HTTP 404——回退为直接领取，方便前后端分批发版）
//   2. 拦截 fetch 与 XMLHttpRequest(axios)，自动为受保护请求取一个池内 nonce；
//   3. 409 + X-Neko-Replay-Status: missing|invalid 时换一个新 nonce 重试一次
//      （nonce 过期或已被消费——重试是安全的，因为请求在进入业务逻辑前就被拒了）。
//
// 只按「路径」判定，且仅处理同源请求；跨域请求附带自定义头会触发 CORS 预检，不在本模块内处理。
// 豁免清单必须与后端 ReplayProtectionFilter 保持一致（不一致只会浪费 nonce，不会误拦）。
import API_CONFIG from '@/config/apiConfig.js'
import { solveProof } from '@/utils/pow.js'

export const NONCE_HEADER = 'X-Neko-Nonce'
export const REPLAY_STATUS_HEADER = 'X-Neko-Replay-Status'

const STATUS_MISSING = 'missing'
const STATUS_INVALID = 'invalid'

const SCOPE_READ = 'r'
const SCOPE_WRITE = 'w'

const REFILL_READ = 16
const REFILL_WRITE = 16
const LOW_WATER = 4
// 服务端 nonce TTL 120s，本地提前作废（留出网络与重试余量），避免用到已过期的 nonce
const MAX_AGE_MS = 90_000
// XHR 延迟发送的最长等待：领不到 nonce 也照发（服务端 Redis 故障时会 fail-open 放行）
const XHR_NONCE_TIMEOUT_MS = 4000

// 与服务端约定的解题算法标识：换题响应里的 algorithm 必须与它一致，否则不盲解
const POW_ALGORITHM = 'sha256-leading-zero-bits'
// 领取被限额（429）时的最长等待；服务端会带 Retry-After
const RETRY_AFTER_MAX_MS = 2000

/** 与后端 EXEMPT_PATHS 对应 */
const EXEMPT_PATHS = new Set([
  '/api/replay/challenge',
  '/api/replay/nonce',
  '/api/music/latest',
  '/api/music/ranking',
  '/api/payment/zpay/notify',
  '/api/user/qrlogin/status'
])
/** 与后端 EXEMPT_PREFIXES 对应 */
const EXEMPT_PREFIXES = ['/api/music/cover/', '/api/user/avatar/']
/**
 * 非 /api 前缀、但按同一约定提前携带 nonce 的接口（客户端版本检查）。
 * 服务端当前尚未对该路径强制校验，提前携带是为后续纳管做好兼容。
 */
const PROTECTED_PATHS = new Set(['/version'])

const pools = { [SCOPE_READ]: [], [SCOPE_WRITE]: [] }
let refillInFlight = null
let installed = false
let outboundFetch = null

function scopeOf(method) {
  const value = String(method || 'GET').toUpperCase()
  if (value === 'GET') return SCOPE_READ
  if (value === 'POST' || value === 'PUT' || value === 'PATCH' || value === 'DELETE') return SCOPE_WRITE
  return null
}

function urlOf(input) {
  if (typeof input === 'string') return input
  if (typeof URL !== 'undefined' && input instanceof URL) return input.href
  if (input && typeof input.url === 'string') return input.url
  return ''
}

function methodOf(input, init) {
  if (init && init.method) return init.method
  if (input && typeof input.method === 'string' && input.method) return input.method
  return 'GET'
}

function resolveUrl(url) {
  try {
    return new URL(String(url), window.location.href)
  } catch {
    return null
  }
}

function isSameBackend(target) {
  if (!target) return false
  if (target.origin === window.location.origin) return true
  const base = API_CONFIG.BASE_URL
  if (!base) return false
  try {
    return target.origin === new URL(base, window.location.href).origin
  } catch {
    return false
  }
}

/** 该请求是否需要防重放 nonce（与后端判定一致）。 */
export function shouldAttachNonce(url, method) {
  if (typeof window === 'undefined') return false
  const scope = scopeOf(method)
  if (!scope) return false
  const target = resolveUrl(url)
  if (!target || !isSameBackend(target)) return false
  const path = target.pathname
  if (EXEMPT_PATHS.has(path)) return false
  if (EXEMPT_PREFIXES.some((prefix) => path.startsWith(prefix))) return false
  // /loser/*/pull 为 SSE 进度流
  if (path.endsWith('/pull')) return false
  const dynamic = path.startsWith('/api/') || path.startsWith('/loser/')
  if (!dynamic && !PROTECTED_PATHS.has(path)) return false
  return true
}

function popFresh(scope) {
  const pool = pools[scope]
  const now = Date.now()
  while (pool.length > 0) {
    const entry = pool.shift()
    if (now - entry.issuedAt < MAX_AGE_MS) return entry.value
  }
  return null
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function retryAfterMs(response) {
  const seconds = Number(response.headers.get('Retry-After'))
  if (!Number.isFinite(seconds) || seconds <= 0) return 200
  return Math.min(seconds * 1000, RETRY_AFTER_MAX_MS)
}

/**
 * 换一道挑战题；被限额（429）时按 Retry-After 等一小会儿再试一次。
 * 返回 null 表示服务端还没有挑战接口（旧版本），调用方回退为直接领取。
 */
async function fetchChallenge(read, write) {
  const url = `${API_CONFIG.BASE_URL}/api/replay/challenge?read=${read}&write=${write}`
  for (let attempt = 0; ; attempt += 1) {
    const response = await outboundFetch(url, { cache: 'no-store' })
    if (response.ok) {
      const data = (await response.json())?.data
      if (!data || typeof data.challenge !== 'string' || typeof data.seed !== 'string') {
        throw new Error('换题响应缺少 challenge / seed')
      }
      if (data.algorithm !== POW_ALGORITHM) {
        throw new Error(`未知的挑战算法：${data.algorithm}`)
      }
      return data
    }
    if (response.status === 404) return null
    if (response.status === 429 && attempt === 0) {
      await sleep(retryAfterMs(response))
      continue
    }
    throw new Error(`换题失败：HTTP ${response.status}`)
  }
}

/**
 * 领取一批 nonce：先换题、本地解题，再拿 challenge + proof 兑换。
 * 题目一次性（换题 / 兑换都可能 429，题也可能失效），失败就重新换一道重试一次。
 */
async function fetchNonces(read, write) {
  if (typeof outboundFetch !== 'function') return {}
  let lastError = null
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const challenge = await fetchChallenge(read, write)
    const query = challenge
      ? new URLSearchParams({
          challenge: challenge.challenge,
          proof: solveProof(challenge.seed, challenge.difficulty)
        })
      : new URLSearchParams({ read: String(read), write: String(write) })
    const response = await outboundFetch(`${API_CONFIG.BASE_URL}/api/replay/nonce?${query}`, {
      cache: 'no-store'
    })
    if (response.ok) {
      const data = (await response.json())?.data
      return data?.nonces || {}
    }
    lastError = new Error(`nonce 签发失败：HTTP ${response.status}`)
    // 400 错解 / 409 题目失效：重解一次；429 限额：等一小会儿再来；其余直接放弃
    if (response.status === 429) {
      await sleep(retryAfterMs(response))
    } else if (response.status !== 400 && response.status !== 409) {
      break
    }
  }
  throw lastError
}

function storeNonces(nonces) {
  const now = Date.now()
  for (const [scope, key] of [[SCOPE_READ, 'read'], [SCOPE_WRITE, 'write']]) {
    const list = nonces?.[key]
    if (!Array.isArray(list)) continue
    for (const value of list) {
      if (typeof value === 'string' && value) pools[scope].push({ value, issuedAt: now })
    }
  }
}

/** 低水位补齐两个池；并发调用会共享同一次请求。 */
function refillPools() {
  if (refillInFlight) return refillInFlight
  const read = pools[SCOPE_READ].length < LOW_WATER ? REFILL_READ : 0
  const write = pools[SCOPE_WRITE].length < LOW_WATER ? REFILL_WRITE : 0
  if (read === 0 && write === 0) return Promise.resolve()
  refillInFlight = fetchNonces(read, write)
    .then(storeNonces)
    .catch((error) => {
      console.warn('[replay] 领取防重放 nonce 失败，本次请求将不带 nonce', error)
    })
    .finally(() => {
      refillInFlight = null
    })
  return refillInFlight
}

/** 同步取一个池内 nonce；池空返回 null。 */
export function takeNonceSync(scope) {
  const value = popFresh(scope)
  if (value && pools[scope].length < LOW_WATER) refillPools()
  return value
}

/** 异步取一个 nonce：优先池内，其次补齐，最后单独领一个。 */
export async function takeNonce(scope) {
  const pooled = takeNonceSync(scope)
  if (pooled) return pooled
  await refillPools()
  const refilled = popFresh(scope)
  if (refilled) return refilled
  try {
    const nonces = await fetchNonces(scope === SCOPE_READ ? 1 : 0, scope === SCOPE_WRITE ? 1 : 0)
    storeNonces(nonces)
    return popFresh(scope)
  } catch (error) {
    console.warn('[replay] 补领防重放 nonce 失败', error)
    return null
  }
}

function withNonce(input, init, nonce) {
  const headers = new Headers(
    input instanceof Request ? input.headers : (init && init.headers) || undefined
  )
  if (nonce) headers.set(NONCE_HEADER, nonce)
  if (input instanceof Request) {
    return { request: new Request(input, { ...(init || {}), headers }) }
  }
  return { request: null, options: { ...(init || {}), headers } }
}

function isReplayRejection(response) {
  if (response.status !== 409) return false
  const status = response.headers.get(REPLAY_STATUS_HEADER)
  return status === STATUS_MISSING || status === STATUS_INVALID
}

/** 预取一批 nonce（应用启动时调用，省掉首个请求的等待）。 */
export function primeReplayNonces() {
  return refillPools()
}

function patchFetch() {
  const previousFetch = outboundFetch
  window.fetch = async (input, init) => {
    const url = urlOf(input)
    const method = methodOf(input, init)
    if (!shouldAttachNonce(url, method)) return previousFetch(input, init)

    const scope = scopeOf(method)
    // Request 对象在重试时可能已被消费（bodyUsed），只对字符串 / URL 输入做自动重试
    const canRetry = !(input instanceof Request)
    let response = null
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const nonce = await takeNonce(scope)
      const { request, options } = withNonce(input, init, nonce)
      response = request ? await previousFetch(request, options) : await previousFetch(input, options)
      if (attempt === 0 && canRetry && isReplayRejection(response)) continue
      return response
    }
    return response
  }
}

function patchXhr() {
  const proto = XMLHttpRequest.prototype
  const originalOpen = proto.open
  const originalSend = proto.send
  const originalAbort = proto.abort
  const originalSetRequestHeader = proto.setRequestHeader

  proto.open = function (method, url, ...rest) {
    this.__nekoReplayMethod = method
    this.__nekoReplayUrl = url
    return originalOpen.call(this, method, url, ...rest)
  }

  proto.abort = function () {
    this.__nekoReplayAborted = true
    return originalAbort.call(this)
  }

  proto.send = function (...args) {
    const self = this
    const method = self.__nekoReplayMethod
    if (!shouldAttachNonce(self.__nekoReplayUrl, method)) {
      return originalSend.apply(self, args)
    }
    const scope = scopeOf(method)
    const finish = (nonce) => {
      if (self.__nekoReplaySent) return undefined
      self.__nekoReplaySent = true
      if (self.__nekoReplayAborted) return undefined
      if (nonce) {
        try {
          originalSetRequestHeader.call(self, NONCE_HEADER, nonce)
        } catch {
          /* 忽略：头已发送 / 非法状态 */
        }
      }
      return originalSend.apply(self, args)
    }

    const pooled = takeNonceSync(scope)
    if (pooled) return finish(pooled)

    // 池内暂无：异步领一个再发。axios 的登录 / 注册 / 验证码等低频请求会走这里。
    takeNonce(scope).then(finish, () => finish(null))
    setTimeout(() => finish(null), XHR_NONCE_TIMEOUT_MS)
    return undefined
  }
}

/**
 * 安装防重放客户端：在 installNekoClientHeader() 之后调用一次即可。
 * 覆盖原生 fetch、XMLHttpRequest 与 axios（浏览器端默认走 XHR）。
 */
export function installReplayProtection() {
  if (installed || typeof window === 'undefined') return
  if (typeof window.fetch !== 'function') return
  installed = true
  outboundFetch = window.fetch.bind(window)
  patchFetch()
  if (typeof XMLHttpRequest !== 'undefined') patchXhr()
  primeReplayNonces()
}
