<script setup>
/**
 * AboutView —— 关于我们
 * ------------------------------------------------------------
 * 静态说明页。黑偏青 + 圆角矩形；列表改用图标而非左侧高亮条。
 */
import { computed, onMounted } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { t } from '@/i18n'

// 文案在渲染时取，切语言后列表立即跟着变
const features = computed(() => [
  t('about.feature1'),
  t('about.feature2'),
  t('about.feature3'),
  t('about.feature4'),
  t('about.feature5'),
  t('about.feature6'),
  t('about.feature7'),
])

const contacts = computed(() => [
  { icon: 'mail', label: 'Email', value: 'support@nekocore.cn', href: 'mailto:support@nekocore.cn' },
  { icon: 'users', label: t('footer.qqGroup'), value: '932258919', href: 'https://qm.qq.com/q/Q9HkDi6Ewk' },
])

onMounted(() => {
  window.scrollTo(0, 0)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <header class="about-hero">
      <h1 class="about-hero__title">{{ $t('about.title') }}</h1>
      <p class="about-hero__sub">{{ $t('about.sub') }}</p>
    </header>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">{{ $t('about.introTitle') }}</h2>
      <p class="about-p">
        {{ $t('about.introP1') }}
      </p>
      <p class="about-p">
        {{ $t('about.introP2') }}
      </p>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">{{ $t('about.featuresTitle') }}</h2>
      <ul class="feature-list">
        <li v-for="f in features" :key="f">
          <NIcon name="circle-check" :size="16" class="feature-list__icon" />
          <span>{{ f }}</span>
        </li>
      </ul>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">{{ $t('about.licenseTitle') }}</h2>
      <p class="about-p">{{ $t('about.licenseP') }}</p>
      <div class="links">
        <NButton
          variant="secondary"
          icon="book"
          icon-after="external-link"
          href="https://github.com/FantasyNetworkCN/NekoMusicDocs"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ $t('about.devDocs') }}
        </NButton>
      </div>
    </NCard>

    <NCard pad="lg" class="about-block">
      <h2 class="about-h2">{{ $t('about.contactTitle') }}</h2>
      <p class="about-p">{{ $t('about.contactP') }}</p>
      <ul class="contact-list">
        <li v-for="c in contacts" :key="c.label">
          <NIcon :name="c.icon" :size="16" class="contact-list__icon" />
          <span class="contact-list__label">{{ c.label }}：</span>
          <a :href="c.href" target="_blank" rel="noopener noreferrer">{{ c.value }}</a>
        </li>
      </ul>
    </NCard>

    <NCard pad="lg" class="about-block about-block--last">
      <h2 class="about-h2">{{ $t('about.thanksTitle') }}</h2>
      <p class="about-p">
        {{ $t('about.thanksP') }}
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
