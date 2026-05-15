import API_CONFIG from '@/config/apiConfig.js'

function getUserToken() {
  return localStorage.getItem('userToken') || ''
}

function authHeaders(extra = {}) {
  return {
    Authorization: getUserToken(),
    ...extra
  }
}

async function parseJson(res) {
  return res.json().catch(() => ({}))
}

/**
 * 创建横屏短视频渲染任务（异步，立即返回 jobId）
 * @param {number} musicId
 * @param {number} [startSec=0]
 */
export async function createVideoRenderJob(musicId, startSec = 0) {
  const token = getUserToken()
  if (!token) {
    throw new Error('请先登录')
  }
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/video/render/create`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ musicId, startSec })
  })
  const data = await parseJson(res)
  if (!res.ok || !data.success) {
    throw new Error(data.message || `创建任务失败 (${res.status})`)
  }
  return data.data || {}
}

/** 查询渲染任务状态 */
export async function fetchVideoRenderStatus(jobId) {
  const token = getUserToken()
  if (!token) {
    throw new Error('请先登录')
  }
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/video/render/${jobId}`, {
    method: 'GET',
    headers: authHeaders()
  })
  const data = await parseJson(res)
  if (!res.ok || !data.success) {
    throw new Error(data.message || `查询失败 (${res.status})`)
  }
  return data.data || {}
}

/**
 * 下载成片 MP4
 * @param {string} jobId
 * @param {string} filename
 */
export async function downloadVideoRenderFile(jobId, filename) {
  const token = getUserToken()
  if (!token) {
    throw new Error('请先登录')
  }
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/video/render/${jobId}/download`, {
    method: 'GET',
    headers: authHeaders()
  })
  if (!res.ok) {
    const data = await parseJson(res)
    throw new Error(data.message || `下载失败 (${res.status})`)
  }
  const blob = await res.blob()
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || `neko-clip-${jobId}.mp4`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
