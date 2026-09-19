<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>
    <main class="shell profile-shell">
      <div class="panel profile-card">
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
              <label>昵称:</label>
              <div class="info-value">
                <template v-if="!editingNickname">
                  <span>{{ user.username }}</span>
                  <button type="button" class="edit-btn" @click="startEditNickname">修改</button>
                </template>
                <template v-else>
                  <input
                    ref="nicknameInputEl"
                    v-model="nicknameInput"
                    class="nickname-input"
                    type="text"
                    maxlength="20"
                    placeholder="请输入新昵称（1-20字）"
                    @keyup.enter="saveNickname"
                    @keyup.esc="cancelEditNickname"
                  />
                  <button
                    type="button"
                    class="save-btn inline-btn"
                    :disabled="savingNickname"
                    @click="saveNickname"
                  >
                    {{ savingNickname ? '保存中…' : '保存' }}
                  </button>
                  <button
                    type="button"
                    class="cancel-btn inline-btn"
                    :disabled="savingNickname"
                    @click="cancelEditNickname"
                  >
                    取消
                  </button>
                </template>
              </div>
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
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
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

// 昵称修改
const editingNickname = ref(false)
const nicknameInput = ref('')
const nicknameInputEl = ref(null)
const savingNickname = ref(false)

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

// 开始修改昵称
const startEditNickname = async () => {
  nicknameInput.value = user.value?.username || ''
  editingNickname.value = true
  await nextTick()
  nicknameInputEl.value?.focus()
  nicknameInputEl.value?.select?.()
}

// 取消修改昵称
const cancelEditNickname = () => {
  editingNickname.value = false
  nicknameInput.value = ''
}

