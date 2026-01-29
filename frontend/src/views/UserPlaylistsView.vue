<template>
  <div class="playlists-view">
    <div class="playlists-container">
      <div class="playlists-header">
        <h2>所有歌单</h2>
        <button @click="goToCreatePlaylist" class="create-btn">
          ➕ 创建歌单
        </button>
      </div>
      
      <div v-if="loading" class="loading">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="playlists.length > 0" class="playlists-list">
        <div 
          v-for="playlist in playlists" 
          :key="playlist.id" 
          class="playlist-item"
          @click="goToPlaylistDetail(playlist.id)"
        >
          <div class="playlist-cover">
            <img 
              :src="getPlaylistCover(playlist)" 
              alt="歌单封面"
              @error="handleCoverError($event)"
            />
          </div>
          <div class="playlist-info">
            <div class="playlist-title">{{ playlist.name }}</div>
            <div class="playlist-meta">
              <span class="playlist-count">{{ playlist.musicCount }} 首歌曲</span>
<!--              <span class="playlist-time">{{ formatTime(playlist.updatedAt) }}</span>-->
            </div>
            <div v-if="playlist.description" class="playlist-description">
              {{ playlist.description }}
            </div>
          </div>
          <div class="playlist-actions">
            <button 
              v-if="isPlaylistOwner(playlist.userId)" 
              @click.stop="showEditDialog(playlist)"
              class="edit-btn"
              title="编辑歌单"
            >
              ✏️
            </button>
            <button 
              v-if="isPlaylistOwner(playlist.userId)" 
              @click.stop="confirmDelete(playlist)"
              class="delete-btn"
              title="删除歌单"
            >
              🗑️
            </button>
          </div>
        </div>
      </div>
      
      <div v-else class="empty-state">
        <div class="empty-icon">📋</div>
        <p>暂无歌单</p>
        <button @click="goToCreatePlaylist" class="create-first-btn">
          创建第一个歌单
        </button>
      </div>
    </div>
    
    <!-- 编辑歌单对话框 -->
    <div v-if="showEdit" class="modal-overlay" @click="closeEditDialog">
      <div class="modal-content" @click.stop>
        <h3>编辑歌单</h3>
        <form @submit.prevent="handleEditPlaylist">
          <div class="form-group">
            <label>歌单名称</label>
            <input 
              v-model="editForm.name" 
              type="text" 
              required 
              maxlength="255"
              placeholder="请输入歌单名称"
            />
          </div>
          <div class="form-group">
            <label>歌单描述</label>
            <textarea 
              v-model="editForm.description" 
              maxlength="500"
              rows="4"
              placeholder="请输入歌单描述（可选）"
            ></textarea>
          </div>
          <div class="form-actions">
            <button type="button" @click="closeEditDialog" class="cancel-btn">取消</button>
            <button type="submit" class="submit-btn">保存</button>
          </div>
        </form>
      </div>
    </div>
    
    <!-- 删除确认对话框 -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click="closeDeleteConfirm">
      <div class="modal-content" @click.stop>
        <h3>确认删除</h3>
        <p>确定要删除歌单"{{ playlistToDelete?.name }}"吗？</p>
        <p class="warning">此操作不可恢复，歌单中的所有音乐也会被移除。</p>
        <div class="form-actions">
          <button @click="closeDeleteConfirm" class="cancel-btn">取消</button>
          <button @click="handleDeletePlaylist" class="delete-confirm-btn">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
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
  // 如果歌单有第一首音乐的封面信息（来自后端返回）
  if (playlist.firstMusicId && playlist.firstMusicCover) {
    return `${API_CONFIG.BASE_URL}/api/music/cover/${playlist.firstMusicId}`
  }
  // 如果歌单有音乐数量，尝试获取歌单详情来获取第一首音乐
  if (playlist.musicCount > 0) {
    // 异步获取，这里暂时返回默认头像
    // 可以在 fetchPlaylists 中为每个歌单添加第一首音乐的封面信息
    fetchPlaylistFirstMusicCover(playlist.id)
  }
  // 如果歌单没有音乐，使用默认用户头像
  return `${API_CONFIG.BASE_URL}/api/user/avatar/default`
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
  event.target.src = `${API_CONFIG.BASE_URL}/api/user/avatar/default`
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
.playlists-view {
  min-height: calc(100vh - 80px);
  padding: 40px 20px;
}

