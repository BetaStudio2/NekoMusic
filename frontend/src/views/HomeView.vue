<template>
  <div class="home-view">
    <div class="content">
      <div class="welcome-card">
        <h1 class="welcome-title">🎵 Neko云音乐</h1>
        <h2 class="welcome-subtitle-main">完全免费的在线音乐播放平台</h2>
        <div class="features">
          <div class="feature-item">
            <span class="feature-icon">💰</span>
            <span class="feature-text">永久免费 · 无需付费</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🎧</span>
            <span class="feature-text">海量音乐 · 高品质音频</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">🔒</span>
            <span class="feature-text">保护隐私 · 不收集数据</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">⚡</span>
            <span class="feature-text">快速播放 · 流畅体验</span>
          </div>
        </div>
        <p class="welcome-subtitle">本站不收集用户数据所以不会有定向推荐</p>
        <p class="welcome-subtitle">不是做不到这是这样不道德</p>
        <div class="developer-link">
          <a href="/api-docs" class="dev-link-btn">
            <span class="dev-icon">📚</span>
            <span class="dev-text">开发者文档</span>
          </a>
        </div>
        <div class="decoration-element">
          <div class="music-note note-1">♪</div>
          <div class="music-note note-2">♫</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  // 添加任何主页特定的逻辑
})
</script>

<style scoped>
.content {
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
  position: relative;
  padding-top: 40px;
}

.welcome-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 25px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  margin: 20px auto;
  max-width: 90%;
  border: 1px solid rgba(255, 255, 255, 0.2);
  overflow: hidden;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.welcome-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: -10px;
  right: -10px;
  bottom: -10px;
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff, #ff9ec0);
  background-size: 400%;
  border-radius: 30px;
  z-index: -1;
  filter: blur(20px);
  opacity: 0.6;
  animation: gradientShift 10s ease infinite;
}

.welcome-title {
  color: #6a5acd;
  margin-bottom: 0.5rem;
  font-size: 2.5rem;
  font-weight: bold;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  background: linear-gradient(45deg, #ff69b4, #6a5acd, #84ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  z-index: 2;
}

.welcome-subtitle {
  color: #887bb0;
  font-size: 1.2rem;
  margin-bottom: 30px;
  position: relative;
  z-index: 1;
}

.decoration-element {
  position: relative;
  height: 60px;
  margin-top: 20px;
}

.music-note {
  position: absolute;
  font-size: 1.8rem;
  opacity: 0.7;
  animation: float 3s ease-in-out infinite;
  position: relative;
  z-index: 1;
}

.note-1 {
  top: 0;
  left: 30%;
  color: #ff9ec0;
  animation-delay: 0s;
}

.note-2 {
  top: 10px;
  right: 30%;
  color: #6a5acd;
  animation-delay: 1.5s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-10px) rotate(5deg);
  }
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.welcome-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(31, 38, 135, 0.5);
}

.developer-link {
  margin: 30px 0;
  position: relative;
  z-index: 2;
}

.dev-link-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 30px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  text-decoration: none;
  border-radius: 30px;
  font-size: 1.1rem;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
  position: relative;
  overflow: hidden;
}

.dev-link-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.5s ease;
}

.dev-link-btn:hover::before {
  left: 100%;
}

.dev-link-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

.dev-icon {
  font-size: 1.3rem;
}

.dev-text {
  position: relative;
  z-index: 1;
}
</style>