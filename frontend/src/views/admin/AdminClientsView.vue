<template>
  <div class="subpage">
    <header class="subpage__head">
      <div>
        <h1 class="subpage__title">客户端接入</h1>
        <p class="subpage__desc">
          登记第三方客户端应用、下发公开 <code>client_id</code> 与机密 <code>secret</code>，
          并查看或吊销已注册的安装实例。每个实例仍使用自己的私钥和短期令牌完成请求签名。
        </p>
      </div>
      <div class="head-actions">
        <NButton variant="secondary" icon="refresh" :disabled="loading" @click="loadAll">刷新</NButton>
        <NButton v-if="canEdit && activeTab === 'apps'" variant="primary" icon="plus" @click="openCreate">
          注册客户端
        </NButton>
      </div>
    </header>

    <p v-if="!canView" class="err">
      <NIcon name="shield-alert" :size="16" />
      当前账号无权查看客户端接入信息。
    </p>

    <template v-else>
      <section class="summary" aria-label="客户端概览">
        <div class="summary__item">
          <span class="summary__label">客户端应用</span>
          <strong class="summary__value">{{ apps.length }}</strong>
        </div>
        <div class="summary__item">
          <span class="summary__label">启用中</span>
          <strong class="summary__value">{{ activeAppCount }}</strong>
        </div>
        <div class="summary__item">
          <span class="summary__label">机密客户端</span>
          <strong class="summary__value">{{ confidentialAppCount }}</strong>
        </div>
        <div class="summary__item">
          <span class="summary__label">活跃实例</span>
          <strong class="summary__value">{{ activeInstanceCount }}</strong>
        </div>
      </section>

      <div class="tabs" role="tablist" aria-label="客户端接入视图">
        <button
          type="button"
          class="tabs__item"
          :class="{ 'tabs__item--active': activeTab === 'apps' }"
          role="tab"
          :aria-selected="activeTab === 'apps'"
          @click="activeTab = 'apps'"
        >
          应用凭据
          <span class="tabs__count">{{ apps.length }}</span>
        </button>
        <button
          type="button"
          class="tabs__item"
          :class="{ 'tabs__item--active': activeTab === 'instances' }"
          role="tab"
          :aria-selected="activeTab === 'instances'"
          @click="activeTab = 'instances'"
        >
          安装实例
          <span class="tabs__count">{{ instances.length }}</span>
        </button>
      </div>

      <p v-if="error" class="err">
        <NIcon name="triangle-alert" :size="16" />
        {{ error }}
      </p>

      <div v-if="loading" class="loading">正在加载客户端数据…</div>

      <template v-else>
        <section v-if="activeTab === 'apps'" class="panel">
          <div class="toolbar">
            <NInput
              v-model="appSearch"
              icon="search"
              placeholder="搜索名称、Client ID、归属方或平台"
              clearable
              class="toolbar__search"
            />
            <select v-model="appStatusFilter" class="select" aria-label="应用状态">
              <option value="">全部状态</option>
              <option value="active">启用中</option>
              <option value="disabled">已停用</option>
            </select>
          </div>

          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>应用</th>
                  <th>Client ID</th>
                  <th>平台 / 类型</th>
                  <th>状态</th>
                  <th>实例</th>
                  <th>有效期</th>
                  <th aria-label="操作" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="app in pagedApps" :key="app.clientId">
                  <td>
                    <div class="cell-title">{{ app.name }}</div>
                    <div class="cell-sub">{{ app.ownerLabel || '未填写归属方' }}</div>
                    <div v-if="app.attestationRequired" class="cell-sub">需要硬件证明</div>
                  </td>
                  <td>
                    <button type="button" class="copy-value" :title="`复制 ${app.clientId}`" @click="copyText(app.clientId, 'Client ID')">
                      {{ app.clientId }}
                    </button>
                    <div v-if="app.allowedOrigins" class="cell-sub clamp" :title="app.allowedOrigins">
                      Origin：{{ app.allowedOrigins }}
                    </div>
                    <div v-else-if="app.packageName" class="cell-sub clamp" :title="app.packageName">
                      {{ app.packageName }}
                    </div>
                    <div v-if="app.signingDigest" class="cell-sub clamp" :title="app.signingDigest">
                      签名：{{ app.signingDigest }}
                    </div>
                  </td>
                  <td>
                    <div>{{ platformLabel(app.platform) }}</div>
                    <div class="tag-line">
                      <NTag :variant="app.confidential ? 'warning' : 'default'" size="sm">
                        {{ app.confidential ? '机密' : '公开' }}
                      </NTag>
                      <NTag v-if="app.official" variant="accent" size="sm">官方</NTag>
                    </div>
                  </td>
                  <td>
                    <NTag :variant="app.status === 'active' && !isExpired(app.expiresAt) ? 'success' : 'danger'" size="sm">
                      {{ statusLabel(app.status) }}
                    </NTag>
                    <div v-if="isExpired(app.expiresAt)" class="cell-sub">已过期</div>
                  </td>
                  <td>
                    <button type="button" class="link-button" @click="openAppInstances(app.clientId)">
                      <span class="cell-strong">{{ instanceStats(app.clientId).total }}</span>
                      <span class="cell-sub"> / 活跃 {{ instanceStats(app.clientId).active }}</span>
                    </button>
                  </td>
                  <td class="cell-dim">
                    <div>{{ formatTime(app.createdAt) }} 创建</div>
                    <div class="cell-sub">{{ app.expiresAt ? `${formatTime(app.expiresAt)} 到期` : '长期有效' }}</div>
                  </td>
                  <td class="cell-actions">
                    <NButton
                      v-if="canEdit && app.confidential && !app.official"
                      size="sm"
                      variant="ghost"
                      icon="refresh"
                      :loading="rotatingId === app.clientId"
                      @click="rotateSecret(app)"
                    >
                      轮换密钥
                    </NButton>
                    <NButton
                      v-if="canEdit"
                      size="sm"
                      :variant="app.status === 'active' ? 'danger' : 'secondary'"
                      @click="toggleApp(app)"
                    >
                      {{ app.status === 'active' ? '停用' : '启用' }}
                    </NButton>
                  </td>
                </tr>
                <tr v-if="!filteredApps.length">
                  <td colspan="7" class="cell-empty">没有匹配的客户端应用</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="filteredApps.length" class="pager">
            <NButton size="sm" variant="secondary" icon="chevron-left" :disabled="appPage <= 1" @click="appPage--">
              上一页
            </NButton>
            <span class="pager__info">第 {{ appPage }} / {{ appTotalPages }} 页</span>
            <NButton
              size="sm"
              variant="secondary"
              icon-after="chevron-right"
              :disabled="appPage >= appTotalPages"
              @click="appPage++"
            >
              下一页
            </NButton>
          </div>
        </section>

        <section v-else class="panel">
          <div class="toolbar">
            <NInput
              v-model="instanceSearch"
              icon="search"
              placeholder="搜索 Instance ID、Client ID、应用名或 App ID"
              clearable
              class="toolbar__search"
            />
            <select v-model="instanceClientFilter" class="select" aria-label="所属应用">
              <option value="">全部应用</option>
              <option v-for="app in apps" :key="app.clientId" :value="app.clientId">
                {{ app.name }} · {{ app.clientId }}
              </option>
            </select>
            <select v-model="instanceStatusFilter" class="select" aria-label="实例状态">
              <option value="">全部状态</option>
              <option value="active">活跃</option>
              <option value="revoked">已吊销</option>
            </select>
          </div>

          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>Instance ID</th>
                  <th>客户端应用</th>
                  <th>平台</th>
                  <th>证明</th>
                  <th>状态</th>
                  <th>最近活动</th>
                  <th>创建时间</th>
                  <th aria-label="操作" />
                </tr>
              </thead>
              <tbody>
                <tr v-for="instance in pagedInstances" :key="instance.instanceId">
                  <td>
                    <button
                      type="button"
                      class="copy-value"
                      :title="`复制 ${instance.instanceId}`"
                      @click="copyText(instance.instanceId, 'Instance ID')"
                    >
                      {{ instance.instanceId }}
                    </button>
                    <div v-if="instance.appId" class="cell-sub clamp" :title="instance.appId">{{ instance.appId }}</div>
                  </td>
                  <td>
                    <div class="cell-title">{{ appName(instance.clientId) }}</div>
                    <div class="cell-sub">{{ instance.clientId }}</div>
                  </td>
                  <td>{{ platformLabel(instance.platform) }}</td>
                  <td>
                    <NTag :variant="instance.attestationLevel === 'none' ? 'default' : 'success'" size="sm">
                      {{ attestationLabel(instance.attestationLevel) }}
                    </NTag>
                  </td>
                  <td>
                    <NTag :variant="instance.status === 'active' && !isExpired(instance.expiresAt) ? 'success' : 'danger'" size="sm">
                      {{ instanceStatusLabel(instance.status) }}
                    </NTag>
                    <div v-if="instance.expiresAt" class="cell-sub">{{ formatTime(instance.expiresAt) }} 到期</div>
                  </td>
                  <td class="cell-dim">{{ formatTime(instance.lastSeenAt) }}</td>
                  <td class="cell-dim">{{ formatTime(instance.createdAt) }}</td>
                  <td class="cell-actions">
                    <NButton
                      v-if="canEdit && instance.status === 'active'"
                      size="sm"
                      variant="danger"
                      icon="shield-alert"
                      :loading="revokingId === instance.instanceId"
                      @click="revokeInstance(instance)"
                    >
                      吊销
                    </NButton>
                  </td>
                </tr>
                <tr v-if="!filteredInstances.length">
                  <td colspan="8" class="cell-empty">没有匹配的安装实例</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="filteredInstances.length" class="pager">
            <NButton size="sm" variant="secondary" icon="chevron-left" :disabled="instancePage <= 1" @click="instancePage--">
              上一页
            </NButton>
            <span class="pager__info">第 {{ instancePage }} / {{ instanceTotalPages }} 页</span>
            <NButton
              size="sm"
              variant="secondary"
              icon-after="chevron-right"
              :disabled="instancePage >= instanceTotalPages"
              @click="instancePage++"
            >
              下一页
            </NButton>
          </div>
        </section>
      </template>
    </template>

    <NModal v-model="creatingOpen" title="注册客户端应用" size="lg" :mask-closable="false" @close="resetCreateForm">
      <div class="form-grid">
        <div class="field">
          <label class="field__label" for="client-name">应用名称</label>
          <NInput id="client-name" v-model="createForm.name" placeholder="例如：某播放器 Android" />
        </div>
        <div class="field">
          <label class="field__label" for="client-owner">归属方 / 联系人</label>
          <NInput id="client-owner" v-model="createForm.ownerLabel" placeholder="例如：某某团队" />
        </div>
        <div class="field">
          <label class="field__label" for="client-platform">平台</label>
          <select id="client-platform" v-model="createForm.platform" class="select select--block">
            <option v-for="item in PLATFORMS" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <div class="field">
          <label class="field__label" for="client-expires">有效期（天，0 = 长期）</label>
          <NInput id="client-expires" v-model="createForm.expiresDays" type="number" min="0" max="3650" />
        </div>
      </div>

      <label class="check">
        <input v-model="createForm.confidential" type="checkbox" />
        机密客户端（注册实例时必须同时提供 secret）
      </label>
      <p class="hint">公开客户端的 client_id 可以公开发布；机密客户端只保存 secret 哈希，明文仅在创建或轮换时显示一次。</p>

      <label class="check">
        <input v-model="createForm.attestationRequired" type="checkbox" :disabled="!supportsAttestation" />
        要求硬件密钥证明（仅 Android）
      </label>
      <p v-if="!supportsAttestation" class="hint">切换为 Android / Android 手表平台后可启用硬件证明。</p>

      <div v-if="createForm.platform === 'web'" class="field field--top">
        <label class="field__label" for="client-origins">允许的 Web Origin</label>
        <NInput
          id="client-origins"
          v-model="createForm.allowedOrigins"
          textarea
          :rows="3"
          placeholder="每行一个 Origin，例如 https://player.example.com"
        />
      </div>

      <div v-if="createForm.attestationRequired" class="form-grid field--top">
        <div class="field">
          <label class="field__label" for="client-package">Android 包名</label>
          <NInput id="client-package" v-model="createForm.packageName" placeholder="例如：com.example.player" />
        </div>
        <div class="field">
          <label class="field__label" for="client-digest">签名证书 SHA-256</label>
          <NInput id="client-digest" v-model="createForm.signingDigest" placeholder="64 位十六进制，可带冒号" />
        </div>
      </div>

      <template #footer>
        <NButton variant="ghost" :disabled="creating" @click="creatingOpen = false">取消</NButton>
        <NButton variant="primary" :loading="creating" @click="submitCreate">创建并生成凭据</NButton>
      </template>
    </NModal>

    <NModal v-model="credentialOpen" title="客户端凭据" :mask-closable="false">
      <p v-if="credentialSecret" class="secret-warning">
        <NIcon name="shield-alert" :size="16" />
        secret 只显示这一次。关闭弹窗后无法再次查看，请立即保存到安全的位置。
      </p>
      <div class="credential-row">
        <span class="credential-row__label">Client ID</span>
        <code class="credential-row__value">{{ credentialClientId }}</code>
        <NButton size="sm" variant="secondary" icon="copy" @click="copyText(credentialClientId, 'Client ID')">复制</NButton>
      </div>
      <div v-if="credentialSecret" class="credential-row">
        <span class="credential-row__label">Secret</span>
        <code class="credential-row__value">{{ credentialSecret }}</code>
        <NButton size="sm" variant="secondary" icon="copy" @click="copyText(credentialSecret, 'Secret')">复制</NButton>
      </div>
      <p v-if="credentialSecret" class="hint">轮换 secret 后，旧 secret 立即不能用于注册新实例；已经注册的实例需单独吊销才会失效。</p>
      <template #footer>
        <NButton variant="primary" @click="credentialOpen = false">我已保存</NButton>
      </template>
    </NModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NInput, NModal, NTag } from '@/ui'