.playlists-container {
  max-width: 1200px;
  margin: 0 auto;
}

.playlists-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding: 0 10px;
}

.playlists-header h2 {
  color: #333;
  font-size: 2.5em;
  margin: 0;
  font-weight: 600;
}

.create-btn {
  background: #667eea;
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 25px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
}

.create-btn:hover {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 5px 20px rgba(102, 126, 234, 0.3);
}

.loading {
  text-align: center;
  color: #666;
  padding: 60px 0;
}

.loading-spinner {
  width: 50px;
  height: 50px;
  border: 4px solid #e5e7eb;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.playlists-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.playlist-item {
  background: white;
  border-radius: 20px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #e5e7eb;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.playlist-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  border-color: #667eea;
}

.playlist-cover {
  width: 80px;
  height: 80px;
  border-radius: 15px;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.playlist-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.playlist-info {
  flex: 1;
  min-width: 0;
}

.playlist-title {
  color: #333;
  font-size: 1.2em;
  font-weight: 600;
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playlist-meta {
  color: #666;
  font-size: 0.9em;
  display: flex;
  gap: 15px;
  margin-bottom: 8px;
}

.playlist-description {
  color: #888;
  font-size: 0.9em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playlist-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.edit-btn,
.delete-btn {
  background: #f3f4f6;
  color: #666;
  border: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2em;
  transition: all 0.3s ease;
}

.edit-btn:hover {
  background: #667eea;
  color: white;
  transform: scale(1.1);
}

.delete-btn:hover {
  background: #ef4444;
  color: white;
  transform: scale(1.1);
}

.empty-state {
  text-align: center;
  color: #666;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 80px;
  margin-bottom: 20px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 1.3em;
  margin-bottom: 30px;
  color: #888;
}

.create-first-btn {
  background: #667eea;
  color: white;
  border: none;
  padding: 15px 40px;
  border-radius: 25px;
  font-size: 1.1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.create-first-btn:hover {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(102, 126, 234, 0.3);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 20px;
  padding: 40px;
  max-width: 500px;
  width: 90%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.modal-content h3 {
  margin: 0 0 30px 0;
  color: #333;
  font-size: 1.8em;
}

.modal-content p {
  color: #666;
  margin-bottom: 20px;
  line-height: 1.6;
}

.modal-content .warning {
  color: #f59e0b;
  font-weight: 500;
  margin-bottom: 30px;
}

.form-group {
  margin-bottom: 25px;
}

.form-group label {
  display: block;
  color: #333;
  font-weight: 600;
  margin-bottom: 10px;
  font-size: 1em;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  font-size: 1em;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-actions {
  display: flex;
  gap: 15px;
  justify-content: flex-end;
  margin-top: 30px;
}

.cancel-btn {
  background: #f3f4f6;
  color: #374151;
  border: none;
  padding: 12px 30px;
  border-radius: 10px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.cancel-btn:hover {
  background: #e5e7eb;
}

.submit-btn,
.delete-confirm-btn {
  background: #667eea;
  color: white;
  border: none;
  padding: 12px 30px;
  border-radius: 10px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.submit-btn:hover,
.delete-confirm-btn:hover {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
}

.delete-confirm-btn {
  background: #ef4444;
}

.delete-confirm-btn:hover {
  background: #dc2626;
  box-shadow: 0 5px 15px rgba(239, 68, 68, 0.3);
}

@media (max-width: 768px) {
  .playlists-header h2 {
    font-size: 2em;
  }
  
  .playlists-list {
    grid-template-columns: 1fr;
  }
  
  .playlist-item {
    padding: 15px;
  }
  
  .playlist-cover {
    width: 60px;
    height: 60px;
  }
  
  .playlist-icon {
    font-size: 30px;
  }
}
</style>