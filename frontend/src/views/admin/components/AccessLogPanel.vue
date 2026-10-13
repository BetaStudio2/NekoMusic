<template>
  <section class="access">
    <header class="access__head">
      <h2 class="access__title">访问日志（安全分析）</h2>
      <p class="access__desc">
        每个请求一条：访问量、来源 IP 与网段、User-Agent、客户端指纹、状态码、耗时与本次的 PoW 难度，
        用于安全分析与爬虫 / 脚本排查。数据落在独立日志库，不参与业务库备份。
      </p>
    </header>

    <p v-if="error" class="err">
      <NIcon name="triangle-alert" :size="16" />
      {{ error }}
    </p>

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
      <div class="card__head">
        <h2 class="card__title">访问趋势</h2>
        <select v-model.number="statsDays" class="select" aria-label="统计天数" @change="loadStats">
          <option :value="7">最近 7 天</option>
          <option :value="14">最近 14 天</option>
          <option :value="30">最近 30 天</option>
        </select>
      </div>
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
      <select v-model="accessQuery.clientType" class="select" aria-label="客户端类型">
        <option value="">全部类型</option>
        <option v-for="type in CLIENT_TYPES" :key="type" :value="type">{{ clientTypeLabel(type) }}</option>
      </select>
      <select v-model="accessQuery.powStatus" class="select" aria-label="PoW 结果">
        <option value="">全部 PoW 结果</option>
        <option v-for="item in POW_STATUSES" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <NInput v-model="accessQuery.ip" placeholder="来源 IP 含…" clearable class="toolbar__search" />
      <NInput v-model="accessQuery.path" placeholder="路径含…" clearable class="toolbar__search" />
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
  </section>
</template>

<script setup>
/**
 * 访问日志面板（安全分析）。
 *
 * 放在管理后台首页「统计分析」里，而不是单独一个日志页：访问量统计与安全分析是运维每天要看的
 * 第一手数据，混在「操作日志」里会让人以为日志页只关心后台动作。
 *
 * 实时更新走 SSE（`/api/admin/logs/access/stream`）：连上后台则新日志直接追加到表格顶部；
 * 翻页或带筛选条件时暂停追加（否则会把不属于当前视图的行混进来），只提示；断线期间缺的行用列表接口补。
 */
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NTag } from '@/ui'
import { fetchAccessLogs, fetchAccessLogStats, openAccessLogStream } from '@/api/adminLogs.js'
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

const error = ref('')
const loading = ref(false)
const statsDays = ref(7)
const stats = ref({})
const access = reactive({ total: 0, page: 1, size: PAGE_SIZE, items: [] })
const accessQuery = reactive({ ip: '', path: '', clientType: '', keyword: '', powStatus: '' })

const trendCanvas = ref(null)
let trendChart = null

const live = ref(true)
const liveState = ref('connecting')
const liveReceived = ref(0)
let liveStop = null
let liveSeq = 0

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
  loadAccess(1)
  loadStats()
  syncLiveStream()
})

onUnmounted(() => {
  closeLiveStream()
  destroyChart()
})

// ── 实时流（SSE） ─────────────────────────────────────────────

function toggleLive() {
  live.value = !live.value
  syncLiveStream()
}

function syncLiveStream() {
  if (live.value) {
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
  if (livePaused.value) return
  row.__liveKey = `live-${(liveSeq += 1)}`
  access.items = [row, ...access.items].slice(0, LIVE_MAX_ROWS)
  access.total += 1
}

async function loadAccess(page) {
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

function clientTypeLabel(value) {
  return CLIENT_TYPE_LABELS[value] || value || '未知'
}

function categoryLabel(value) {
  return CATEGORY_LABELS[value] || value || '—'
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

function powLabel(value) {
  return POW_LABELS[value] || value
}

function powTitle(row) {
  if (!row.pow_status) return '本次请求未经过防重放挑战（静态资源 / 无需 nonce 的接口）'
  if (POW_REJECTED.has(row.pow_status)) return `PoW 未通过：${powLabel(row.pow_status)}`
  return `PoW：${powLabel(row.pow_status)}`
}
</script>

<style scoped>
.access {
  margin-top: var(--n-space-6);
}

.access__head {
  margin-bottom: var(--n-space-4);
}

.access__title {
  margin: 0 0 var(--n-space-1);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.access__desc {
  margin: 0;
  max-width: 72ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.err {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-4);
  color: var(--n-color-danger, #ff6b6b);
  font-size: var(--n-text-sm);
}

/* ==================== 统计卡 ==================== */
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-5);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-1);
}

.stat__number {
  color: var(--n-text);
  font-size: var(--n-text-2xl);
  font-weight: var(--n-weight-bold);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.stat__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ==================== 卡片 ==================== */
.card {
  margin-bottom: var(--n-space-5);
}

.card__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-3);
}

.card__title {
  margin: 0 0 var(--n-space-4);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
}

.card__head .card__title {
  margin-bottom: 0;
}

.chart__canvas {
  position: relative;
  width: 100%;
  min-height: 260px;
  margin-top: var(--n-space-4);
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-5);
}

.ranking {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
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
  font-variant-numeric: tabular-nums;
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
  flex: 1 1 180px;
  max-width: 260px;
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

.pager__info {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}
</style>