import {
  createAdminClientApp,
  fetchAdminClientApps,
  fetchAdminClientInstances,
  revokeAdminClientInstance,
  rotateAdminClientSecret,
  updateAdminClientAppStatus
} from '@/api/adminClients.js'

const PAGE_SIZE = 15
const PLATFORMS = [
  { value: 'web', label: 'Web' },
  { value: 'pc', label: 'PC' },
  { value: 'android', label: 'Android' },
  { value: 'androidwatch', label: 'Android 手表' },
  { value: 'windows', label: 'Windows' },
  { value: 'linux', label: 'Linux' },
  { value: 'mac', label: 'macOS' },
  { value: 'ios', label: 'iOS' },
  { value: 'other', label: '其他' }
]

const PLATFORM_LABELS = Object.fromEntries(PLATFORMS.map((item) => [item.value, item.label]))
const toast = useToast()
const adminInfo = ref({})

const loading = ref(false)
const error = ref('')
const apps = ref([])
const instances = ref([])
const activeTab = ref('apps')

const appSearch = ref('')
const appStatusFilter = ref('')
const appPage = ref(1)

const instanceSearch = ref('')
const instanceClientFilter = ref('')
const instanceStatusFilter = ref('')
const instancePage = ref(1)

const creatingOpen = ref(false)
const creating = ref(false)
const rotatingId = ref('')
const revokingId = ref('')
const credentialOpen = ref(false)
const credentialClientId = ref('')
const credentialSecret = ref('')

