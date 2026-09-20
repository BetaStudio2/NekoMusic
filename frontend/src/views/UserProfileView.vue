<template>
  <AmbientBackdrop />

  <PageShell width="narrow">
    <div class="profile">
      <!-- 头部 -->
      <header class="profile__head">
        <div class="profile__avatar-wrap">
          <img :src="userAvatar" alt="用户头像" class="profile__avatar" @error="handleAvatarError" />
          <input id="avatar-upload" type="file" accept="image/*" class="profile__file" @change="handleAvatarUpload" />
          <label for="avatar-upload" class="profile__avatar-btn" title="更换头像">
            <NIcon name="image-plus" :size="16" />
          </label>
        </div>

        <div class="profile__ident">
          <h1 class="profile__name">
            {{ user.username }}
            <RouterLink v-if="user.isVip" to="/vip" class="profile__vip">VIP</RouterLink>
          </h1>
          <p class="profile__email">{{ user.email }}</p>
          <p class="profile__joined">
            <NIcon name="calendar" :size="14" />
            加入时间 {{ formatDate(user.createdAt) }}
          </p>
        </div>
      </header>

      <!-- 标签页 -->
      <div class="tabs" role="tablist" aria-label="个人中心">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          role="tab"
          class="tabs__btn"
          :class="{ 'tabs__btn--active': activeTab === tab.key }"
          :aria-selected="activeTab === tab.key"
          @click="changeTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 个人信息 -->
      <div v-if="activeTab === 'profile'" class="panel">
        <h2 class="panel__title">个人信息</h2>

        <div class="info">
          <span class="info__label">昵称</span>
          <div class="info__value">
            <template v-if="!editingNickname">
              <span class="info__text">{{ user.username }}</span>
              <NButton size="sm" variant="secondary" icon="pencil" @click="startEditNickname">修改</NButton>
            </template>
            <template v-else>
              <NInput
                ref="nicknameInputEl"
                v-model="nicknameInput"
                class="info__edit"
                maxlength="20"
                placeholder="请输入新昵称（1-20字）"
                @keyup.enter="saveNickname"
                @keyup.esc="cancelEditNickname"
              />
              <NButton size="sm" variant="primary" :loading="savingNickname" @click="saveNickname">保存</NButton>
              <NButton size="sm" variant="ghost" :disabled="savingNickname" @click="cancelEditNickname">取消</NButton>
            </template>
          </div>
        </div>

        <div class="info">
          <span class="info__label">邮箱</span>
          <span class="info__text">{{ user.email }}</span>
        </div>

        <div class="info">
          <span class="info__label">会员状态</span>
          <span class="info__text">{{ user.isVip ? '会员' : '非会员' }}</span>
        </div>

        <div class="info">
          <span class="info__label">会员到期</span>
          <span class="info__text">{{ formatVipExpiresAt(user.vipExpiresAt) }}</span>
        </div>

        <div class="info info--last">
          <span class="info__label">注册时间</span>
          <span class="info__text">{{ formatDate(user.createdAt) }}</span>
        </div>
      </div>

      <!-- 安全设置 -->
      <div v-else class="panel">
        <h2 class="panel__title">安全设置</h2>

        <div class="field">
          <label class="field__label" for="cur-pwd">当前密码</label>
          <NInput id="cur-pwd" v-model="currentPassword" type="password" icon="lock" placeholder="请输入当前密码" />
        </div>
        <div class="field">
          <label class="field__label" for="new-pwd">新密码</label>
          <NInput id="new-pwd" v-model="newPassword" type="password" icon="key" placeholder="请输入新密码" />
        </div>
        <div class="field">
          <label class="field__label" for="cfm-pwd">确认新密码</label>
          <NInput id="cfm-pwd" v-model="confirmNewPassword" type="password" icon="key" placeholder="请确认新密码" />
        </div>

        <NButton variant="primary" icon="shield-check" :loading="changePasswordLoading" @click="changePassword">
          修改密码
        </NButton>
      </div>
    </div>
  </PageShell>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NCard, NInput, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
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
.profile {
  width: 100%;
}

/* ==================== 头部 ==================== */
.profile__head {
  display: flex;
  align-items: center;
  gap: clamp(18px, 3vw, 28px);
  padding: clamp(20px, 3vw, 28px);
  margin-bottom: var(--n-space-5);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
  box-shadow: var(--n-shadow);
}

.profile__avatar-wrap {
  position: relative;
  flex: none;
  width: clamp(88px, 16vw, 112px);
  aspect-ratio: 1;
}

.profile__avatar {
  width: 100%;
  height: 100%;
  border-radius: var(--n-radius-lg);
  object-fit: cover;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
}

.profile__file {
  display: none;
}

.profile__avatar-btn {
  position: absolute;
  right: -6px;
  bottom: -6px;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--n-radius-control);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  border: 2px solid var(--n-bg);
  cursor: pointer;
  transition: transform var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .profile__avatar-btn:hover {
    transform: translateY(-1px);
    background: var(--n-accent-strong);
  }
}

.profile__ident {
  min-width: 0;
}

.profile__name {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  margin: 0 0 var(--n-space-2);
  font-size: clamp(1.3rem, 3vw, 1.7rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
  overflow-wrap: anywhere;
}

.profile__vip {
  flex: none;
  padding: 4px 10px;
  border-radius: var(--n-radius-xs);
  background: linear-gradient(135deg, var(--n-accent-strong), var(--n-accent));
  color: var(--n-text-inverse);
  font-size: 0.66rem;
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.1em;
}

.profile__email {
  margin: 0 0 var(--n-space-1);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  overflow-wrap: anywhere;
}

.profile__joined {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

/* ==================== 标签页 ==================== */
.tabs {
  display: flex;
  gap: var(--n-space-1);
  padding: 4px;
  margin-bottom: var(--n-space-5);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
}

.tabs__btn {
  flex: 1;
  padding: 9px var(--n-space-4);
  border-radius: var(--n-radius-xs);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .tabs__btn:hover {
    color: var(--n-text);
  }
}

.tabs__btn--active {
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

/* ==================== 面板 ==================== */
.panel {
  padding: clamp(20px, 3vw, 28px);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
}

.panel__title {
  margin: 0 0 var(--n-space-5);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.01em;
}

/* ==================== 信息行 ==================== */
.info {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-3) 0;
  border-bottom: 1px solid var(--n-line-subtle);
}

.info--last {
  border-bottom: none;
  padding-bottom: 0;
}

.info__label {
  flex: none;
  width: 84px;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

.info__value {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  flex: 1;
  min-width: 0;
}

.info__text {
  color: var(--n-text);
  font-size: var(--n-text-base);
  overflow-wrap: anywhere;
}

.info__edit {
  flex: 1;
  min-width: 0;
}

/* ==================== 表单字段 ==================== */
.field {
  margin-bottom: var(--n-space-4);
}

.field__label {
  display: block;
  margin-bottom: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-medium);
}

@media (max-width: 560px) {
  .profile__head {
    flex-direction: column;
    text-align: center;
  }

  .profile__name {
    justify-content: center;
  }

  .info {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--n-space-2);
  }

  .info__label {
    width: auto;
  }

  .info__value {
    width: 100%;
  }
}
</style>
