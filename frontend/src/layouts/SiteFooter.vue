<script setup>
/**
 * SiteFooter —— 全站页脚
 * ------------------------------------------------------------
 * 黑偏青 + 圆角矩形。不负责为播放条预留空间（由 App.vue 统一处理）。
 */
import NIcon from '@/icons/NIcon.vue'

const year = new Date().getFullYear()

const links = [
  { label: '关于我们', to: '/about', icon: 'info' },
  { label: '隐私政策', to: '/privacy', icon: 'shield-check' },
  { label: 'QQ群', href: 'https://qm.qq.com/q/Q9HkDi6Ewk', icon: 'users' },
  { label: '邮件支持', href: 'mailto:support@cnmsb.xin', icon: 'mail', text: 'support@cnmsb.xin' },
]
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer__inner">
      <div class="site-footer__info">
        <p>
          备案号：<a href="https://beian.miit.gov.cn/#/Integrated/recordQuery">黔ICP备2026007098号</a>
        </p>
        <p>
          组织：<a href="https://www.cnmsb.xin/" class="site-footer__org">Fantasy Network「梦幻网络」</a>
        </p>
      </div>

      <nav class="site-footer__links" aria-label="页脚链接">
        <template v-for="l in links" :key="l.label">
          <RouterLink v-if="l.to" :to="l.to" class="site-footer__link">
            <NIcon :name="l.icon" :size="14" />
            {{ l.label }}
          </RouterLink>
          <a v-else :href="l.href" class="site-footer__link">
            <NIcon :name="l.icon" :size="14" />
            {{ l.text || l.label }}
          </a>
        </template>
      </nav>
    </div>

    <div class="site-footer__bottom">
      <p>© {{ year }} NekoMusic. All rights reserved.</p>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  position: relative;
  z-index: var(--n-z-content);
  border-top: 1px solid var(--n-line);
  background: rgba(4, 9, 11, 0.6);
  color: var(--n-text-muted);
}

.site-footer__inner {
  width: min(1400px, calc(100% - 2 * var(--n-content-gutter)));
  margin: 0 auto;
  padding: var(--n-space-8) 0 var(--n-space-4);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--n-space-4) var(--n-space-8);
  align-items: start;
}

.site-footer__info p {
  margin: 0 0 var(--n-space-1);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.site-footer__org {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.site-footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2) var(--n-space-5);
  justify-content: flex-end;
}

.site-footer__link {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  transition: color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .site-footer__link:hover {
    color: var(--n-accent-strong);
  }
}

.site-footer__bottom {
  width: min(1400px, calc(100% - 2 * var(--n-content-gutter)));
  margin: 0 auto;
  padding: var(--n-space-3) 0 var(--n-space-5);
  border-top: 1px solid var(--n-line-subtle);
  text-align: center;
  font-size: var(--n-text-xs);
  color: var(--n-text-faint);
}

.site-footer__bottom p {
  margin: 0;
}

@media (max-width: 768px) {
  .site-footer__inner {
    grid-template-columns: 1fr;
  }

  .site-footer__links {
    justify-content: flex-start;
  }
}
</style>
