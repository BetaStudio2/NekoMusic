<template>
  <div class="upload-view">
    <div class="upload-container">
      <div class="upload-header">
        <h1 class="upload-title">上传音乐</h1>
        <p class="upload-subtitle">分享您的音乐到 Neko云音乐平台</p>
      </div>

      <form @submit.prevent="handleSubmit" class="upload-form">
        <!-- 歌曲文件上传 -->
        <div class="form-group">
          <label class="form-label">音乐文件 <span class="required">*</span></label>
          <div class="file-upload-area" :class="{ 'has-file': musicFile }" @click="selectMusicFile">
            <input
              ref="musicFileInput"
              type="file"
              accept="audio/*"
              @change="handleMusicFileChange"
              style="display: none"
            />
            <div v-if="!musicFile" class="upload-placeholder">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 18V5l12-2v13"></path>
                <circle cx="6" cy="18" r="3"></circle>
                <circle cx="18" cy="16" r="3"></circle>
              </svg>
              <p>点击选择音乐文件</p>
              <span class="file-hint">支持 MP3、FLAC、WAV 等格式</span>
            </div>
            <div v-else class="file-info">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 18V5l12-2v13"></path>
                <circle cx="6" cy="18" r="3"></circle>
                <circle cx="18" cy="16" r="3"></circle>
              </svg>
              <span class="file-name">{{ musicFile.name }}</span>
              <button type="button" @click.stop="removeMusicFile" class="remove-file-btn">×</button>
            </div>
          </div>
        </div>

        <!-- 封面图片上传 -->
        <div class="form-group">
          <label class="form-label">封面图片</label>
          <div class="file-upload-area cover-upload" :class="{ 'has-file': coverFile }" @click="selectCoverFile">
            <input
              ref="coverFileInput"
              type="file"
              accept="image/*"
              @change="handleCoverFileChange"
              style="display: none"
            />
            <div v-if="!coverFile" class="upload-placeholder">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                <circle cx="8.5" cy="8.5" r="1.5"></circle>
                <polyline points="21 15 16 10 5 21"></polyline>
              </svg>
              <p>点击选择封面图片</p>
              <span class="file-hint">支持 JPG、PNG 格式</span>
            </div>
            <div v-else class="file-preview">
              <img :src="coverPreview" alt="封面预览" class="cover-preview-img" />
              <button type="button" @click.stop="removeCoverFile" class="remove-file-btn">×</button>
            </div>
          </div>
        </div>

        <!-- 歌曲信息表单 -->
        <div class="form-row">
          <div class="form-group">
            <label class="form-label">歌曲标题 <span class="required">*</span></label>
            <input
              v-model="formData.title"
              type="text"
              class="form-input"
              placeholder="输入歌曲标题"
              required
            />
          </div>
          <div class="form-group">
            <label class="form-label">歌手/艺术家 <span class="required">*</span></label>
            <input
              v-model="formData.artist"
              type="text"
              class="form-input"
              placeholder="输入歌手或艺术家名称"
              required
            />
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">专辑</label>
          <input
            v-model="formData.album"
            type="text"
            class="form-input"
            placeholder="输入专辑名称（可选）"
          />
        </div>

        <div class="form-group">
          <label class="form-label">歌曲描述</label>
          <textarea
            v-model="formData.description"
            class="form-textarea"
            placeholder="输入歌曲描述（可选）"
            rows="3"
          ></textarea>
        </div>

        <!-- 提交按钮 -->
        <button type="submit" class="submit-btn" :disabled="uploading">
          <svg v-if="!uploading" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="17 8 12 3 7 8"></polyline>
            <line x1="12" y1="3" x2="12" y2="15"></line>
          </svg>
          <span v-if="uploading">上传中...</span>
          <span v-else>上传音乐</span>
        </button>
      </form>

      <!-- 上传进度 -->
      <div v-if="uploadProgress > 0 && uploading" class="progress-container">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
        </div>
        <span class="progress-text">{{ uploadProgress }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()

// 文件引用
const musicFileInput = ref(null)
const coverFileInput = ref(null)

// 文件数据
const musicFile = ref(null)
const coverFile = ref(null)
const coverPreview = ref(null)

// 表单数据
const formData = ref({
  title: '',
  artist: '',
  album: '',
  description: ''
})

// 上传状态
const uploading = ref(false)
const uploadProgress = ref(0)

// 选择音乐文件
const selectMusicFile = () => {
  musicFileInput.value.click()
}

// 选择封面文件
const selectCoverFile = () => {
  coverFileInput.value.click()
}

// 处理音乐文件选择
const handleMusicFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    musicFile.value = file
    // 自动填充歌曲标题（使用文件名去除扩展名）
    const fileName = file.name.replace(/\.[^/.]+$/, '')
    if (!formData.value.title) {
      formData.value.title = fileName
    }
  }
}

