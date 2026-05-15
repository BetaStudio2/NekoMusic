import API_CONFIG from '@/config/apiConfig.js'

/**
 * 公开：获取 VIP 价目表
 * @returns {Promise<Array<{id:number,months:number,days:number,priceYuan:number,sortOrder:number,updatedAt:string}>>}
 */
export async function fetchVipPricing() {
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/vip/pricing`)
  const data = await res.json()
  if (!data.success) {
    throw new Error(data.message || '加载价目失败')
  }
  return Array.isArray(data.data) ? data.data : []
}

/**
 * 管理员：全量替换价目
 * @param {Array<{months:number,days:number,priceYuan:number}>} items
 * @param {string} adminToken Bearer 后的 token
 */
export async function replaceVipPricing(items, adminToken) {
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/admin/vip/pricing`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminToken}`
    },
    body: JSON.stringify({ items })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data.success) {
    throw new Error(data.message || `保存失败 (${res.status})`)
  }
  return Array.isArray(data.data) ? data.data : []
}