const emptyCreateForm = () => ({
  name: '',
  ownerLabel: '',
  platform: 'web',
  confidential: true,
  attestationRequired: false,
  allowedOrigins: '',
  packageName: '',
  signingDigest: '',
  expiresDays: 0
})
const createForm = reactive(emptyCreateForm())

const canView = computed(() => ['super_admin', 'admin'].includes(adminInfo.value.role || ''))
const canEdit = canView
const supportsAttestation = computed(() => ['android', 'androidwatch'].includes(createForm.platform))

const activeAppCount = computed(() => apps.value.filter((app) => app.status === 'active' && !isExpired(app.expiresAt)).length)
const confidentialAppCount = computed(() => apps.value.filter((app) => app.confidential).length)
const activeInstanceCount = computed(() => instances.value.filter((item) => item.status === 'active' && !isExpired(item.expiresAt)).length)

const appById = computed(() => new Map(apps.value.map((app) => [app.clientId, app])))
const instanceCounts = computed(() => {
  const counts = new Map()
  for (const instance of instances.value) {
    const current = counts.get(instance.clientId) || { total: 0, active: 0 }
    current.total += 1
    if (instance.status === 'active' && !isExpired(instance.expiresAt)) current.active += 1
    counts.set(instance.clientId, current)
  }
  return counts
})

