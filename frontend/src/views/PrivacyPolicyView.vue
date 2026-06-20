<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <main class="shell privacy-shell">
      <header class="panel privacy-hero">
        <p class="privacy-eyebrow">Privacy Policy</p>
        <h1 class="privacy-title">隐私政策</h1>
        <p class="privacy-sub">
          本政策说明 Neko歌姬计划在提供网页、Android 与 PC 客户端服务时如何收集、使用、保存和保护你的信息。
        </p>
        <p class="privacy-updated">更新日期：2026 年 6 月 20 日</p>
      </header>

      <section class="panel privacy-section">
        <h2>我们收集的信息</h2>
        <p>
          当你注册、登录或使用账号功能时，我们可能会收集用户名、邮箱、头像、密码加密摘要、会员状态、收藏、歌单和上传记录等必要信息。
        </p>
        <p>
          当你搜索、播放、下载、上传音乐或使用歌单迁入功能时，我们可能会记录与功能相关的请求参数、设备与浏览器信息、IP 地址、访问时间和错误日志，用于保障服务稳定和安全。
        </p>
      </section>

      <section class="panel privacy-section">
        <h2>信息用途</h2>
        <ul>
          <li>提供账号登录、收藏、歌单、上传审核、会员状态展示等核心功能。</li>
          <li>维护平台安全，识别异常请求、滥用行为和服务故障。</li>
          <li>改进搜索、播放、客户端更新和迁入体验。</li>
          <li>在你主动联系我们时，用于回复问题、处理反馈或完成必要的账号支持。</li>
        </ul>
      </section>

      <section class="panel privacy-section">
        <h2>Cookie 与本地存储</h2>
        <p>
          网站会使用 Cookie、localStorage 或类似技术保存登录状态、基础偏好和必要的会话信息。你可以通过浏览器设置清除这些数据，但部分账号功能可能需要重新登录。
        </p>
      </section>

      <section class="panel privacy-section">
        <h2>第三方服务</h2>
        <p>
          平台可能使用邮件、支付、验证码、对象存储或开源音乐相关接口等第三方服务来完成特定功能。我们只会在实现对应功能所必需的范围内传递必要信息。
        </p>
      </section>

      <section class="panel privacy-section">
        <h2>数据保存与保护</h2>
        <p>
          我们会采取合理的技术和管理措施保护数据，包括权限控制、加密存储敏感凭据、日志访问限制和异常访问排查。由于互联网环境并非绝对安全，我们无法承诺完全杜绝所有风险。
        </p>
      </section>

      <section class="panel privacy-section">
        <h2>你的权利</h2>
        <p>
          你可以请求查询、更正或删除与账号相关的个人信息，也可以停止使用服务并清除本地保存的登录信息。涉及平台安全、审计、争议处理或法律要求的数据，可能会在必要期限内保留。
        </p>
      </section>

      <section class="panel privacy-section privacy-section--last">
        <h2>联系我们</h2>
        <p>
          如果你对本隐私政策或个人信息处理有疑问，请通过
          <a href="mailto:support@cnmsb.xin">support@cnmsb.xin</a>
          联系我们。
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
.privacy-shell {
  max-width: 920px;
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  padding: clamp(20px, 3vw, 28px) clamp(18px, 3vw, 28px);
  margin-bottom: 16px;
}

.privacy-hero {
  padding: clamp(30px, 5vw, 46px) clamp(20px, 4vw, 36px);
}

.privacy-eyebrow {
  margin: 0 0 10px;
  color: var(--accent2);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.privacy-title {
  margin: 0 0 12px;
  color: var(--text);
  font-size: clamp(2rem, 5vw, 3rem);
  line-height: 1.05;
}

.privacy-sub {
  max-width: 760px;
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 1rem;
  line-height: 1.75;
}

.privacy-updated {
  margin: 0;
  color: rgba(255, 255, 255, 0.48);
  font-size: 0.88rem;
}

.privacy-section--last {
  margin-bottom: 0;
}

.privacy-section h2 {
  margin: 0 0 14px;
  color: var(--text);
  font-size: 1.18rem;
}

.privacy-section p {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 0.95rem;
  line-height: 1.8;
}

.privacy-section p:last-child {
  margin-bottom: 0;
}

.privacy-section ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.privacy-section li {
  margin-bottom: 8px;
  padding: 10px 0 10px 12px;
  border-left: 3px solid rgba(34, 211, 238, 0.55);
  color: var(--muted);
  font-size: 0.94rem;
  line-height: 1.55;
}

.privacy-section a {
  color: var(--accent2);
  font-weight: 700;
  text-decoration: none;
}

.privacy-section a:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .privacy-shell {
    max-width: none;
  }
}
</style>
