<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell">
      <header class="panel about-hero">
        <h1 class="about-hero__title">关于 Neko云音乐</h1>
        <p class="about-hero__sub">完全免费的在线音乐播放平台</p>
      </header>

      <section class="panel about-section">
        <h2 class="about-h2">项目介绍</h2>
        <p class="about-p">
          Neko云音乐是一个完全免费的在线音乐播放平台，致力于为用户提供高品质的音乐体验。
          我们相信音乐应该自由分享，让每个人都能轻松享受音乐的乐趣。
        </p>
        <p class="about-p">
          平台支持在线播放、音乐搜索、个人收藏、歌单创建等功能，无需付费，永久免费。
        </p>
      </section>

      <section class="panel about-section">
        <h2 class="about-h2">主要特性</h2>
        <ul class="feature-list">
          <li>高品质音频播放</li>
          <li>强大的音乐搜索功能</li>
          <li>个人收藏和歌单管理</li>
          <li>Android / PC 客户端支持从网易云音乐迁入歌单（链接或 ID，匹配站内曲库）</li>
          <li>支持移动端和PC端</li>
          <li>完全免费，无需付费</li>
          <li>简洁美观的用户界面</li>
        </ul>
      </section>

      <section class="panel about-section">
        <h2 class="about-h2">开源协议</h2>
        <p class="about-p">本项目遵循 MIT 开源协议，欢迎贡献代码、提出建议和反馈问题。</p>
        <div class="links">
          <a
            href="https://github.com/FantasyNetworkCN/NekoMusicDocs"
            target="_blank"
            rel="noopener noreferrer"
            class="link-btn"
          >
            开发者文档
          </a>
        </div>
      </section>

      <section class="panel about-section">
        <h2 class="about-h2">联系我们</h2>
        <p class="about-p">如果您有任何问题或建议，欢迎通过以下方式联系我们：</p>
        <ul class="contact-list">
          <li>
            Email:
            <a href="mailto:support@cnmsb.xin" class="inline-link">support@cnmsb.xin</a>
          </li>
        </ul>
      </section>

      <section class="panel about-section about-section--last">
        <h2 class="about-h2">致谢</h2>
        <p class="about-p">
          感谢所有使用 Neko云音乐的用户，感谢所有为项目做出贡献的开发者。
          您的支持是我们不断前进的动力！
        </p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  window.scrollTo(0, 0)
})
</script>

<style scoped>
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  padding: clamp(20px, 3vw, 28px) clamp(18px, 3vw, 26px);
  margin-bottom: 16px;
}

.about-section--last {
  margin-bottom: 0;
}

.about-hero {
  text-align: center;
  padding: clamp(28px, 4vw, 40px) 24px;
}

.about-hero__title {
  margin: 0 0 10px;
  font-size: clamp(1.75rem, 4vw, 2.35rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  background: linear-gradient(120deg, #e9d5ff, #a5f3fc, #c4b5fd);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.about-hero__sub {
  margin: 0;
  font-size: 1rem;
  color: var(--muted);
}

.about-h2 {
  margin: 0 0 14px;
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text);
}

.about-p {
  margin: 0 0 12px;
  font-size: 0.95rem;
  color: var(--muted);
  line-height: 1.75;
}

.about-p:last-child {
  margin-bottom: 0;
}

.feature-list,
.contact-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feature-list li,
.contact-list li {
  font-size: 0.92rem;
  color: var(--muted);
  padding: 10px 0 10px 12px;
  border-left: 3px solid rgba(34, 211, 238, 0.55);
  margin-bottom: 8px;
  line-height: 1.5;
}

.inline-link {
  color: var(--accent2);
  font-weight: 600;
  text-decoration: none;
}

.inline-link:hover {
  text-decoration: underline;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.link-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 700;
  text-decoration: none;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 24px rgba(139, 92, 246, 0.28);
  transition: filter 0.15s var(--ease);
}

.link-btn:hover {
  filter: brightness(1.06);
}

@media (max-width: 768px) {
  .about-h2 {
    font-size: 1.1rem;
  }
}
</style>
