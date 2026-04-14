<template>
  <div class="about-view">
    <div class="about-container">
      <div class="about-header">
        <h1>关于 Neko云音乐</h1>
        <p class="subtitle">完全免费的在线音乐播放平台</p>
      </div>

      <div class="about-content">
        <section class="about-section">
          <h2>🎵 项目介绍</h2>
          <p>
            Neko云音乐是一个完全免费的在线音乐播放平台，致力于为用户提供高品质的音乐体验。
            我们相信音乐应该自由分享，让每个人都能轻松享受音乐的乐趣。
          </p>
          <p>
            平台支持在线播放、音乐搜索、个人收藏、歌单创建等功能，无需付费，永久免费。
          </p>
        </section>

        <section class="about-section">
          <h2>✨ 主要特性</h2>
          <ul class="feature-list">
            <li>🎧 高品质音频播放</li>
            <li>🔍 强大的音乐搜索功能</li>
            <li>❤️ 个人收藏和歌单管理</li>
            <li>📱 支持移动端和PC端</li>
            <li>💰 完全免费，无需付费</li>
            <li>🎨 简洁美观的用户界面</li>
          </ul>
        </section>

        <section class="about-section">
          <h2>🚀 技术栈</h2>
          <div class="tech-stack">
            <div class="tech-item">
              <h3>前端</h3>
              <p>Vue.js 3 + Vite</p>
            </div>
            <div class="tech-item">
              <h3>后端</h3>
              <p>Java + Jetty</p>
            </div>
            <div class="tech-item">
              <h3>数据库</h3>
              <p>MySQL + Redis</p>
            </div>
          </div>
        </section>

        <section class="about-section">
          <h2>📝 开源协议</h2>
          <p>
            本项目遵循 MIT 开源协议，欢迎贡献代码、提出建议和反馈问题。
          </p>
          <div class="links">
            <a href="https://github.com/NyaNyagulugulu/NekoMusic" target="_blank" rel="noopener">
              GitHub 仓库
            </a>
            <a href="https://github.com/NyaNyagulugulu/NekoMusicDocs" target="_blank" rel="noopener">
              开发者文档
            </a>
          </div>
        </section>

        <section class="about-section">
          <h2>🤝 联系我们</h2>
          <p>如果您有任何问题或建议，欢迎通过以下方式联系我们：</p>
          <ul class="contact-list">
            <li>📧 Email: <a href="mailto:support@cnmsb.xin">support@cnmsb.xin</a></li>
            <li>🐱 GitHub: <a href="https://github.com/NyaNyagulugulu" target="_blank" rel="noopener">@NyaNyagulugulu</a></li>
          </ul>
        </section>

        <section class="about-section">
          <h2>🙏 致谢</h2>
          <p>
            感谢所有使用 Neko云音乐的用户，感谢所有为项目做出贡献的开发者。
            您的支持是我们不断前进的动力！
          </p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  window.scrollTo(0, 0)
})
</script>

<style scoped>
.about-view {
  min-height: calc(100vh - 60px);
  padding: 40px 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.about-container {
  max-width: 900px;
  margin: 0 auto;
}

.about-header {
  text-align: center;
  margin-bottom: 60px;
}

.about-header h1 {
  font-size: 3rem;
  color: #2c3e50;
  margin-bottom: 15px;
  font-weight: 700;
}

.subtitle {
  font-size: 1.2rem;
  color: #7f8c8d;
}

.about-content {
  background: white;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.about-section {
  margin-bottom: 40px;
}

.about-section:last-child {
  margin-bottom: 0;
}

.about-section h2 {
  font-size: 1.8rem;
  color: #34495e;
  margin-bottom: 20px;
  font-weight: 600;
}

.about-section p {
  font-size: 1rem;
  color: #555;
  line-height: 1.8;
  margin-bottom: 15px;
}

.feature-list,
.contact-list {
  list-style: none;
  padding: 0;
}

.feature-list li,
.contact-list li {
  font-size: 1.05rem;
  color: #555;
  padding: 10px 0;
  padding-left: 10px;
  border-left: 3px solid #3498db;
  margin-bottom: 10px;
}

.contact-list a {
  color: #3498db;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.contact-list a:hover {
  color: #2980b9;
  text-decoration: underline;
}

.tech-stack {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.tech-item {
  background: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
}

.tech-item h3 {
  font-size: 1.1rem;
  color: #2c3e50;
  margin-bottom: 10px;
  font-weight: 600;
}

.tech-item p {
  font-size: 0.95rem;
  color: #7f8c8d;
  margin: 0;
}

.links {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
  margin-top: 20px;
}

.links a {
  display: inline-block;
  padding: 12px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  text-decoration: none;
  border-radius: 8px;
  font-weight: 500;
  transition: transform 0.2s, box-shadow 0.2s;
}

.links a:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

@media (max-width: 768px) {
  .about-header h1 {
    font-size: 2rem;
  }

  .about-content {
    padding: 25px;
  }

  .about-section h2 {
    font-size: 1.5rem;
  }
}
</style>