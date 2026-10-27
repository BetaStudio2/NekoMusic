import API_CONFIG from '@/config/apiConfig.js'

function adminAuthHeader() {
  const token = localStorage.getItem('adminToken')
  if (!token) {
    throw new Error('请先登录管理后台')
  }
  return { Authorization: `Bearer ${token}` }
}

async function adminRequest(path, options = {}) {
  const res = await fetch(`${API_CONFIG.BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...adminAuthHeader(),
      ...(options.headers || {})
    }
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data.success) {
    throw new Error(data.message || `请求失败 (${res.status})`)
  }
  return data.data || {}
}

export async function fetchAdminClientApps() {
  const data = await adminRequest('/api/admin/client-apps')
  return Array.isArray(data.items) ? data.items : []
}

/**
 * @param {{
 *   name: string,
 *   ownerLabel?: string,
 *   platform: string,
 *   confidential: boolean,
 *   attestationRequired: boolean,
 *   allowedOrigins?: string,
 *   packageName?: string,
 *   signingDigest?: string,
 *   expiresDays?: number
 * }} payload
 */
export async function createAdminClientApp(payload) {
  return adminRequest('/api/admin/client-apps', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function updateAdminClientAppStatus(clientId, status) {
  return adminRequest(`/api/admin/client-apps/${encodeURIComponent(clientId)}`, {
    method: 'PUT',
    body: JSON.stringify({ status })
  })
}

export async function rotateAdminClientSecret(clientId) {
  return adminRequest(`/api/admin/client-apps/${encodeURIComponent(clientId)}/secret`, {
    method: 'POST'
  })
}

export async function fetchAdminClientInstances(clientId = '') {
  const query = clientId ? `?client_id=${encodeURIComponent(clientId)}` : ''
  const data = await adminRequest(`/api/admin/client-instances${query}`)
  return Array.isArray(data.items) ? data.items : []
}

export async function revokeAdminClientInstance(instanceId) {
  return adminRequest(`/api/admin/client-instances/${encodeURIComponent(instanceId)}`, {
    method: 'DELETE'
  })
}
