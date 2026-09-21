<template>
  <AmbientBackdrop />

  <PageShell width="default">
    <header class="head">
      <div>
        <h1 class="head__title">我的歌单</h1>
        <p class="head__sub">管理自建歌单，点击进入详情或编辑。</p>
      </div>
      <NButton variant="primary" icon="plus" @click="goToCreatePlaylist">创建歌单</NButton>
    </header>

    <div v-if="loading" class="state">
      <NSpinner :size="28" />
    </div>

    <div v-else-if="playlists.length > 0" class="grid">
      <NCard
        v-for="playlist in playlists"
        :key="playlist.id"
        hoverable
        pad="none"
        class="pl"
        tabindex="0"
        role="link"
        @click="goToPlaylistDetail(playlist.id)"
        @keydown.enter.prevent="goToPlaylistDetail(playlist.id)"
      >
        <div class="pl__cover">
          <img :src="getPlaylistCover(playlist)" :alt="playlist.name" loading="lazy" @error="handleCoverError($event)" />
        </div>
        <div class="pl__body">
          <h2 class="pl__title">{{ playlist.name }}</h2>
          <p class="pl__meta">
            <NIcon name="music" :size="13" />
            {{ playlist.musicCount }} 首
          </p>
          <p v-if="playlist.description" class="pl__desc">{{ playlist.description }}</p>
        </div>
        <div v-if="isPlaylistOwner(playlist.userId)" class="pl__actions" @click.stop>
          <NButton size="sm" variant="secondary" icon="pencil" @click="showEditDialog(playlist)">编辑</NButton>
          <NButton size="sm" variant="danger" icon="trash-2" @click="confirmDelete(playlist)">删除</NButton>
        </div>
      </NCard>
    </div>

    <NCard v-else pad="lg" class="empty">
      <span class="empty__icon"><NIcon name="list-music" :size="26" /></span>
      <h2 class="empty__title">暂无歌单</h2>
      <p class="empty__text">创建第一个歌单，把喜欢的曲目收在一起。</p>
      <NButton variant="primary" icon="plus" @click="goToCreatePlaylist">创建歌单</NButton>
    </NCard>

    <!-- 编辑歌单 -->
    <NModal v-model="showEdit" title="编辑歌单">
      <div class="field">
        <label class="field__label" for="edit-name">歌单名称</label>
        <NInput id="edit-name" v-model="editForm.name" maxlength="255" required />
      </div>
      <div class="field field--last">
        <label class="field__label" for="edit-desc">歌单描述</label>
        <NInput id="edit-desc" v-model="editForm.description" textarea :rows="4" maxlength="500" />
      </div>
      <template #footer>
        <NButton variant="ghost" @click="closeEditDialog">取消</NButton>
        <NButton variant="primary" @click="handleEditPlaylist">保存</NButton>
      </template>
    </NModal>

    <!-- 删除确认 -->
    <NModal v-model="showDeleteConfirm" title="确认删除" size="sm">
      <p class="del-text">确定要删除歌单「{{ playlistToDelete?.name }}」吗？</p>
      <p class="del-warn">此操作不可恢复，歌单内曲目会从歌单中移除。</p>
      <template #footer>
        <NButton variant="ghost" @click="closeDeleteConfirm">取消</NButton>
        <NButton variant="danger" @click="handleDeletePlaylist">删除</NButton>
      </template>
    </NModal>
  </PageShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { applyVipFromPlaylistsResponse } from '@/utils/userVip.js'
import { avatarUrl } from '@/utils/userAvatar.js'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const toast = useToast()
const router = useRouter()

// 响应式数据
const playlists = ref([])
const loading = ref(true)
const showEdit = ref(false)
const showDeleteConfirm = ref(false)
const currentPlaylist = ref(null)
const playlistToDelete = ref(null)
const editForm = ref({
  id: null,
  name: '',
  description: ''
})

// 获取当前用户信息
const getCurrentUser = () => {
  const userStr = localStorage.getItem('user')
  return userStr ? JSON.parse(userStr) : null
}

// 检查是否是歌单所有者
const isPlaylistOwner = (playlistUserId) => {
  const currentUser = getCurrentUser()
  return currentUser && currentUser.id === playlistUserId
}

// 获取Token
const getToken = () => {
  return localStorage.getItem('userToken')
}

