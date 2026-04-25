<template>
  <div class="download-page">
    <div class="background-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
    </div>
    
    <div class="download-container">
      <div class="header-section">
        <div class="logo-wrapper">
          <img src="/Neko云音乐.svg" alt="Neko云音乐" class="app-logo" />
          <div class="logo-glow"></div>
        </div>
        <h1 class="app-name">Neko云音乐</h1>
        <p class="app-slogan">完全免费的音乐平台</p>
        <div class="slogan-decoration"></div>
      </div>
      
      <div v-if="loading" class="loading">
        <div class="loading-spinner"></div>
        <p>正在获取下载信息...</p>
      </div>
      
      <div v-else-if="error" class="error">
        <svg class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <circle cx="12" cy="12" r="10" stroke-width="2"/>
          <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round"/>
        </svg>
        <p>{{ error }}</p>
      </div>
      
      <div v-else class="download-sections">
        <!-- 移动端下载 -->
        <div class="mobile-section">
          <div class="section-badge">移动端</div>
          <div class="mobile-header">
            <div class="mobile-icon-wrapper">
              <svg class="mobile-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M17 2H7C5.34315 2 4 3.34315 4 5V19C4 20.6569 5.34315 22 7 22H17C18.6569 22 20 20.6569 20 19V5C20 3.34315 18.6569 2 17 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12 18H12.01" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <div class="icon-glow"></div>
            </div>
            <h2 class="mobile-title">Android 版</h2>
          </div>
          
          <div class="mobile-version">
            <span class="version-label">当前版本</span>
            <span class="version-text">{{ versionInfo.ver }}</span>
          </div>
          
          <a :href="versionInfo.updateUrl" class="mobile-download-btn" download>
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 16V4M12 16L8 12M12 16L16 12M4 20H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>立即下载</span>
            <div class="btn-shine"></div>
          </a>

        </div>
        
        <!-- PC端下载 -->
        <div class="pc-section">
          <div class="section-badge pc-badge">桌面版</div>
          <div class="pc-header">
            <div class="pc-icon-wrapper">
              <svg class="pc-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="2" y="3" width="20" height="14" rx="2" stroke="currentColor" stroke-width="2"/>
                <path d="M6 21H18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M10 21V19H14V21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              </svg>
              <div class="icon-glow"></div>
            </div>
            <h2 class="pc-title">桌面版</h2>
          </div>
          
          <div class="pc-version">
            <span class="version-label">最新版本</span>
            <span class="version-text">v{{ versionInfo.pc?.pc_ver || versionInfo.ver }}</span>
          </div>
          
          <div class="platform-downloads">
            <!-- Windows -->
            <a :href="windowsDownloadUrl" class="platform-btn" download>
              <div class="platform-icon-wrapper">
                <svg class="platform-icon" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M0 3.449L9.75 2.1v9.451H0m10.949-9.602L24 0v11.4H10.949M0 12.6h9.75v9.451L0 20.699M10.949 12.6H24V24l-12.9-1.801"/>
                </svg>
              </div>
              <div class="platform-info">
                <span class="platform-name">Windows</span>
                <span class="platform-desc">.exe</span>
              </div>
              <svg class="download-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M7 10L12 15L17 10" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12 15V3" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </a>
            
            <!-- Linux -->
            <a :href="linuxDownloadUrl" class="platform-btn" download>
              <div class="platform-icon-wrapper">
                <svg t="1770027777023" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="1772" width="128" height="128"><path d="M896 837.12c0 20.48-24.746667 37.546667-82.986667 55.68-46.72 14.72-74.88 78.506667-133.546666 70.186667-45.866667-6.4-44.373333-33.28-72.96-43.733334-26.453333-9.6-157.44-12.16-192 21.76-43.093333 42.666667-134.826667 0.426667-166.613334-7.68-42.88-11.093333-108.8-19.2-117.546666-39.68-9.173333-21.333333 7.68-33.493333 14.293333-61.44 3.626667-15.573333-19.2-50.56-11.306667-68.053333 11.093333-24.32 48.853333-7.893333 67.2-18.346667 17.28-6.4 27.733333-24.106667 25.386667-42.24 7.893333 25.813333 3.626667 41.6-16 52.906667-24.32 13.866667-56.533333-1.706667-62.72 12.8-7.04 16.426667 17.706667 42.453333 12.8 67.2-5.76 29.226667-20.053333 36.053333-13.226667 50.346667 5.333333 11.52 73.813333 20.266667 111.146667 29.013333 30.933333 7.253333 110.506667 44.373333 140.8 3.413333 20.906667-28.586667-13.866667-84.693333-17.066667-90.453333a1027.413333 1027.413333 0 0 0-84.266666-120.746667c-43.306667-45.226667-53.333333-16-60.16-19.2-6.826667-3.2-9.6-43.733333 12.373333-76.586666 16.853333-25.173333 33.28-87.893333 53.973333-126.293334 9.173333-17.066667 81.28-105.813333 81.28-124.16-0.213333-69.76-57.813333-286.72 108.8-292.906666 142.933333-5.333333 157.653333 126.72 157.653334 161.706666-1.92 36.266667 3.2 72.533333 14.933333 106.666667 42.453333 110.293333 92.16 106.453333 131.2 229.12 35.413333 110.933333 10.026667 138.453333-1.706667 138.24-27.733333-0.64-27.733333 58.453333-80 49.28-34.346667-5.973333-34.346667-55.893333-57.173333-48.426667-17.28 5.76-17.706667 45.013333-10.24 69.546667 20.266667 66.133333-30.293333 136.746667 24.106667 160 49.066667 20.906667 78.506667-39.68 125.013333-54.826667 69.12-22.613333 83.84-33.493333 83.626667-46.08-0.213333-16.213333-29.44-17.706667-48-33.28a60.949333 60.949333 0 0 1-23.253334-51.2c0.213333-9.813333 2.773333-19.413333 7.893334-27.946666-0.213333 24.32 8.32 48 24.533333 66.133333 23.68 20.693333 53.76 18.56 53.76 49.28z m-221.226667-189.44c-3.413333-13.866667-2.773333-70.4-7.466666-90.453333-16.426667-68.693333-38.826667-81.92-38.826667-82.346667-11.733333-76.373333-49.706667-90.453333-49.706667-122.666667 0-15.36 6.613333-21.333333 6.613334-41.6 0-12.8-21.12-12.8-35.2-19.413333-9.813333-4.48-19.626667-8.746667-29.653334-13.013333-2.986667-5.973333-4.48-12.586667-4.266666-19.413334 0-2.773333 1.066667-32.64 24.533333-32.64 27.52 0 27.306667 33.066667 27.306667 33.066667 0 11.733333-5.12 13.226667-5.12 20.266667 0 3.84 4.693333 5.973333 8.533333 5.973333 7.466667 0 14.08-8.106667 15.146667-19.626667 2.773333-30.72-8.106667-66.986667-42.453334-69.76-48.213333-4.053333-47.573333 49.706667-44.8 74.24-11.733333-6.613333-25.813333-8.32-38.826666-4.48 0-33.28-6.826667-66.346667-30.506667-64.853333-23.68 1.706667-29.226667 28.8-29.226667 44.16 0 28.586667 11.946667 40.106667 14.72 41.386667 2.986667 1.28 7.466667-1.493333 9.386667-5.12 1.28-2.773333-1.28-4.053333-3.413333-4.053334-3.413333 0-12.373333-10.88-12.373334-23.893333 0-10.24 4.693333-25.386667 20.266667-25.386667 12.373333 0 18.986667 16.853333 19.413333 28.586667 0.213333 4.693333-0.213333 9.386667-1.066666 14.08-8.96 8.96-18.56 17.28-29.013334 24.32-6.613333 3.626667-18.346667 12.16-18.346666 21.333333-0.213333 2.986667 1.493333 5.973333 4.053333 7.253334 10.24 4.48 20.906667 29.013333 47.36 29.013333 36.693333 0 72.533333-10.24 103.68-29.653333 4.266667-6.186667 10.24-8.106667 13.226667-4.266667 4.266667 5.333333-2.346667 12.16-8.533334 13.44-30.933333 13.44-62.72 24.533333-95.36 33.066667-24.96 2.133333-33.706667-7.893333-34.346666-1.706667 10.24 12.8 25.386667 20.48 41.813333 21.12 31.146667-2.133333 59.733333-26.24 80-33.706667 8.106667-2.773333 11.093333-0.426667 11.946667 1.493334 0.853333 1.92 1.706667 7.253333-9.813334 11.52-31.573333 11.733333-73.173333 54.4-92.373333 54.4-27.733333 0-46.293333-53.12-55.253333-51.2-2.133333 0.426667 0 11.52-4.906667 25.386666-4.48 12.16-20.693333 37.973333-26.666667 55.253334-5.333333 13.653333-4.266667 28.8 2.773334 41.6a207.637333 207.637333 0 0 0-55.893334 146.773333c3.413333 46.293333-11.52 38.186667-11.52 38.186667 13.44 26.453333 33.066667 49.066667 57.173334 66.346666 27.946667 22.4 54.186667 46.933333 54.826666 60.8-1.28 8.32-4.693333 16-10.24 22.4 6.186667 9.173333 13.44 17.493333 21.76 24.96 23.253333 18.773333 53.973333 25.6 82.986667 17.92 34.346667-0.64 66.773333-16.426667 88.32-43.306666 8.746667-11.946667 33.706667-31.146667 28.373333-49.066667-7.253333-24.746667-8.106667-91.093333 34.986667-81.493333 0.213333-11.946667 7.04-22.826667 17.706667-28.16 0.213333-0.64-8.106667-2.133333-11.733334-17.066667z" p-id="1773" fill="#ffffff"></path><path d="M500.693333 262.4c-1.28 0-2.986667-1.493333-4.693333-4.693333a8.064 8.064 0 0 0-7.893333-4.48c8.746667-1.493333 13.653333 0.426667 15.146666 5.12 0.64 1.493333 0 3.2-1.493333 3.626666-0.426667 0.426667-0.64 0.426667-1.066667 0.426667z m-38.826666-9.386667c-3.413333 0-6.4 1.92-7.893334 4.906667-4.266667 7.893333-6.826667 4.053333-6.826666 1.92 0.213333-8.746667 13.013333-6.826667 14.72-6.826667z" p-id="1774" fill="#ffffff"></path></svg>
              </div>
              <div class="platform-info">
                <span class="platform-name">Linux</span>
                <span class="platform-desc">.deb</span>
              </div>
              <svg class="download-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M7 10L12 15L17 10" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12 15V3" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </a>
            
            <!-- macOS -->
            <a :href="macDownloadUrl" class="platform-btn" download>
              <div class="platform-icon-wrapper">
                <svg class="platform-icon" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"/>
                </svg>
              </div>
              <div class="platform-info">
                <span class="platform-name">macOS</span>
                <span class="platform-desc">.dmg</span>
              </div>
              <svg class="download-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M7 10L12 15L17 10" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M12 15V3" stroke-width="2" stroke-linecap="round"/>
              </svg>
            </a>
          </div>
          
