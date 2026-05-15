<template>
  <div class="profile-container">
    <div class="profile-card">
      <div class="profile-header">
        <div class="avatar-section">
          <img :src="userAvatar" alt="用户头像" class="profile-avatar" @error="handleAvatarError" />
          <div class="upload-avatar-btn">
            <input type="file" id="avatar-upload" accept="image/*" @change="handleAvatarUpload" />
            <label for="avatar-upload">更换头像</label>
          </div>
        </div>
        <div class="user-info">
          <h2 class="username">
            {{ user.username }}
            <router-link v-if="user.isVip" to="/vip" class="vip-badge" title="会员中心">VIP</router-link>
          </h2>
          <p class="email">{{ user.email }}</p>
          <p class="join-date">加入时间: {{ formatDate(user.createdAt) }}</p>
        </div>
      </div>
      
      <div class="profile-content">
        <div class="profile-tabs">
          <button 
            v-for="tab in tabs" 
            :key="tab.key"
            :class="['tab-btn', { active: activeTab === tab.key }]"
            @click="changeTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
        
        <div class="tab-content">
          <div v-if="activeTab === 'profile'" class="tab-panel">
            <h3>个人信息</h3>
            <div class="info-item">
              <label>用户名:</label>
              <span>{{ user.username }}</span>
            </div>
            <div class="info-item">
              <label>邮箱:</label>
              <span>{{ user.email }}</span>
            </div>
            <div class="info-item">
              <label>会员状态:</label>
              <span>{{ user.isVip ? '会员' : '非会员' }}</span>
            </div>
            <div class="info-item">
              <label>会员到期:</label>
              <span>{{ formatVipExpiresAt(user.vipExpiresAt) }}</span>
            </div>
            <div class="info-item">
              <label>注册时间:</label>
              <span>{{ formatDate(user.createdAt) }}</span>
            </div>
          </div>
          
          <div v-if="activeTab === 'security'" class="tab-panel">
            <h3>安全设置</h3>
            <div class="form-group">
              <label>当前密码:</label>
              <input type="password" v-model="currentPassword" placeholder="请输入当前密码" />
            </div>
            <div class="form-group">
              <label>新密码:</label>
              <input type="password" v-model="newPassword" placeholder="请输入新密码" />
            </div>
            <div class="form-group">
              <label>确认新密码:</label>
              <input type="password" v-model="confirmNewPassword" placeholder="请确认新密码" />
            </div>
            <button @click="changePassword" class="save-btn" :disabled="changePasswordLoading">修改密码</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
import { formatVipExpiresAt, syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'

const toast = useToast()

const vipSyncTick = ref(0)

// 获取用户信息（vipSyncTick 用于在歌单接口合并 VIP 后触发重读 localStorage）
const user = computed(() => {
  vipSyncTick.value
  const userStr = localStorage.getItem('user');
  if (!userStr || userStr === 'undefined' || userStr === 'null') {
    return null;
  }
  try {
    return JSON.parse(userStr);
  } catch (e) {
    console.error('解析用户信息失败:', e);
    return null;
  }
})

// 检查用户是否登录
const isLoggedIn = computed(() => {
  return localStorage.getItem('userToken') !== null;
})

// 如果用户未登录，重定向到登录页
if (!isLoggedIn.value || !user.value) {
  window.location.href = '/login';
}

const bumpUserFromStorage = () => {
  vipSyncTick.value++
}

onMounted(async () => {
  window.addEventListener(USER_VIP_SYNC_EVENT, bumpUserFromStorage)
  await syncUserVipFromPlaylistsApi()
  bumpUserFromStorage()
})

onUnmounted(() => {
  window.removeEventListener(USER_VIP_SYNC_EVENT, bumpUserFromStorage)
})

const userAvatar = computed(() => {
  // 使用用户 ID 获取头像
  const userId = user.value ? user.value.id : 'default';
  return `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`;
})

const activeTab = ref('profile')
const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const changePasswordLoading = ref(false)

const tabs = [
  { key: 'profile', label: '个人信息' },
  { key: 'security', label: '安全设置' }
]

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '未知';
  const date = new Date(dateString);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
}

// 切换标签页
const changeTab = (tabKey) => {
  activeTab.value = tabKey
}

// 处理头像上传
const handleAvatarUpload = (event) => {
  const file = event.target.files[0];
  if (!file) return;
  
  // 这里可以实现上传头像的逻辑
  console.log('选择的头像文件:', file.name);
  toast.info('头像上传功能将在后续版本中实现');
}

