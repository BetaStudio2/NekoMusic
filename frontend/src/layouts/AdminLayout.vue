<script setup>
/**
 * AdminLayout —— 管理后台布局壳
 * ------------------------------------------------------------
 * 统一提供「侧栏 + 顶栏 + 内容区」，子页面通过嵌套路由渲染进 <RouterView>。
 * 取代原先每个管理页各自复制一套 shell 的做法。
 *
 * 注意：旧管理页的深色皮肤仍由 assets/main.css 的 .admin-layout 段统一覆盖，
 * 完整重制在迁移批次 5 进行。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import AdminSidebar from '@/components/AdminSidebar.vue'
import NIcon from '@/icons/NIcon.vue'

const router = useRouter()
const sidebarRef = ref(null)
const adminInfo = ref({})

function loadAdminInfo() {
  const stored = localStorage.getItem('adminInfo')
  if (!stored) return
  try {
    adminInfo.value = JSON.parse(stored)
  } catch (e) {
    console.error('解析管理员信息失败:', e)
    adminInfo.value = {}
  }
}

const toggleSidebar = () => sidebarRef.value?.toggleSidebar()

function logout() {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminInfo')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}

onMounted(() => {
  loadAdminInfo()
  window.addEventListener('storage', loadAdminInfo)
})

onUnmounted(() => {
  window.removeEventListener('storage', loadAdminInfo)
})
</script>

<template>
  <div class="admin-layout">
    <AdminSidebar ref="sidebarRef" />

    <div class="admin-main-content">
      <div class="admin-header">
        <button type="button" class="menu-toggle-btn" aria-label="打开菜单" @click="toggleSidebar">
          <NIcon name="menu" :size="24" />
        </button>
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button type="button" class="logout-button" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="admin-content-wrapper">
        <RouterView />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100dvh;
  color: var(--n-text);
}

.admin-main-content {
  flex: 1;
  min-width: 0;
  margin-left: 250px;
  padding: var(--n-space-5);
  display: flex;
  flex-direction: column;
  transition: margin-left var(--n-duration) var(--n-ease);
}

.admin-header {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-4) var(--n-space-5);
  margin-bottom: var(--n-space-5);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
  backdrop-filter: var(--n-blur);
  -webkit-backdrop-filter: var(--n-blur);
}

.menu-toggle-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--n-radius-control);
  border: 1px solid var(--n-line);
  background: var(--n-surface-soft);
  color: var(--n-text-muted);
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .menu-toggle-btn:hover {
    background: var(--n-surface-hover);
    color: var(--n-text);
  }
}

.admin-user-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-4);
  flex: 1;
  min-width: 0;
  color: var(--n-text-muted);
}

.logout-button {
  flex: none;
  padding: 8px 16px;
  border-radius: var(--n-radius-control);
  border: 1px solid rgba(255, 107, 107, 0.28);
  background: var(--n-danger-soft);
  color: #ffb3b3;
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  transition: background var(--n-duration-fast) var(--n-ease), border-color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .logout-button:hover {
    background: rgba(255, 107, 107, 0.22);
    border-color: rgba(255, 107, 107, 0.42);
  }
}

.admin-content-wrapper {
  flex: 1;
  min-height: 0;
  padding: 0 var(--n-space-5);
  overflow: auto;
}

@media (max-width: 768px) {
  .admin-main-content {
    margin-left: 0;
  }

  .menu-toggle-btn {
    display: inline-flex;
  }
}
</style>
