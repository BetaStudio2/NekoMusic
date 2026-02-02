<template>
  <div class="download-page">
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
      
      <div v-else class="download-sections">
        <!-- 移动端下载 -->
        <div class="mobile-section">
          <div class="mobile-header">
            <div class="mobile-icon-wrapper">
              <svg class="mobile-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M17 2H7C5.34315 2 4 3.34315 4 5V19C4 20.6569 5.34315 22 7 22H17C18.6569 22 20 20.6569 20 19V5C20 3.34315 18.6569 2 17 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12 18H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <h2 class="mobile-title">Android 版</h2>
          </div>
          
          <div class="mobile-version">
            <span class="version-text">版本 {{ versionInfo.ver }}</span>
          </div>
          
          <a :href="versionInfo.updateUrl" class="mobile-download-btn" download>
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 16V4M12 16L8 12M12 16L16 12M4 20H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            立即下载
          </a>
          
          <div class="mobile-features">
            <div class="feature-tag">完全免费</div>
            <div class="feature-tag">海量音乐</div>
            <div class="feature-tag">高品质</div>
          </div>
        </div>
        
        <!-- PC端下载 -->
        <div class="pc-section">
          <div class="pc-header">
            <div class="pc-icon-wrapper">
              <svg class="pc-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="2" y="3" width="20" height="14" rx="2" stroke="currentColor" stroke-width="2"/>
                <path d="M6 21H18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M10 21V19H14V21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </div>
            <h2 class="pc-title">桌面版</h2>
          </div>
          
          <div class="pc-version">
            <span class="version-text">v{{ versionInfo.pc?.pc_ver || versionInfo.ver }}</span>
          </div>
          
          <div class="platform-downloads">
            <!-- Windows -->
            <a :href="windowsDownloadUrl" class="platform-btn" download>
              <svg class="platform-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M0 3.449L9.75 2.1v9.451H0m10.949-9.602L24 0v11.4H10.949M0 12.6h9.75v9.451L0 20.699M10.949 12.6H24V24l-12.9-1.801"/>
              </svg>
              <div class="platform-info">
                <span class="platform-name">Windows</span>
                <span class="platform-desc">.exe 安装包</span>
              </div>
            </a>
            
            <!-- Linux -->
            <a :href="linuxDownloadUrl" class="platform-btn" download>
              <svg class="platform-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-5.5-2.5l7.51-3.22-7.52-3.22L6.5 9l7.51 3.22L6.5 15.5l7.52 3.22L14 18l-7.51-3.22z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <div class="platform-info">
                <span class="platform-name">Linux</span>
                <span class="platform-desc">.deb 包</span>
              </div>
            </a>
            
            <!-- macOS -->
            <a :href="macDownloadUrl" class="platform-btn" download>
              <svg class="platform-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
              </svg>
              <div class="platform-info">
                <span class="platform-name">macOS</span>
                <span class="platform-desc">.dmg 镜像</span>
              </div>
            </a>
          </div>
          
          <div class="pc-features">
            <div class="feature-item" v-for="(feature, index) in versionInfo.pc?.features || []" :key="index">
              <svg class="check-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M20 6L9 17L4 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span>{{ feature }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'

const versionInfo = ref({ ver: '', updateUrl: '' })
const loading = ref(true)
const error = ref('')

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

// 替换版本号占位符
const replaceVersion = (url) => {
  if (!url) return ''
  const pcVer = versionInfo.value.pc?.pc_ver || versionInfo.value.ver
  return url.replace('{pc_ver}', pcVer)
}

// 计算属性：获取实际的下载链接
const windowsDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.windows || versionInfo.value.pc?.downloadUrl || versionInfo.value.updateUrl
  return replaceVersion(url)
})

const linuxDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.Linux
  return replaceVersion(url)
})

const macDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.mac
  return replaceVersion(url)
})

onMounted(() => {
  fetchVersionInfo()
})
</script>

<style scoped>
.download-page {
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
  overflow-y: auto;
}

.download-container {
  text-align: center;
  color: white;
  width: 100%;
  max-width: 800px;
  padding: 20px 0;
}