// 处理头像加载错误
const handleAvatarError = (event) => {
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="150" height="150" viewBox="0 0 150 150"><rect width="150" height="150" fill="%236a5acd"/><text x="75" y="95" font-family="Arial" font-size="24" fill="white" text-anchor="middle">U</text></svg>';
}

// 修改密码
const changePassword = async () => {
  if (!currentPassword.value || !newPassword.value || !confirmNewPassword.value) {
    toast.error('请填写所有密码字段');
    return;
  }
  
  if (newPassword.value !== confirmNewPassword.value) {
    toast.error('新密码与确认密码不一致');
    return;
  }
  
  if (newPassword.value.length < 6) {
    toast.error('新密码长度不能少于6位');
    return;
  }
  
  changePasswordLoading.value = true;
  try {
    // 这里可以实现修改密码的API调用
    console.log('修改密码请求:', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value
    });
    
    toast.info('修改密码功能将在后续版本中实现');
  } catch (error) {
    console.error('修改密码失败:', error);
    toast.error('修改密码失败，请稍后重试');
  } finally {
    changePasswordLoading.value = false;
  }
}
</script>

<style scoped>
.profile-container {
  min-height: calc(100vh - 200px);
  padding: 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.profile-card {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 30px;
  width: 100%;
  max-width: 800px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.profile-card::before {
  content: '';
  position: absolute;
  top: -10px;
  left: -10px;
  right: -10px;
  bottom: -10px;
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff, #ff9ec0);
  background-size: 400%;
  border-radius: 25px;
  z-index: -1;
  filter: blur(20px);
  opacity: 0.6;
  animation: gradientShift 10s ease infinite;
}

@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  margin-bottom: 30px;
}

.avatar-section {
  text-align: center;
}

.profile-avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
}

.upload-avatar-btn {
  margin-top: 15px;
}

.upload-avatar-btn input[type="file"] {
  display: none;
}

.upload-avatar-btn label {
  display: inline-block;
  padding: 8px 16px;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(147, 112, 219, 0.8));
  color: white;
  border-radius: 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
}

.upload-avatar-btn label:hover {
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.95), rgba(122, 91, 192, 0.95));
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(106, 90, 205, 0.6);
}

.user-info .username {
  margin: 0;
  font-size: 1.8rem;
  color: #6a5acd;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
  background: linear-gradient(45deg, #ff9ec0, #6a5acd, #84ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.user-info .username .vip-badge {
  -webkit-text-fill-color: initial;
  background: linear-gradient(135deg, #ffe082, #ffb300);
  color: #3d2a00;
  font-size: 0.55rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 4px 10px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(255, 179, 0, 0.35);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.user-info .email {
  margin: 10px 0;
  font-size: 1.1rem;
  color: #6a5acd;
}

.user-info .join-date {
  margin: 5px 0 0;
  font-size: 0.9rem;
  color: #9370db;
}

.profile-content {
  display: flex;
  flex-direction: column;
}

.profile-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 20px;
  cursor: pointer;
  font-size: 1rem;
  color: #6a5acd;
  transition: all 0.3s ease;
}

.tab-btn:hover {
  background: rgba(255, 255, 255, 0.4);
}

.tab-btn.active {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(147, 112, 219, 0.8));
  color: white;
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
}

.tab-content {
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 15px;
  padding: 25px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.tab-panel h3 {
  margin-top: 0;
  margin-bottom: 20px;
  color: #6a5acd;
  font-size: 1.4rem;
}

.info-item {
  display: flex;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.info-item label {
  font-weight: bold;
  color: #6a5acd;
  width: 120px;
  flex-shrink: 0;
}

.info-item span {
  flex-grow: 1;
  color: #5c4b7b;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
  color: #6a5acd;
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  border: none;
  border-radius: 30px;
  font-size: 1rem;
  outline: none;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: all 0.3s ease;
  color: #333;
}

.form-group input::placeholder {
  color: rgba(92, 75, 123, 0.6);
}

.form-group input:focus {
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 8px 32px rgba(106, 90, 205, 0.3);
  background: rgba(255, 255, 255, 0.35);
}

.save-btn {
  padding: 12px 25px;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(147, 112, 219, 0.8));
  color: white;
  border: none;
  border-radius: 30px;
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
}

.save-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.95), rgba(122, 91, 192, 0.95));
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(106, 90, 205, 0.6);
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }
  
  .profile-card {
    padding: 20px;
    margin: 10px;
  }
  
  .info-item {
    flex-direction: column;
  }
  
  .info-item label {
    margin-bottom: 5px;
  }
}
</style>