const filteredApps = computed(() => {
  const query = appSearch.value.trim().toLowerCase()
  return apps.value.filter((app) => {
    if (appStatusFilter.value && app.status !== appStatusFilter.value) return false
    if (!query) return true
    return [app.name, app.clientId, app.ownerLabel, app.platform, app.packageName, app.allowedOrigins]
      .some((value) => String(value || '').toLowerCase().includes(query))
  })
})

const filteredInstances = computed(() => {
  const query = instanceSearch.value.trim().toLowerCase()
  return instances.value.filter((instance) => {
    if (instanceClientFilter.value && instance.clientId !== instanceClientFilter.value) return false
    if (instanceStatusFilter.value && instance.status !== instanceStatusFilter.value) return false
    if (!query) return true
    return [instance.instanceId, instance.clientId, instance.platform, instance.appId, instance.signingDigest, appName(instance.clientId)]
      .some((value) => String(value || '').toLowerCase().includes(query))
  })
})

const appTotalPages = computed(() => Math.max(1, Math.ceil(filteredApps.value.length / PAGE_SIZE)))
const instanceTotalPages = computed(() => Math.max(1, Math.ceil(filteredInstances.value.length / PAGE_SIZE)))
const pagedApps = computed(() => filteredApps.value.slice((appPage.value - 1) * PAGE_SIZE, appPage.value * PAGE_SIZE))
const pagedInstances = computed(() => filteredInstances.value.slice((instancePage.value - 1) * PAGE_SIZE, instancePage.value * PAGE_SIZE))

