<template>
  <footer class="footer" :class="{ 'footer--chrome-dark': chromeDark }">
    <div class="footer-content">
      <div class="footer-info">
        <div class="footer-text">
          <p>备案号：<a href="https://beian.miit.gov.cn/#/Integrated/recordQuery" >黔ICP备2026007098号</a></p>
          <p>组织：<a href="https://www.cnmsb.xin/" class="fantasy-network">Fantasy Network「梦幻网络」</a></p>
        </div>
      </div>
      <div class="footer-links">
        <router-link to="/about" class="footer-link">关于我们</router-link>
        <router-link to="/privacy" class="footer-link">隐私政策</router-link>
        <a href="https://qm.qq.com/q/Q9HkDi6Ewk" class="footer-link">QQ群</a>
        <a href="mailto:support@cnmsb.xin" class="footer-link">邮件：support@cnmsb.xin</a>
      </div>
    </div>
    <div class="footer-bottom">
      <p>&copy; {{ new Date().getFullYear() }} NekoMusic. All rights reserved.</p>
    </div>
  </footer>
</template>

<script setup>
defineProps({
  chromeDark: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.footer {
  background: rgba(8, 13, 19, 0.9);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  color: var(--text);
  padding: 26px 0 18px;
  margin-top: auto;
  width: 100%;
  border-top: 1px solid var(--line);
  box-shadow: 0 -16px 42px rgba(0, 0, 0, 0.32);
  position: relative; /* 确保可以设置z-index */
  z-index: 999; /* 设置低于播放器的层级 */
  margin-bottom: 88px; /* 为底部播放器预留空间 */
}

.footer-content {
  width: min(1400px, calc(100% - 32px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px 28px;
  align-items: start;
}

.footer-info {
  flex: 1;
  min-width: 0;
}

.footer-text p {
  margin: 4px 0;
  font-size: 0.88rem;
  color: var(--muted);
  line-height: 1.5;
}

.fantasy-network {
  font-weight: bold;
  color: var(--text);
}

.footer-links {
  display: flex;
  gap: 20px;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.footer-link {
  color: var(--accent-strong);
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 600;
  transition: color 0.2s var(--ease), transform 0.2s var(--ease);
  position: relative;
}

.footer-link::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 1px;
  background: var(--accent-strong);
  transition: width 0.2s var(--ease);
}

.footer-link:hover {
  color: #ffffff;
  transform: translateY(-2px);
}

.footer-link:hover::after {
  width: 100%;
}

.footer-bottom {
  width: min(1400px, calc(100% - 32px));
  margin: 18px auto 0;
  padding: 12px 0 0;
  text-align: center;
  border-top: 1px solid var(--line);
  font-size: 0.8rem;
  color: var(--faint);
}

@media (max-width: 768px) {
  .footer-content {
    grid-template-columns: 1fr;
    text-align: left;
  }
  
  .footer-links {
    justify-content: flex-start;
  }
}

.footer--chrome-dark {
  background: rgba(7, 11, 16, 0.92);
}

.footer--chrome-dark .footer-text p {
  color: var(--muted);
}

.footer--chrome-dark .fantasy-network {
  color: var(--text);
}

.footer--chrome-dark .footer-link {
  color: var(--accent-strong);
}

.footer--chrome-dark .footer-link::after {
  background: var(--accent-strong);
}

.footer--chrome-dark .footer-link:hover {
  color: #ffffff;
}

.footer--chrome-dark .footer-bottom {
  border-top-color: var(--line);
  color: var(--muted);
}
</style>
