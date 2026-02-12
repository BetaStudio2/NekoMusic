<template>
  <div class="upload-view">
    <div class="upload-container">
      <!-- 左侧封面 -->
      <div class="cover-side">
        <div 
              class="cover-upload" 
              :class="{ 'has-cover': coverFile, 'drag-over': isCoverDragging }"
              @click="selectCoverFile"
              @dragover.prevent="isCoverDragging = true"
              @dragleave.prevent="isCoverDragging = false"
              @drop.prevent="handleCoverDrop"
            >
          <input
            ref="coverFileInput"
            type="file"
            accept="image/*"
            @change="handleCoverFileChange"
            style="display: none"
          />
          <div v-if="!coverFile" class="cover-placeholder">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="3" width="18" height="18" rx="2"></rect>
              <circle cx="8.5" cy="8.5" r="1.5"></circle>
              <polyline points="21 15 16 10 5 21"></polyline>
            </svg>
            <p>上传封面</p>
            <span>建议 800x800</span>
          </div>
          <div v-else class="cover-preview">
            <img :src="coverPreview" alt="封面" />
            <div class="cover-overlay">
              <button type="button" @click.stop="removeCoverFile" class="change-btn">更换</button>
            </div>
          </div>
        </div>

        <!-- 歌词文件 -->
        <div class="lyrics-upload-section">
          <label class="side-label">歌词文件</label>
          <div class="file-upload lyrics-upload" :class="{ 'has-file': lyricsFile }" @click="selectLyricsFile">
            <input
              ref="lyricsFileInput"
              type="file"
              accept=".lrc"
              @change="handleLyricsFileChange"
              style="display: none"
            />
            <div v-if="!lyricsFile" class="file-placeholder">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
              <span>选择歌词文件</span>
            </div>
            <div v-else class="file-info">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
              <span>{{ lyricsFile.name }}</span>
              <button type="button" @click.stop="removeLyricsFile" class="remove-btn"></button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单 -->
      <div class="form-side">
        <h2 class="page-title">上传音乐</h2>

        <form @submit.prevent="handleSubmit" class="upload-form">
          <!-- 音乐文件 -->
          <div class="form-group">
            <div 
              class="file-upload" 
              :class="{ 'has-file': musicFile, 'drag-over': isDragging }"
              @click="selectMusicFile"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <input
                ref="musicFileInput"
                type="file"
                accept="audio/*"
                @change="handleMusicFileChange"
                style="display: none"
              />
              <div v-if="!musicFile" class="file-placeholder">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17 8 12 3 7 8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                <span>选择音频文件</span>
              </div>
              <div v-else class="file-info">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18V5l12-2v13"></path>
                  <circle cx="6" cy="18" r="3"></circle>
                  <circle cx="18" cy="16" r="3"></circle>
                </svg>
                <span>{{ musicFile.name }}</span>
                <button type="button" @click.stop="removeMusicFile" class="remove-btn"></button>
              </div>
            </div>
          </div>

          <!-- 歌曲标题 -->
          <div class="form-group">
            <label class="label">歌曲标题 <span class="required">*</span></label>
            <input
              v-model="formData.title"
              type="text"
              class="input"
              placeholder="输入歌曲标题"
              required
            />
          </div>

          <!-- 歌手 -->
          <div class="form-group">
            <label class="label">歌手 <span class="required">*</span></label>
            <input
              v-model="formData.artist"
              type="text"
              class="input"
              placeholder="输入歌手名称"
              required
            />
          </div>

          <!-- 专辑 -->
          <div class="form-group">
            <label class="label">专辑</label>
            <input
              v-model="formData.album"
              type="text"
              class="input"
              placeholder="输入专辑名称"
            />
          </div>

          <!-- 标签 -->
          <div class="form-group">
            <label class="label">标签</label>
            <input
              v-model="formData.tags"
              type="text"
              class="input"
              placeholder="输入标签，多个标签用逗号分隔"
            />
          </div>

          <!-- 语言 -->
          <div class="form-group">
            <label class="label">语言 <span class="required">*</span></label>
            <select v-model="formData.language" class="input" required>
              <option value="" disabled>请选择语言</option>
              <option value="中文">中文</option>
              <option value="粤语">粤语</option>
              <option value="上海语">上海语</option>
              <option value="英文">英文</option>
              <option value="日语">日语</option>
              <option value="韩语">韩语</option>
              <option value="法语">法语</option>
              <option value="德语">德语</option>
              <option value="俄语">俄语</option>
              <option value="纯音乐">纯音乐</option>
            </select>
          </div>

          <!-- 提交按钮 -->
          <button type="submit" class="submit-btn" :disabled="uploading">
            <span v-if="!uploading">发布音乐</span>
            <span v-else>上传中 {{ uploadProgress }}%</span>
          </button>
        </form>
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

const musicFileInput = ref(null)
const coverFileInput = ref(null)
const lyricsFileInput = ref(null)

const musicFile = ref(null)
const coverFile = ref(null)
const coverPreview = ref(null)
const lyricsFile = ref(null)

const formData = ref({
  title: '',
  artist: '',
  album: '',
  tags: '',
  language: ''
})

const uploading = ref(false)
const uploadProgress = ref(0)

const isDragging = ref(false)
const isCoverDragging = ref(false)

const selectMusicFile = () => {
  musicFileInput.value.click()
}

const selectCoverFile = () => {
  coverFileInput.value.click()
}

const handleMusicFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    musicFile.value = file
    const fileName = file.name.replace(/\.[^/.]+$/, '')
    if (!formData.value.title) {
      formData.value.title = fileName
    }
  }
}

const handleCoverFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

const removeMusicFile = () => {
  musicFile.value = null
  musicFileInput.value.value = ''
}

