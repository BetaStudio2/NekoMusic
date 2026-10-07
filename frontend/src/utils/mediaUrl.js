// 播放地址解析
// ------------------------------------------------------------
// 后端 GET /api/music/file/{id} 返回 JSON：
//   {"success":true,"data":{"url":"/media/music/..."}}
// 该接口由通用防重放机制保护（见 utils/replayNonce.js），fetch 拦截器会自动补一次性
// X-Neko-Nonce，因此这里直接 fetch 即可；重放 / 过期会在服务端被拒（409）。
//
// 返回的 /media/music/... 是固定地址、由 CDN 共享缓存，缓存语义不变。
import API_CONFIG from '@/config/apiConfig.js'

/**
 * 解析指定音乐的可用媒体地址。
 * @param {number|string} musicId 音乐 ID
 * @param {{ quality?: string }} [options]
 * @returns {Promise<string>} 媒体地址（绝对 URL）
 */
export async function resolveMediaUrl(musicId, options = {}) {
  if (musicId == null || musicId === '') {
    throw new Error('musicId 不能为空')
  }
  const query = options.quality ? `?quality=${encodeURIComponent(options.quality)}` : ''
  const response = await fetch(
    `${API_CONFIG.BASE_URL}/api/music/file/${musicId}${query}`,
    { cache: 'no-store' }
  )
  if (!response.ok) {
    throw new Error(`获取播放地址失败：HTTP ${response.status}`)
  }
  const json = await response.json()
  const url = json?.data?.url
  if (!url) {
    throw new Error('播放地址响应缺少 url')
  }
  return url.startsWith('http') ? url : `${API_CONFIG.BASE_URL}${url}`
}

export default resolveMediaUrl
