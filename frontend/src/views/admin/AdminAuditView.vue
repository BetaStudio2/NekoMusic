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
        <div class="admin-header-section">
          <h2 class="admin-title">审核管理</h2>
          <p class="admin-subtitle">审核用户上传的音乐，通过审核后将自动添加到音乐库中</p>
        </div>
        
        <div v-if="isLoading" class="content-placeholder">
          <p>正在加载待审核列表...</p>
        </div>
        
        <div v-else-if="pendingUploads.length === 0" class="content-placeholder">
          <p>暂无待审核的音乐</p>
        </div>
        
        <div v-else class="audit-list">
          <div v-for="upload in pendingUploads" :key="upload.id" class="audit-card">
            <div class="audit-card-header">
              <div class="audit-cover-section">
                <img 
                  v-if="upload.coverFilePath && coverUrls[upload.id]" 
                  :src="coverUrls[upload.id]" 
                  class="audit-cover-image"
                  @error="handleCoverError"
                  alt="专辑封面"
                />
                <div v-else class="audit-cover-placeholder">
                  <span v-if="loadingCovers[upload.id]">加载中...</span>
                  <span v-else>🎵</span>
                </div>
              </div>
              <div class="audit-title-section">
                <h3 class="audit-music-title">{{ upload.title }}</h3>
                <p class="audit-music-artist">{{ upload.artist }}</p>
              </div>
              <div class="audit-meta-section">
                <span class="audit-time">{{ formatDateTime(upload.createdAt) }}</span>
                <span class="audit-user">用户ID: {{ upload.userId }}</span>
              </div>
            </div>
            
            <div class="audit-card-body">
              <div class="audit-details-grid">
                <div class="audit-detail-item">
                  <span class="detail-icon">💿</span>
                  <span class="detail-label">专辑:</span>
                  <span class="detail-value">{{ upload.album || '未知专辑' }}</span>
                </div>
                <div class="audit-detail-item">
                  <span class="detail-icon">🌐</span>
                  <span class="detail-label">语言:</span>
                  <span class="detail-value">{{ upload.language }}</span>
                </div>
                <div class="audit-detail-item">
                  <span class="detail-icon">⏱️</span>
                  <span class="detail-label">时长:</span>
                  <span class="detail-value">{{ formatDuration(upload.duration) }}</span>
                </div>
                <div class="audit-detail-item">
                  <span class="detail-icon">🏷️</span>
                  <span class="detail-label">标签:</span>
                  <span class="detail-value">{{ upload.tags || '无' }}</span>
                </div>
              </div>
              
