import API_CONFIG from '@/config/apiConfig.js'

/**
 * 歌曲评论 API
 * ------------------------------------------------------------
 * 列表 / 发表 / 回复 / 删除全部复用同一个端点 `/api/comments`：
 *   - GET    /api/comments?musicId=&page=&pageSize=  拉取楼层（含回复）
 *   - POST   /api/comments                           发表评论或回复（parentId）
 *   - DELETE /api/comments?id=                       删除自己的评论
 */
const ENDPOINT = '/api/comments'

function authHeaders(extra = {}) {
  const token = typeof window === 'undefined' ? null : localStorage.getItem('userToken')
  return token ? { ...extra, Authorization: `Bearer ${token}` } : { ...extra }
}

async function request(url, options = {}) {
  const response = await fetch(url, options)
  let body = null
  try {
    body = await response.json()
  } catch {
    body = null
  }
  if (!response.ok || (body && body.success === false)) {
    throw new Error((body && body.message) || `请求失败（${response.status}）`)
  }
  return body
}

/** 拉取某首歌的评论楼层；回复随楼层一起返回。 */
export async function fetchComments(musicId, { page = 1, pageSize } = {}) {
  const params = new URLSearchParams({ musicId: String(musicId), page: String(page) })
  if (pageSize) params.set('pageSize', String(pageSize))
  const body = await request(`${API_CONFIG.BASE_URL}${ENDPOINT}?${params.toString()}`, {
    headers: authHeaders(),
  })
  return body?.data ?? { comments: [], total: 0, totalComments: 0, hasMore: false }
}

/** 发表评论（parentId 为空）或回复（parentId 为楼层 / 回复的 id）。 */
export async function postComment({ musicId, content, parentId = null }) {
  const payload = parentId ? { musicId, content, parentId } : { musicId, content }
  const body = await request(`${API_CONFIG.BASE_URL}${ENDPOINT}`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(payload),
  })
  return body?.data ?? null
}

/** 删除自己的评论（管理员令牌可删任意一条）。 */
export async function deleteComment(id) {
  const params = new URLSearchParams({ id: String(id) })
  return request(`${API_CONFIG.BASE_URL}${ENDPOINT}?${params.toString()}`, {
    method: 'DELETE',
    headers: authHeaders(),
  })
}