<!--          <div class="pc-features">-->
<!--            <div class="features-title">功能特性</div>-->
<!--            <div class="feature-item" v-for="(feature, index) in versionInfo.pc?.features || []" :key="index">-->
<!--              <div class="feature-icon-wrapper">-->
<!--                <svg class="check-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">-->
<!--                  <path d="M20 6L9 17L4 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>-->
<!--                </svg>-->
<!--              </div>-->
<!--              <span>{{ feature }}</span>-->
<!--            </div>-->
<!--          </div>-->
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
  const url = versionInfo.value.pc?.linux
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
  overflow-x: hidden;
}

/* 背景装饰形状 */
.background-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.3;
  animation: floatShape 20s ease-in-out infinite;
}

.shape-1 {
  width: 400px;
  height: 400px;
  background: #ff69b4;
  top: -100px;
  left: -100px;
  animation-delay: 0s;
}

.shape-2 {
  width: 300px;
  height: 300px;
  background: #3b82f6;
  bottom: -50px;
  right: -50px;
  animation-delay: -5s;
}

.shape-3 {
  width: 200px;
  height: 200px;
  background: #10b981;
  top: 50%;
  left: 50%;
  animation-delay: -10s;
}

@keyframes floatShape {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
  }
  33% {
    transform: translate(30px, -30px) rotate(120deg);
  }
  66% {
    transform: translate(-20px, 20px) rotate(240deg);
  }
}

