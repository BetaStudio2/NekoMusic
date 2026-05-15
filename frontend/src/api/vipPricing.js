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
/**
 * 已登录用户发起 VIP 支付（ZPay mapi）
 * @param {number} pricingId vip_pricing.id
 * @param {'alipay'|'wxpay'} payType
 * @returns {Promise<{ outTradeNo?: string, payurl?: string, payurl2?: string, qrcode?: string, img?: string, O_id?: string, trade_no?: string }>}
 */
export async function createVipPayOrder(pricingId, payType) {
  const token = localStorage.getItem('userToken')
  if (!token) {
    throw new Error('请先登录')
  }
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/vip/pay/create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token
    },
    body: JSON.stringify({ pricingId, payType })
  })
  const data = await res.json().catch(() => ({}))
  if (!res.ok || !data.success) {
    throw new Error(data.message || `下单失败 (${res.status})`)
  }
  return data.data && typeof data.data === 'object' ? data.data : {}
}

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
