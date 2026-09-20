<script setup>
/**
 * AboutView —— 关于我们
 * ------------------------------------------------------------
 * 静态说明页。黑偏青 + 圆角矩形；列表改用图标而非左侧高亮条。
 */
import { onMounted } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const features = [
  '高品质音频播放',
  '强大的音乐搜索功能',
  '个人收藏与歌单管理',
  'Android / PC 客户端支持从网易云、QQ 音乐、酷狗音乐迁入歌单（链接或 ID，匹配站内曲库）',
  '移动端与桌面端一致体验',
  '完全免费，无需付费',
  '简洁美观的界面',
]

const contacts = [
  { icon: 'mail', label: 'Email', value: 'support@cnmsb.xin', href: 'mailto:support@cnmsb.xin' },
  { icon: 'users', label: 'QQ群', value: '932258919', href: 'https://qm.qq.com/q/Q9HkDi6Ewk' },
]

onMounted(() => {
  window.scrollTo(0, 0)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <header class="about-hero">
      <h1 class="about-hero__title">关于 Neko歌姬计划</h1>
      <p class="about-hero__sub">完全免费的在线音乐播放平台</p>
    </header>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">项目介绍</h2>
      <p class="about-p">
        Neko歌姬计划是一个完全免费的在线音乐播放平台，致力于为用户提供高品质的音乐体验。
        我们相信音乐应该自由分享，让每个人都能轻松享受音乐的乐趣。
      </p>
      <p class="about-p">
        平台支持在线播放、音乐搜索、个人收藏、歌单创建等功能，无需付费，永久免费。
      </p>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">主要特性</h2>
      <ul class="feature-list">
        <li v-for="f in features" :key="f">
          <NIcon name="circle-check" :size="16" class="feature-list__icon" />
          <span>{{ f }}</span>
        </li>
      </ul>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">开源协议</h2>
      <p class="about-p">本项目遵循 AGPL v3 开源协议，欢迎贡献代码、提出建议和反馈问题。</p>
      <div class="links">
        <NButton
          variant="secondary"
          icon="book"
          icon-after="external-link"
          href="https://github.com/FantasyNetworkCN/NekoMusicDocs"
          target="_blank"
          rel="noopener noreferrer"
        >
          开发者文档
        </NButton>
      </div>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">联系我们</h2>
      <p class="about-p">如果您有任何问题或建议，欢迎通过以下方式联系我们：</p>
      <ul class="contact-list">
        <li v-for="c in contacts" :key="c.label">
          <NIcon :name="c.icon" :size="16" class="contact-list__icon" />
          <span class="contact-list__label">{{ c.label }}：</span>
          <a :href="c.href" target="_blank" rel="noopener noreferrer">{{ c.value }}</a>
        </li>
      </ul>
    </NCard>

    <NCard pad="lg" class="about-block about-block--last">
      <h2 class="about-h2">致谢</h2>
      <p class="about-p">
        感谢所有使用 Neko歌姬计划的用户，感谢所有为项目做出贡献的开发者。
        您的支持是我们不断前进的动力！
      </p>
    </NCard>
  </PageShell>
</template>

<style scoped>
.about-hero {
  text-align: center;
  padding: clamp(8px, 2vw, 20px) 0 clamp(24px, 4vw, 36px);
}

.about-hero__title {
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.75rem, 4vw, 2.35rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.about-hero__sub {
  margin: 0;
  color: var(--n-text-muted);
}

.about-block {
  margin-bottom: var(--n-space-4);
}

.about-block--last {
  margin-bottom: 0;
}

.about-h2 {
  margin: 0 0 var(--n-space-4);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.01em;
}

.about-p {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.about-p:last-child {
  margin-bottom: 0;
}

.feature-list,
.contact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
}

.feature-list li,
.contact-list li {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.feature-list__icon,
.contact-list__icon {
  flex: none;
  margin-top: 3px;
  color: var(--n-accent);
}

.contact-list__label {
  flex: none;
  color: var(--n-text);
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-4);
}
</style>
