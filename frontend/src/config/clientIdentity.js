// 客户端标识与实例身份（Web）
// ------------------------------------------------------------
// 所有发往本站后端的动态请求统一携带：
//   - X-Neko-Client: web+<版本>
//   - X-Neko-Client-Id / Instance / Token / Timestamp / Signature
//   - Authorization: Bearer <用户令牌>（已登录时；调用方显式设置的不覆盖）
//
// client_id 是公开应用标识，单独泄露它不能伪造请求。真正的身份来自每个浏览器实例独立生成、
// 不可导出的 ECDSA P-256 私钥；私钥存入 IndexedDB，短期实例令牌存 localStorage。注册和续签
// 都先领取一次性挑战，普通请求再对「方法 + 路径 + 查询串 + 时间戳 + body 摘要 + nonce」签名。
//
// 严禁用脚本改写 User-Agent：WebKit / Firefox 与 Chromium 的行为不一致，改写后可能被后端
// 判成伪造 Web 客户端。Web 端身份只由 X-Neko-Client 与实例签名自报。
//
// 请求补丁顺序很重要：本模块先安装，防重放模块后安装。实际发送时防重放处在外层，先补
// X-Neko-Nonce，再把请求交给这里签名；这样签名串里的 nonce 与服务端最终收到的一致。
import axios from 'axios'
import { locale } from '@/i18n'
import API_CONFIG from './apiConfig.js'

/** 标头名；原生客户端（pc/android）必须与此保持一致 */
export const NEKO_CLIENT_HEADER = 'X-Neko-Client'

/** 平台标识；三端分别是 web / pc / android */
export const NEKO_CLIENT_NAME = 'web'

/** 官方 Web 客户端公开 ID；具体实例仍必须持有自己的私钥 */
export const NEKO_CLIENT_ID = 'nkc_pub_web_prod'

export const NEKO_CLIENT_VERSION =
  typeof __NEKO_WEB_VERSION__ === 'string' ? __NEKO_WEB_VERSION__ : 'dev'

export const NEKO_CLIENT_VALUE = `${NEKO_CLIENT_NAME}+${NEKO_CLIENT_VERSION}`

const HEADER_CLIENT_ID = 'X-Neko-Client-Id'
const HEADER_INSTANCE = 'X-Neko-Client-Instance'
const HEADER_TOKEN = 'X-Neko-Client-Token'
const HEADER_TIMESTAMP = 'X-Neko-Client-Timestamp'
const HEADER_SIGNATURE = 'X-Neko-Client-Signature'
const HEADER_CLIENT_STATUS = 'X-Neko-Client-Status'
const NONCE_HEADER = 'X-Neko-Nonce'

const PROTOCOL_VERSION = 'neko-client-v1'
const STREAMED_BODY_HASH = '-'
const EMPTY_BODY_HASH = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
const TOKEN_REFRESH_AHEAD_MS = 24 * 60 * 60 * 1000

const STORAGE_KEY = 'nekoClientIdentity.v1'
const IDB_NAME = 'neko-client-identity'
const IDB_STORE = 'keys'
const IDB_KEY = 'web-p256-v1'

const BOOTSTRAP_PATHS = new Set([
  '/api/client/challenge',
  '/api/client/register',
  '/api/client/token',
  '/api/replay/challenge',
  '/api/replay/nonce',
  '/api/music/latest',
  '/api/music/ranking',
  '/api/payment/zpay/notify',
  '/api/user/qrlogin/status',
  '/api/user/notifications/stream',
  '/api/user/upload/preview',
])
const EXEMPT_PREFIXES = ['/api/music/cover/', '/api/user/avatar/']

const STATIC_REQUEST_HEADERS = {
  [NEKO_CLIENT_HEADER]: NEKO_CLIENT_VALUE,
}

/** 语言标头名；与后端按 `Accept-Language` 选文案的约定一致 */
export const ACCEPT_LANGUAGE_HEADER = 'Accept-Language'
/** 用户令牌标头；与后端 `Authorization: Bearer <token>` 约定一致 */
export const AUTHORIZATION_HEADER = 'Authorization'
const USER_TOKEN_STORAGE_KEY = 'userToken'

let installed = false
let directFetch = null
let keyPairCache = null
let memoryKeyPair = null
let identityCache = null
let identityInFlight = null
let recoveryInFlight = null

function nekoRequestHeaders() {
  return {
    ...STATIC_REQUEST_HEADERS,
    [ACCEPT_LANGUAGE_HEADER]: locale.value,
  }
}

