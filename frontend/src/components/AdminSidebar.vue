<template>
  <div class="admin-sidebar" :class="{ 'open': isOpen }">
    <div class="sidebar-header">
      <h3>管理中心</h3>
      <button class="close-sidebar-btn" @click="toggleSidebar">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
        </svg>
      </button>
    </div>
    <nav class="sidebar-nav">
      <ul>
        <li>
          <router-link to="/admin" class="nav-link" :class="{ 'active': isActiveRoute('/admin') }">
            <span class="nav-icon">📊</span>
            <span class="nav-text">统计概览</span>
          </router-link>
        </li>
        <li v-if="hasPermission('music_view')">
          <router-link to="/admin/music" class="nav-link" :class="{ 'active': isActiveRoute('/admin/music') }">
            <span class="nav-icon">🎵</span>
            <span class="nav-text">音乐管理</span>
          </router-link>
        </li>
        <li v-if="hasPermission('music_view')">
          <router-link to="/admin/lyrics" class="nav-link" :class="{ 'active': isActiveRoute('/admin/lyrics') }">
            <span class="nav-icon">📝</span>
            <span class="nav-text">歌词编辑</span>
          </router-link>
        </li>
        <li v-if="hasPermission('audit_view')">
          <router-link to="/admin/audit" class="nav-link" :class="{ 'active': isActiveRoute('/admin/audit') }">
            <span class="nav-icon">✅</span>
            <span class="nav-text">审核管理</span>
          </router-link>
        </li>
        <li v-if="hasPermission('user_view')">
          <router-link to="/admin/users" class="nav-link" :class="{ 'active': isActiveRoute('/admin/users') }">
            <span class="nav-icon">👥</span>
            <span class="nav-text">用户管理</span>
          </router-link>
        </li>
        <li v-if="hasPermission('user_edit')">
          <router-link to="/admin/vip-pricing" class="nav-link" :class="{ 'active': isActiveRoute('/admin/vip-pricing') }">
            <span class="nav-icon">💎</span>
            <span class="nav-text">VIP 价目</span>
          </router-link>
        </li>
        <li v-if="hasPermission('release_manage')">
          <router-link to="/admin/releases" class="nav-link" :class="{ 'active': isActiveRoute('/admin/releases') }">
            <span class="nav-icon">📦</span>
            <span class="nav-text">客户端更新</span>
          </router-link>
        </li>
      </ul>
    </nav>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { computed, onMounted, ref } from 'vue'

const route = useRoute()
const adminInfo = ref(null)
const isOpen = ref(false)

// 切换侧边栏
const toggleSidebar = () => {
  isOpen.value = !isOpen.value
}

// 关闭侧边栏
const closeSidebar = () => {
  isOpen.value = false
}

// 暴露方法给父组件
defineExpose({
  toggleSidebar,
  closeSidebar
})

// 检查当前路由是否与指定路径完全匹配
const isActiveRoute = (path) => {
  if (path === '/admin') return route.path === '/admin'
  return route.path === path || route.path.startsWith(path + '/')
}

// 获取管理员信息
onMounted(() => {
  const storedAdminInfo = localStorage.getItem('adminInfo')
  if (storedAdminInfo) {
    adminInfo.value = JSON.parse(storedAdminInfo)
  }
})

// 权限检查
const hasPermission = (permission) => {
  if (!adminInfo.value) return false
  
  const role = adminInfo.value.role || 'admin'
  
  // 超级管理员拥有所有权限
  if (role === 'super_admin') return true
  
  // 管理员权限
  if (role === 'admin') {
    switch (permission) {
      case 'music_view':
      case 'music_add':
      case 'music_edit':
      case 'music_delete':
      case 'audit_view':
      case 'audit_approve':
      case 'audit_reject':
      case 'user_view':
      case 'user_edit':
      case 'user_delete':
      case 'stats_view':
      case 'release_manage':
        return true
      default:
        return false
    }
  }
  
  // 审核员权限（只能查看自己的账号，不能删除）
  if (role === 'auditor') {
    switch (permission) {
      case 'audit_view':
      case 'audit_approve':
      case 'audit_reject':
      case 'stats_view':
      case 'user_view':
        return true
      default:
        return false
    }
  }
  
  return false
}
</script>

<style scoped>
.admin-sidebar {
  width: 250px;
  background: rgba(8, 13, 19, 0.96);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border-right: 1px solid rgba(143, 174, 198, 0.14);
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  height: 100dvh;
  z-index: 1000;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid rgba(143, 174, 198, 0.14);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-header h3 {
  margin: 0;
  color: var(--neko-text);
  font-size: 1.3rem;
  flex: 1;
  letter-spacing: -0.03em;
}

.close-sidebar-btn {
  display: none;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(143, 174, 198, 0.14);
  border-radius: 10px;
  color: var(--neko-muted);
  cursor: pointer;
  padding: 5px;
  transition: color 0.2s var(--neko-ease), background 0.2s var(--neko-ease);
}

.close-sidebar-btn:hover {
  color: var(--neko-text);
  background: rgba(255, 255, 255, 0.08);
}

.close-sidebar-btn svg {
  width: 24px;
  height: 24px;
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
  color: var(--neko-muted);
  text-decoration: none;
  transition: color 0.2s var(--neko-ease), background 0.2s var(--neko-ease), border-color 0.2s var(--neko-ease);
  border-left: 3px solid transparent;
}

.nav-link:hover {
  background: rgba(105, 200, 223, 0.07);
  color: var(--neko-text);
  border-left-color: rgba(105, 200, 223, 0.45);
}

.nav-link.active {
  background: rgba(105, 200, 223, 0.12);
  color: var(--neko-accent-strong);
  border-left-color: var(--neko-accent);
}

.nav-icon {
  margin-right: 12px;
  font-size: 1.2rem;
  width: 24px;
  text-align: center;
}

.nav-text {
  font-size: 0.95rem;
  font-weight: 650;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-sidebar {
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    box-shadow: 2px 0 10px rgba(0, 0, 0, 0.2);
  }
  
  .admin-sidebar.open {
    transform: translateX(0);
  }
  
  .close-sidebar-btn {
    display: block;
  }
}
</style>
