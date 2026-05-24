<template>
  <div class="pl-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <main class="shell">
      <header class="page-head">
        <div class="page-head-row">
          <div>
            <h1 class="page-title">我的歌单</h1>
            <p class="page-lede">管理自建歌单，点击进入详情或编辑。</p>
          </div>
          <button type="button" class="btn-primary" @click="goToCreatePlaylist">创建歌单</button>
        </div>
      </header>

      <section v-if="loading" class="panel state-panel">
        <div class="state state--loading">
          <div class="state__spinner" aria-hidden="true" />
          <p class="state__text">加载中…</p>
        </div>
      </section>

      <div v-else-if="playlists.length > 0" class="pl-grid">
        <article
          v-for="playlist in playlists"
          :key="playlist.id"
          class="pl-card panel"
          tabindex="0"
          role="link"
          @click="goToPlaylistDetail(playlist.id)"
          @keydown.enter.prevent="goToPlaylistDetail(playlist.id)"
        >
          <div class="pl-card-cover">
            <img :src="getPlaylistCover(playlist)" alt="" @error="handleCoverError($event)" />
          </div>
          <div class="pl-card-body">
            <h2 class="pl-card-title">{{ playlist.name }}</h2>
            <p class="pl-card-meta">{{ playlist.musicCount }} 首</p>
            <p v-if="playlist.description" class="pl-card-desc">{{ playlist.description }}</p>
          </div>
          <div v-if="isPlaylistOwner(playlist.userId)" class="pl-card-actions" @click.stop>
            <button type="button" class="btn-chip" title="编辑歌单" @click="showEditDialog(playlist)">编辑</button>
            <button type="button" class="btn-chip btn-chip--danger" title="删除歌单" @click="confirmDelete(playlist)">
              删除
            </button>
          </div>
        </article>
      </div>

      <section v-else class="panel state-panel state--empty">
        <h2 class="state__title">暂无歌单</h2>
        <p class="state__text">创建第一个歌单，把喜欢的曲目收在一起。</p>
        <button type="button" class="btn-primary" @click="goToCreatePlaylist">创建歌单</button>
      </section>
    </main>

    <div v-if="showEdit" class="modal-overlay" @click.self="closeEditDialog">
      <div class="modal panel" role="dialog" aria-labelledby="edit-title" @click.stop>
        <button type="button" class="modal-close" aria-label="关闭" @click="closeEditDialog">×</button>
        <h3 id="edit-title" class="modal-title">编辑歌单</h3>
        <form @submit.prevent="handleEditPlaylist">
          <div class="form-group">
            <label for="edit-name">歌单名称</label>
            <input id="edit-name" v-model="editForm.name" type="text" required maxlength="255" />
          </div>
          <div class="form-group">
            <label for="edit-desc">歌单描述</label>
            <textarea id="edit-desc" v-model="editForm.description" maxlength="500" rows="4" />
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-ghost" @click="closeEditDialog">取消</button>
            <button type="submit" class="btn-primary">保存</button>
          </div>
        </form>
      </div>
    </div>

    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="closeDeleteConfirm">
      <div class="modal panel" role="dialog" aria-labelledby="del-title" @click.stop>
        <button type="button" class="modal-close" aria-label="关闭" @click="closeDeleteConfirm">×</button>
        <h3 id="del-title" class="modal-title">确认删除</h3>
        <p class="modal-text">确定要删除歌单「{{ playlistToDelete?.name }}」吗？</p>
        <p class="modal-warn">此操作不可恢复，歌单内曲目会从歌单中移除。</p>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" @click="closeDeleteConfirm">取消</button>
          <button type="button" class="btn-primary btn-primary--danger" @click="handleDeletePlaylist">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { applyVipFromPlaylistsResponse } from '@/utils/userVip.js'
import { useToast } from 'vue-toastification'

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
      
      console.log('歌单列表数据:', playlists.value)
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
  return `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`
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
  event.target.src = `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`
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
.pl-page {
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --line: rgba(255, 255, 255, 0.1);
  --accent2: #22d3ee;
  --radius: 16px;
  --radius-lg: 22px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  --shadow: 0 24px 80px rgba(0, 0, 0, 0.45);

  position: relative;
  min-height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  color: var(--text);
  background: transparent;
}

.ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.ambient__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.48;
}