// 获取歌单列表
const fetchPlaylists = async () => {
  loading.value = true
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlists`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success) {
      playlists.value = data.playlists || []
      applyVipFromPlaylistsResponse(data)

      // 为每个有音乐的歌单异步获取第一首音乐的封面
      playlists.value.forEach(playlist => {
        if (playlist.musicCount > 0) {
          fetchPlaylistFirstMusicCover(playlist.id)
        }
      })
      
    } else {
      toast.error(data.message || '获取歌单列表失败')
    }
  } catch (error) {
    console.error('获取歌单列表失败:', error)
    toast.error('获取歌单列表失败')
  } finally {
    loading.value = false
  }
}

// 跳转到创建歌单页面
const goToCreatePlaylist = () => {
  router.push('/playlist/create')
}

// 跳转到歌单详情页
const goToPlaylistDetail = (playlistId) => {
  router.push(`/playlist/${playlistId}`)
}

// 显示编辑对话框
const showEditDialog = (playlist) => {
  currentPlaylist.value = playlist
  editForm.value = {
    id: playlist.id,
    name: playlist.name,
    description: playlist.description || ''
  }
  showEdit.value = true
}

// 关闭编辑对话框
const closeEditDialog = () => {
  showEdit.value = false
  currentPlaylist.value = null
  editForm.value = { id: null, name: '', description: '' }
}

// 处理编辑歌单
const handleEditPlaylist = async () => {
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/update`, {
      method: 'POST',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: editForm.value.id,
        name: editForm.value.name,
        description: editForm.value.description
      })
    })
    
    const data = await response.json()
    if (data.success) {
      toast.success('歌单更新成功')
      closeEditDialog()
      await fetchPlaylists()
    } else {
      toast.error(data.message || '歌单更新失败')
    }
  } catch (error) {
    console.error('歌单更新失败:', error)
    toast.error('歌单更新失败')
  }
}

// 显示删除确认对话框
const confirmDelete = (playlist) => {
  playlistToDelete.value = playlist
  showDeleteConfirm.value = true
}

// 关闭删除确认对话框
const closeDeleteConfirm = () => {
  showDeleteConfirm.value = false
  playlistToDelete.value = null
}

// 处理删除歌单
const handleDeletePlaylist = async () => {
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/delete`, {
      method: 'POST',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: playlistToDelete.value.id
      })
    })
    
    const data = await response.json()
    if (data.success) {
      toast.success('歌单删除成功')
      closeDeleteConfirm()
      await fetchPlaylists()
    } else {
      toast.error(data.message || '歌单删除失败')
    }
  } catch (error) {
    console.error('歌单删除失败:', error)
    toast.error('歌单删除失败')
  }
}

// 获取歌单封面
const getPlaylistCover = (playlist) => {
  if (playlist.firstMusicId && playlist.firstMusicCover) {
    return `${API_CONFIG.BASE_URL}/api/music/cover/${playlist.firstMusicId}`
  }
  if (playlist.musicCount > 0) {
    fetchPlaylistFirstMusicCover(playlist.id)
  }
  const u = getCurrentUser()
  const userId = u ? u.id : 'default'
  return avatarUrl(userId)
}

// 异步获取歌单第一首音乐的封面
const fetchPlaylistFirstMusicCover = async (playlistId) => {
  try {
    const token = getToken()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/music/${playlistId}`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success && data.musicList && data.musicList.length > 0) {
      const firstMusic = data.musicList[0]
      const playlist = playlists.value.find(p => p.id === playlistId)
      if (playlist) {
        playlist.firstMusicId = firstMusic.id
        playlist.firstMusicCover = firstMusic.coverPath
      }
    }
  } catch (error) {
    console.error('获取歌单第一首音乐封面失败:', error)
  }
}

// 处理封面加载错误
const handleCoverError = (event) => {
  const u = getCurrentUser()
  const userId = u ? u.id : 'default'
  event.target.src = avatarUrl(userId)
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  
  return date.toLocaleDateString('zh-CN')
}

onMounted(() => {
  fetchPlaylists()
})
</script>

<style scoped>
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: var(--n-space-6);
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

/* ==================== 卡片网格 ==================== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--n-space-5);
}

.pl {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  outline-offset: 3px;
}

.pl__cover {
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: var(--n-surface-soft);
  border-bottom: 1px solid var(--n-line-subtle);
}

.pl__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--n-duration-slow) var(--n-ease);
}

@media (hover: hover) {
  .pl:hover .pl__cover img {
    transform: scale(1.05);
  }
}

.pl__body {
  flex: 1;
  padding: var(--n-space-4);
}

.pl__title {
  margin: 0 0 var(--n-space-1);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pl__meta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.pl__desc {
  margin: var(--n-space-2) 0 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pl__actions {
  display: flex;
  gap: var(--n-space-2);
  padding: var(--n-space-3) var(--n-space-4);
  border-top: 1px solid var(--n-line-subtle);
}

/* ==================== 空状态 ==================== */
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

/* ==================== 弹窗字段 ==================== */
.field {
  margin-bottom: var(--n-space-5);
}

.field--last {
  margin-bottom: 0;
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
}

.del-text {
  margin: 0 0 var(--n-space-2);
  color: var(--n-text);
  line-height: var(--n-leading-normal);
}

.del-warn {
  margin: 0;
  color: var(--n-warning);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}
</style>
