<template>
  <div class="admin-layout">
    <AdminSidebar />
    
    <div class="admin-main-content">
      <div class="admin-header">
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button @click="logout" class="logout-button">退出登录</button>
        </div>
      </div>
      
      <div class="admin-content-wrapper">
        <div class="admin-subpage">
          <h2>审核管理</h2>
          <p>审核用户上传的音乐，通过审核后将自动添加到音乐库中。</p>
          
          <div v-if="isLoading" class="loading">
            <p>正在加载待审核列表...</p>
          </div>
          
          <div v-else-if="pendingUploads.length === 0" class="no-data">
            <p>暂无待审核的音乐</p>
          </div>
          
          <div v-else class="audit-list">
            <div v-for="upload in pendingUploads" :key="upload.id" class="audit-item">
              <div class="audit-item-header">
                <div class="audit-item-title">
                  <span class="music-title">{{ upload.title }}</span>
                  <span class="music-artist">{{ upload.artist }}</span>
                </div>
                <div class="audit-item-info">
                  <span class="upload-time">{{ formatDateTime(upload.createdAt) }}</span>
                  <span class="upload-user">用户ID: {{ upload.userId }}</span>
                </div>
              </div>
              
              <div class="audit-item-details">
                <div class="detail-row">
                  <span class="detail-label">专辑:</span>
                  <span class="detail-value">{{ upload.album || '未知专辑' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">语言:</span>
                  <span class="detail-value">{{ upload.language }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">时长:</span>
                  <span class="detail-value">{{ formatDuration(upload.duration) }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">标签:</span>
                  <span class="detail-value">{{ upload.tags || '无' }}</span>
                </div>
              </div>
              
              <div class="audit-item-files">
                <div class="file-item">
                  <span class="file-icon">🎵</span>
                  <span class="file-name">{{ getFileName(upload.musicFilePath) }}</span>
                </div>
                <div v-if="upload.coverFilePath" class="file-item">
                  <span class="file-icon">🖼️</span>
                  <span class="file-name">{{ getFileName(upload.coverFilePath) }}</span>
                </div>
                <div class="file-item">
                  <span class="file-icon">📝</span>
                  <span class="file-name">{{ getFileName(upload.lyricsFilePath) }}</span>
                </div>
              </div>
              
              <div class="audit-item-actions">
                <div class="player-preview" v-if="currentPlayingId === upload.id">
                  <audio ref="audioPlayer" :src="getMusicPreviewUrl(upload.musicFilePath)" controls></audio>
                </div>
                
                <div class="action-buttons">
                  <button class="preview-btn" @click="playPreview(upload.id, upload.musicFilePath)" :disabled="currentPlayingId === upload.id">
                    {{ currentPlayingId === upload.id ? '播放中...' : '试听' }}
                  </button>
                  <button class="approve-btn" @click="approveUpload(upload.id)">通过</button>
                  <button class="reject-btn" @click="showRejectModal(upload.id)">拒绝</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 拒绝确认模态框 -->
    <Transition name="modal">
      <div v-if="showRejectConfirm" class="edit-modal-overlay" @click="closeRejectModal">
        <div class="edit-modal" @click.stop ref="rejectModalRef">
          <div class="modal-header">
            <h3>拒绝审核</h3>
            <button class="close-btn" @click="closeRejectModal">&times;</button>
          </div>
          <div class="modal-content">
            <div class="form-group">
              <label>拒绝原因</label>
              <textarea v-model="rejectReason" placeholder="请输入拒绝原因（可选）" rows="4"></textarea>
            </div>
          </div>
          <div class="form-actions modal-actions">
            <button class="secondary-btn" @click="closeRejectModal">取消</button>
            <button class="primary-btn reject-confirm-btn" @click="confirmReject">确认拒绝</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AdminSidebar from '@/components/AdminSidebar.vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()

const adminInfo = ref({ username: '' })
const pendingUploads = ref([])
const isLoading = ref(true)
const currentPlayingId = ref(null)
const audioPlayer = ref(null)
const showRejectConfirm = ref(false)
const rejectUploadId = ref(null)
const rejectReason = ref('')
const rejectModalRef = ref(null)

// 获取管理员信息
const getAdminInfo = () => {
  const adminData = localStorage.getItem('adminInfo')
  if (adminData) {
    adminInfo.value = JSON.parse(adminData)
  }
}

// 获取待审核列表
const fetchPendingUploads = async () => {
  isLoading.value = true
  try {
    const token = localStorage.getItem('adminToken')
    if (!token) {
      toast.error('请先登录管理员账号')
      router.push('/admin/login')
      return
    }
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/audit/pending`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    const result = await response.json()
    
    if (result.success) {
      pendingUploads.value = result.data || []
    } else {
      toast.error(result.message || '获取待审核列表失败')
    }
  } catch (error) {
    console.error('获取待审核列表失败:', error)
    toast.error('获取待审核列表失败')
  } finally {
    isLoading.value = false
  }
}

// 审核通过
const approveUpload = async (uploadId) => {
  try {
    const token = localStorage.getItem('adminToken')
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/audit/approve/${uploadId}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    const result = await response.json()
    
    if (result.success) {
      toast.success('审核通过，音乐已添加到库中')
      // 从列表中移除已审核的项目
      pendingUploads.value = pendingUploads.value.filter(u => u.id !== uploadId)
      // 停止播放
      if (currentPlayingId.value === uploadId) {
        stopPreview()
      }
    } else {
      toast.error(result.message || '审核通过失败')
    }
  } catch (error) {
    console.error('审核通过失败:', error)
    toast.error('审核通过失败')
  }
}

// 显示拒绝模态框
const showRejectModal = (uploadId) => {
  rejectUploadId.value = uploadId
  rejectReason.value = ''
  showRejectConfirm.value = true
}

// 关闭拒绝模态框
const closeRejectModal = () => {
  showRejectConfirm.value = false
  rejectUploadId.value = null
  rejectReason.value = ''
}

// 确认拒绝
const confirmReject = async () => {
  if (!rejectUploadId.value) return
  
  try {
    const token = localStorage.getItem('adminToken')
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/admin/audit/reject/${rejectUploadId.value}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        reason: rejectReason.value || '管理员拒绝审核'
      })
    })
    
    const result = await response.json()
    
    if (result.success) {
      toast.success('审核拒绝成功')
      // 从列表中移除已审核的项目
      pendingUploads.value = pendingUploads.value.filter(u => u.id !== rejectUploadId.value)
      closeRejectModal()
      // 停止播放
      if (currentPlayingId.value === rejectUploadId.value) {
        stopPreview()
      }
    } else {
      toast.error(result.message || '审核拒绝失败')
    }
  } catch (error) {
    console.error('审核拒绝失败:', error)
    toast.error('审核拒绝失败')
  }
}

// 试听音乐
const playPreview = (uploadId, musicFilePath) => {
  if (currentPlayingId.value === uploadId) {
    return
  }
  
  currentPlayingId.value = uploadId
  
  // 等待DOM更新后播放
  setTimeout(() => {
    if (audioPlayer.value) {
      audioPlayer.value.play().catch(error => {
        console.error('播放失败:', error)
        toast.error('播放失败')
        currentPlayingId.value = null
      })
    }
  }, 100)
}

// 停止播放
const stopPreview = () => {
  if (audioPlayer.value) {
    audioPlayer.value.pause()
    audioPlayer.value.currentTime = 0
  }
  currentPlayingId.value = null
}

// 获取音乐预览URL
const getMusicPreviewUrl = (filePath) => {
  // 对于审核目录的文件，需要特殊处理
  // 这里假设后端有一个专门的服务来提供审核文件的访问
  // 如果后端没有，可以暂时返回空，需要添加后端支持
  return `${API_CONFIG.BASE_URL}/api/user/upload/preview?path=${encodeURIComponent(filePath)}`
}

// 获取文件名
const getFileName = (filePath) => {
  if (!filePath) return ''
  const parts = filePath.split(/[/\\]/)
  return parts[parts.length - 1]
}

// 格式化日期时间
const formatDateTime = (dateTime) => {
  if (!dateTime) return ''
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化时长
const formatDuration = (seconds) => {
  if (!seconds) return '0:00'
  const minutes = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${minutes}:${secs.toString().padStart(2, '0')}`
}

// 退出登录
const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('adminInfo')
  router.push('/admin/login')
}

onMounted(() => {
  getAdminInfo()
  fetchPendingUploads()
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px;
  display: flex;
  flex-direction: column;
}

.admin-header {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  padding: 15px 30px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.admin-user-info {
  display: flex;
  align-items: center;
  gap: 15px;
  color: #fff;
}

.logout-button {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #fff;
  padding: 8px 16px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.logout-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.admin-content-wrapper {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
}

.admin-subpage {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 15px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}

.admin-subpage h2 {
  margin: 0 0 10px;
  color: #333;
  font-size: 1.8rem;
}

.admin-subpage p {
  margin: 0 0 30px;
  color: #666;
}

.loading, .no-data {
  text-align: center;
  padding: 50px;
  color: #999;
}

.audit-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.audit-item {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  padding: 20px;
  transition: all 0.3s ease;
}

.audit-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.audit-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.audit-item-title {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.music-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: #333;
}

.music-artist {
  font-size: 0.95rem;
  color: #666;
}

.audit-item-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: flex-end;
}

.upload-time, .upload-user {
  font-size: 0.85rem;
  color: #999;
}

.audit-item-details {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 15px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-label {
  font-weight: 500;
  color: #666;
  min-width: 50px;
}

.detail-value {
  color: #333;
}

.audit-item-files {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 15px;
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.9rem;
  color: #666;
}

.file-icon {
  font-size: 1.1rem;
}

.file-name {
  word-break: break-all;
}

.audit-item-actions {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.player-preview {
  background: #f0f0f0;
  padding: 10px;
  border-radius: 8px;
}

.player-preview audio {
  width: 100%;
  height: 32px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.preview-btn {
  background: #6a5acd;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 0.9rem;
}

.preview-btn:hover:not(:disabled) {
  background: #5848c2;
}

.preview-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.approve-btn {
  background: #52c41a;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 0.9rem;
}

.approve-btn:hover {
  background: #3cb814;
}

.reject-btn {
  background: #f5222d;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 0.9rem;
}

.reject-btn:hover {
  background: #d91a24;
}

/* 模态框样式 */
.edit-modal-overlay {
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

.edit-modal {
  background: #fff;
  border-radius: 10px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #e0e0e0;
}

.modal-header h3 {
  margin: 0;
  color: #333;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
  transition: color 0.3s ease;
}

.close-btn:hover {
  color: #333;
}

.modal-content {
  padding: 20px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #333;
  font-weight: 500;
}

.form-group textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-family: inherit;
  resize: vertical;
  min-height: 100px;
}

.form-group textarea:focus {
  outline: none;
  border-color: #6a5acd;
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.secondary-btn {
  background: #fff;
  color: #333;
  border: 1px solid #ddd;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.secondary-btn:hover {
  background: #f5f5f5;
}

.primary-btn {
  background: #6a5acd;
  color: #fff;
  border: none;
  padding: 10px 20px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.primary-btn:hover {
  background: #5848c2;
}

.reject-confirm-btn {
  background: #f5222d;
}

.reject-confirm-btn:hover {
  background: #d91a24;
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-main-content {
    margin-left: 0;
  }
  
  .admin-header {
    padding: 15px;
  }
  
  .admin-content-wrapper {
    padding: 15px;
  }
  
  .admin-subpage {
    padding: 20px;
  }
  
  .audit-item-header {
    flex-direction: column;
    gap: 10px;
  }
  
  .audit-item-info {
    align-items: flex-start;
  }
  
  .audit-item-details {
    flex-direction: column;
    gap: 10px;
  }
}
</style>