watch([appSearch, appStatusFilter], () => { appPage.value = 1 })
watch([instanceSearch, instanceClientFilter, instanceStatusFilter], () => { instancePage.value = 1 })
watch(appTotalPages, (total) => { if (appPage.value > total) appPage.value = total })
watch(instanceTotalPages, (total) => { if (instancePage.value > total) instancePage.value = total })
watch(supportsAttestation, (supported) => { if (!supported) createForm.attestationRequired = false })

onMounted(() => {
  const stored = localStorage.getItem('adminInfo')
  if (stored) {
    try {
      adminInfo.value = JSON.parse(stored)
    } catch {
      adminInfo.value = {}
    }
  }
  if (canView.value) loadAll()
})

async function loadAll() {
  if (!canView.value) return
  loading.value = true
  error.value = ''
  try {
    const [appItems, instanceItems] = await Promise.all([
      fetchAdminClientApps(),
      fetchAdminClientInstances()
    ])
    apps.value = appItems
    instances.value = instanceItems
  } catch (e) {
    error.value = e.message || '加载客户端数据失败'
    toast.error(error.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, emptyCreateForm())
  creatingOpen.value = true
}

function resetCreateForm() {
  if (!creating.value) Object.assign(createForm, emptyCreateForm())
}

async function submitCreate() {
  const name = createForm.name.trim()
  if (!name) {
    toast.error('请填写应用名称')
    return
  }
  const digest = createForm.signingDigest.replace(/:/g, '').trim().toLowerCase()
  if (createForm.attestationRequired) {
    if (!supportsAttestation.value) {
      toast.error('硬件证明仅支持 Android 平台')
      return
    }
    if (!createForm.packageName.trim() || !/^[0-9a-f]{64}$/.test(digest)) {
      toast.error('硬件证明需要填写包名和 64 位 SHA-256 签名摘要')
      return
    }
  }
  creating.value = true
  try {
    const data = await createAdminClientApp({
      name,
      ownerLabel: createForm.ownerLabel.trim(),
      platform: createForm.platform,
      confidential: createForm.confidential,
      attestationRequired: createForm.attestationRequired,
      allowedOrigins: createForm.platform === 'web' ? createForm.allowedOrigins.trim() : '',
      packageName: createForm.attestationRequired ? createForm.packageName.trim() : '',
      signingDigest: createForm.attestationRequired ? digest : '',
      expiresDays: Number(createForm.expiresDays) || 0
    })
    creatingOpen.value = false
    showCredential(data.clientId, data.clientSecret || '')
    toast.success('客户端已创建')
    await loadAll()
  } catch (e) {
    toast.error(e.message || '创建客户端失败')
  } finally {
    creating.value = false
  }
}

async function toggleApp(app) {
  const nextStatus = app.status === 'active' ? 'disabled' : 'active'
  const action = nextStatus === 'active' ? '启用' : '停用'
  const extra = app.official ? '这是官方客户端，停用会导致对应平台无法访问。' : ''
  if (!window.confirm(`确定${action}「${app.name}」吗？${extra}`)) return
  try {
    await updateAdminClientAppStatus(app.clientId, nextStatus)
    toast.success(`已${action}「${app.name}」`)
    await loadAll()
  } catch (e) {
    toast.error(e.message || `${action}客户端失败`)
  }
}

async function rotateSecret(app) {
  if (!window.confirm(`确定轮换「${app.name}」的 secret 吗？旧 secret 会立即失效。`)) return
  rotatingId.value = app.clientId
  try {
    const data = await rotateAdminClientSecret(app.clientId)
    showCredential(data.clientId || app.clientId, data.clientSecret || '')
    toast.success('Secret 已轮换')
  } catch (e) {
    toast.error(e.message || '轮换 secret 失败')
  } finally {
    rotatingId.value = ''
  }
}

async function revokeInstance(instance) {
  if (!window.confirm(`确定吊销实例 ${instance.instanceId} 吗？该实例之后将无法再发起请求。`)) return
  revokingId.value = instance.instanceId
  try {
    await revokeAdminClientInstance(instance.instanceId)
    toast.success('实例已吊销')
    await loadAll()
  } catch (e) {
    toast.error(e.message || '吊销实例失败')
  } finally {
    revokingId.value = ''
  }
}

function openAppInstances(clientId) {
  instanceClientFilter.value = clientId
  activeTab.value = 'instances'
}

function showCredential(clientId, secret) {
  credentialClientId.value = clientId || ''
  credentialSecret.value = secret || ''
  credentialOpen.value = true
}

function appName(clientId) {
  return appById.value.get(clientId)?.name || clientId || '未知应用'
}

function instanceStats(clientId) {
  return instanceCounts.value.get(clientId) || { total: 0, active: 0 }
}

function isExpired(expiresAt) {
  return Boolean(expiresAt && Number(expiresAt) > 0 && Number(expiresAt) <= Date.now())
}

function formatTime(value) {
  if (!value) return '—'
  return new Date(Number(value)).toLocaleString('zh-CN', { hour12: false })
}

function platformLabel(value) {
  return PLATFORM_LABELS[value] || value || '—'
}

function statusLabel(status) {
  return status === 'active' ? '启用中' : status === 'disabled' ? '已停用' : status || '—'
}

function instanceStatusLabel(status) {
  return status === 'active' ? '活跃' : status === 'revoked' ? '已吊销' : status || '—'
}

function attestationLabel(value) {
  if (!value || value === 'none') return '无'
  if (value === 'tee') return 'TEE'
  if (value === 'strongbox') return 'StrongBox'
  return value
}

async function copyText(value, label) {
  const text = String(value || '')
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      const copied = document.execCommand('copy')
      document.body.removeChild(textarea)
      if (!copied) throw new Error('copy failed')
    }
    toast.success(`${label}已复制`)
  } catch {
    toast.error(`复制${label}失败，请手动复制`)
  }
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
  max-width: 76ch;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.head-actions {
  display: flex;
  gap: var(--n-space-2);
  flex-wrap: wrap;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-4);
}

