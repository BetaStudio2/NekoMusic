<template>
  <div class="not-found-container">
    <div class="not-found-content">
      <div class="error-code">404</div>
      <div class="error-message">页面未找到</div>
      <div class="error-description">抱歉，您访问的页面不存在或已被移除</div>
      <div class="error-actions">
        <router-link to="/" class="btn btn-primary">
          <span class="btn-text">返回首页</span>
        </router-link>
        <button @click="goBack" class="btn btn-secondary">
          <span class="btn-text">返回上一页</span>
        </button>
      </div>
      <div class="decoration">
        <div class="music-note note-1">♪</div>
        <div class="music-note note-2">♫</div>
        <div class="music-note note-3">♪</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}
</script>

<style scoped>
.not-found-container {
  min-height: calc(100vh - 200px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  position: relative;
  overflow: hidden;
}

.not-found-content {
  text-align: center;
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 30px;
  padding: 60px 40px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  max-width: 600px;
  width: 90%;
  animation: fadeIn 0.6s ease-out;
}

.not-found-content::before {
  content: '';
  position: absolute;
  top: -15px;
  left: -15px;
  right: -15px;
  bottom: -15px;
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff, #ff9ec0);
  background-size: 400%;
  border-radius: 35px;
  z-index: -1;
  filter: blur(20px);
  opacity: 0.5;
  animation: gradientShift 10s ease infinite;
}

.error-code {
  font-size: 8rem;
  font-weight: bold;
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 20px;
  line-height: 1;
  text-shadow: 0 0 30px rgba(106, 90, 205, 0.3);
  animation: bounce 2s ease-in-out infinite;
}

.error-message {
  font-size: 2rem;
  color: #6a5acd;
  margin-bottom: 15px;
  font-weight: 600;
}

.error-description {
  font-size: 1.1rem;
  color: #887bb0;
  margin-bottom: 40px;
  line-height: 1.6;
}

.error-actions {
  display: flex;
  gap: 15px;
  justify-content: center;
  flex-wrap: wrap;
}

.btn {
  padding: 12px 30px;
  border-radius: 25px;
  border: none;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.5s ease;
}

.btn:hover::before {
  left: 100%;
}

.btn-text {
  position: relative;
  z-index: 1;
}

.btn-primary {
  background: linear-gradient(45deg, #6a5acd, #84ffff);
  color: white;
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(106, 90, 205, 0.6);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.3);
  color: #6a5acd;
  border: 2px solid rgba(106, 90, 205, 0.3);
}

.btn-secondary:hover {
  background: rgba(106, 90, 205, 0.1);
  border-color: rgba(106, 90, 205, 0.6);
  transform: translateY(-2px);
}

.decoration {
  position: relative;
  height: 60px;
  margin-top: 30px;
}

.music-note {
  position: absolute;
  font-size: 2rem;
  opacity: 0.6;
  animation: float 3s ease-in-out infinite;
}

.note-1 {
  top: 0;
  left: 20%;
  color: #ff9ec0;
  animation-delay: 0s;
}

.note-2 {
  top: 15px;
  right: 20%;
  color: #6a5acd;
  animation-delay: 1s;
}

.note-3 {
  top: 5px;
  left: 50%;
  transform: translateX(-50%);
  color: #84ffff;
  animation-delay: 2s;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-15px) rotate(10deg);
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

@media (max-width: 768px) {
  .error-code {
    font-size: 5rem;
  }

  .error-message {
    font-size: 1.5rem;
  }

  .error-description {
    font-size: 1rem;
  }

  .error-actions {
    flex-direction: column;
    width: 100%;
  }

  .btn {
    width: 100%;
  }

  .not-found-content {
    padding: 40px 20px;
  }
}
</style>