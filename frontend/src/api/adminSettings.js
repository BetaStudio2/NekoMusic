import API_CONFIG from '@/config/apiConfig.js'

/**
 * 后台系统设置：读取与保存运行时配置。
 *
 * 服务端把设置项按分组返回，每项自带类型与约束（min/max、是否需重启）。敏感项只回传
 * `configured`（是否已配置），不回传明文；保存时未提交的敏感项保持原值。
 */

/** 取管理员令牌；缺失时抛错，由页面跳回登录页 */
function requireAdminToken() {
  const token = localStorage.getItem('adminToken')
  if (!token) {
    throw new Error('请先登录管理员账号')
  }
  return token
}

async function readJson(res) {
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data.success) {
    throw new Error(data.message || `请求失败 (${res.status})`)
  }
  return data.data
}

/**
 * 读取全部系统设置。
 * @returns {Promise<Array<{key:string,title:string,settings:Array<object>}>>}
 */
export async function fetchAdminSettings() {
  const token = requireAdminToken()
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/settings`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const data = await readJson(res)
  return Array.isArray(data?.groups) ? data.groups : []
}

/**
 * 批量保存设置。
 * @param {Record<string, string>} values 键为设置项 key，值统一用字符串表示
 * @returns {Promise<{updated:number,restartRequired:string[]}>}
 */
export async function saveAdminSettings(values) {
  const token = requireAdminToken()
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/settings`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify({ values })
  })
  const data = await readJson(res)
  return {
    updated: Number(data?.updated) || 0,
    restartRequired: Array.isArray(data?.restartRequired) ? data.restartRequired : []
  }
}