<!--              <div class="audit-files-section">-->
<!--                <div class="file-item">-->
<!--                  <span class="file-icon">🎵</span>-->
<!--                  <span class="file-name">{{ getFileName(upload.musicFilePath) }}</span>-->
<!--                </div>-->
<!--                <div v-if="upload.coverFilePath" class="file-item">-->
<!--                  <span class="file-icon">🖼️</span>-->
<!--                  <span class="file-name">{{ getFileName(upload.coverFilePath) }}</span>-->
<!--                </div>-->
<!--                <div class="file-item">-->
<!--                  <span class="file-icon">📝</span>-->
<!--                  <span class="file-name">{{ getFileName(upload.lyricsFilePath) }}</span>-->
<!--                </div>-->
<!--              </div>-->
              
              <!-- 歌词预览区域 -->
              <div class="lyrics-preview-section">
                <div class="lyrics-preview-header">
                  <span class="lyrics-preview-title">歌词预览</span>
                  <button 
                    class="toggle-lyrics-btn" 
                    @click="toggleLyricsPreview(upload.id)"
                    :title="showLyricsPreviewId === upload.id ? '隐藏歌词' : '显示歌词'"
                  >
                    {{ showLyricsPreviewId === upload.id ? '隐藏' : '显示' }}
                  </button>
                </div>
                <div v-if="showLyricsPreviewId === upload.id" class="lyrics-preview-content">
                  <div v-if="uploadLyrics[upload.id]" class="lyrics-text">
                    <div 
                      v-for="(line, index) in uploadLyrics[upload.id]" 
                      :key="index"
                      class="lyric-line"
                    >
                      <div class="lyric-text">{{ line.text }}</div>
                      <div class="lyric-translation" v-if="line.translation">{{ line.translation }}</div>
                    </div>
                  </div>
                  <div v-else class="lyrics-loading">
                    <span v-if="loadingLyrics[upload.id]">加载歌词中...</span>
                    <span v-else>无歌词</span>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="audit-card-footer">
              <div class="player-section" v-if="currentPlayingId === upload.id">
                <audio 
                  ref="audioPlayer" 
                  :src="currentAudioUrl" 
                  controls
                  @ended="handleAudioEnded"
                  @error="handleAudioError"
                ></audio>
              </div>
              
              <div class="audit-actions">
                <button class="action-btn preview-btn" @click="playPreview(upload.id, upload.musicFilePath)" :disabled="currentPlayingId === upload.id || isLoadingAudio">
                  {{ isLoadingAudio ? '加载中...' : (currentPlayingId === upload.id ? '播放中...' : '🎧 试听') }}
                </button>
                <button class="action-btn approve-btn" @click="approveUpload(upload.id)">✅ 通过</button>
                <button class="action-btn reject-btn" @click="showRejectModal(upload.id)">❌ 拒绝</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 拒绝确认模态框 -->
    <Transition name="modal">
      <div v-if="showRejectConfirm" class="modal-overlay" @click="closeRejectModal">
        <div class="modal-content" @click.stop ref="rejectModalRef">
          <div class="modal-header">
            <h3>拒绝审核</h3>
            <button class="close-btn" @click="closeRejectModal">&times;</button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label>拒绝原因</label>
              <textarea v-model="rejectReason" placeholder="请输入拒绝原因（可选）" rows="4"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button class="action-btn secondary-btn" @click="closeRejectModal">取消</button>
            <button class="action-btn reject-confirm-btn" @click="confirmReject">确认拒绝</button>
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
const currentAudioUrl = ref(null)
const audioPlayer = ref(null)
const isLoadingAudio = ref(false)
const showLyricsPreviewId = ref(null)
const uploadLyrics = ref({})
const loadingLyrics = ref({})
const showRejectConfirm = ref(false)
const rejectUploadId = ref(null)
const rejectReason = ref('')
const rejectModalRef = ref(null)
const coverUrls = ref({})
const loadingCovers = ref({})

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
      
      // 加载所有封面图片
      pendingUploads.value.forEach(upload => {
        if (upload.coverFilePath) {
          loadCoverImage(upload.id, upload.coverFilePath)
        }
      })
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
      pendingUploads.value = pendingUploads.value.filter(u => u.id !== uploadId)
      
      // 清理相关缓存
      if (currentPlayingId.value === uploadId) {
        stopPreview()
      }
      if (showLyricsPreviewId.value === uploadId) {
        showLyricsPreviewId.value = null
      }
      delete uploadLyrics.value[uploadId]
      delete loadingLyrics.value[uploadId]
      
      // 清理封面图片URL
      if (coverUrls.value[uploadId]) {
        URL.revokeObjectURL(coverUrls.value[uploadId])
        delete coverUrls.value[uploadId]
      }
      delete loadingCovers.value[uploadId]
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
      pendingUploads.value = pendingUploads.value.filter(u => u.id !== rejectUploadId.value)
      
      // 清理相关缓存
      closeRejectModal()
      if (currentPlayingId.value === rejectUploadId.value) {
        stopPreview()
      }
      if (showLyricsPreviewId.value === rejectUploadId.value) {
        showLyricsPreviewId.value = null
      }
      delete uploadLyrics.value[rejectUploadId.value]
      delete loadingLyrics.value[rejectUploadId.value]
      
      // 清理封面图片URL
      if (coverUrls.value[rejectUploadId.value]) {
        URL.revokeObjectURL(coverUrls.value[rejectUploadId.value])
        delete coverUrls.value[rejectUploadId.value]
      }
      delete loadingCovers.value[rejectUploadId.value]
    } else {
      toast.error(result.message || '审核拒绝失败')
    }
  } catch (error) {
    console.error('审核拒绝失败:', error)
    toast.error('审核拒绝失败')
  }
}

