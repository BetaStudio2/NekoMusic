<template>
  <Layout />
  <UpdateNotification 
    v-if="updateAvailable && updateInfo"
    :version="updateInfo.version"
    :downloadUrl="updateInfo.downloadUrl"
    @ignore="handleIgnoreUpdate"
    @update="handleUpdate"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Layout from './components/Layout.vue'
import UpdateNotification from './components/UpdateNotification.vue'
import apiConfig from './config/apiConfig'
import { APP_VERSION } from './version'

const updateAvailable = ref(false)
const updateInfo = ref(null)

// 检测系统类型
const getOSType = () => {
  const platform = navigator.platform.toLowerCase()
  if (platform.includes('win')) return 'windows'
  if (platform.includes('mac')) return 'mac'
  if (platform.includes('linux')) return 'linux'
  return 'windows'
}

// 统一的 API 请求函数
async function apiRequest(url, options = {}) {
  const fullUrl = url.startsWith('http') ? url : `${apiConfig.BASE_URL}${url}`
  const separator = fullUrl.includes('?') ? '&' : '?'
  const urlWithTimestamp = `${fullUrl}${separator}_t=${Date.now()}`
  
  return fetch(urlWithTimestamp, {
    ...options,
    headers: {
      'Cache-Control': 'no-cache, no-store, must-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0',
      ...(options.headers || {})
    },
    cache: 'no-store'
  })
}

const checkForUpdates = async () => {
  try {
    const response = await apiRequest(apiConfig.UPDATE_CHECK, {
      cache: 'no-store',
      headers: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache',
        'Expires': '0'
      }
    })
    const data = await response.json()
    
    if (data.pc && data.pc.pc_ver) {
      const remoteVersion = data.pc.pc_ver
      
      if (remoteVersion !== APP_VERSION) {
        const osType = getOSType()
        let downloadUrl = ''
        
        if (data.pc[osType]) {
          downloadUrl = data.pc[osType].replace('{pc_ver}', remoteVersion)
        } else {
          downloadUrl = data.pc.windows.replace('{pc_ver}', remoteVersion)
        }
        
        updateAvailable.value = true
        updateInfo.value = {
          version: remoteVersion,
          downloadUrl: downloadUrl
        }
      }
    }
  } catch (error) {
    console.error('检查更新失败:', error)
  }
}

const handleIgnoreUpdate = () => {
  updateAvailable.value = false
  updateInfo.value = null
}

const handleUpdate = () => {
  if (updateInfo.value && updateInfo.value.downloadUrl) {
    window.dispatchEvent(new CustomEvent('start-download', { 
      detail: { 
        url: updateInfo.value.downloadUrl,
        version: updateInfo.value.version
      } 
    }))
    handleIgnoreUpdate()
  }
}

onMounted(() => {
  // 延迟1秒后检查更新，避免影响启动速度
  setTimeout(() => {
    checkForUpdates()
  }, 1000)
})
</script>

<style scoped>
/* 不需要额外样式 */
</style>
