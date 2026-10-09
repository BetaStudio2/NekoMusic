<script setup>
/**
 * NotificationsView —— 站内消息（收件箱）
 * ------------------------------------------------------------
 * 契约：
 *  - GET  /api/user/notifications?since=&before=&limit=  列表（Authorization: 裸 userToken）
 *  - GET  /api/user/notifications/stream                实时推送（SSE），新消息直接插到最前
 *  - POST /api/user/notifications/read                   标记已读（ids 为空 = 全部已读）
 *  - 点击消息：先标记已读，再按 link 跳到对应站内页面（如 /detail/{musicId}）
 *
 * 「已登录才可见」由路由守卫与页面自身共同兜底；未登录时弹登录面板。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'
import { openAuthDialog } from '@/composables/useAuthDialog'
import {
  fetchNotifications,
  markNotificationsRead,
  NOTIFICATION_SYNC_EVENT,
  subscribeNotifications,
  syncNotificationStream,
} from '@/api/notifications.js'

const PAGE_SIZE = 20

const router = useRouter()
const toast = useToast()

const items = ref([])
const unread = ref(0)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
let notificationSubscription = null

const isEmpty = computed(() => !loading.value && items.value.length === 0)
/** 本地最新游标：items 新的在前，第一条就是当前最大 id */
const newestId = computed(() => (items.value.length ? items.value[0].id : 0))

function token() {
  return localStorage.getItem('userToken')
}

function syncBadge(count) {
  window.dispatchEvent(new CustomEvent(NOTIFICATION_SYNC_EVENT, { detail: { unread: count } }))
}

/** 首屏：拉最新一页，顺带把未读数刷新掉 */
async function loadFirstPage() {
  if (!token()) {
    loading.value = false
    openAuthDialog('login')
    return
  }
  loading.value = true
  try {
    const data = await fetchNotifications({ limit: PAGE_SIZE })
    items.value = data.items || []
    unread.value = data.unread || 0
    hasMore.value = Boolean(data.hasMore)
    syncBadge(unread.value)
  } catch (error) {
    console.error('获取站内消息失败:', error)
    toast.error('消息加载失败，请稍后再试')
  } finally {
    loading.value = false
  }
}

/** 翻更早的历史：以当前最旧一条的 id 作为 before */
async function loadMore() {
  if (loadingMore.value || !items.value.length) return
  loadingMore.value = true
  try {
    const oldestId = items.value[items.value.length - 1].id
    const data = await fetchNotifications({ before: oldestId, limit: PAGE_SIZE })
    const incoming = data.items || []
    const known = new Set(items.value.map((item) => item.id))
    items.value = items.value.concat(incoming.filter((item) => !known.has(item.id)))
    unread.value = data.unread || 0
    hasMore.value = Boolean(data.hasMore)
  } catch (error) {
    console.error('加载更多站内消息失败:', error)
    toast.error('加载失败，请稍后再试')
  } finally {
    loadingMore.value = false
  }
}

async function markAllRead() {
  if (unread.value === 0) return
  try {
    const data = await markNotificationsRead([])
    unread.value = data.unread ?? 0
    items.value = items.value.map((item) => ({ ...item, read: true }))
    syncBadge(unread.value)
  } catch (error) {
    console.error('标记已读失败:', error)
    toast.error('操作失败，请稍后再试')
  }
}

async function openItem(item) {
  if (!item.read) {
    try {
      const data = await markNotificationsRead([item.id])
      unread.value = data.unread ?? unread.value
      item.read = true
      syncBadge(unread.value)
    } catch (error) {
      // 标记失败不拦跳转：消息仍然是已送达的，下次进页面还会补拉
      console.error('标记单条已读失败:', error)
    }
  }
  if (item.link) router.push(item.link)
}

// ── 实时推送：连上校准未读，之后新消息直接插到最前 ──────────────

/** 连上（含自动重连）时校准未读数；若服务端游标更新则补拉断线期间漏掉的消息。 */
async function handleStreamReady(data) {
  if (typeof data.unread === 'number') unread.value = data.unread
  if (items.value.length && data.latestId > newestId.value) await pullNewer()
}

/** 新消息：插入列表并更新红点，不再请求列表接口。 */
function handleStreamMessage(item) {
  if (items.value.some((existing) => existing.id === item.id)) return
  items.value = [item, ...items.value]
  if (!item.read) unread.value += 1
  syncBadge(unread.value)
}