.app-logo {
  width: 100px;
  height: 100px;
  margin-bottom: 15px;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.3));
}

.app-name {
  font-size: 2rem;
  font-weight: bold;
  margin-bottom: 8px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.app-slogan {
  font-size: 1rem;
  margin-bottom: 35px;
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

.download-sections {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

/* 移动端样式 - 圆润可爱风格 */
.mobile-section {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.25), rgba(255, 255, 255, 0.1));
  border-radius: 30px;
  padding: 35px 25px;
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
}

.mobile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}

.mobile-icon-wrapper {
  width: 70px;
  height: 70px;
  background: linear-gradient(135deg, #ff9ec0, #ff69b4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 15px;
  box-shadow: 0 4px 15px rgba(255, 105, 180, 0.4);
}

.mobile-icon {
  width: 35px;
  height: 35px;
  color: white;
}

.mobile-title {
  font-size: 1.4rem;
  font-weight: bold;
  color: white;
  margin: 0;
}

.mobile-version {
  margin-bottom: 25px;
}

.mobile-version .version-text {
  background: rgba(255, 255, 255, 0.2);
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 0.95rem;
  font-weight: 600;
}

.mobile-download-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: linear-gradient(135deg, #ff69b4, #ff1493);
  color: white;
  text-decoration: none;
  padding: 15px 45px;
  border-radius: 30px;
  font-size: 1.1rem;
  font-weight: bold;
  box-shadow: 0 4px 15px rgba(255, 20, 147, 0.4);
  transition: all 0.3s ease;
  width: 100%;
  max-width: 250px;
}

.mobile-download-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(255, 20, 147, 0.6);
}

.mobile-download-btn .btn-icon {
  width: 22px;
  height: 22px;
}

.mobile-features {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 25px;
  flex-wrap: wrap;
}

.feature-tag {
  background: rgba(255, 255, 255, 0.15);
  padding: 6px 14px;
  border-radius: 15px;
  font-size: 0.85rem;
  font-weight: 500;
}

/* PC端样式 - 简洁商务风格 */
.pc-section {
  background: linear-gradient(145deg, rgba(20, 20, 30, 0.95), rgba(30, 30, 45, 0.9));
  border-radius: 12px;
  padding: 40px 30px;
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.pc-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  margin-bottom: 25px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 20px;
}

.pc-icon-wrapper {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 15px rgba(59, 130, 246, 0.3);
}

.pc-icon {
  width: 32px;
  height: 32px;
  color: white;
}

.pc-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: white;
  margin: 0;
  letter-spacing: 0.5px;
}

.pc-version {
  margin-bottom: 25px;
}

.pc-version .version-text {
  background: rgba(59, 130, 246, 0.2);
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 0.95rem;
  font-weight: 700;
  color: white;
  letter-spacing: 0.5px;
}

.platform-downloads {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 30px;
}

.platform-btn {
  display: flex;
  align-items: center;
  gap: 15px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: white;
  text-decoration: none;
  padding: 14px 20px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.platform-btn:hover {
  background: rgba(59, 130, 246, 0.2);
  border-color: rgba(59, 130, 246, 0.4);
  transform: translateX(5px);
}

.platform-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.platform-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.platform-name {
  font-size: 1rem;
  font-weight: 600;
}

.platform-desc {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.6);
}

.pc-features {
  margin-top: 30px;
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.85);
}

.feature-item .check-icon {
  width: 18px;
  height: 18px;
  color: #10b981;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .download-container {
    max-width: 400px;
  }
  
  .mobile-section,
  .pc-section {
    padding: 30px 20px;
  }
  
  .mobile-title,
  .pc-title {
    font-size: 1.2rem;
  }
  
  .mobile-download-btn {
    font-size: 1rem;
    padding: 14px 35px;
  }
  
  .pc-header {
    flex-direction: column;
    gap: 10px;
  }
  
  .platform-btn {
    padding: 12px 16px;
  }
  
  .platform-icon {
    width: 24px;
    height: 24px;
  }
  
  .platform-name {
    font-size: 0.95rem;
  }
  
  .platform-desc {
    font-size: 0.8rem;
  }
}
</style>