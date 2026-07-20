<template>
  <div class="admin-layout">
    <AdminSidebar ref="sidebarRef" />

    <div class="admin-main-content">
      <div class="admin-header">
        <button type="button" class="menu-toggle-btn" @click="toggleSidebar">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z" />
          </svg>
        </button>
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button type="button" class="logout-button" @click="logout">退出登录</button>
        </div>
      </div>

      <div class="admin-content-wrapper">
        <div class="admin-subpage">
          <h2>VIP 价目表</h2>
          <p>全量维护套餐时长（月 + 天）与价格（元）。保存后会立即对前台「会员中心」生效。</p>

          <div class="toolbar">
            <button type="button" class="btn-ghost" :disabled="loading" @click="loadRows">重新加载</button>
            <button type="button" class="btn-ghost" @click="addRow">添加一行</button>
            <button type="button" class="btn-primary" :disabled="saving || loading" @click="saveRows">保存价目</button>
          </div>

          <p v-if="loadError" class="err">{{ loadError }}</p>

          <div class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>月</th>
                  <th>天</th>
                  <th>价格（元）</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in rows" :key="idx">
                  <td><input v-model.number="row.months" type="number" min="0" class="cell-inp" /></td>
                  <td><input v-model.number="row.days" type="number" min="0" class="cell-inp" /></td>
                  <td><input v-model.number="row.priceYuan" type="number" min="0" step="0.01" class="cell-inp" /></td>
                  <td><button type="button" class="btn-del" @click="removeRow(idx)">删除</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'vue-toastification'
import AdminSidebar from '@/components/AdminSidebar.vue'
import { fetchVipPricing, replaceVipPricing } from '@/api/vipPricing.js'

const router = useRouter()
const toast = useToast()
const sidebarRef = ref(null)
const adminInfo = ref({})
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')

const toggleSidebar = () => sidebarRef.value?.toggleSidebar()

const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminInfo')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}

const loadRows = async () => {
  loadError.value = ''
  loading.value = true
  try {
    const list = await fetchVipPricing()
    rows.value = list.map((r) => ({
      months: r.months,
      days: r.days,
      priceYuan: r.priceYuan
    }))
  } catch (e) {
    loadError.value = e.message || '加载失败'
    toast.error(loadError.value)
  } finally {
    loading.value = false
  }
}

const addRow = () => {
  rows.value.push({ months: 1, days: 0, priceYuan: 0 })
}

const removeRow = (idx) => {
  rows.value.splice(idx, 1)
}

const validate = () => {
  if (!rows.value.length) {
    toast.error('至少保留一行价目')
    return false
  }
  for (let i = 0; i < rows.value.length; i++) {
    const r = rows.value[i]
    const m = Number(r.months) || 0
    const d = Number(r.days) || 0
    const p = Number(r.priceYuan)
    if (m < 0 || d < 0 || m + d <= 0) {
      toast.error(`第 ${i + 1} 行：月、天须为非负整数，且至少一项大于 0`)
      return false
    }
    if (Number.isNaN(p) || p < 0 || !Number.isFinite(p)) {
      toast.error(`第 ${i + 1} 行：价格须为非负有限数`)
      return false
    }
  }
  return true
}

const saveRows = async () => {
  if (!validate()) return
  const token = localStorage.getItem('adminToken')
  if (!token) {
    router.push('/admin/login')
    return
  }
  saving.value = true
  try {
    const items = rows.value.map((r) => ({
      months: Number(r.months) || 0,
      days: Number(r.days) || 0,
      priceYuan: Number(r.priceYuan)
    }))
    const saved = await replaceVipPricing(items, token)
    rows.value = saved.map((r) => ({ months: r.months, days: r.days, priceYuan: r.priceYuan }))
    toast.success('价目已保存')
  } catch (e) {
    toast.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  if (!storedToken || !storedAdminInfo) {
    router.push('/admin/login')
    return
  }
  try {
    adminInfo.value = JSON.parse(storedAdminInfo)
  } catch {
    router.push('/admin/login')
    return
  }
  const role = adminInfo.value.role || 'admin'
  if (role === 'auditor') {
    toast.info('无权限访问价目管理')
    router.replace('/admin')
    return
  }
  await loadRows()
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px;
  padding: 20px;
  transition: margin-left 0.3s ease;
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.admin-header {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.menu-toggle-btn {
  display: none;
  background: none;
  border: none;
  color: #887bb0;
  cursor: pointer;
  padding: 5px;
}

.menu-toggle-btn svg {
  width: 28px;
  height: 28px;
}

.admin-user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex: 1;
}

.logout-button {
  background: linear-gradient(135deg, rgba(220, 20, 60, 0.8), rgba(105, 200, 223, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 0.9rem;
}

.admin-content-wrapper {
  flex: 1;
  padding: 0 20px;
  overflow: auto;
}

.admin-subpage {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.admin-subpage h2 {
  color: #69c8df;
  margin: 0 0 12px 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
}

.btn-primary,
.btn-ghost {
  padding: 8px 16px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-primary {
  background: linear-gradient(135deg, #69c8df, #69c8df);
  color: #fff;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-ghost {
  background: rgba(105, 200, 223, 0.12);
  color: #5c4b7b;
}

.btn-ghost:disabled {
  opacity: 0.5;
}

.err {
  color: #c0392b;
  font-size: 0.9rem;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 10px;
  overflow: hidden;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(105, 200, 223, 0.15);
}

.data-table th {
  background: rgba(105, 200, 223, 0.15);
  color: #5c4b7b;
  font-weight: 600;
}

.cell-inp {
  width: 100%;
  max-width: 140px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid rgba(105, 200, 223, 0.25);
}

.btn-del {
  background: rgba(231, 76, 60, 0.15);
  color: #c0392b;
  border: none;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
}

@media (max-width: 900px) {
  .admin-main-content {
    margin-left: 0;
    padding-bottom: 100px;
  }
  .menu-toggle-btn {
    display: block;
  }
}
</style>