// 处理封面文件选择
const handleCoverFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

// 移除音乐文件
const removeMusicFile = () => {
  musicFile.value = null
  musicFileInput.value.value = ''
}

// 移除封面文件
const removeCoverFile = () => {
  coverFile.value = null
  coverPreview.value = null
  coverFileInput.value.value = ''
}

// 提交表单
const handleSubmit = async () => {
  if (!musicFile.value) {
    toast.error('请选择音乐文件')
    return
  }

  if (!formData.value.title || !formData.value.artist) {
    toast.error('请填写歌曲标题和歌手信息')
    return
  }

  uploading.value = true
  uploadProgress.value = 0

  try {
    // 创建 FormData 对象
    const form = new FormData()
    form.append('file', musicFile.value)
    form.append('title', formData.value.title)
    form.append('artist', formData.value.artist)
    if (formData.value.album) {
      form.append('album', formData.value.album)
    }
    if (formData.value.description) {
      form.append('description', formData.value.description)
    }
    if (coverFile.value) {
      form.append('cover', coverFile.value)
    }

    // 使用 XMLHttpRequest 以支持上传进度
    const xhr = new XMLHttpRequest()
    
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        const progress = Math.round((event.loaded / event.total) * 100)
        uploadProgress.value = progress
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status === 200) {
        const response = JSON.parse(xhr.responseText)
        if (response.success) {
          toast.success('音乐上传成功！')
          setTimeout(() => {
            router.push('/')
          }, 1500)
        } else {
          toast.error(response.message || '上传失败')
        }
      } else {
        toast.error('上传失败，请稍后重试')
      }
      uploading.value = false
    })

    xhr.addEventListener('error', () => {
      toast.error('网络错误，请检查连接后重试')
      uploading.value = false
    })

    xhr.open('POST', `${API_CONFIG.BASE_URL}/api/music/upload`)
    xhr.send(form)

  } catch (error) {
    console.error('上传错误:', error)
    toast.error('上传失败，请稍后重试')
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-view {
  min-height: calc(100vh - 80px);
  padding: 40px 20px;
  //background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.upload-container {
  max-width: 600px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.upload-header {
  text-align: center;
  margin-bottom: 40px;
}

.upload-title {
  font-size: 2rem;
  font-weight: 700;
  color: #667eea;
  margin: 0 0 10px 0;
}

.upload-subtitle {
  font-size: 1rem;
  color: #666;
  margin: 0;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 0.95rem;
  font-weight: 600;
  color: #333;
}

.required {
  color: #ff6b6b;
  margin-left: 2px;
}

.form-input,
.form-textarea {
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 10px;
  font-size: 1rem;
  transition: all 0.3s ease;
  font-family: inherit;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.file-upload-area {
  border: 2px dashed #e0e0e0;
  border-radius: 15px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  min-height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-upload-area:hover {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

.file-upload-area.has-file {
  border-style: solid;
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

.upload-placeholder svg {
  color: #aaa;
  margin-bottom: 15px;
}

.upload-placeholder p {
  font-size: 1rem;
  color: #333;
  margin: 0 0 5px 0;
}

.file-hint {
  font-size: 0.85rem;
  color: #999;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.file-info svg {
  color: #667eea;
  flex-shrink: 0;
}

.file-name {
  flex: 1;
  font-size: 0.95rem;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: #ff6b6b;
  color: white;
  border-radius: 50%;
  font-size: 1.2rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.remove-file-btn:hover {
  background: #ee5a5a;
  transform: scale(1.1);
}

.cover-upload {
  padding: 20px;
}

.file-preview {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
}

.cover-preview-img {
  max-width: 200px;
  max-height: 200px;
  object-fit: cover;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.submit-btn {
  padding: 14px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 1.05rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.progress-container {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-top: 20px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.9rem;
  font-weight: 600;
  color: #667eea;
  min-width: 45px;
}

@media (max-width: 768px) {
  .upload-container {
    padding: 25px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .upload-title {
    font-size: 1.5rem;
  }
}
</style>