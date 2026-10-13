<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">操作日志</h1>
        <p class="subpage__desc">
          记录后台写操作：系统设置修改、审核通过 / 驳回、音乐上传 / 修改 / 删除、歌词修改等，并保留操作人、时间、结果与来源。
        </p>
      </div>
    </header>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="tabs__item"
        :class="{ 'tabs__item--active': activeTab === tab.key }"
        :aria-selected="activeTab === tab.key"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <p v-if="error" class="err">
      <NIcon name="triangle-alert" :size="16" />
      {{ error }}
    </p>

    <!-- ==================== 操作日志 ==================== -->
    <template v-if="activeTab === 'operations'">
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
    </template>

    <!-- ==================== 访问日志 ==================== -->
    <template v-else>
      <div class="stats">
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.todayRequests ?? 0 }}</span>
          <span class="stat__label">今日访问</span>
        </NCard>
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.todayUniqueIps ?? 0 }}</span>
          <span class="stat__label">今日独立 IP</span>
        </NCard>
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.todayBots ?? 0 }}</span>
          <span class="stat__label">今日爬虫 / 扫描</span>
        </NCard>
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.todayErrors ?? 0 }}</span>
          <span class="stat__label">今日异常（≥400）</span>
        </NCard>
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.todayPowRejected ?? 0 }}</span>
          <span class="stat__label">今日 PoW 拦截</span>
        </NCard>
        <NCard pad="lg" class="stat">
          <span class="stat__number">{{ stats.totalRequests ?? 0 }}</span>
          <span class="stat__label">累计访问</span>
        </NCard>
      </div>

      <NCard pad="lg" class="card">
        <h2 class="card__title">最近 {{ statsDays }} 天访问趋势</h2>
        <div class="chart__canvas">
          <canvas ref="trendCanvas"></canvas>
        </div>
      </NCard>

      <div class="grid-two">
        <NCard pad="lg" class="card">
          <h2 class="card__title">Top 来源 IP</h2>
          <ul class="ranking">
            <li v-for="item in stats.topIps || []" :key="item.key">
              <span class="ranking__key" :title="item.key">{{ item.key || '未知' }}</span>
              <span class="ranking__count">{{ item.count }}</span>
            </li>
          </ul>
        </NCard>
        <NCard pad="lg" class="card">
          <h2 class="card__title">Top 路径</h2>
          <ul class="ranking">
            <li v-for="item in stats.topPaths || []" :key="item.key">
              <span class="ranking__key" :title="item.key">{{ item.key || '未知' }}</span>
              <span class="ranking__count">{{ item.count }}</span>
            </li>
          </ul>
        </NCard>
        <NCard pad="lg" class="card">
          <h2 class="card__title">客户端类型</h2>
          <ul class="ranking">
            <li v-for="(count, type) in stats.clientTypes || {}" :key="type">
              <span class="ranking__key">{{ clientTypeLabel(type) }}</span>
              <span class="ranking__count">{{ count }}</span>
            </li>
          </ul>
        </NCard>
        <NCard pad="lg" class="card">
          <h2 class="card__title">Top User-Agent</h2>
          <ul class="ranking">
            <li v-for="item in stats.topUserAgents || []" :key="item.key">
              <span class="ranking__key" :title="item.key">{{ item.key || '（空）' }}</span>
              <span class="ranking__count">{{ item.count }}</span>
            </li>
          </ul>
        </NCard>
      </div>

      <div class="toolbar">
        <select v-model.number="statsDays" class="select" aria-label="统计天数" @change="loadStats">
          <option :value="7">最近 7 天</option>
          <option :value="14">最近 14 天</option>
          <option :value="30">最近 30 天</option>
        </select>
        <select v-model="accessQuery.clientType" class="select" aria-label="客户端类型">
          <option value="">全部类型</option>
          <option v-for="type in CLIENT_TYPES" :key="type" :value="type">{{ clientTypeLabel(type) }}</option>
        </select>
        <NInput v-model="accessQuery.ip" placeholder="来源 IP 含…" clearable class="toolbar__search" />
        <NInput v-model="accessQuery.path" placeholder="路径含…" clearable class="toolbar__search" />
        <select v-model="accessQuery.powStatus" class="select" aria-label="PoW 结果">
          <option value="">全部 PoW 结果</option>
          <option v-for="item in POW_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <NInput
          v-model="accessQuery.keyword"
          icon="search"
          placeholder="路径 / UA / IP 关键字…"
          clearable
          class="toolbar__search"
          @keyup.enter="loadAccess(1)"
        />
        <NButton variant="primary" icon="search" :disabled="loading" @click="loadAccess(1)">查询</NButton>
        <NButton
          :variant="live ? 'primary' : 'secondary'"
          icon="activity"
          :title="liveHint"
          @click="toggleLive"
        >{{ liveLabel }}</NButton>
      </div>

      <p v-if="live && livePaused" class="live-note">
        <NIcon name="activity" :size="14" />
        实时更新已开启，但当前在第 {{ access.page }} 页 / 带筛选条件，新记录不插入表格；回到第 1 页并清空筛选即可看到实时追加。
      </p>

      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>时间</th>
              <th>请求</th>
              <th>状态</th>
              <th>PoW</th>
              <th>耗时</th>
              <th>分类</th>
              <th>客户端</th>
              <th>平台 / 版本</th>
              <th>来源 IP</th>
              <th>User-Agent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in access.items" :key="row.id ?? row.__liveKey">
              <td class="cell-dim">{{ formatTime(row.occurred_at) }}</td>
              <td class="cell-path" :title="fullPath(row)">
                <span class="cell-method">{{ row.method }}</span> {{ row.path }}
              </td>
              <td>
                <NTag :variant="statusVariant(row.status)" size="sm">{{ row.status }}</NTag>
              </td>
              <td class="cell-dim" :title="powTitle(row)">
                <template v-if="row.pow_status">
                  <span v-if="row.pow_difficulty">难度 {{ row.pow_difficulty }} · </span>{{ powLabel(row.pow_status) }}
                </template>
                <template v-else>—</template>
              </td>
              <td class="cell-dim">{{ row.duration_ms }} ms</td>
              <td class="cell-dim">{{ categoryLabel(row.category) }}</td>
              <td>{{ clientTypeLabel(row.client_type) }}</td>
              <td class="cell-dim">{{ platformText(row) }}</td>
              <td class="cell-dim">{{ row.client_ip }}</td>
              <td class="cell-ua" :title="row.user_agent">{{ row.user_agent || '（空）' }}</td>
            </tr>
            <tr v-if="!access.items.length">
              <td colspan="10" class="cell-empty">暂无记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <NButton size="sm" variant="secondary" icon="chevron-left" :disabled="access.page <= 1 || loading" @click="loadAccess(access.page - 1)">
          上一页
        </NButton>
        <span class="pager__info">共 {{ access.total }} 条 · 第 {{ access.page }} / {{ totalPages(access) }} 页</span>
        <NButton
          size="sm"
          variant="secondary"
          icon-after="chevron-right"
          :disabled="access.page >= totalPages(access) || loading"
          @click="loadAccess(access.page + 1)"
        >
          下一页
        </NButton>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NTag } from '@/ui'
