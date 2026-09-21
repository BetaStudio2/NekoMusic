<script setup>
/**
 * CommentPanel —— 单曲评论面板
 * ------------------------------------------------------------
 * 数据来自 `/api/comments`（列表 / 发表 / 回复 / 删除同一个端点）。
 * 结构为「楼层 + 楼层内回复」两层：回复再回复仍挂回同一楼层，用 @昵称 标记对象。
 * 每条评论展示头像、昵称、时间与 IP 归属地。
 */
import { computed, nextTick, ref, watch } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import NButton from '@/ui/NButton.vue'
import { avatarUrl, useAvatarVersion } from '@/utils/userAvatar'
import { useAuth } from '@/composables/useAuth'
import { openAuthDialog } from '@/composables/useAuthDialog'
import { useToast } from '@/composables/useToast'
import { deleteComment, fetchComments, postComment } from '@/api/comments'

const props = defineProps({
  musicId: { type: [Number, String], required: true },
})

const emit = defineEmits(['count'])

const PAGE_SIZE = 20
const MAX_LENGTH = 500

const { isLoggedIn } = useAuth()
const avatarVersion = useAvatarVersion()
const toast = useToast()

const floors = ref([])
const floorTotal = ref(0)
const commentTotal = ref(0)
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const submitting = ref(false)
const content = ref('')
const replyTarget = ref(null)
const textareaRef = ref(null)

const canSubmit = computed(() => content.value.trim().length > 0 && !submitting.value)
const remaining = computed(() => MAX_LENGTH - content.value.length)