.download-container {
  text-align: center;
  color: white;
  width: 100%;
  max-width: 900px;
  padding: 30px 0;
  position: relative;
  z-index: 1;
}

/* 头部区域 */
.header-section {
  margin-bottom: 40px;
}

.logo-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: 20px;
}

.app-logo {
  width: 120px;
  height: 120px;
  filter: drop-shadow(0 8px 20px rgba(0, 0, 0, 0.3));
  animation: logoFloat 3s ease-in-out infinite;
}

.logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 150px;
  height: 150px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
  border-radius: 50%;
  animation: glowPulse 2s ease-in-out infinite;
}

@keyframes logoFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes glowPulse {
  0%, 100% {
    opacity: 0.5;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.8;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

.app-name {
  font-size: 2.5rem;
  font-weight: bold;
  margin-bottom: 10px;
  text-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  background: linear-gradient(135deg, #ffffff 0%, #e0e7ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.app-slogan {
  font-size: 1.1rem;
  margin-bottom: 20px;
  opacity: 0.9;
  font-weight: 300;
  letter-spacing: 1px;
}

.slogan-decoration {
  width: 80px;
  height: 3px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.6), transparent);
  margin: 0 auto;
}

/* 加载和错误状态 */
.loading,
.error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  backdrop-filter: blur(10px);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(255, 255, 255, 0.2);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.error {
  color: #ff6b6b;
}

.error-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 10px;
}

