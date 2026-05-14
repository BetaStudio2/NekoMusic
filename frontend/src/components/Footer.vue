<template>
  <footer class="footer">
    <div class="footer-content">
      <div class="footer-info">
        <div class="footer-text">
          <p>备案号：黔ICP备2026007098号</p>
          <p>组织：<span class="fantasy-network">Fantasy Network「梦幻网络」</span></p>
        </div>
      </div>
      <div class="footer-links">
        <a href="about" class="footer-link">关于我们</a>
        <a href="mailto:support@cnmsb.xin" class="footer-link">邮件：support@cnmsb.xin</a>
      </div>
    </div>
    <div class="footer-bottom">
      <p>&copy; {{ new Date().getFullYear() }} NekoMusic. All rights reserved.</p>
    </div>
  </footer>
</template>

<script setup>
// Footer组件逻辑
</script>

<style scoped>
.footer {
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: #5c4b7b;
  padding: 30px 0 10px;
  margin-top: auto;
  width: 100%;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 -8px 32px rgba(31, 38, 135, 0.37);
  position: relative; /* 确保可以设置z-index */
  z-index: 999; /* 设置低于播放器的层级 */
  margin-bottom: 80px; /* 为底部播放器预留空间 */
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
}

.footer-info {
  flex: 1;
  min-width: 250px;
}

.footer-text p {
  margin: 5px 0;
  font-size: 0.9rem;
  color: #5c4b7b;
}

.fantasy-network {
  font-weight: bold;
  color: #6a5acd;
  text-shadow: 0 0 5px rgba(106, 90, 205, 0.5);
}

.footer-links {
  display: flex;
  gap: 20px;
  flex: 0 0 auto;
}

.footer-link {
  color: #6a5acd;
  text-decoration: none;
  font-size: 0.9rem;
  transition: color 0.3s ease, transform 0.3s ease;
  position: relative;
}

.footer-link::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 1px;
  background: #6a5acd;
  transition: width 0.3s ease;
}

.footer-link:hover {
  color: #9370db;
  transform: translateY(-2px);
}

.footer-link:hover::after {
  width: 100%;
}

.footer-bottom {
  max-width: 1200px;
  margin: 20px auto 0;
  padding: 10px 20px;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 0.8rem;
  color: #887bb0;
}

@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
    text-align: center;
  }
  
  .footer-links {
    justify-content: center;
    margin-top: 10px;
  }
}
</style>