.summary__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
}

.summary__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
}

.summary__value {
  color: var(--n-text);
  font-size: var(--n-text-xl);
  font-variant-numeric: tabular-nums;
}

.tabs {
  display: flex;
  gap: var(--n-space-2);
  margin-bottom: var(--n-space-4);
}

.tabs__item {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  height: 38px;
  padding: 0 var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.tabs__item--active {
  border-color: var(--n-accent-line);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.tabs__count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: var(--n-radius-circle);
  background: var(--n-surface-hover);
  color: inherit;
  font-size: var(--n-text-xs);
  font-variant-numeric: tabular-nums;
}

.panel {
  min-width: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-4);
}

.toolbar__search {
  flex: 1 1 260px;
  min-width: 0;
}

.select {
  height: 40px;
  max-width: 100%;
  padding: 0 var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text);
  font-size: var(--n-text-base);
  outline: none;
}

.select:focus {
  border-color: var(--n-accent-line);
  box-shadow: var(--n-shadow-glow);
}

.select option {
  background: var(--n-bg-elevated);
  color: var(--n-text);
}

.select--block {
  width: 100%;
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
  white-space: nowrap;
}

.table th {
  background: var(--n-accent-soft);
  font-weight: var(--n-weight-semibold);
}

.table tbody tr:last-child td {
  border-bottom: none;
}

