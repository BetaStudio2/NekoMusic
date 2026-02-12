<template>
  <div class="admin-sidebar">
    <div class="sidebar-header">
      <h3>管理中心</h3>
    </div>
    <nav class="sidebar-nav">
      <ul>
        <li>
          <router-link to="/admin" class="nav-link" :class="{ 'active': isActiveRoute('/admin') }">
            <span class="nav-icon">📊</span>
            <span class="nav-text">统计概览</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/music" class="nav-link" :class="{ 'active': isActiveRoute('/admin/music') }">
            <span class="nav-icon">🎵</span>
            <span class="nav-text">音乐管理</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/audit" class="nav-link" :class="{ 'active': isActiveRoute('/admin/audit') }">
            <span class="nav-icon">✅</span>
            <span class="nav-text">审核管理</span>
          </router-link>
        </li>
        <li>
          <router-link to="/admin/users" class="nav-link" :class="{ 'active': isActiveRoute('/admin/users') }">
            <span class="nav-icon">👥</span>
            <span class="nav-text">用户管理</span>
          </router-link>
        </li>
      </ul>
    </nav>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { computed } from 'vue'

const route = useRoute()

// 检查当前路由是否与指定路径完全匹配
const isActiveRoute = (path) => {
  return route.path === path
}
</script>

<style scoped>
.admin-sidebar {
  width: 250px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-right: 1px solid rgba(255, 255, 255, 0.18);
  height: 100vh;
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 1; /* 设置一个较低的z-index值，让footer在最顶层 */
  padding: 20px 0;
}

.sidebar-header {
  padding: 0 20px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 20px;
}

.sidebar-header h3 {
  color: #6a5acd;
  margin: 0;
  font-size: 1.3rem;
  text-align: center;
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
}

.sidebar-nav ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.sidebar-nav li {
  margin-bottom: 5px;
}

.nav-link {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  color: #887bb0;
  text-decoration: none;
  transition: all 0.3s ease;
  border-left: 3px solid transparent;
}

.nav-link:hover {
  background: rgba(106, 90, 205, 0.1);
  color: #6a5acd;
  border-left: 3px solid #6a5acd;
}

.nav-link.active {
  background: rgba(106, 90, 205, 0.2);
  color: #6a5acd;
  border-left: 3px solid #6a5acd;
}

.nav-icon {
  margin-right: 12px;
  font-size: 1.2rem;
  width: 24px;
  text-align: center;
}

.nav-text {
  font-size: 0.95rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-sidebar {
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  
  .admin-sidebar.open {
    transform: translateX(0);
  }
}
</style>