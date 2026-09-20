<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">VIP 价目表</h1>
        <p class="subpage__desc">全量维护套餐时长（月 + 天）与价格（元）。保存后会立即对前台「会员中心」生效。</p>
      </div>
      <div class="subpage__actions">
        <NButton variant="secondary" icon="refresh" :disabled="loading" @click="loadRows">重新加载</NButton>
        <NButton variant="secondary" icon="plus" @click="addRow">添加一行</NButton>
        <NButton variant="primary" icon="check" :disabled="saving || loading" @click="saveRows">保存价目</NButton>
      </div>
    </header>

    <p v-if="loadError" class="err">
      <NIcon name="triangle-alert" :size="16" />
      {{ loadError }}
    </p>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>月</th>
            <th>天</th>
            <th>价格（元）</th>
            <th aria-label="操作" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, idx) in rows" :key="idx">
            <td><NInput v-model.number="row.months" type="number" min="0" class="cell" /></td>
            <td><NInput v-model.number="row.days" type="number" min="0" class="cell" /></td>
            <td><NInput v-model.number="row.priceYuan" type="number" min="0" step="0.01" class="cell" /></td>
            <td class="cell-actions">
              <NButton size="sm" variant="danger" icon="trash-2" title="删除该行" @click="removeRow(idx)" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { fetchVipPricing, replaceVipPricing } from '@/api/vipPricing.js'

const router = useRouter()
const toast = useToast()
const adminInfo = ref({})
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')



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
.subpage {
  width: 100%;
}

.subpage__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
}

.subpage__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.25rem, 2.6vw, 1.6rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.02em;
  color: var(--n-text);
}

.subpage__desc {
  margin: 0;
  max-width: 68ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.subpage__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
}

.err {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-5);
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid rgba(255, 107, 107, 0.28);
  border-radius: var(--n-radius-control);
  background: var(--n-danger-soft);
  color: #ffb3b3;
  font-size: var(--n-text-sm);
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
}

.table {
  width: 100%;
  border-collapse: collapse;
  color: var(--n-text);
  font-size: var(--n-text-sm);
}

.table th,
.table td {
  padding: var(--n-space-3) var(--n-space-4);
  text-align: left;
  border-bottom: 1px solid var(--n-line-subtle);
  vertical-align: middle;
}

.table th {
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.cell {
  width: 100%;
  max-width: 180px;
}

.cell-actions {
  width: 1%;
  white-space: nowrap;
}
</style>
