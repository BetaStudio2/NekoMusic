<template>
  <div v-if="isMobile" class="mobile-download">
    <div class="download-container">
      <img src="/Neko云音乐.svg" alt="Neko云音乐" class="app-logo" />
      <h1 class="app-name">Neko云音乐</h1>
      <p class="app-slogan">完全免费的音乐平台</p>
      
      <div v-if="loading" class="loading">
        正在获取下载信息...
      </div>
      
      <div v-else-if="error" class="error">
        {{ error }}
      </div>
      
      <div v-else class="download-info">
        <div class="version-info">
          <span class="version-label">当前版本：</span>
          <span class="version-number">{{ versionInfo.ver }}</span>
        </div>
        
        <a :href="versionInfo.updateUrl" class="download-button" download>
          <svg class="download-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 16V4M12 16L8 12M12 16L16 12M4 20H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          立即下载
        </a>
        
        <p class="download-tip">点击按钮即可下载安装包</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const isMobile = ref(false)
const versionInfo = ref({ ver: '', updateUrl: '' })
const loading = ref(true)
const error = ref('')

// 检测是否是移动设备
const checkMobile = () => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera
  return /android|ipad|iphone|ipod/i.test(userAgent)
}

// 获取版本信息
const fetchVersionInfo = async () => {
  try {
    const response = await axios.get('/version.json', {
      timeout: 5000
    })
    versionInfo.value = response.data
  } catch (err) {
    console.error('获取版本信息失败:', err)
    error.value = '获取下载信息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  isMobile.value = checkMobile()
  if (isMobile.value) {
    fetchVersionInfo()
  }
})
</script>

<style scoped>
.mobile-download {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  margin: 0;
  box-sizing: border-box;
}

.download-container {
  text-align: center;
  color: white;
  width: 100%;
  max-width: 400px;
}

.app-logo {
  width: 120px;
  height: 120px;
  margin-bottom: 20px;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.3));
}

.app-name {
  font-size: 2rem;
  font-weight: bold;
  margin-bottom: 10px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.app-slogan {
  font-size: 1.1rem;
  margin-bottom: 40px;
  opacity: 0.9;
}

.loading,
.error {
  font-size: 1rem;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.error {
  color: #ff6b6b;
}

.download-info {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  padding: 30px 20px;
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.version-info {
  margin-bottom: 30px;
  font-size: 1rem;
}

.version-label {
  opacity: 0.8;
}

.version-number {
  font-weight: bold;
  font-size: 1.2rem;
}

.download-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: white;
  color: #667eea;
  text-decoration: none;
  padding: 16px 40px;
  border-radius: 50px;
  font-size: 1.1rem;
  font-weight: bold;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
  transition: all 0.3s ease;
  width: 100%;
  max-width: 280px;
}

.download-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
}

.download-button:active {
  transform: translateY(0);
}

.download-icon {
  width: 24px;
  height: 24px;
}

.download-tip {
  margin-top: 20px;
  font-size: 0.9rem;
  opacity: 0.8;
}
</style>