// 保存昵称
const saveNickname = async () => {
  const nickname = nicknameInput.value.trim()

  if (!nickname) {
    toast.error('昵称不能为空')
    return
  }
  if (nickname.length > 20) {
    toast.error('昵称长度需在1-20之间')
    return
  }
  if (nickname === user.value?.username) {
    toast.info('昵称没有变化')
    cancelEditNickname()
    return
  }

  const token = localStorage.getItem('userToken')
  if (!token) {
    toast.error('登录状态已失效，请重新登录')
    return
  }

  savingNickname.value = true
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/nickname/change`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ nickname })
    })

    const data = await response.json().catch(() => ({}))
    if (response.ok && data.success) {
      const savedNickname = data.data?.nickname || nickname
      // 同步更新本地缓存的用户信息，页面头部/个人信息即时刷新
      const previousUser = localStorage.getItem('user')
      try {
        const stored = localStorage.getItem('user')
        if (stored) {
          const parsed = JSON.parse(stored)
          parsed.username = savedNickname
          localStorage.setItem('user', JSON.stringify(parsed))
        }
      } catch (e) {
        console.error('更新本地用户信息失败:', e)
      }
      bumpUserFromStorage()
      // 通知导航栏等监听 storage 的组件更新昵称显示
      window.dispatchEvent(new StorageEvent('storage', {
        key: 'user',
        oldValue: previousUser,
        newValue: localStorage.getItem('user')
      }))
      toast.success(data.message || '昵称修改成功')
      cancelEditNickname()
    } else {
      toast.error(data.message || '昵称修改失败')
    }
  } catch (error) {
    console.error('修改昵称失败:', error)
    toast.error('昵称修改失败，请稍后重试')
  } finally {
    savingNickname.value = false
  }
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
.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(105, 200, 223, 0.14), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
}

.profile-card {
  width: 100%;
  max-width: 800px;
  padding: clamp(22px, 3vw, 32px);
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 30px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 24px;
}

.avatar-section {
  text-align: center;
}

.profile-avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.14);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
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
  background: linear-gradient(135deg, #9beaff, var(--accent2));
  color: #0c0a14;
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.86rem;
  font-weight: 700;
  transition: filter 0.15s var(--ease);
  box-shadow: 0 6px 18px rgba(105, 200, 223, 0.28);
}

.upload-avatar-btn label:hover {
  filter: brightness(1.06);
}

.user-info .username {
  margin: 0;
  font-size: clamp(1.35rem, 3vw, 1.75rem);
  font-weight: 800;
  background: linear-gradient(120deg, #d7edf5, #c8f7ff, #9beaff);
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
  background: linear-gradient(135deg, #9beaff, #69c8df);
  color: #061014;
  font-size: 0.55rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 4px 10px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(105, 200, 223, 0.35);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.user-info .email {
  margin: 10px 0;
  font-size: 1rem;
  color: var(--muted);
}

.user-info .join-date {
  margin: 5px 0 0;
  font-size: 0.88rem;
  color: var(--faint);
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
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--muted);
  transition: background 0.15s var(--ease), border-color 0.15s var(--ease), color 0.15s var(--ease);
}

.tab-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text);
}

.tab-btn.active {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.45), rgba(105, 200, 223, 0.2));
  border-color: rgba(105, 200, 223, 0.45);
  color: var(--text);
  box-shadow: 0 6px 20px rgba(105, 200, 223, 0.25);
}

.tab-content {
  background: rgba(0, 0, 0, 0.18);
  border-radius: var(--radius);
  padding: 22px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.tab-panel h3 {
  margin-top: 0;
  margin-bottom: 18px;
  color: var(--text);
  font-size: 1.15rem;
  font-weight: 800;
}

.info-item {
  display: flex;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.info-item label {
  font-weight: 700;
  color: var(--accent2);
  width: 120px;
  flex-shrink: 0;
}

.info-item span {
  flex-grow: 1;
  color: var(--muted);
}

.info-item .info-value {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.info-item .info-value span {
  flex: 1;
  min-width: 0;
  color: var(--muted);
  word-break: break-all;
}

.nickname-input {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 9px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  color: var(--text);
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.nickname-input::placeholder {
  color: var(--faint);
}

.nickname-input:focus {
  border-color: rgba(105, 200, 223, 0.45);
  box-shadow: 0 0 0 3px rgba(105, 200, 223, 0.12);
}

.edit-btn {
  font-family: inherit;
  flex-shrink: 0;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid rgba(105, 200, 223, 0.35);
  background: rgba(105, 200, 223, 0.12);
  color: var(--accent2);
  font-size: 0.82rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s var(--ease), border-color 0.15s var(--ease);
}

.edit-btn:hover {
  background: rgba(105, 200, 223, 0.2);
  border-color: rgba(105, 200, 223, 0.5);
}

.inline-btn {
  flex-shrink: 0;
  margin-top: 0;
  padding: 9px 18px;
  font-size: 0.84rem;
}

.cancel-btn {
  font-family: inherit;
  flex-shrink: 0;
  padding: 9px 18px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.06);
  color: var(--muted);
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s var(--ease), color 0.15s var(--ease);
}

.cancel-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text);
}

.cancel-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 700;
  font-size: 0.88rem;
  color: rgba(255, 255, 255, 0.88);
}

.form-group input {
  width: 100%;
  box-sizing: border-box;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.22);
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
  color: var(--text);
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}

.form-group input::placeholder {
  color: var(--faint);
}

.form-group input:focus {
  border-color: rgba(105, 200, 223, 0.45);
  box-shadow: 0 0 0 3px rgba(105, 200, 223, 0.12);
}

.save-btn {
  font-family: inherit;
  margin-top: 8px;
  padding: 11px 22px;
  background: linear-gradient(135deg, #9beaff, var(--accent2));
  color: #0c0a14;
  border: none;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(105, 200, 223, 0.28);
  transition: filter 0.15s var(--ease);
}

.save-btn:hover:not(:disabled) {
  filter: brightness(1.05);
}

.save-btn:disabled {
  opacity: 0.55;
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