@media (hover: hover) {
  .table tbody tr:hover td {
    background: var(--n-surface-soft);
  }
}

.cell-title,
.cell-strong {
  color: var(--n-text);
  font-weight: var(--n-weight-medium);
}

.cell-dim {
  color: var(--n-text-muted);
}

.cell-sub {
  margin-top: 3px;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.clamp {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-line {
  display: flex;
  gap: 4px;
  margin-top: 5px;
}

.cell-actions {
  display: flex;
  gap: var(--n-space-2);
}

.cell-empty {
  padding: var(--n-space-10) var(--n-space-4) !important;
  text-align: center;
  color: var(--n-text-faint);
}

.copy-value {
  max-width: 360px;
  overflow: hidden;
  padding: 0;
  border: 0;
  background: none;
  color: var(--n-text);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-xs);
  text-align: left;
  text-overflow: ellipsis;
  cursor: copy;
}

.link-button {
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

@media (hover: hover) {
  .copy-value:hover {
    color: var(--n-accent-strong);
  }
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--n-space-4);
  margin-top: var(--n-space-4);
}

.pager__info {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-variant-numeric: tabular-nums;
}

.loading {
  padding: var(--n-space-10);
  color: var(--n-text-muted);
  text-align: center;
}

.err {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0 0 var(--n-space-4);
  color: var(--n-danger);
  font-size: var(--n-text-sm);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--n-space-4);
}

.field {
  margin-bottom: var(--n-space-4);
}

.field--top {
  margin-top: var(--n-space-4);
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
}

.check {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-2);
  margin-top: var(--n-space-3);
  color: var(--n-text);
  font-size: var(--n-text-sm);
  cursor: pointer;
}

.check input {
  margin-top: 3px;
}

.hint {
  margin: var(--n-space-2) 0 var(--n-space-4);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  line-height: var(--n-leading-normal);
}

.secret-warning {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-2);
  padding: var(--n-space-3);
  border: 1px solid rgba(247, 198, 106, 0.28);
  border-radius: var(--n-radius-control);
  background: var(--n-warning-soft);
  color: var(--n-warning);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.credential-row {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--n-space-3);
  margin-top: var(--n-space-4);
}

.credential-row__label {
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.credential-row__value {
  min-width: 0;
  overflow-wrap: anywhere;
  padding: var(--n-space-2) var(--n-space-3);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  color: var(--n-text);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-xs);
}

@media (max-width: 900px) {
  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