/** 以本地最新 id 为游标补拉断线期间漏掉的消息。 */
async function pullNewer() {
  if (!token() || !items.value.length) return
  try {
    const data = await fetchNotifications({ since: newestId.value, limit: PAGE_SIZE })
    const incoming = data.items || []
    const known = new Set(items.value.map((item) => item.id))
    const fresh = incoming.filter((item) => !known.has(item.id))
    if (fresh.length) items.value = fresh.concat(items.value)
    unread.value = data.unread ?? unread.value
  } catch (error) {
    console.error('补拉站内消息失败:', error)
  }
}

function formatTime(raw) {
  if (!raw) return ''
  const text = String(raw).replace('T', ' ')
  return text.length >= 16 ? text.slice(5, 16) : text
}

onMounted(() => {
  loadFirstPage()
  notificationSubscription = subscribeNotifications({
    onReady: handleStreamReady,
    onMessage: handleStreamMessage,
  })
  window.addEventListener('storage', handleStorageChange)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  if (notificationSubscription) notificationSubscription.close()
})

function handleStorageChange(event) {
  if (event.key !== 'userToken') return
  syncNotificationStream()
  if (token()) loadFirstPage()
  else {
    items.value = []
    unread.value = 0
    syncBadge(0)
  }
}
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default" title="消息中心" subtitle="评论回复等消息会出现在这里，离线期间的消息上线后自动补齐">
    <template #actions>
      <NButton
        variant="secondary"
        size="sm"
        icon="check"
        :disabled="unread === 0"
        @click="markAllRead"
      >
        全部已读<span v-if="unread > 0">（{{ unread }}）</span>
      </NButton>
    </template>

    <div v-if="loading" class="notice__loading">
      <NSpinner :size="24" />
      <p>正在加载消息…</p>
    </div>

    <NCard v-else-if="isEmpty" pad="lg" class="notice__empty">
      <NIcon name="inbox" :size="26" />
      <p>暂无消息</p>
      <p class="notice__empty-sub">有人回复你的评论时，会在这里提醒你。</p>
    </NCard>

    <template v-else>
      <ul class="notice__list">
        <li v-for="item in items" :key="item.id">
          <button
            type="button"
            class="notice__item"
            :class="{ 'notice__item--unread': !item.read }"
            @click="openItem(item)"
          >
            <span class="notice__dot" :class="{ 'notice__dot--on': !item.read }" aria-hidden="true" />
            <span class="notice__body">
              <span class="notice__title">{{ item.title }}</span>
              <span v-if="item.body" class="notice__text">{{ item.body }}</span>
              <span class="notice__meta">
                <NIcon name="clock" :size="13" />
                {{ formatTime(item.createdAt) }}
              </span>
            </span>
            <NIcon v-if="item.link" name="chevron-right" :size="16" class="notice__arrow" />
          </button>
        </li>
      </ul>

      <div v-if="hasMore" class="notice__more">
        <NButton variant="ghost" size="sm" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中…' : '加载更早的消息' }}
        </NButton>
      </div>
    </template>
  </PageShell>
</template>

<style scoped>
.notice__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-8) 0;
  color: var(--n-text-muted);
}

.notice__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-2);
  text-align: center;
  color: var(--n-text-muted);
}

.notice__empty-sub {
  font-size: var(--n-text-sm);
}

.notice__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--n-space-2);
}

.notice__item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-3);
  padding: var(--n-space-3) var(--n-space-4);
  text-align: left;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius);
  background: var(--n-surface-soft);
  color: inherit;
  cursor: pointer;
}

@media (hover: hover) {
  .notice__item:hover {
    border-color: var(--n-line-strong);
    background: var(--n-surface-hover);
  }
}

.notice__item--unread {
  border-color: var(--n-accent-line);
}

.notice__dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 50%;
  background: transparent;
}

.notice__dot--on {
  background: var(--n-accent-strong);
}

.notice__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.notice__title {
  font-weight: var(--n-weight-bold);
  color: var(--n-text);
}

.notice__text {
  color: var(--n-text-muted);
  overflow-wrap: anywhere;
}

.notice__meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--n-text-xs);
  color: var(--n-text-faint);
}

.notice__arrow {
  flex: none;
  margin-top: 4px;
  color: var(--n-text-muted);
}

.notice__more {
  display: flex;
  justify-content: center;
  padding: var(--n-space-4) 0;
}
</style>