import {
  fetchAccessLogs,
  fetchAccessLogStats,
  fetchOperationLogs,
  openAccessLogStream
} from '@/api/adminLogs.js'
import {
  Chart as ChartJS,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js'

ChartJS.register(
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Title,
  Tooltip,
  Legend,
  Filler
)

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

/** 与后端 ClientFingerprint 的类型保持一致 */
const CLIENT_TYPES = ['browser', 'official_client', 'third_party', 'bot', 'scanner', 'unknown']

const CLIENT_TYPE_LABELS = {
  browser: '浏览器',
  official_client: '官方客户端',
  third_party: '第三方客户端',
  bot: '爬虫',
  scanner: '安全扫描器',
  unknown: '未知'
}

const CATEGORY_LABELS = {
  api: '动态接口',
  admin: '管理后台',
  page: '页面',
  media: '媒体',
  static: '静态资源',
  other: '其它'
}

const role = ref('')
const activeTab = ref('access')
const error = ref('')
const loading = ref(false)
const statsDays = ref(7)

const access = reactive({ total: 0, page: 1, size: PAGE_SIZE, items: [] })
const operations = reactive({ total: 0, page: 1, size: PAGE_SIZE, items: [] })
const stats = ref({})

const accessQuery = reactive({ ip: '', path: '', clientType: '', keyword: '', powStatus: '' })
const operationQuery = reactive({ module: '', keyword: '' })

const trendCanvas = ref(null)
let trendChart = null

/** 实时流最多在表格顶部保留多少行；再多就丢掉最旧的实时行，避免页面越开越卡。 */
const LIVE_MAX_ROWS = 200
/** PoW 结果标签：与后端 PowAudit 的词表一致（仅后台展示用）。 */
const POW_STATUSES = [
  { value: 'verified', label: '通过（verified）' },
  { value: 'missing', label: '缺 nonce（missing）' },
  { value: 'invalid', label: '无效 / 重放（invalid）' },
  { value: 'expired', label: '挑战过期（expired）' },
  { value: 'mismatch', label: '来源不匹配（mismatch）' },
  { value: 'bad_proof', label: '解答不合格（bad_proof）' },
  { value: 'issued', label: '下发挑战（issued）' },
  { value: 'exchanged', label: '兑换 nonce（exchanged）' },
  { value: 'rate_limited', label: '被限额（rate_limited）' },
  { value: 'marked', label: '加压越界（marked）' },
  { value: 'error', label: '故障（error）' }
]
const POW_LABELS = Object.fromEntries(POW_STATUSES.map((item) => [item.value, item.label]))
/** 只有这些结果算「没通过」，与后端统计口径一致。 */
const POW_REJECTED = new Set([
  'missing',
  'invalid',
  'expired',
  'mismatch',
  'bad_proof',
  'rate_limited',
  'marked',
  'error'
])

const live = ref(true)
const liveState = ref('connecting')
const liveReceived = ref(0)
let liveStop = null
let liveSeq = 0

const canViewOperations = computed(() => role.value === 'admin' || role.value === 'super_admin')
const canViewAccess = computed(() =>
  ['super_admin', 'admin', 'auditor'].includes(role.value)
)
const tabs = computed(() => {
  const list = []
  if (canViewOperations.value) list.push({ key: 'operations', label: '操作日志' })
  if (canViewAccess.value) list.push({ key: 'access', label: '访问日志（安全分析）' })
  return list
})

const livePaused = computed(() =>
  access.page !== 1 ||
  Boolean(accessQuery.ip || accessQuery.path || accessQuery.clientType || accessQuery.keyword || accessQuery.powStatus)
)
const liveLabel = computed(() => {
  if (!live.value) return '实时已关闭'
  if (liveState.value === 'open') return '实时更新中'
  if (liveState.value === 'connecting') return '实时连接中…'
  return '实时已断开'
})
const liveHint = computed(() => {
  if (!live.value) return '点击开启访问日志实时更新（SSE）'
  if (liveState.value === 'error') return `实时流已断开（累计收到 ${liveReceived.value} 条），点击重连`
  return `已实时收到 ${liveReceived.value} 条新日志，点击关闭`
})

onMounted(() => {
  const stored = localStorage.getItem('adminInfo')
  if (stored) {
    try {
      role.value = JSON.parse(stored).role || 'admin'
    } catch {
      role.value = 'admin'
    }
  }
  activeTab.value = canViewOperations.value ? 'operations' : 'access'
  loadActiveTab()
  syncLiveStream()
})

onUnmounted(() => {
  closeLiveStream()
  destroyChart()
})

function switchTab(key) {
  if (activeTab.value === key) return
  activeTab.value = key
  loadActiveTab()
  syncLiveStream()
}

// ── 实时流（SSE） ─────────────────────────────────────────────
// 只在「访问日志」标签页且已勾选实时时保持连接：实时流会一直占用一条长连接，
// 停在操作日志页还开着纯属浪费（服务端也限制了并发连接数）。

function toggleLive() {
  live.value = !live.value
  syncLiveStream()
}

function syncLiveStream() {
  if (live.value && activeTab.value === 'access' && canViewAccess.value) {
    openLiveStream()
  } else {
    closeLiveStream()
  }
}

function openLiveStream() {
  if (liveStop || !liveTransportReady()) return
  liveState.value = 'connecting'
  liveStop = openAccessLogStream({
    onRecord: applyLiveRecord,
    onState: (state) => {
      liveState.value = state
      // 鉴权失败（401/403）时浏览器会自动无限重连，这里主动收手，等用户手动重开
      if (state === 'error') closeLiveStream()
    }
  })
}

function closeLiveStream() {
  if (!liveStop) return
  liveStop()
  liveStop = null
}

function liveTransportReady() {
  return typeof EventSource !== 'undefined' && Boolean(localStorage.getItem('adminToken'))
}

/** 新日志插入表格顶部：翻页或带筛选时不动表格（那会把不属于当前视图的行混进来）。 */
function applyLiveRecord(row) {
  liveReceived.value += 1
  if (activeTab.value !== 'access' || livePaused.value) return
  row.__liveKey = `live-${(liveSeq += 1)}`
  access.items = [row, ...access.items].slice(0, LIVE_MAX_ROWS)
  access.total += 1
}

function loadActiveTab() {
  if (activeTab.value === 'operations') {
    loadOperations(1)
  } else {
    loadAccess(1)
    loadStats()
  }
}

async function loadOperations(page) {
  if (!canViewOperations.value) return
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

async function loadAccess(page) {
  if (!canViewAccess.value) return
  loading.value = true
  error.value = ''
  try {
    const data = await fetchAccessLogs({
      page,
      size: PAGE_SIZE,
      ip: accessQuery.ip,
      path: accessQuery.path,
      clientType: accessQuery.clientType,
      powStatus: accessQuery.powStatus,
      keyword: accessQuery.keyword
    })
    access.total = data.total || 0
    access.page = data.page || page
    access.items = data.items || []
  } catch (e) {
    error.value = e.message
    access.items = []
    access.total = 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  if (!canViewAccess.value) return
  try {
    stats.value = await fetchAccessLogStats(statsDays.value)
    await nextTick()
    renderChart()
  } catch (e) {
    error.value = e.message
  }
}

function renderChart() {
  if (!trendCanvas.value) return
  const trend = stats.value.dailyTrend || []
  destroyChart()
  trendChart = new ChartJS(trendCanvas.value, {
    type: 'line',
    data: {
      labels: trend.map((item) => item.date),
      datasets: [
        {
          label: '访问量',
          data: trend.map((item) => item.count),
          borderColor: '#5fd0e0',
          backgroundColor: 'rgba(95, 208, 224, 0.16)',
          fill: true,
          tension: 0.32,
          pointRadius: 2
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { color: 'rgba(255,255,255,0.55)' }, grid: { display: false } },
        y: {
          beginAtZero: true,
          ticks: { color: 'rgba(255,255,255,0.55)', precision: 0 },
          grid: { color: 'rgba(255,255,255,0.06)' }
        }
      }
    }
  })
}

function destroyChart() {
  if (trendChart) {
    trendChart.destroy()
    trendChart = null
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

function clientTypeLabel(value) {
  return CLIENT_TYPE_LABELS[value] || value || '未知'
}

function categoryLabel(value) {
  return CATEGORY_LABELS[value] || value || '—'
}

function powLabel(value) {
  return POW_LABELS[value] || value
}

function powTitle(row) {
  if (!row.pow_status) return '本次请求未经过防重放挑战（静态资源 / 无需 nonce 的接口）'
  if (POW_REJECTED.has(row.pow_status)) return `PoW 未通过：${powLabel(row.pow_status)}`
  return `PoW：${powLabel(row.pow_status)}`
}

function platformText(row) {
  if (!row.client_platform) return '—'
  return row.client_version ? `${row.client_platform} / ${row.client_version}` : row.client_platform
}

function statusVariant(status) {
  if (status >= 500) return 'danger'
  if (status >= 400) return 'warning'
  return 'success'
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

/* ==================== 标签页 ==================== */
.tabs {
  display: flex;
  gap: var(--n-space-1);
  margin-bottom: var(--n-space-4);
  border-bottom: 1px solid var(--n-line);
}

.tabs__item {
  padding: var(--n-space-2) var(--n-space-4);
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--n-text-muted);
  font-size: var(--n-text-base);
  cursor: pointer;
  transition: color var(--n-duration-fast) var(--n-ease);
}

.tabs__item--active {
  border-bottom-color: var(--n-accent);
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-semibold);
}

/* ==================== 统计卡 ==================== */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-4);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-1);
}

.stat__number {
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: var(--n-weight-bold);
  color: var(--n-text);
}

.stat__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 卡片 ==================== */
.card {
  margin-bottom: var(--n-space-4);
}

.card__title {
  margin: 0 0 var(--n-space-3);
  font-size: var(--n-text-base);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.chart__canvas {
  position: relative;
  height: 260px;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-4);
}

.ranking {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
}

.ranking li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-3);
  font-size: var(--n-text-sm);
}

.ranking__key {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--n-text-muted);
}

.ranking__count {
  flex: none;
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

/* ==================== 工具条 ==================== */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-4);
}

.toolbar__search {
  min-width: 200px;
}

.select {
  height: var(--n-tap-min);
  padding: 0 var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-sm);
  background: var(--n-surface-soft);
  color: var(--n-text);
  font-size: var(--n-text-sm);
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

.cell-ua {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--n-text-faint);
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

.live-note {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-3);
  padding: var(--n-space-2) var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-md);
  background: var(--n-surface-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
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