function userAvatar(userId) {
  // 读一次 avatarVersion，换头像后这里的 URL 会整体刷新
  return avatarUrl(userId, avatarVersion.value)
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return value
  const diff = Math.floor((Date.now() - date.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function applyPage(data, append) {
  const list = Array.isArray(data?.comments) ? data.comments : []
  floors.value = append ? [...floors.value, ...list] : list
  floorTotal.value = data?.total ?? list.length
  commentTotal.value = data?.totalComments ?? floorTotal.value
  hasMore.value = !!data?.hasMore
  emit('count', commentTotal.value)
}

async function load(reset = true) {
  const targetPage = reset ? 1 : page.value + 1
  if (reset) {
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const data = await fetchComments(props.musicId, { page: targetPage, pageSize: PAGE_SIZE })
    page.value = targetPage
    applyPage(data, !reset)
  } catch (e) {
    toast.error(e.message || '评论加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

watch(
  () => props.musicId,
  () => {
    floors.value = []
    commentTotal.value = 0
    floorTotal.value = 0
    replyTarget.value = null
    content.value = ''
    load(true)
  },
  { immediate: true }
)

function startReply(floor, reply = null) {
  if (!isLoggedIn.value) {
    openAuthDialog()
    return
  }
  replyTarget.value = {
    id: reply ? reply.id : floor.id,
    nickname: reply ? reply.user?.nickname : floor.user?.nickname,
    rootId: floor.id,
  }
  nextTick(() => textareaRef.value?.focus())
}

function cancelReply() {
  replyTarget.value = null
}

async function submit() {
  if (!isLoggedIn.value) {
    openAuthDialog()
    return
  }
  const text = content.value.trim()
  if (!text) return
  submitting.value = true
  try {
    await postComment({
      musicId: props.musicId,
      content: text,
      parentId: replyTarget.value?.id ?? null,
    })
    content.value = ''
    replyTarget.value = null
    await load(true)
    toast.success('发表成功')
  } catch (e) {
    toast.error(e.message || '发表失败')
  } finally {
    submitting.value = false
  }
}

async function remove(comment) {
  if (typeof window !== 'undefined' && !window.confirm('确定删除这条评论吗？')) return
  try {
    await deleteComment(comment.id)
    await load(true)
    toast.success('已删除')
  } catch (e) {
    toast.error(e.message || '删除失败')
  }
}
</script>

<template>
  <section class="cmt" aria-label="歌曲评论">
    <header class="cmt__head">
      <h2 class="cmt__title">
        评论
        <span class="cmt__count">{{ commentTotal }}</span>
      </h2>
    </header>

    <div class="cmt__composer">
      <template v-if="isLoggedIn">
        <div v-if="replyTarget" class="cmt__reply-chip">
          <NIcon name="corner-down-left" :size="14" />
          <span class="cmt__reply-name">回复 @{{ replyTarget.nickname || '该用户' }}</span>
          <button type="button" class="cmt__reply-cancel" aria-label="取消回复" @click="cancelReply">
            <NIcon name="close" :size="14" />
          </button>
        </div>
        <textarea
          ref="textareaRef"
          v-model="content"
          class="cmt__input"
          :maxlength="MAX_LENGTH"
          :placeholder="replyTarget ? `回复 @${replyTarget.nickname || ''}` : '说点什么吧…'"
          rows="3"
          @keydown.ctrl.enter="submit"
          @keydown.meta.enter="submit"
        />
        <div class="cmt__composer-foot">
          <span class="cmt__counter" :class="{ 'is-low': remaining < 30 }">{{ content.length }}/{{ MAX_LENGTH }}</span>
          <NButton size="sm" variant="primary" icon="send" :loading="submitting" :disabled="!canSubmit" @click="submit">
            {{ replyTarget ? '回复' : '发表' }}
          </NButton>
        </div>
      </template>
      <div v-else class="cmt__login">
        <p>登录后即可发表评论与回复</p>
        <NButton size="sm" variant="primary" icon="login" @click="openAuthDialog()">登录 / 注册</NButton>
      </div>
    </div>

    <div class="cmt__list">
      <p v-if="loading" class="cmt__state">评论加载中…</p>
      <p v-else-if="!floors.length" class="cmt__state">还没有评论，来抢沙发吧</p>

      <article v-for="floor in floors" :key="floor.id" class="cmt__floor">
        <div class="cmt__row">
          <img class="cmt__avatar" :src="userAvatar(floor.user?.id)" :alt="floor.user?.nickname" loading="lazy" />
          <div class="cmt__main">
            <div class="cmt__meta">
              <span class="cmt__nick">{{ floor.user?.nickname || '未知用户' }}</span>
              <time class="cmt__time" :title="floor.createdAt">{{ formatTime(floor.createdAt) }}</time>
              <span v-if="floor.ipRegion" class="cmt__ip">
                <NIcon name="map-pin" :size="12" />
                {{ floor.ipRegion }}
              </span>
            </div>
            <p class="cmt__text">{{ floor.content }}</p>
            <div class="cmt__actions">
              <button type="button" class="cmt__action" @click="startReply(floor)">
                <NIcon name="corner-down-left" :size="13" />
                回复
              </button>
              <button
                v-if="floor.canDelete"
                type="button"
                class="cmt__action cmt__action--danger"
                @click="remove(floor)"
              >
                <NIcon name="trash" :size="13" />
                删除
              </button>
            </div>
          </div>
        </div>

        <div v-if="floor.replies && floor.replies.length" class="cmt__replies">
          <div v-for="reply in floor.replies" :key="reply.id" class="cmt__reply">
            <img class="cmt__avatar cmt__avatar--sm" :src="userAvatar(reply.user?.id)" :alt="reply.user?.nickname" loading="lazy" />
            <div class="cmt__main">
              <div class="cmt__meta">
                <span class="cmt__nick">{{ reply.user?.nickname || '未知用户' }}</span>
                <span v-if="reply.replyToUser" class="cmt__at">
                  <NIcon name="corner-down-left" :size="12" />
                  {{ reply.replyToUser.nickname }}
                </span>
                <time class="cmt__time" :title="reply.createdAt">{{ formatTime(reply.createdAt) }}</time>
                <span v-if="reply.ipRegion" class="cmt__ip">
                  <NIcon name="map-pin" :size="12" />
                  {{ reply.ipRegion }}
                </span>
              </div>
              <p class="cmt__text">{{ reply.content }}</p>
              <div class="cmt__actions">
                <button type="button" class="cmt__action" @click="startReply(floor, reply)">
                  <NIcon name="corner-down-left" :size="13" />
                  回复
                </button>
                <button
                  v-if="reply.canDelete"
                  type="button"
                  class="cmt__action cmt__action--danger"
                  @click="remove(reply)"
                >
                  <NIcon name="trash" :size="13" />
                  删除
                </button>
              </div>
            </div>
          </div>
        </div>
      </article>

      <div v-if="hasMore" class="cmt__more">
        <NButton size="sm" variant="secondary" :loading="loadingMore" @click="load(false)">加载更多</NButton>
      </div>
      <p v-else-if="floors.length && floorTotal > 0" class="cmt__state cmt__state--end">已显示全部评论</p>
    </div>
  </section>
</template>

<style scoped>
.cmt {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3, 12px);
  min-height: 0;
  color: var(--n-text);
}

.cmt__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cmt__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.cmt__count {
  font-size: 12px;
  font-weight: 500;
  color: var(--n-text-muted);
}

.cmt__composer {
  background: var(--n-surface-soft, rgba(255, 255, 255, 0.05));
  border: 1px solid var(--n-line, rgba(255, 255, 255, 0.1));
  border-radius: var(--n-radius, 12px);
  padding: 10px;
}

.cmt__reply-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 3px 8px;
  border-radius: var(--n-radius-pill, 999px);
  background: var(--n-accent-soft, rgba(120, 160, 255, 0.16));
  color: var(--n-accent, #7aa2ff);
  font-size: 12px;
}

.cmt__reply-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cmt__reply-cancel {
  display: inline-flex;
  border: 0;
  background: none;
  padding: 0;
  color: inherit;
  cursor: pointer;
}

.cmt__input {
  width: 100%;
  resize: vertical;
  min-height: 64px;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--n-text);
  font: inherit;
  font-size: 13px;
  line-height: 1.6;
}

.cmt__composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.cmt__counter {
  font-size: 12px;
  color: var(--n-text-faint, rgba(255, 255, 255, 0.4));
  font-variant-numeric: tabular-nums;
}

.cmt__counter.is-low {
  color: var(--n-warning, #f5a623);
}

.cmt__login {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cmt__login p {
  margin: 0;
  font-size: 13px;
  color: var(--n-text-muted);
}

.cmt__list {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3, 12px);
  overflow-y: auto;
  min-height: 0;
  padding-right: 2px;
}

.cmt__state {
  margin: 12px 0;
  font-size: 13px;
  color: var(--n-text-muted);
  text-align: center;
}

.cmt__state--end {
  font-size: 12px;
  color: var(--n-text-faint, rgba(255, 255, 255, 0.35));
}

.cmt__floor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--n-line-subtle, rgba(255, 255, 255, 0.06));
}

.cmt__row,
.cmt__reply {
  display: flex;
  gap: 10px;
}

.cmt__avatar {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 50%;
  object-fit: cover;
  background: var(--n-surface-strong, rgba(255, 255, 255, 0.08));
}

.cmt__avatar--sm {
  width: 26px;
  height: 26px;
}

.cmt__main {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cmt__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
}

.cmt__nick {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text);
}

.cmt__at,
.cmt__ip,
.cmt__time {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--n-text-faint, rgba(255, 255, 255, 0.45));
}

.cmt__at {
  color: var(--n-accent, #7aa2ff);
}

.cmt__text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.cmt__actions {
  display: flex;
  gap: 12px;
}

.cmt__action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--n-text-muted);
  cursor: pointer;
  transition: color 0.15s ease;
}

.cmt__action:hover {
  color: var(--n-accent, #7aa2ff);
}

.cmt__action--danger:hover {
  color: var(--n-danger, #ff6b6b);
}

.cmt__replies {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-left: 46px;
  padding: 8px 10px;
  border-radius: var(--n-radius-sm, 8px);
  background: var(--n-surface-soft, rgba(255, 255, 255, 0.04));
}

.cmt__more {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}
</style>
