<template>
  <div class="create-playlist-view">
    <div class="create-container">
      <div class="create-header">
        <button @click="goBack" class="back-btn">← 返回</button>
        <h2>创建歌单</h2>
      </div>
      
      <form @submit.prevent="handleCreatePlaylist" class="create-form">
        <div class="form-group">
          <label>歌单名称 <span class="required">*</span></label>
          <input 
            v-model="playlistName" 
            type="text" 
            required 
            maxlength="255"
            placeholder="请输入歌单名称"
            autofocus
          />
          <span class="char-count">{{ playlistName.length }}/255</span>
        </div>
        
        <div class="form-group">
          <label>歌单描述</label>
          <textarea 
            v-model="playlistDescription" 
            maxlength="500"
            rows="4"
            placeholder="请输入歌单描述（可选）"
          ></textarea>
          <span class="char-count">{{ playlistDescription.length }}/500</span>
        </div>
        
        <div class="form-actions">
          <button type="button" @click="goBack" class="cancel-btn">取消</button>
          <button type="submit" class="submit-btn" :disabled="submitting">
            {{ submitting ? '创建中...' : '创建歌单' }}
          </button>
        </div>
      </form>
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

const playlistName = ref('')
const playlistDescription = ref('')
const submitting = ref(false)

const getToken = () => {
  return localStorage.getItem('userToken')
}

const handleCreatePlaylist = async () => {
  if (submitting.value) return
  
  submitting.value = true
  
  try {
    const token = getToken()
    const requestData = {
      name: playlistName.value.trim()
    }
    
    // 只有当描述不为空时才添加到请求中
    if (playlistDescription.value.trim()) {
      requestData.description = playlistDescription.value.trim()
    }
    
    console.log('创建歌单请求:', requestData)
    console.log('Token:', token)
    console.log('API URL:', `${API_CONFIG.BASE_URL}/api/user/playlist/create`)
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/playlist/create`, {
      method: 'POST',
      headers: {
        'Authorization': token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestData)
    })
    
    console.log('响应状态:', response.status)
    const data = await response.json()
    console.log('响应数据:', data)
    
    if (data.success) {
      toast.success('歌单创建成功')
      router.push('/playlists')
    } else {
      toast.error(data.message || '歌单创建失败')
    }
  } catch (error) {
    console.error('歌单创建失败:', error)
    toast.error('歌单创建失败')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.create-playlist-view {
  min-height: calc(100vh - 80px);
  padding: 40px 20px;
}

.create-container {
  max-width: 600px;
  margin: 0 auto;
}

.create-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 40px;
  color: #333;
}

.back-btn {
  background: #f3f4f6;
  color: #666;
  border: none;
  padding: 10px 20px;
  border-radius: 20px;
  font-size: 1em;
  cursor: pointer;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: #e5e7eb;
}

.create-header h2 {
  margin: 0;
  font-size: 2.5em;
  font-weight: 600;
}

.create-form {
  background: white;
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.form-group {
  margin-bottom: 30px;
  position: relative;
}

.form-group label {
  display: block;
  color: #333;
  font-weight: 600;
  margin-bottom: 10px;
  font-size: 1.1em;
}

.required {
  color: #ef4444;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 14px 18px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  font-size: 1em;
  transition: all 0.3s ease;
  box-sizing: border-box;
  font-family: inherit;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group textarea {
  resize: vertical;
  min-height: 120px;
}

.char-count {
  position: absolute;
  right: 0;
  bottom: -25px;
  color: #9ca3af;
  font-size: 0.9em;
}

.form-actions {
  display: flex;
  gap: 15px;
  justify-content: flex-end;
  margin-top: 40px;
}

.cancel-btn {
  background: #f3f4f6;
  color: #374151;
  border: none;
  padding: 14px 35px;
  border-radius: 12px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.cancel-btn:hover {
  background: #e5e7eb;
}

.submit-btn {
  background: #667eea;
  color: white;
  border: none;
  padding: 14px 35px;
  border-radius: 12px;
  font-size: 1em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.submit-btn:hover:not(:disabled) {
  background: #5568d3;
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(102, 126, 234, 0.3);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .create-header h2 {
    font-size: 2em;
  }
  
  .create-form {
    padding: 30px;
  }
  
  .form-actions {
    flex-direction: column-reverse;
  }
  
  .cancel-btn,
  .submit-btn {
    width: 100%;
  }
}
</style>