<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">操作日志</h1>
        <p class="subpage__desc">
          记录后台写操作：系统设置修改、审核通过 / 驳回、音乐上传 / 修改 / 删除、歌词修改等，并保留操作人、时间、结果与来源。
          访问量与安全分析见首页「统计分析」。
        </p>
      </div>
    </header>

    <p v-if="error" class="err">
      <NIcon name="triangle-alert" :size="16" />
      {{ error }}
    </p>

    <div class="toolbar">
      <select v-model="operationQuery.module" class="select" aria-label="模块">
        <option value="">全部模块</option>
        <option v-for="name in MODULES" :key="name" :value="name">{{ name }}</option>
      </select>
      <NInput
        v-model="operationQuery.keyword"
        icon="search"
        placeholder="路径 / 查询串 / 管理员 / 说明…"
        clearable
        class="toolbar__search"
        @keyup.enter="loadOperations(1)"
      />
      <NButton variant="primary" icon="search" :disabled="loading" @click="loadOperations(1)">查询</NButton>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>时间</th>
            <th>管理员</th>
            <th>角色</th>
            <th>模块</th>
            <th>动作</th>
            <th>请求</th>
            <th>状态</th>
            <th>耗时</th>
            <th>来源 IP</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in operations.items" :key="row.id">
            <td class="cell-dim">{{ formatTime(row.occurred_at) }}</td>
            <td class="cell-strong">{{ row.admin_username || '—' }}</td>
            <td>{{ roleLabel(row.admin_role) }}</td>
            <td>{{ row.module || '—' }}</td>
            <td>{{ row.action || '—' }}</td>
            <td class="cell-path" :title="fullPath(row)">
              <span class="cell-method">{{ row.method }}</span> {{ row.path }}
            </td>
            <td>
              <NTag :variant="row.success ? 'success' : 'danger'" size="sm">{{ row.status }}</NTag>
            </td>
            <td class="cell-dim">{{ row.duration_ms }} ms</td>
            <td class="cell-dim">{{ row.client_ip }}</td>
          </tr>
          <tr v-if="!operations.items.length">
            <td colspan="9" class="cell-empty">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager">
      <NButton size="sm" variant="secondary" icon="chevron-left" :disabled="operations.page <= 1 || loading" @click="loadOperations(operations.page - 1)">
        上一页
      </NButton>
      <span class="pager__info">共 {{ operations.total }} 条 · 第 {{ operations.page }} / {{ totalPages(operations) }} 页</span>
      <NButton
        size="sm"
        variant="secondary"
        icon-after="chevron-right"
        :disabled="operations.page >= totalPages(operations) || loading"
        @click="loadOperations(operations.page + 1)"
      >
        下一页
      </NButton>
    </div>
  </div>
</template>

<script setup>
/**
 * 操作日志：后台写操作的人话留痕（谁、什么时候、对哪个模块做了什么、结果如何）。
 *
 * 这里只放操作日志——访问量统计与安全分析（含实时流）在首页「统计分析」里
 * （见 components/AccessLogPanel.vue）。
 */
import { onMounted, reactive, ref } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NInput, NTag } from '@/ui'
import { fetchOperationLogs } from '@/api/adminLogs.js'

const PAGE_SIZE = 50

/** 与后端 RequestAuditFilter.ADMIN_MODULES 的模块名保持一致 */
const MODULES = [
  '用户管理',
  '音乐管理',
  '歌词管理',
  '审核管理',
  '客户端更新',
  '系统设置',
  '网易云补全',
  'VIP 定价',
  '防重放标记',
  '管理员管理',
  '管理员登录',
  '数据统计'
]

const error = ref('')
const loading = ref(false)
const operations = reactive({ total: 0, page: 1, size: PAGE_SIZE, items: [] })
const operationQuery = reactive({ module: '', keyword: '' })

onMounted(() => {
  loadOperations(1)
})

async function loadOperations(page) {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchOperationLogs({
      page,
      size: PAGE_SIZE,
      module: operationQuery.module,
      keyword: operationQuery.keyword
    })
    operations.total = data.total || 0
    operations.page = data.page || page
    operations.items = data.items || []
  } catch (e) {
    error.value = e.message
    operations.items = []
    operations.total = 0
  } finally {
    loading.value = false
  }
}

function totalPages(page) {
  return Math.max(1, Math.ceil((page.total || 0) / (page.size || PAGE_SIZE)))
}

function formatTime(millis) {
  if (!millis) return '—'
  return new Date(Number(millis)).toLocaleString('zh-CN', { hour12: false })
}

function fullPath(row) {
  return row.query ? `${row.path}?${row.query}` : row.path
}

function roleLabel(value) {
  return { super_admin: '超级管理员', admin: '管理员', auditor: '审核员' }[value] || value || '—'
}
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
  margin-bottom: var(--n-space-5);
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
  max-width: 72ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

/* ==================== 工具条 ==================== */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-4);
}

.select {
  height: 40px;
  padding: 0 var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-sm);
  background: var(--n-surface-2);
  color: var(--n-text);
  font-size: var(--n-text-sm);
}

.toolbar__search {
  flex: 1 1 220px;
  max-width: 320px;
}

/* ==================== 表格 ==================== */
.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-md);
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--n-text-sm);
}

.table th,
.table td {
  padding: var(--n-space-3);
  text-align: left;
  border-bottom: 1px solid var(--n-line-subtle);
  vertical-align: top;
}

.table th {
  color: var(--n-text-faint);
  font-weight: var(--n-weight-medium);
  white-space: nowrap;
}

.table tbody tr:last-child td {
  border-bottom: none;
}

.cell-dim {
  color: var(--n-text-muted);
  white-space: nowrap;
}

.cell-strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
}

.cell-path {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--n-text-muted);
}

.cell-method {
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-semibold);
}

.cell-empty {
  text-align: center;
  color: var(--n-text-faint);
}

/* ==================== 分页 ==================== */
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-4);
  margin-top: var(--n-space-4);
}

.pager__info {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.err {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-4);
  color: var(--n-color-danger, #ff6b6b);
  font-size: var(--n-text-sm);
}
</style>