const removeCoverFile = () => {
  coverFile.value = null
  coverPreview.value = null
  coverFileInput.value.value = ''
}

const selectLyricsFile = () => {
  lyricsFileInput.value.click()
}

const handleLyricsFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    lyricsFile.value = file
  }
}

const removeLyricsFile = () => {
  lyricsFile.value = null
  lyricsFileInput.value.value = ''
}

const handleDrop = (event) => {
  isDragging.value = false
  const file = event.dataTransfer.files[0]
  if (file && file.type.startsWith('audio/')) {
    musicFile.value = file
    const fileName = file.name.replace(/\.[^/.]+$/, '')
    if (!formData.value.title) {
      formData.value.title = fileName
    }
  }
}

const handleCoverDrop = (event) => {
  isCoverDragging.value = false
  const file = event.dataTransfer.files[0]
  if (file && file.type.startsWith('image/')) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

const handleSubmit = async () => {
  if (!musicFile.value) {
    toast.error('请选择音乐文件')
    return
  }

  if (!formData.value.title || !formData.value.artist) {
    toast.error('请填写歌曲标题和歌手')
    return
  }

  if (!formData.value.language) {
    toast.error('请选择语言')
    return
  }

  uploading.value = true
  uploadProgress.value = 0

  try {
    const form = new FormData()
    form.append('file', musicFile.value)
    form.append('title', formData.value.title)
    form.append('artist', formData.value.artist)
    if (formData.value.album) form.append('album', formData.value.album)
    if (formData.value.tags) form.append('tags', formData.value.tags)
    form.append('language', formData.value.language)
    if (coverFile.value) form.append('cover', coverFile.value)
    if (lyricsFile.value) form.append('lyrics', lyricsFile.value)

    const xhr = new XMLHttpRequest()
    
    xhr.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        uploadProgress.value = Math.round((event.loaded / event.total) * 100)
      }
    })

    xhr.addEventListener('load', () => {
      if (xhr.status === 200) {
        const response = JSON.parse(xhr.responseText)
        if (response.success) {
          toast.success('上传成功')
          setTimeout(() => router.push('/'), 1500)
        } else {
          toast.error(response.message || '上传失败')
        }
      } else {
        toast.error('上传失败')
      }
      uploading.value = false
    })

    xhr.addEventListener('error', () => {
      toast.error('网络错误')
      uploading.value = false
    })

    xhr.open('POST', `${API_CONFIG.BASE_URL}/api/music/upload`)
    xhr.send(form)

  } catch (error) {
    toast.error('上传失败')
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-view {
  min-height: calc(100vh - 80px);
  padding: 40px 20px;
}

.upload-container {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 50px;
  align-items: start;
}

/* 左侧封面 */
.cover-side {
  position: sticky;
  top: 20px;
}

.cover-upload {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.3);
  border: 2px dashed rgba(102, 126, 234, 0.3);
}

.cover-upload:hover,
.cover-upload.drag-over {
  border-color: #667eea;
  transform: scale(1.02);
  background: rgba(102, 126, 234, 0.1);
}

.cover-upload.has-cover {
  border-style: solid;
  border-color: rgba(102, 126, 234, 0.5);
}

.cover-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #667eea;
  text-align: center;
  padding: 20px;
}

.cover-placeholder svg {
  opacity: 0.8;
}

.cover-placeholder p {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.cover-placeholder span {
  font-size: 14px;
  opacity: 0.7;
}

.cover-preview {
  width: 100%;
  height: 100%;
  position: relative;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.cover-preview:hover .cover-overlay {
  opacity: 1;
}

.change-btn {
  padding: 10px 24px;
  background: white;
  color: #333;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.change-btn:hover {
  transform: scale(1.05);
}

/* 左侧歌词上传 */
.lyrics-upload-section {
  margin-top: 24px;
}

.side-label {
  color: #333;
  font-size: 14px;
  font-weight: 600;
  display: block;
  margin-bottom: 8px;
}

.lyrics-upload {
  padding: 20px;
}

/* 右侧表单 */
.form-side {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.page-title {
  color: #333;
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 30px 0;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  color: #333;
  font-size: 14px;
  font-weight: 600;
}

.required {
  color: #ef4444;
  margin-left: 2px;
}

.file-upload {
  border: 2px dashed rgba(102, 126, 234, 0.3);
  border-radius: 16px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.2);
}

.file-upload:hover,
.file-upload.drag-over {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.1);
}

.file-upload.has-file {
  border-style: solid;
  border-color: #667eea;
  padding: 20px;
  text-align: left;
}

.file-placeholder {
  color: #667eea;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.file-placeholder span {
  font-size: 15px;
  font-weight: 500;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-info svg {
  color: #667eea;
  flex-shrink: 0;
}

.file-info span {
  flex: 1;
  font-size: 15px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #ef4444;
  color: white;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
}

.remove-btn::before,
.remove-btn::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 10px;
  height: 2px;
  background: white;
  transform: translate(-50%, -50%) rotate(45deg);
}

.remove-btn::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.input,
.textarea {
  padding: 14px 18px;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  font-size: 15px;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
}

.input::placeholder,
.textarea::placeholder {
  color: #999;
}

.input:focus,
.textarea:focus {
  outline: none;
  border-color: #667eea;
  background: rgba(255, 255, 255, 0.7);
}

.textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.submit-btn {
  padding: 16px 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
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

@media (max-width: 900px) {
  .upload-container {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .cover-side {
    position: static;
    display: flex;
    justify-content: center;
  }

  .cover-upload {
    max-width: 300px;
  }
}

@media (max-width: 600px) {
  .form-side {
    padding: 30px 20px;
  }

  .page-title {
    font-size: 24px;
  }
}
</style>