// 试听音乐
const playPreview = async (uploadId, musicFilePath) => {
  if (currentPlayingId.value === uploadId) {
    return
  }
  
  // 先停止当前播放
  stopPreview()
  
  currentPlayingId.value = uploadId
  isLoadingAudio.value = true
  
  try {
    const token = localStorage.getItem('adminToken')
    if (!token) {
      toast.error('请先登录管理员账号')
      currentPlayingId.value = null
      isLoadingAudio.value = false
      return
    }
    
    // 使用Fetch请求获取音频文件
    const response = await fetch(getMusicPreviewUrl(musicFilePath), {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    // 创建Blob URL
    const blob = await response.blob()
    currentAudioUrl.value = URL.createObjectURL(blob)
    
    // 播放音频
    setTimeout(() => {
      if (audioPlayer.value) {
        audioPlayer.value.play().catch(error => {
          console.error('播放失败:', error)
          toast.error('播放失败，请检查网络或文件状态')
          currentPlayingId.value = null
        })
      }
      isLoadingAudio.value = false
    }, 100)
    
  } catch (error) {
    console.error('加载音频失败:', error)
    toast.error('加载音频失败: ' + error.message)
    currentPlayingId.value = null
    isLoadingAudio.value = false
  }
}

// 停止播放
const stopPreview = () => {
  if (audioPlayer.value) {
    audioPlayer.value.pause()
    audioPlayer.value.currentTime = 0
  }
  
  // 释放Blob URL
  if (currentAudioUrl.value) {
    URL.revokeObjectURL(currentAudioUrl.value)
    currentAudioUrl.value = null
  }
  
  currentPlayingId.value = null
}

// 音频播放结束处理
const handleAudioEnded = () => {
  currentPlayingId.value = null
  isLoadingAudio.value = false
}

// 音频播放错误处理
const handleAudioError = (error) => {
  console.error('音频加载错误:', error)
  toast.error('音频加载失败，请检查文件是否存在')
  currentPlayingId.value = null
  isLoadingAudio.value = false
}

// 获取音乐预览URL
const getMusicPreviewUrl = (filePath) => {
  return `${API_CONFIG.BASE_URL}/api/user/upload/preview?path=${encodeURIComponent(filePath)}`
}

// 获取封面预览URL
const getCoverPreviewUrl = (coverFilePath) => {
  if (!coverFilePath) return ''
  return `${API_CONFIG.BASE_URL}/api/user/upload/preview?path=${encodeURIComponent(coverFilePath)}`
}

// 加载封面图片
const loadCoverImage = async (uploadId, coverFilePath) => {
  if (!coverFilePath || coverUrls.value[uploadId] || loadingCovers.value[uploadId]) {
    return
  }

  loadingCovers.value[uploadId] = true

  try {
    const token = localStorage.getItem('adminToken')
    if (!token) {
      console.error('未找到管理员token')
      return
    }

    const response = await fetch(getCoverPreviewUrl(coverFilePath), {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const blob = await response.blob()
    coverUrls.value[uploadId] = URL.createObjectURL(blob)

  } catch (error) {
    console.error('加载封面失败:', error)
  } finally {
    loadingCovers.value[uploadId] = false
  }
}

// 处理封面加载错误
const handleCoverError = (event) => {
  event.target.style.display = 'none'
  const placeholder = event.target.nextElementSibling
  if (placeholder) {
    placeholder.style.display = 'flex'
    const span = placeholder.querySelector('span')
    if (span) {
      span.textContent = '🎵'
    }
  }
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

// 切换歌词预览
const toggleLyricsPreview = async (uploadId) => {
  if (showLyricsPreviewId.value === uploadId) {
    // 如果已经显示，则隐藏
    showLyricsPreviewId.value = null
    return
  }
  
  // 显示歌词并加载
  showLyricsPreviewId.value = uploadId
  
  // 如果还没有加载过歌词，则加载
  if (!uploadLyrics.value[uploadId] && !loadingLyrics.value[uploadId]) {
    await loadLyricsForUpload(uploadId)
  }
}

// 加载上传的歌词
const loadLyricsForUpload = async (uploadId) => {
  loadingLyrics.value[uploadId] = true
  
  try {
    const token = localStorage.getItem('adminToken')
    if (!token) {
      toast.error('请先登录管理员账号')
      return
    }
    
    // 获取上传记录中的歌词文件路径
    const upload = pendingUploads.value.find(u => u.id === uploadId)
    if (!upload || !upload.lyricsFilePath) {
      uploadLyrics.value[uploadId] = []
      return
    }
    
    // 获取歌词文件
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/upload/preview?path=${encodeURIComponent(upload.lyricsFilePath)}`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    
    const lyricsText = await response.text()
    
    // 解析歌词
    uploadLyrics.value[uploadId] = parseLrcLyrics(lyricsText)
    
  } catch (error) {
    console.error('加载歌词失败:', error)
    uploadLyrics.value[uploadId] = []
  } finally {
    loadingLyrics.value[uploadId] = false
  }
}

// 解析LRC歌词格式
const parseLrcLyrics = (lrcText) => {
  if (!lrcText) {
    return []
  }
  
  const lines = lrcText.split('\n')
  const parsed = []
  
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    
    // 跳过空行
    if (!line) {
      continue
    }
    
    // 匹配时间戳歌词行 [mm:ss.xx] 或 [mm:ss.xxx]
    const timeRegex = /\[(\d{2}):(\d{2})\.(\d{2,3})\]/
    const timeMatch = line.match(timeRegex)
    
    if (timeMatch) {
      // 这是歌词行，提取时间和文本
      const minutes = parseInt(timeMatch[1])
      const seconds = parseInt(timeMatch[2])
      const milliseconds = parseInt(timeMatch[3])
      
      // 根据毫秒部分的位数正确计算秒数
      let millisecondsDivisor
      if (milliseconds.toString().length === 2) {
        millisecondsDivisor = 100 // 两位毫秒，如 .25
      } else {
        millisecondsDivisor = 1000 // 三位毫秒，如 .250
      }
      const timeInSeconds = minutes * 60 + seconds + (milliseconds / millisecondsDivisor)
      const text = line.replace(timeRegex, '').trim()
      
      // 查找下一行是否有翻译
      let translation = ''
      if (i + 1 < lines.length) {
        const nextLine = lines[i + 1].trim()
        // 检查是否是JSON格式的翻译行
        const jsonMatch = nextLine.match(/^\{["\'](.+)["\']\}$/)
        if (jsonMatch) {
          translation = jsonMatch[1]
        }
      }
      
      parsed.push({
        time: timeInSeconds,
        text: text,
        translation: translation
      })
    }
  }
  
  // 按时间排序
  parsed.sort((a, b) => a.time - b.time)
  return parsed
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
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px;
  padding: 20px;
  transition: margin-left 0.3s ease;
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.admin-header {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  margin-bottom: 20px;
  flex-shrink: 0;
}

.admin-user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logout-button {
  background: linear-gradient(135deg, rgba(220, 20, 60, 0.8), rgba(255, 99, 71, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(220, 20, 60, 0.3);
}

.logout-button:hover {
  background: linear-gradient(135deg, rgba(190, 10, 50, 0.9), rgba(235, 79, 51, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(220, 20, 60, 0.5);
}

.admin-content-wrapper {
  flex: 1;
  padding: 0 20px;
  min-height: 0;
  overflow: auto;
}

.admin-header-section {
  margin-bottom: 30px;
}

.admin-title {
  color: #6a5acd;
  margin: 0 0 10px 0;
  font-size: 1.8rem;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  position: relative;
  z-index: 1;
}

.admin-subtitle {
  color: #887bb0;
  font-size: 1rem;
  margin: 0;
  position: relative;
  z-index: 1;
}

.content-placeholder {
  padding: 40px;
  color: #887bb0;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  text-align: center;
}

.audit-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.audit-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  overflow: hidden;
  transition: transform 0.3s ease;
}

.audit-card:hover {
  transform: translateY(-3px);
}

.audit-card-header {
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  gap: 15px;
  align-items: flex-start;
}

.audit-cover-section {
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
}

.audit-cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.audit-cover-image:hover {
  transform: scale(1.05);
}

.audit-cover-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.3), rgba(138, 43, 226, 0.3));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
}

.audit-title-section {
  flex: 1;
  min-width: 0;
}

.audit-title-section h3 {
  margin: 0 0 5px 0;
  color: #6a5acd;
  font-size: 1.3rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.audit-title-section p {
  margin: 0;
  color: #887bb0;
  font-size: 0.95rem;
}

.audit-meta-section {
  display: flex;
  flex-direction: column;
  gap: 5px;
  align-items: flex-end;
  text-align: right;
}

.audit-time, .audit-user {
  font-size: 0.85rem;
  color: #887bb0;
}

.audit-card-body {
  padding: 20px;
}

.audit-details-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin-bottom: 20px;
}

.audit-detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 15px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
}

.detail-icon {
  font-size: 1.2rem;
}

.detail-label {
  font-weight: 500;
  color: #887bb0;
  min-width: 50px;
}

.detail-value {
  color: #6a5acd;
  font-weight: 600;
}

.audit-files-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 15px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 10px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.9rem;
  color: #887bb0;
}

.file-icon {
  font-size: 1.1rem;
}

.file-name {
  word-break: break-all;
}

.lyrics-preview-section {
  margin-top: 15px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  overflow: hidden;
}

.lyrics-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 15px;
  background: rgba(106, 90, 205, 0.1);
  border-bottom: 1px solid rgba(106, 90, 205, 0.2);
}

.lyrics-preview-title {
  color: #6a5acd;
  font-weight: 600;
  font-size: 0.95rem;
}

.toggle-lyrics-btn {
  background: rgba(106, 90, 205, 0.2);
  color: #6a5acd;
  border: 1px solid rgba(106, 90, 205, 0.3);
  border-radius: 15px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.3s ease;
}

.toggle-lyrics-btn:hover {
  background: rgba(106, 90, 205, 0.3);
  transform: translateY(-1px);
}

.lyrics-preview-content {
  padding: 15px;
  max-height: 200px;
  overflow-y: auto;
}

.lyrics-text {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.lyric-line {
  color: #887bb0;
  font-size: 0.85rem;
  padding: 4px 8px;
  border-radius: 5px;
  transition: all 0.3s ease;
}

.lyric-line:hover {
  background: rgba(106, 90, 205, 0.1);
  color: #6a5acd;
}

.lyrics-loading {
  text-align: center;
  padding: 20px;
  color: #887bb0;
  font-size: 0.9rem;
}

.audit-card-footer {
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.player-section {
  background: rgba(255, 255, 255, 0.2);
  padding: 10px;
  border-radius: 10px;
}

.player-section audio {
  width: 100%;
  height: 32px;
}

.audit-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.action-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  text-decoration: none;
  display: inline-block;
}

.preview-btn {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(106, 90, 205, 0.3);
}

.preview-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(106, 90, 205, 0.5);
}

.preview-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.approve-btn {
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.8), rgba(50, 205, 50, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(82, 196, 26, 0.3);
}

.approve-btn:hover {
  background: linear-gradient(135deg, rgba(62, 176, 6, 0.9), rgba(30, 185, 30, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(82, 196, 26, 0.5);
}

.reject-btn {
  background: linear-gradient(135deg, rgba(245, 34, 45, 0.8), rgba(255, 99, 71, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(245, 34, 45, 0.3);
}

.reject-btn:hover {
  background: linear-gradient(135deg, rgba(225, 14, 25, 0.9), rgba(235, 79, 51, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(245, 34, 45, 0.5);
}

/* 模态框样式 */
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
  background: rgba(255, 255, 255, 0.95);
  border-radius: 15px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.modal-header h3 {
  margin: 0;
  color: #6a5acd;
  font-size: 1.3rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #887bb0;
  transition: color 0.3s ease;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.close-btn:hover {
  color: #6a5acd;
  background: rgba(106, 90, 205, 0.1);
}

.modal-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  margin-bottom: 10px;
  color: #6a5acd;
  font-weight: 500;
}

.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid rgba(106, 90, 205, 0.3);
  border-radius: 10px;
  font-family: inherit;
  resize: vertical;
  min-height: 100px;
  background: rgba(255, 255, 255, 0.5);
  transition: all 0.3s ease;
}

.form-group textarea:focus {
  outline: none;
  border-color: #6a5acd;
  background: rgba(255, 255, 255, 0.8);
}

.modal-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.5);
  color: #6a5acd;
  border: 1px solid rgba(106, 90, 205, 0.3);
}

.secondary-btn:hover {
  background: rgba(255, 255, 255, 0.8);
}

.reject-confirm-btn {
  background: linear-gradient(135deg, rgba(245, 34, 45, 0.8), rgba(255, 99, 71, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(245, 34, 45, 0.3);
}

.reject-confirm-btn:hover {
  background: linear-gradient(135deg, rgba(225, 14, 25, 0.9), rgba(235, 79, 51, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(245, 34, 45, 0.5);
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
    padding: 10px;
  }
  
  .admin-layout {
    flex-direction: column;
  }
  
  .audit-card-header {
    flex-wrap: wrap;
  }
  
  .audit-cover-section {
    width: 60px;
    height: 60px;
  }
  
  .audit-title-section {
    width: calc(100% - 75px);
  }
  
  .audit-meta-section {
    width: 100%;
    align-items: flex-start;
    text-align: left;
  }
  
  .audit-details-grid {
    grid-template-columns: 1fr;
  }
  
  .audit-actions {
    flex-direction: column;
  }
  
  .action-btn {
    width: 100%;
  }
}
</style>