/** 给同源后端请求补基础标头；调用方显式设置的值始终优先。 */
function applyBaseRequestHeaders(headers) {
  for (const [name, value] of Object.entries(nekoRequestHeaders())) {
    if (!headers.has(name)) headers.set(name, value)
  }
  if (!headers.has(AUTHORIZATION_HEADER)) {
    const authorization = userAuthorizationValue()
    if (authorization) headers.set(AUTHORIZATION_HEADER, authorization)
  }
}

function userAuthorizationValue() {
  if (typeof window === 'undefined' || !window.localStorage) return ''
  try {
    const token = window.localStorage.getItem(USER_TOKEN_STORAGE_KEY)
    return token ? `Bearer ${token}` : ''
  } catch {
    return ''
  }
}

function shouldAttachHeader(url) {
  if (!url || typeof window === 'undefined') return false
  let target
  try {
    target = new URL(String(url), window.location.href)
  } catch {
    return false
  }
  if (target.origin === window.location.origin) return true
  const base = API_CONFIG.BASE_URL
  if (!base) return false
  try {
    return target.origin === new URL(base, window.location.href).origin
  } catch {
    return false
  }
}

/** 与服务端 RequestPath 的「路由视角」保持一致：解开路径段编码，并消解重复段。 */
function canonicalPath(url) {
  const raw = url.pathname || '/'
  const trailingSlash = raw.length > 1 && raw.endsWith('/')
  const segments = []
  for (const part of raw.split('/')) {
    let segment = part
    const semicolon = segment.indexOf(';')
    if (semicolon >= 0) segment = segment.slice(0, semicolon)
    try {
      segment = decodeURIComponent(segment)
    } catch {
      // 保留原始段，让服务端最终判定；非法编码不应在客户端被静默改写
    }
    if (!segment || segment === '.') continue
    if (segment === '..') {
      segments.pop()
      continue
    }
    segments.push(segment)
  }
  return '/' + segments.join('/') + (trailingSlash && segments.length ? '/' : '')
}

function shouldAttachIdentity(url, method) {
  if (!url || typeof window === 'undefined') return false
  const verb = String(method || 'GET').toUpperCase()
  if (verb === 'OPTIONS' || verb === 'HEAD') return false
  let target
  try {
    target = new URL(String(url), window.location.href)
  } catch {
    return false
  }
  if (!shouldAttachHeader(target.href)) return false
  const path = canonicalPath(target)
  if (BOOTSTRAP_PATHS.has(path)) return false
  if (EXEMPT_PREFIXES.some((prefix) => path.startsWith(prefix))) return false
  if (path.endsWith('/pull')) return false
  return path.startsWith('/api/') || path.startsWith('/loser/')
}

