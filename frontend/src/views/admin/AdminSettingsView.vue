<template>
  <div class="admin-subpage">
    <h2>系统设置</h2>
    <p>配置系统参数，管理平台设置和全局配置。</p>
    
    <div class="settings-tabs">
      <button 
        v-for="tab in settingsTabs" 
        :key="tab.key" 
        :class="['tab-btn', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        {{ tab.title }}
      </button>
    </div>
    
    <div class="tab-content">
      <!-- 基本设置 -->
      <div v-if="activeTab === 'basic'" class="tab-panel">
        <h3>基本设置</h3>
        <div class="form-group">
          <label>网站标题</label>
          <input 
            type="text" 
            v-model="settings.basic.siteTitle" 
            placeholder="输入网站标题"
          />
        </div>
        
        <div class="form-group">
          <label>网站描述</label>
          <textarea 
            v-model="settings.basic.description" 
            placeholder="输入网站描述"
            rows="3"
          ></textarea>
        </div>
        
        <div class="form-group">
          <label>是否开启注册</label>
          <div class="switch-group">
            <label class="switch">
              <input 
                type="checkbox" 
                v-model="settings.basic.registrationEnabled"
              >
              <span class="slider"></span>
            </label>
            <span class="switch-label">
              {{ settings.basic.registrationEnabled ? '开启' : '关闭' }}
            </span>
          </div>
        </div>
        
        <div class="form-actions">
          <button class="save-btn" @click="saveSettings('basic')">保存设置</button>
        </div>
      </div>
      
      <!-- 音乐设置 -->
      <div v-if="activeTab === 'music'" class="tab-panel">
        <h3>音乐设置</h3>
        <div class="form-group">
          <label>最大上传大小 (MB)</label>
          <input 
            type="number" 
            v-model="settings.music.maxUploadSize" 
            placeholder="输入最大上传大小"
          />
        </div>
        
        <div class="form-group">
          <label>支持的音频格式</label>
          <input 
            type="text" 
            v-model="settings.music.supportedFormats" 
            placeholder="例如: mp3,wav,flac"
          />
        </div>
        
        <div class="form-group">
          <label>是否启用音乐审核</label>
          <div class="switch-group">
            <label class="switch">
              <input 
                type="checkbox" 
                v-model="settings.music.moderationEnabled"
              >
              <span class="slider"></span>
            </label>
            <span class="switch-label">
              {{ settings.music.moderationEnabled ? '启用' : '禁用' }}
            </span>
          </div>
        </div>
        
        <div class="form-actions">
          <button class="save-btn" @click="saveSettings('music')">保存设置</button>
        </div>
      </div>
      
      <!-- 安全设置 -->
      <div v-if="activeTab === 'security'" class="tab-panel">
        <h3>安全设置</h3>
        <div class="form-group">
          <label>登录尝试次数限制</label>
          <input 
            type="number" 
            v-model="settings.security.loginAttempts" 
            placeholder="输入登录尝试次数限制"
          />
        </div>
        
        <div class="form-group">
          <label>登录锁定时间 (分钟)</label>
          <input 
            type="number" 
            v-model="settings.security.lockoutTime" 
            placeholder="输入登录锁定时间"
          />
        </div>
        
        <div class="form-group">
          <label>会话超时时间 (分钟)</label>
          <input 
            type="number" 
            v-model="settings.security.sessionTimeout" 
            placeholder="输入会话超时时间"
          />
        </div>
        
        <div class="form-group">
          <label>是否启用双因素认证</label>
          <div class="switch-group">
            <label class="switch">
              <input 
                type="checkbox" 
                v-model="settings.security.twoFactorAuth"
              >
              <span class="slider"></span>
            </label>
            <span class="switch-label">
              {{ settings.security.twoFactorAuth ? '启用' : '禁用' }}
            </span>
          </div>
        </div>
        
        <div class="form-actions">
          <button class="save-btn" @click="saveSettings('security')">保存设置</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'vue-toastification'

const toast = useToast()

const router = useRouter()

// 检查管理员登录状态
onMounted(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  
  if (!storedToken || !storedAdminInfo) {
    router.push('/admin/login')
  }
})

// 设置标签页
const activeTab = ref('basic')
const settingsTabs = ref([
  { key: 'basic', title: '基本设置' },
  { key: 'music', title: '音乐设置' },
  { key: 'security', title: '安全设置' }
])

// 设置数据
const settings = ref({
  basic: {
    siteTitle: 'NekoMusic 音乐平台',
    description: '一个现代化的音乐播放平台',
    registrationEnabled: true
  },
  music: {
    maxUploadSize: 50,
    supportedFormats: 'mp3,wav,flac',
    moderationEnabled: true
  },
  security: {
    loginAttempts: 5,
    lockoutTime: 30,
    sessionTimeout: 120,
    twoFactorAuth: false
  }
})

// 保存设置
const saveSettings = (tab) => {
  toast.success(`${settingsTabs.value.find(t => t.key === tab).title} 已保存！`)
  // 这里可以实现实际的保存逻辑
  console.log('保存设置:', settings.value[tab])
}
</script>

<style scoped>
.admin-subpage {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.admin-subpage h2 {
  color: #69c8df;
  margin: 0 0 20px 0;
  font-size: 1.5rem;
}

.settings-tabs {
  display: flex;
  margin-bottom: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.tab-btn {
  padding: 10px 20px;
  background: transparent;
  border: none;
  color: #887bb0;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.3s ease;
}

.tab-btn.active {
  color: #69c8df;
  border-bottom: 2px solid #69c8df;
  font-weight: bold;
}

.tab-btn:hover:not(.active) {
  color: #69c8df;
  background: rgba(105, 200, 223, 0.1);
  border-radius: 5px 5px 0 0;
}

.tab-content {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  padding: 20px;
}

.tab-panel h3 {
  color: #69c8df;
  margin: 0 0 20px 0;
  font-size: 1.2rem;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: #5c4b7b;
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  max-width: 400px;
  padding: 10px 15px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border: 1px solid rgba(105, 200, 223, 0.5);
  box-shadow: 0 0 0 2px rgba(105, 200, 223, 0.2);
  background: rgba(255, 255, 255, 0.35);
}

.switch-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* Switch 开关样式 */
.switch {
  position: relative;
  display: inline-block;
  width: 50px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  transition: .4s;
  border-radius: 24px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .4s;
  border-radius: 50%;
}

input:checked + .slider {
  background-color: #69c8df;
}

input:checked + .slider:before {
  transform: translateX(26px);
}

.switch-label {
  color: #887bb0;
  font-size: 0.9rem;
}

.form-actions {
  margin-top: 30px;
  display: flex;
  justify-content: flex-start;
}

.save-btn {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.8), rgba(105, 200, 223, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 10px 25px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(105, 200, 223, 0.3);
}

.save-btn:hover {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(105, 200, 223, 0.5);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-subpage {
    padding: 20px 20px 150px 20px;
  }
}
</style>