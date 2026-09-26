<script setup>
/**
 * UserFavoritesView —— 我的收藏
 * ------------------------------------------------------------
 * 契约（保持与旧实现一致，勿改）：
 *  - GET /api/user/favorites（Authorization: 裸 userToken）
 *  - DELETE /api/user/favorites/{id}
 *  - 播放：经 usePlaybackBridge.playTrack / playTracks 统一驱动 GlobalPlayer
 */
import { ref, onMounted, watch } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import { useToast } from '@/composables/useToast'
import { openAuthDialog } from '@/composables/useAuthDialog'
import { useAuth } from '@/composables/useAuth'
import { playTracks, playTrackInList } from '@/composables/usePlaybackBridge'

const toast = useToast()
const { token: authToken } = useAuth()

const favorites = ref([])
const loading = ref(true)
const confirmOpen = ref(false)
const pendingRemove = ref(null)

const getToken = () => localStorage.getItem('userToken')

async function fetchFavorites() {
  if (!getToken()) {
    loading.value = false
    openAuthDialog('login')
    return
  }
  loading.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: { Authorization: getToken() },
    })
    const data = await response.json()
    if (data.success) {
      favorites.value = data.favorites || []
    } else {
      console.error('获取收藏列表失败:', data.message)
      if (response.status === 401) {
        localStorage.removeItem('userToken')
        localStorage.removeItem('userInfo')
        openAuthDialog('login')
      }
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error)
  } finally {
    loading.value = false
  }
}

/** 播放单曲：以收藏列表为播放队列，保证「下一首」能接着播放 */
function playMusic(music) {
  playTrackInList(music, favorites.value)
}

function playAllFavorites() {
  if (!favorites.value.length) {
    toast.warning('收藏列表为空')
    return
  }
  playTracks(favorites.value, 0)
  toast.success(`已开始播放全部 ${favorites.value.length} 首收藏音乐`)
}

function askRemove(music) {
  pendingRemove.value = music
  confirmOpen.value = true
}

async function confirmRemove() {
  const music = pendingRemove.value
  confirmOpen.value = false
  pendingRemove.value = null
  if (music) await removeFavorite(music.id)
}

async function removeFavorite(musicId) {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${musicId}`, {
      method: 'DELETE',
      headers: { Authorization: getToken() },
    })
    const data = await response.json()
    if (data.success) {
      favorites.value = favorites.value.filter((m) => m.id !== musicId)
      toast.success('取消收藏成功')
    } else {
      console.error('取消收藏失败:', data.message)
      toast.error('取消收藏失败: ' + data.message)
    }
  } catch (error) {
    console.error('取消收藏失败:', error)
    toast.error('取消收藏失败')
  }
}

const getCoverUrl = (musicId) => `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`

function handleImageError(event) {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/0`
}

onMounted(fetchFavorites)

// 在弹窗里登录成功后自动加载收藏
watch(authToken, (token) => {
  if (token) fetchFavorites()
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <!-- 页头 -->
    <header class="head">
      <div class="head__main">
        <h1 class="head__title">我的收藏</h1>
        <p class="head__sub">
          <template v-if="favorites.length">共 {{ favorites.length }} 首</template>
          <template v-else>收藏喜欢的音乐，随时回来听</template>
        </p>
      </div>
      <NButton
        v-if="favorites.length"
        variant="primary"
        icon="play"
        @click="playAllFavorites"
      >
        播放全部
      </NButton>
    </header>

    <!-- 加载 -->
    <div v-if="loading" class="state">
      <NSpinner :size="28" />
    </div>

    <!-- 空状态 -->
    <NCard v-else-if="!favorites.length" pad="lg" class="empty">
      <span class="empty__icon"><NIcon name="heart" :size="28" /></span>
      <h2 class="empty__title">还没有收藏任何音乐</h2>
      <p class="empty__text">在播放页点击收藏，之后就能在这里快速找到。</p>
      <NButton variant="primary" icon="home" to="/">去发现音乐</NButton>
    </NCard>

    <!-- 列表 -->
    <div v-else class="list">
      <article v-for="music in favorites" :key="music.id" class="row">
        <img
          :src="getCoverUrl(music.id)"
          :alt="music.title"
          class="row__cover"
          loading="lazy"
          decoding="async"
          @error="handleImageError"
        />
        <button type="button" class="row__info" @click="playMusic(music)">
          <span class="row__title">{{ music.title }}</span>
          <span class="row__artist">作曲：{{ music.artist }}</span>
          <span class="row__album">专辑：{{ music.album || '未知专辑' }}</span>
        </button>
        <div class="row__actions">
          <NButton size="sm" variant="secondary" icon="play" @click="playMusic(music)">播放</NButton>
          <NButton size="sm" variant="danger" icon="heart-off" @click="askRemove(music)">移除</NButton>
        </div>
      </article>
    </div>

    <NModal v-model="confirmOpen" title="取消收藏" size="sm">
      <p class="confirm-text">
        确定要取消收藏「{{ pendingRemove?.title }}」吗？
      </p>
      <template #footer>
        <NButton variant="ghost" @click="confirmOpen = false">取消</NButton>
        <NButton variant="danger" @click="confirmRemove">确定移除</NButton>
      </template>
    </NModal>
  </PageShell>
</template>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
}

.head__main {
  min-width: 0;
}

.head__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
}

.head__sub {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.state {
  display: flex;
  justify-content: center;
  padding: var(--n-space-16) 0;
}

/* ===== 空状态 ===== */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--n-space-3);
  padding: var(--n-space-12) var(--n-space-6);
}

.empty__icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.empty__title {
  margin: 0;
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.empty__text {
  margin: 0 0 var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

/* ===== 列表 ===== */
.list {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
  transition: border-color var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .row:hover {
    border-color: var(--n-line-strong);
    background: var(--n-surface-hover);
  }
}

.row__cover {
  width: 52px;
  height: 52px;
  object-fit: cover;
  border-radius: var(--n-radius-sm);
  border: 1px solid var(--n-line-subtle);
  flex: none;
}

.row__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  text-align: left;
}

.row__title {
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
  font-size: var(--n-text-base);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__artist {
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__album {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row__actions {
  display: flex;
  gap: var(--n-space-2);
  flex: none;
}

.confirm-text {
  margin: 0;
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

@media (max-width: 560px) {
  .row {
    flex-wrap: wrap;
  }

  .row__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