/* 下载区域 */
.download-sections {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

/* 徽章 */
.section-badge {
  position: absolute;
  top: -12px;
  left: 20px;
  background: linear-gradient(135deg, #ff9ec0, #ff69b4);
  color: white;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 105, 180, 0.4);
  letter-spacing: 0.5px;
}

.pc-badge {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

/* 移动端样式 */
.mobile-section {
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.05));
  border-radius: 32px;
  padding: 45px 30px;
  backdrop-filter: blur(20px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  border: 2px solid rgba(255, 255, 255, 0.3);
  position: relative;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.mobile-section:hover {
  transform: translateY(-5px);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.25);
}

.mobile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 25px;
}

.mobile-icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #ff9ec0, #ff69b4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow: 0 8px 24px rgba(255, 105, 180, 0.5);
  transition: transform 0.3s ease;
}

.mobile-section:hover .mobile-icon-wrapper {
  transform: scale(1.1) rotate(5deg);
}

.icon-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.4) 0%, transparent 70%);
  border-radius: 50%;
  animation: iconGlow 2s ease-in-out infinite;
}

@keyframes iconGlow {
  0%, 100% {
    opacity: 0.5;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.8;
    transform: translate(-50%, -50%) scale(1.2);
  }
}

.mobile-icon {
  width: 40px;
  height: 40px;
  color: white;
  position: relative;
  z-index: 1;
}

.mobile-title {
  font-size: 1.6rem;
  font-weight: bold;
  color: white;
  margin: 0;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.mobile-version {
  display: flex;
  gap: 10px;
  justify-content: center;
  align-items: center;
  margin-bottom: 30px;
}

.version-label {
  background: rgba(255, 255, 255, 0.15);
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
  opacity: 0.9;
}

.version-text {
  background: rgba(255, 255, 255, 0.25);
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.mobile-download-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: linear-gradient(135deg, #ff69b4, #ff1493);
  color: white;
  text-decoration: none;
  padding: 18px 55px;
  border-radius: 35px;
  font-size: 1.15rem;
  font-weight: bold;
  box-shadow: 0 6px 20px rgba(255, 20, 147, 0.5);
  transition: all 0.3s ease;
  width: 100%;
  max-width: 280px;
  overflow: hidden;
}

.mobile-download-btn:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 8px 28px rgba(255, 20, 147, 0.7);
}

.mobile-download-btn:active {
  transform: translateY(-2px) scale(0.98);
}

.btn-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.mobile-download-btn:hover .btn-shine {
  left: 100%;
}

.btn-icon {
  width: 24px;
  height: 24px;
  position: relative;
  z-index: 1;
}

.mobile-features {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 30px;
  flex-wrap: wrap;
}

.feature-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.15);
  padding: 8px 18px;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 500;
  transition: all 0.3s ease;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.feature-tag:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
}

.tag-icon {
  font-size: 1rem;
}

/* PC端样式 */
.pc-section {
  background: linear-gradient(145deg, rgba(15, 15, 25, 0.95), rgba(25, 25, 40, 0.9));
  border-radius: 16px;
  padding: 50px 35px;
  backdrop-filter: blur(20px);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  position: relative;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.pc-section:hover {
  transform: translateY(-5px);
  box-shadow: 0 16px 56px rgba(0, 0, 0, 0.6);
}

.pc-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-bottom: 30px;
  border-bottom: 2px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 25px;
}

.pc-icon-wrapper {
  position: relative;
  width: 70px;
  height: 70px;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.4);
  transition: transform 0.3s ease;
}