function toBase64Url(bytes) {
  let binary = ''
  for (let index = 0; index < bytes.length; index += 1) {
    binary += String.fromCharCode(bytes[index])
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

function hex(bytes) {
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return hex(new Uint8Array(digest))
}

/** WebCrypto ECDSA 返回 IEEE P1363（r||s），Java/OpenSSL 默认验签需要 DER。 */
function p1363ToDer(signature) {
  if (!signature || signature.length !== 64) {
    throw new Error('P-256 签名长度不是 64 字节')
  }
  const integer = (offset) => {
    let start = offset
    const end = offset + 32
    while (start < end - 1 && signature[start] === 0) start += 1
    let value = Array.from(signature.slice(start, end))
    if (value[0] & 0x80) value = [0, ...value]
    return [0x02, value.length, ...value]
  }
  const body = [...integer(0), ...integer(32)]
  return new Uint8Array([0x30, body.length, ...body])
}

function randomInstanceId() {
  if (crypto.randomUUID) return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const value = hex(bytes)
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`
}

function openIdentityDb() {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB 不可用'))
      return
    }
    const request = indexedDB.open(IDB_NAME, 1)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(IDB_STORE)) db.createObjectStore(IDB_STORE)
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('IndexedDB 打开失败'))
  })
}

async function idbGet(key) {
  const db = await openIdentityDb()
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readonly')
      const request = tx.objectStore(IDB_STORE).get(key)
      request.onsuccess = () => resolve(request.result || null)
      request.onerror = () => reject(request.error)
    })
  } finally {
    db.close()
  }
}

async function idbPut(key, value) {
  const db = await openIdentityDb()
  try {
    await new Promise((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readwrite')
      tx.objectStore(IDB_STORE).put(value, key)
      tx.oncomplete = () => resolve()
      tx.onerror = () => reject(tx.error)
    })
  } finally {
    db.close()
  }
}

async function loadKeyPair() {
  if (keyPairCache) return keyPairCache
  try {
    const stored = await idbGet(IDB_KEY)
    if (stored?.privateKey && stored?.publicKey) keyPairCache = stored
  } catch (error) {
    console.warn('[client] 读取本机实例私钥失败，将使用临时私钥', error)
  }
  if (!keyPairCache && memoryKeyPair) keyPairCache = memoryKeyPair
  return keyPairCache
}

async function generateKeyPair() {
  if (!globalThis.crypto?.subtle) throw new Error('浏览器不支持 WebCrypto，无法建立客户端身份')
  const pair = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['sign', 'verify'],
  )
  keyPairCache = pair
  memoryKeyPair = pair
  try {
    await idbPut(IDB_KEY, pair)
  } catch (error) {
    console.warn('[client] IndexedDB 不可用，本次会话使用临时私钥', error)
  }
  return pair
}

async function publicKeySpki(pair) {
  const encoded = await crypto.subtle.exportKey('spki', pair.publicKey)
  return toBase64Url(new Uint8Array(encoded))
}

async function signCanonical(pair, canonical) {
  const signature = await crypto.subtle.sign(
    { name: 'ECDSA', hash: 'SHA-256' },
    pair.privateKey,
    new TextEncoder().encode(canonical),
  )
  return toBase64Url(p1363ToDer(new Uint8Array(signature)))
}

function loadStoredIdentity() {
  if (typeof window === 'undefined' || !window.localStorage) return null
  try {
    const value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || 'null')
    if (!value || typeof value !== 'object') return null
    if (
      value.clientId !== NEKO_CLIENT_ID ||
      value.platform !== NEKO_CLIENT_NAME ||
      typeof value.instanceId !== 'string' ||
      typeof value.publicKey !== 'string'
    ) {
      return null
    }
    return value
  } catch {
    return null
  }
}

function saveStoredIdentity(value) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    // 隐私模式可能禁用 localStorage：令牌只保留在内存，刷新页面后重新注册
  }
}

function clearStoredIdentity() {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

async function bootstrapRequest(path, { method = 'GET', body } = {}) {
  if (typeof directFetch !== 'function') throw new Error('客户端身份尚未初始化')
  const headers = nekoRequestHeaders()
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await directFetch(`${API_CONFIG.BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
    credentials: 'same-origin',
  })
  let payload = null
  try {
    payload = await response.json()
  } catch {
    // 下面按状态码给统一错误
  }
  if (!response.ok || payload?.success !== true || !payload?.data) {
    const error = new Error(`客户端身份接口失败：HTTP ${response.status}`)
    error.status = response.status
    error.path = path
    error.message = `客户端身份接口失败：HTTP ${response.status} ${path}` +
      (payload?.message ? `（${payload.message}）` : '')
    error.replayStatus = response.headers.get('X-Neko-Replay-Status') || ''
    error.clientStatus = response.headers.get('X-Neko-Client-Status') || ''
    throw error
  }
  return payload.data
}

async function requestClientChallenge(purpose) {
  const query = new URLSearchParams({
    client_id: NEKO_CLIENT_ID,
    platform: NEKO_CLIENT_NAME,
    purpose,
  })
  const data = await bootstrapRequest(`/api/client/challenge?${query}`)
  if (data.protocol !== PROTOCOL_VERSION || typeof data.challenge !== 'string') {
    throw new Error('客户端身份挑战响应格式不正确')
  }
  return data.challenge
}

async function refreshIdentity(stored, pair) {
  const challenge = await requestClientChallenge('token')
  const timestamp = Math.floor(Date.now() / 1000)
  const canonical = [
    'neko-client-token-v1',
    NEKO_CLIENT_ID,
    NEKO_CLIENT_NAME,
    stored.instanceId,
    challenge,
    timestamp,
  ].join('\n')
  const signature = await signCanonical(pair, canonical)
  const data = await bootstrapRequest('/api/client/token', {
    method: 'POST',
    body: {
      client_id: NEKO_CLIENT_ID,
      platform: NEKO_CLIENT_NAME,
      instance_id: stored.instanceId,
      challenge,
      timestamp,
      signature,
    },
  })
  return {
    ...stored,
    token: String(data.token || ''),
    expiresAt: Number(data.expires_at || 0) * 1000,
    publicKey: await publicKeySpki(pair),
  }
}

async function registerIdentity(pair) {
  const challenge = await requestClientChallenge('register')
  const keyPair = pair || (await generateKeyPair())
  const spki = await publicKeySpki(keyPair)
  const instanceId = randomInstanceId()
  const timestamp = Math.floor(Date.now() / 1000)
  const canonical = [
    'neko-client-register-v1',
    NEKO_CLIENT_ID,
    NEKO_CLIENT_NAME,
    instanceId,
    spki,
    challenge,
    timestamp,
  ].join('\n')
  const signature = await signCanonical(keyPair, canonical)
  const data = await bootstrapRequest('/api/client/register', {
    method: 'POST',
    body: {
      client_id: NEKO_CLIENT_ID,
      platform: NEKO_CLIENT_NAME,
      instance_id: instanceId,
      public_key: spki,
      challenge,
      timestamp,
      signature,
      origin: window.location.origin,
    },
  })
  if (String(data.instance_id || '') !== instanceId || !data.token) {
    throw new Error('客户端注册响应格式不正确')
  }
  return {
    clientId: NEKO_CLIENT_ID,
    platform: NEKO_CLIENT_NAME,
    instanceId,
    token: String(data.token),
    expiresAt: Number(data.expires_at || 0) * 1000,
    publicKey: spki,
  }
}

async function buildIdentity(forceRefresh) {
  let pair = await loadKeyPair()
  const stored = loadStoredIdentity()
  if (pair && stored) {
    let spki = ''
    try {
      spki = await publicKeySpki(pair)
    } catch {
      pair = null
    }
    if (pair && spki === stored.publicKey) {
      if (!forceRefresh && stored.token && stored.expiresAt > Date.now() + TOKEN_REFRESH_AHEAD_MS) {
        identityCache = { ...stored, pair }
        return identityCache
      }
      try {
        const refreshed = await refreshIdentity(stored, pair)
        if (!refreshed.token) throw new Error('续签响应缺少 token')
        saveStoredIdentity(refreshed)
        identityCache = { ...refreshed, pair }
        return identityCache
      } catch (error) {
        if (forceRefresh) throw error
        // 未到期的令牌仍可继续用；网络抖动不应立刻多发一次注册。
        if (stored.token && stored.expiresAt > Date.now() + 5 * 60 * 1000) {
          console.warn('[client] 实例令牌续签失败，暂时沿用尚未过期的令牌', error)
          identityCache = { ...stored, pair }
          return identityCache
        }
        console.warn('[client] 实例令牌已过期且续签失败，将重新登记本机实例', error)
        clearStoredIdentity()
        const registered = await registerIdentity(pair)
        saveStoredIdentity(registered)
        identityCache = { ...registered, pair }
        return identityCache
      }
    }
  }

  const registered = await registerIdentity(pair || undefined)
  pair = keyPairCache
  saveStoredIdentity(registered)
  identityCache = { ...registered, pair }
  return identityCache
}

function ensureIdentity(forceRefresh = false) {
  if (!forceRefresh && identityCache?.token && identityCache.expiresAt > Date.now() + TOKEN_REFRESH_AHEAD_MS) {
    return Promise.resolve(identityCache)
  }
  if (identityInFlight) return identityInFlight
  identityInFlight = buildIdentity(forceRefresh)
    .catch((error) => {
      console.warn('[client] 建立客户端实例身份失败', error)
      throw error
    })
    .finally(() => {
      identityInFlight = null
    })
  return identityInFlight
}

/** 应用启动时调用：后台完成首次注册 / 续签，避免首个业务请求额外等待。 */
export function primeClientIdentity() {
  return ensureIdentity().catch(() => null)
}

function recoverIdentity() {
  if (recoveryInFlight) return recoveryInFlight
  identityCache = null
  recoveryInFlight = ensureIdentity(true)
    .catch((error) => {
      console.warn('[client] 恢复客户端实例身份失败', error)
      return null
    })
    .finally(() => {
      recoveryInFlight = null
    })
  return recoveryInFlight
}

function canonicalRequest(method, path, query, identity, timestamp, bodyHash, nonce) {
  return [
    PROTOCOL_VERSION,
    String(method || 'GET').toUpperCase(),
    path,
    query || '',
    identity.clientId,
    identity.instanceId,
    identity.platform,
    timestamp,
    bodyHash,
    nonce || '',
  ].join('\n')
}

async function identityHeaders(method, url, bodyHash, nonce) {
  const identity = await ensureIdentity()
  const target = new URL(String(url), window.location.href)
  const timestamp = Math.floor(Date.now() / 1000)
  const canonical = canonicalRequest(
    method,
    canonicalPath(target),
    target.search.startsWith('?') ? target.search.slice(1) : '',
    identity,
    timestamp,
    bodyHash,
    nonce,
  )
  return {
    [HEADER_CLIENT_ID]: identity.clientId,
    [HEADER_INSTANCE]: identity.instanceId,
    [HEADER_TOKEN]: identity.token,
    [HEADER_TIMESTAMP]: String(timestamp),
    [HEADER_SIGNATURE]: await signCanonical(identity.pair, canonical),
  }
}

async function requestBodyHash(request) {
  const method = request.method.toUpperCase()
  if (method === 'GET' || method === 'HEAD' || !request.body) return EMPTY_BODY_HASH
  const contentType = request.headers.get('Content-Type') || ''
  if (contentType.toLowerCase().startsWith('multipart/')) return STREAMED_BODY_HASH
  try {
    const bytes = await request.clone().arrayBuffer()
    return await sha256Hex(new Uint8Array(bytes))
  } catch (error) {
    console.warn('[client] 读取请求体失败，无法计算签名摘要', error)
    return EMPTY_BODY_HASH
  }
}

function isInvalidClientResponse(response) {
  return response?.status === 401 && response.headers.get(HEADER_CLIENT_STATUS) === 'invalid'
}

function patchFetch() {
  if (typeof window === 'undefined' || typeof window.fetch !== 'function') return
  const previousFetch = window.fetch.bind(window)
  window.fetch = async (input, init) => {
    const rawUrl =
      typeof input === 'string' || input instanceof URL
        ? String(input)
        : input instanceof Request
          ? input.url
          : ''
    const method = init?.method || (input instanceof Request ? input.method : 'GET')
    if (!rawUrl || !shouldAttachHeader(rawUrl)) return previousFetch(input, init)

    let request
    try {
      request = input instanceof Request ? new Request(input, init) : new Request(rawUrl, init)
    } catch (error) {
      console.warn('[client] 构造签名请求失败，按原请求发送', error)
      return previousFetch(input, init)
    }

    try {
      const baseHeaders = new Headers(request.headers)
      applyBaseRequestHeaders(baseHeaders)
      request = new Request(request, { headers: baseHeaders })
    } catch (error) {
      console.warn('[client] 补客户端基础标头失败，按原请求发送', error)
      return previousFetch(input, init)
    }
    if (!shouldAttachIdentity(rawUrl, method)) return previousFetch(request)

    try {
      const bodyHash = await requestBodyHash(request)
      const nonce = request.headers.get(NONCE_HEADER) || ''
      const headers = new Headers(request.headers)
      for (const [name, value] of Object.entries(
        await identityHeaders(request.method, request.url, bodyHash, nonce),
      )) {
        headers.set(name, value)
      }
      const signed = new Request(request, { headers })
      let retry = null
      try {
        retry = signed.clone()
      } catch {
        // 流式 body 无法克隆：只能尝试一次
      }
      const response = await previousFetch(signed)
      if (retry && isInvalidClientResponse(response)) {
        const recovered = await recoverIdentity()
        if (recovered?.token) {
          const retryHeaders = new Headers(retry.headers)
          for (const [name, value] of Object.entries(
            await identityHeaders(retry.method, retry.url, bodyHash, retry.headers.get(NONCE_HEADER) || ''),
          )) {
            retryHeaders.set(name, value)
          }
          return previousFetch(new Request(retry, { headers: retryHeaders }))
        }
      }
      return response
    } catch (error) {
      console.warn('[client] 客户端身份签名失败，按原请求发送', error)
      return previousFetch(input, init)
    }
  }
}

async function xhrBodyHash(body, headerMap) {
  if (body === null || body === undefined) return EMPTY_BODY_HASH
  if (typeof FormData !== 'undefined' && body instanceof FormData) return STREAMED_BODY_HASH
  if (typeof Blob !== 'undefined' && body instanceof Blob) {
    return sha256Hex(new Uint8Array(await body.arrayBuffer()))
  }
  if (body instanceof ArrayBuffer) return sha256Hex(new Uint8Array(body))
  if (ArrayBuffer.isView(body)) {
    return sha256Hex(new Uint8Array(body.buffer, body.byteOffset, body.byteLength))
  }
  if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
    return sha256Hex(new TextEncoder().encode(String(body)))
  }
  if (typeof body === 'string') return sha256Hex(new TextEncoder().encode(body))
  const contentType = headerMap.get('content-type') || ''
  if (contentType.toLowerCase().startsWith('multipart/')) return STREAMED_BODY_HASH
  try {
    return sha256Hex(new TextEncoder().encode(String(body)))
  } catch {
    return STREAMED_BODY_HASH
  }
}

