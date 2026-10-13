import API_CONFIG from '@/config/apiConfig.js'

/**
 * 后台日志查询：访问日志（安全分析 / 访问量统计）与操作日志（管理动作留痕）。
 *
 * 只有管理后台使用；权限由服务端把关：
 *  - 访问日志：除普通用户外的角色均可查看（审核员及以上）
 *  - 操作日志：仅管理员及以上
 */

function adminAuthHeader() {
  const token = localStorage.getItem('adminToken')
  if (!token) {
    throw new Error('请先登录管理后台')
  }
  return { Authorization: `Bearer ${token}` }
}

async function readJson(res) {
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data.success) {
    throw new Error(data.message || `请求失败 (${res.status})`)
  }
  return data.data
}

/** 去掉空值，拼查询串 */
function toQuery(params) {
  const search = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return
    search.set(key, String(value))
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

/**
 * 访问日志分页检索。
 * @param {{page?:number,size?:number,ip?:string,path?:string,clientType?:string,status?:number,from?:number,to?:number,keyword?:string}} params
 * @returns {Promise<{total:number,page:number,size:number,items:Array<object>}>}
 */
export async function fetchAccessLogs(params) {
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/logs/access${toQuery(params)}`, {
    headers: { 'Content-Type': 'application/json', ...adminAuthHeader() }
  })
  return readJson(res)
}

/**
 * 访问量概览与聚合榜。
 * @param {number} days 统计窗口（天）
 */
export async function fetchAccessLogStats(days) {
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/logs/access/stats${toQuery({ days })}`, {
    headers: { 'Content-Type': 'application/json', ...adminAuthHeader() }
  })
  return readJson(res)
}

/**
 * 操作日志分页检索。
 * @param {{page?:number,size?:number,adminId?:number,module?:string,from?:number,to?:number,keyword?:string}} params
 * @returns {Promise<{total:number,page:number,size:number,items:Array<object>}>}
 */
export async function fetchOperationLogs(params) {
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/logs/operations${toQuery(params)}`, {
    headers: { 'Content-Type': 'application/json', ...adminAuthHeader() }
  })
  return readJson(res)
}