.ambient__blob--a {
  width: 400px;
  height: 400px;
  background: rgba(139, 92, 246, 0.38);
  top: -120px;
  left: -80px;
}

.ambient__blob--b {
  width: 320px;
  height: 320px;
  background: rgba(34, 211, 238, 0.2);
  bottom: -40px;
  right: -60px;
}

.ambient__blob--c {
  width: 260px;
  height: 260px;
  background: rgba(52, 211, 153, 0.14);
  top: 40%;
  right: 20%;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.28;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 55% at 50% 15%, black, transparent);
}

.shell {
  position: relative;
  z-index: 1;
  width: min(1100px, 100%);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 28px) clamp(14px, 3.5vw, 24px) 48px;
}

.page-head {
  margin-bottom: 22px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--line);
}

.page-head-row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: clamp(1.45rem, 3vw, 1.85rem);
  font-weight: 800;
  letter-spacing: -0.03em;
}

.page-lede {
  margin: 0;
  font-size: 0.9rem;
  color: var(--muted);
  line-height: 1.45;
  max-width: 40ch;
}

.btn-primary {
  font-family: inherit;
  border: none;
  cursor: pointer;
  padding: 11px 20px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.88rem;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 28px rgba(139, 92, 246, 0.35);
  white-space: nowrap;
}

.btn-primary:hover {
  filter: brightness(1.06);
}

.btn-primary--danger {
  background: linear-gradient(135deg, #fb7185, #f43f5e);
  box-shadow: 0 8px 28px rgba(244, 63, 94, 0.3);
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.state-panel {
  padding: 36px 24px;
}

.state {
  text-align: center;
}

.state--loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.state__spinner {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--accent2);
  animation: spin 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state__text {
  margin: 0;
  color: var(--muted);
  font-size: 0.92rem;
}

.state--empty .state__title {
  margin: 0 0 10px;
  font-size: 1.2rem;
  font-weight: 800;
}

.state--empty .state__text {
  margin: 0 0 22px;
  line-height: 1.55;
}

.pl-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.pl-card {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s var(--ease), border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.pl-card:focus-visible {
  outline: 2px solid var(--accent2);
  outline-offset: 3px;
}

@media (hover: hover) {
  .pl-card:hover {
    transform: translateY(-3px);
    border-color: rgba(139, 92, 246, 0.4);
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4);
  }
}

.pl-card-cover {
  aspect-ratio: 16 / 10;
  background: rgba(0, 0, 0, 0.25);
}

.pl-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.pl-card-body {
  padding: 14px 16px 10px;
  flex: 1;
  min-height: 0;
}

.pl-card-title {
  margin: 0 0 6px;
  font-size: 1.02rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.25;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pl-card-meta {
  margin: 0;
  font-size: 0.8rem;
  color: var(--accent2);
  font-weight: 600;
}

.pl-card-desc {
  margin: 8px 0 0;
  font-size: 0.78rem;
  color: var(--faint);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pl-card-actions {
  display: flex;
  gap: 8px;
  padding: 0 12px 12px;
  flex-wrap: wrap;
}

.btn-chip {
  font-family: inherit;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.9);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-chip:hover {
  background: rgba(255, 255, 255, 0.12);
}

.btn-chip--danger {
  border-color: rgba(251, 113, 133, 0.4);
  color: #fecdd3;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(6, 5, 12, 0.72);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal {
  position: relative;
  width: min(440px, 100%);
  padding: 26px 22px 22px;
  color: var(--text);
}

.modal-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: none;
  background: none;
  color: var(--faint);
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
}

.modal-title {
  margin: 0 0 18px;
  font-size: 1.15rem;
  font-weight: 800;
}

.modal-text,
.modal-warn {
  margin: 0 0 12px;
  font-size: 0.88rem;
  color: var(--muted);
  line-height: 1.5;
}

.modal-warn {
  color: #fcd34d;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 0.82rem;
  font-weight: 600;
  margin-bottom: 6px;
  color: rgba(255, 255, 255, 0.78);
}

.form-group input,
.form-group textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  color: var(--text);
  font-family: inherit;
  font-size: 0.92rem;
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: rgba(34, 211, 238, 0.45);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.btn-ghost {
  font-family: inherit;
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.88);
  font-weight: 600;
  font-size: 0.86rem;
  cursor: pointer;
}
</style>