function patchXhr() {
  if (typeof window === 'undefined' || typeof XMLHttpRequest === 'undefined') return
  const proto = XMLHttpRequest.prototype
  const originalOpen = proto.open
  const originalSetRequestHeader = proto.setRequestHeader
  const originalSend = proto.send
  const originalAbort = proto.abort

  proto.open = function (method, url, ...rest) {
    this.__nekoClientMethod = method
    this.__nekoClientUrl = url
    this.__nekoClientHeaders = new Map()
    this.__nekoClientSent = false
    this.__nekoClientAborted = false
    return originalOpen.call(this, method, url, ...rest)
  }

  proto.setRequestHeader = function (name, value) {
    this.__nekoClientHeaders = this.__nekoClientHeaders || new Map()
    this.__nekoClientHeaders.set(String(name).toLowerCase(), String(value))
    return originalSetRequestHeader.call(this, name, value)
  }

  proto.abort = function () {
    this.__nekoClientAborted = true
    return originalAbort.call(this)
  }

  proto.send = function (...args) {
    const self = this
    const sameBackend = shouldAttachHeader(self.__nekoClientUrl)
    if (sameBackend) {
      const headerMap = self.__nekoClientHeaders || new Map()
      for (const [name, value] of Object.entries(nekoRequestHeaders())) {
        if (!headerMap.has(name.toLowerCase())) {
          headerMap.set(name.toLowerCase(), value)
          originalSetRequestHeader.call(self, name, value)
        }
      }
      if (!headerMap.has(AUTHORIZATION_HEADER.toLowerCase())) {
        const authorization = userAuthorizationValue()
        if (authorization) {
          headerMap.set(AUTHORIZATION_HEADER.toLowerCase(), authorization)
          originalSetRequestHeader.call(self, AUTHORIZATION_HEADER, authorization)
        }
      }
    }
    if (!shouldAttachIdentity(self.__nekoClientUrl, self.__nekoClientMethod)) {
      return originalSend.apply(self, args)
    }
    if (self.__nekoClientSent) return undefined
    self.__nekoClientSent = true

    const sendUnsigned = () => {
      if (self.__nekoClientAborted) return undefined
      return originalSend.apply(self, args)
    }
    const finish = async () => {
      if (self.__nekoClientAborted) return undefined
      try {
        const headerMap = self.__nekoClientHeaders || new Map()
        const bodyHash = await xhrBodyHash(args[0], headerMap)
        const nonce = headerMap.get(NONCE_HEADER.toLowerCase()) || ''
        const headers = await identityHeaders(
          self.__nekoClientMethod,
          self.__nekoClientUrl,
          bodyHash,
          nonce,
        )
        for (const [name, value] of Object.entries(headers)) {
          headerMap.set(name.toLowerCase(), value)
          originalSetRequestHeader.call(self, name, value)
        }
      } catch (error) {
        console.warn('[client] XHR 客户端身份签名失败，按原请求发送', error)
      }
      if (self.__nekoClientAborted) return undefined
      const result = originalSend.apply(self, args)
      self.addEventListener(
        'load',
        () => {
          if (self.status === 401 && self.getResponseHeader(HEADER_CLIENT_STATUS) === 'invalid') {
            recoverIdentity()
          }
        },
        { once: true },
      )
      return result
    }
    finish().catch(() => sendUnsigned())
    return undefined
  }
}

/** 安装客户端标识、实例签名与用户令牌注入；须在 installReplayProtection() 之前调用。 */
export function installNekoClientHeader() {
  if (installed || typeof window === 'undefined') return
  installed = true
  directFetch = window.fetch.bind(window)
  for (const [name, value] of Object.entries(STATIC_REQUEST_HEADERS)) {
    axios.defaults.headers.common[name] = value
  }
  patchFetch()
  patchXhr()
}