.pc-section:hover .pc-icon-wrapper {
  transform: scale(1.1) rotate(-5deg);
}

.pc-icon {
  width: 36px;
  height: 36px;
  color: white;
  position: relative;
  z-index: 1;
}

.pc-title {
  font-size: 1.7rem;
  font-weight: 700;
  color: white;
  margin: 0;
  letter-spacing: 1px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.pc-version {
  display: flex;
  gap: 12px;
  justify-content: center;
  align-items: center;
  margin-bottom: 35px;
}

.pc-version .version-label {
  background: rgba(59, 130, 246, 0.2);
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  color: #93c5fd;
  letter-spacing: 0.5px;
}

.pc-version .version-text {
  background: rgba(59, 130, 246, 0.3);
  padding: 8px 24px;
  border-radius: 8px;
  font-size: 1.05rem;
  font-weight: 700;
  color: white;
  letter-spacing: 0.5px;
  border: 1px solid rgba(59, 130, 246, 0.4);
}

.platform-downloads {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 35px;
}

.platform-btn {
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: white;
  text-decoration: none;
  padding: 16px 24px;
  border-radius: 12px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.platform-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  transform: scaleY(0);
  transition: transform 0.3s ease;
}

.platform-btn:hover {
  background: rgba(59, 130, 246, 0.15);
  border-color: rgba(59, 130, 246, 0.3);
  transform: translateX(8px);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.3);
}

.platform-btn:hover::before {
  transform: scaleY(1);
}

.platform-icon-wrapper {
  width: 44px;
  height: 44px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.platform-icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.platform-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.platform-name {
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.platform-desc {
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.5);
  font-weight: 400;
}

.download-arrow {
  width: 20px;
  height: 20px;
  color: rgba(255, 255, 255, 0.4);
  transition: all 0.3s ease;
}

.platform-btn:hover .download-arrow {
  color: #3b82f6;
  transform: translateY(3px);
}

.pc-features {
  margin-top: 35px;
  text-align: left;
}

.features-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 20px;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 0.5px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.85);
  transition: all 0.3s ease;
}

.feature-item:hover {
  color: white;
  transform: translateX(5px);
}

.feature-icon-wrapper {
  width: 26px;
  height: 26px;
  background: rgba(16, 185, 129, 0.15);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.check-icon {
  width: 16px;
  height: 16px;
  color: #10b981;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .download-container {
    max-width: 450px;
    padding: 20px 0;
  }
  
  .header-section {
    margin-bottom: 30px;
  }
  
  .app-logo {
    width: 100px;
    height: 100px;
  }
  
  .app-name {
    font-size: 2rem;
  }
  
  .app-slogan {
    font-size: 1rem;
  }
  
  .mobile-section,
  .pc-section {
    padding: 35px 25px;
  }
  
  .mobile-title,
  .pc-title {
    font-size: 1.3rem;
  }
  
  .mobile-download-btn {
    font-size: 1.05rem;
    padding: 16px 45px;
    max-width: 250px;
  }
  
  .pc-header {
    flex-direction: column;
    gap: 12px;
  }
  
  .pc-icon-wrapper {
    width: 60px;
    height: 60px;
  }
  
  .pc-icon {
    width: 30px;
    height: 30px;
  }
  
  .platform-btn {
    padding: 14px 20px;
  }
  
  .platform-icon-wrapper {
    width: 40px;
    height: 40px;
  }
  
  .platform-icon {
    width: 24px;
    height: 24px;
  }
  
  .platform-name {
    font-size: 1rem;
  }
  
  .platform-desc {
    font-size: 0.8rem;
  }
  
  .feature-item {
    font-size: 0.9rem;
    padding: 10px 0;
  }
}

@media (max-width: 480px) {
  .download-page {
    padding: 15px;
  }
  
  .download-container {
    max-width: 100%;
  }
  
  .mobile-section,
  .pc-section {
    padding: 30px 20px;
    border-radius: 24px;
  }
  
  .section-badge {
    left: 15px;
    padding: 5px 12px;
    font-size: 0.75rem;
  }
  
  .mobile-icon-wrapper {
    width: 65px;
    height: 65px;
  }
  
  .mobile-icon {
    width: 32px;
    height: 32px;
  }
  
  .mobile-features {
    gap: 8px;
  }
  
  .feature-tag {
    padding: 6px 14px;
    font-size: 0.8rem;
  }
  
  .tag-icon {
    font-size: 0.9rem;
  }
}
</style>