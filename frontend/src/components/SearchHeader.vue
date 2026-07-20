<template>
  <header class="search-header" :class="{ 'search-header--chrome-dark': chromeDark }">
    <div class="header-content">
      <div class="logo-container">
        <h1 class="logo" @click="goHome">Neko歌姬计划</h1>
      </div>
      <div class="search-container-wrapper">
        <div class="search-container">
          <input
            v-model="searchQuery"
            @input="handleInput"
            @keyup.enter="performSearch"
            type="text"
            placeholder="搜索音乐、艺术家或歌词"
            class="search-input"
            :disabled="isLoading"
          />
        </div>
        
        <!-- 搜索结果下拉框 -->
        <div v-if="showResults && searchResults && searchResults.length > 0" class="search-results">
          <div 
            v-for="result in searchResults" 
            :key="result.id" 
            class="result-item"
            @click="selectResult(result)"
          >
            <img 
              :src="getCoverUrl(result.id)" 
              :alt="result.title"
              class="result-cover"
              @error="handleImageError"
            />
            <div class="result-info">
              <div class="result-title">
                <span class="result-title__text">{{ result.title }}</span>
                <LrcBadge :show="!!result.lrc" />
              </div>
              <div class="result-artist">作曲：{{ result.artist }}</div>
              <div class="result-album">专辑：{{ result.album || '未知专辑' }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="auth-container">
        <div v-if="isLoggedIn" class="user-info">
          <img
            :src="userAvatar"
            alt="用户头像"
            class="user-avatar"
            title="个人中心"
            @click="goToAccount"
            @error="handleAvatarError"
          />
          <span class="username">{{ username }}</span>
          <router-link
            to="/vip"
            class="header-vip-pill"
            :class="{ 'header-vip-pill--active': user?.isVip }"
            :title="user?.isVip ? '会员已开通 · 点击查看会员中心' : '非会员 · 点击查看会员中心'"
          >
            VIP
          </router-link>
          <button @click="goToPlaylists" class="playlists-btn" title="我的歌单">我的歌单</button>
          <button @click="goToFavorites" class="favorites-btn" title="我的收藏">我的收藏</button>
          <button @click="logout" class="logout-btn">退出</button>
        </div>
        <button v-else @click="goToLogin" class="login-btn">登录</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import LrcBadge from '@/components/LrcBadge.vue'
import { syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'

defineProps({
  /** 与首页深色壳层一致，避免顶栏浅色玻璃与主内容撞色 */
  chromeDark: {
    type: Boolean,
    default: false
  }
})

const router = useRouter()
const searchQuery = ref('')
const searchResults = ref(null)
const showResults = ref(false)
const isLoading = ref(false)
let debounceTimer = null

// 用户信息响应式变量
const isLoggedIn = ref(false)
const user = ref(null)
const username = ref('')

// 初始化用户状态
const initializeUserState = () => {
  const token = localStorage.getItem('userToken')
  isLoggedIn.value = token !== null && token !== undefined
  
  const userStr = localStorage.getItem('user')
  if (!userStr || userStr === 'undefined' || userStr === 'null') {
    user.value = null
    username.value = ''
  } else {
    try {
      user.value = JSON.parse(userStr)
      username.value = user.value ? user.value.username : ''
    } catch (e) {
      console.error('解析用户信息失败:', e)
      user.value = null
      username.value = ''
    }
  }
}

// 监听storage事件，当localStorage变化时更新状态
const handleStorageChange = (event) => {
  if (event.key === 'userToken' || event.key === 'user') {
    initializeUserState()
  }
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/login')
}

// 退出登录
const logout = () => {
  const previousToken = localStorage.getItem('userToken');
  const previousUser = localStorage.getItem('user');
  
  localStorage.removeItem('userToken');
  localStorage.removeItem('user');
  
  // 触发storage事件，确保其他标签页或组件能够检测到状态变化
  if (previousToken) {
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'userToken',
      oldValue: previousToken,
      newValue: null
    }));
  }
  
  router.push('/');
}

// 跳转到收藏页面
const goToFavorites = () => {
  router.push('/favorites')
}

const goToPlaylists = () => {
  router.push('/playlists')
}

const goToAccount = () => {
  router.push('/account')
}

// 处理头像加载错误
const handleAvatarError = (event) => {
  // 如果默认头像也加载失败，使用base64编码的简单头像
  event.target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40"><rect width="40" height="40" fill="%236a5acd"/><text x="20" y="25" font-family="Arial" font-size="16" fill="white" text-anchor="middle">U</text></svg>';
}

const userAvatar = computed(() => {
  // 使用用户 ID 获取头像
  const userId = user.value ? user.value.id : 'default';
  return `${API_CONFIG.BASE_URL}/api/user/avatar/${userId}`;
})

// 防抖搜索函数 - 只获取结果，不跳转页面
const debouncedSearch = (query) => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  
  debounceTimer = setTimeout(async () => {
    if (query.trim()) {
      await fetchSearchResults(query)
    } else {
      // 如果输入框为空，清空搜索结果
      searchResults.value = null
      showResults.value = false
    }
    
    // 确保输入框保持焦点
    setTimeout(() => {
      const inputElement = document.querySelector('.search-input')
      if (inputElement && document.activeElement !== inputElement) {
        inputElement.focus()
      }
    }, 0)
  }, 500) // 500ms防抖延迟
}

// 获取搜索结果，不跳转页面
const fetchSearchResults = async (query) => {
  try {
    isLoading.value = true
    
    // 发送POST搜索请求到后端
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: query
      })
    })
    
    const data = await response.json()
    
    // 检查响应是否成功
    if (response.ok) {
      if (data.success && data.results) {
        searchResults.value = data.results
        showResults.value = true
        console.log('搜索成功:', data.results)
      } else {
        // 当后端返回null时，不显示结果框
        searchResults.value = null
        showResults.value = false
        console.log('未找到匹配结果:', data.message)
      }
    } else {
      console.error('搜索失败:', data.message || '未知错误')
      searchResults.value = null
      showResults.value = false
    }
  } catch (error) {
    console.error('搜索请求失败:', error)
    searchResults.value = null
    showResults.value = false
  } finally {
    isLoading.value = false
  }
}

// 跳转到首页
const goHome = () => {
  router.push('/')
  searchQuery.value = ''
  searchResults.value = null
  showResults.value = false
}

// 处理输入事件 - 触发防抖搜索
const handleInput = () => {
  if (searchQuery.value.trim()) {
    debouncedSearch(searchQuery.value)
  } else {
    searchResults.value = null
    showResults.value = false
  }
}

// 执行搜索
const performSearch = async () => {
  if (!searchQuery.value.trim()) return
  
  try {
    isLoading.value = true
    
    // 发送POST搜索请求到后端
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        query: searchQuery.value
      })
    })
    
    const data = await response.json()
    
    // 检查响应是否成功
    if (response.ok) {
      if (data.success && data.results) {
        searchResults.value = data.results
        showResults.value = true
        console.log('搜索成功:', data.results)
      } else {
        // 当后端返回null时，不显示结果框
        searchResults.value = null
        showResults.value = false
        console.log('未找到匹配结果:', data.message)
      }
    } else {
      console.error('搜索失败:', data.message || '未知错误')
      searchResults.value = null
      showResults.value = false
    }
  } catch (error) {
    console.error('搜索请求失败:', error)
    searchResults.value = null
    showResults.value = false
  } finally {
    isLoading.value = false
    
    // 只有在回车时才跳转页面
    router.push(`/search/${encodeURIComponent(searchQuery.value)}`)
    
    // 确保输入框保持焦点
    setTimeout(() => {
      const inputElement = document.querySelector('.search-input')
      if (inputElement && document.activeElement !== inputElement) {
        inputElement.focus()
      }
    }, 0)
  }
}

// 选择结果项
const selectResult = (result) => {
  // 设置当前播放的音乐到localStorage，触发全局播放器
  localStorage.setItem('currentPlayingMusic', JSON.stringify(result));
  
  // 点击搜索结果直接跳转到播放页面
  router.push(`/detail/${result.id}`)
  searchResults.value = null
  showResults.value = false
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  // 返回新的API端点，通过音乐ID获取封面
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  // 如果图片加载失败，使用后端API的默认图标
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`;
}

// 点击外部区域隐藏搜索结果
const handleClickOutside = (event) => {
  const searchHeader = event.target.closest('.search-header')
  if (!searchHeader) {
    showResults.value = false
  }
}

onMounted(async () => {
  initializeUserState()
  window.addEventListener('storage', handleStorageChange)
  window.addEventListener(USER_VIP_SYNC_EVENT, initializeUserState)
  document.addEventListener('click', handleClickOutside)
  await syncUserVipFromPlaylistsApi()
  initializeUserState()
})

onUnmounted(() => {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
  }
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener(USER_VIP_SYNC_EVENT, initializeUserState)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.search-header {
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid var(--line);
  background: rgba(8, 13, 19, 0.88);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.header-content {
  width: min(1400px, calc(100% - 32px));
  margin: 0 auto;
  min-height: 72px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 12px 0;
}

.logo-container {
  display: flex;
  align-items: center;
  min-width: 0;
}

.auth-container {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.login-btn,
.logout-btn,
.favorites-btn,
.playlists-btn {
  appearance: none;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text);
  border-radius: 999px;
  font-size: 0.84rem;
  font-weight: 700;
  padding: 9px 14px;
  cursor: pointer;
  transition: transform 0.18s var(--ease), border-color 0.18s var(--ease), background 0.18s var(--ease);
}

.login-btn {
  background: linear-gradient(135deg, rgba(105, 200, 223, 0.2), rgba(105, 200, 223, 0.08));
  border-color: rgba(105, 200, 223, 0.26);
  color: var(--text);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px 6px 6px;
  border: 1px solid rgba(143, 174, 198, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid rgba(143, 174, 198, 0.24);
  cursor: pointer;
}

.username {
  max-width: 110px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--muted);
  font-weight: 600;
  font-size: 0.88rem;
}

.header-vip-pill {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  padding: 7px 12px;
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  border-radius: 999px;
  text-decoration: none;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(143, 174, 198, 0.18);
}

.header-vip-pill--active {
  color: #071115;
  background: linear-gradient(135deg, #9beaff, #69c8df);
  border-color: rgba(155, 234, 255, 0.65);
}

.logout-btn {
  border-color: rgba(255, 107, 107, 0.2);
  background: rgba(255, 107, 107, 0.08);
  color: #ffd2d2;
}

.favorites-btn,
.playlists-btn {
  color: var(--muted);
}

.logo {
  margin: 0;
  font-size: clamp(1.05rem, 1.8vw, 1.35rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  color: var(--text);
  cursor: pointer;
  white-space: nowrap;
}

.search-container-wrapper {
  position: relative;
  display: flex;
  min-width: 0;
}

.search-container {
  position: relative;
  width: 100%;
}

.search-input {
  width: 100%;
  min-width: 0;
  padding: 14px 16px 14px 18px;
  border-radius: 999px;
  border: 1px solid rgba(143, 174, 198, 0.18);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text);
  font-size: 0.95rem;
  outline: none;
  transition: border-color 0.18s var(--ease), background 0.18s var(--ease), box-shadow 0.18s var(--ease);
}

.search-input::placeholder {
  color: var(--faint);
}

.search-input:focus {
  border-color: rgba(105, 200, 223, 0.45);
  background: rgba(255, 255, 255, 0.07);
  box-shadow: 0 0 0 3px rgba(105, 200, 223, 0.12);
}

.search-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-results {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  width: 100%;
  max-height: 320px;
  overflow: auto;
  border: 1px solid rgba(143, 174, 198, 0.14);
  border-radius: 18px;
  background: rgba(8, 13, 19, 0.98);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.42);
}

.result-item {
  padding: 12px 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(143, 174, 198, 0.08);
  transition: background 0.18s var(--ease);
}

.result-item:last-child {
  border-bottom: none;
}

.result-item:hover {
  background: rgba(105, 200, 223, 0.06);
}

.result-cover {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.result-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--text);
  font-size: 0.92rem;
  font-weight: 700;
}

.result-title__text,
.result-artist,
.result-album {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-artist {
  color: var(--accent-strong);
  font-size: 0.82rem;
}

.result-album {
  color: var(--faint);
  font-size: 0.78rem;
}

@media (hover: hover) {
  .login-btn:hover,
  .logout-btn:hover,
  .favorites-btn:hover,
  .playlists-btn:hover,
  .header-vip-pill:hover {
    transform: translateY(-1px);
    border-color: rgba(105, 200, 223, 0.34);
    background: rgba(255, 255, 255, 0.08);
  }
}

.search-header--chrome-dark {
  background: rgba(7, 11, 16, 0.9);
}

.search-header--chrome-dark .login-btn,
.search-header--chrome-dark .logout-btn,
.search-header--chrome-dark .favorites-btn,
.search-header--chrome-dark .playlists-btn {
  background: rgba(255, 255, 255, 0.04);
}

.search-header--chrome-dark .search-results {
  background: rgba(6, 10, 15, 0.99);
}

@media (max-width: 1024px) {
  .header-content {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .auth-container {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .header-content {
    width: min(100%, calc(100% - 20px));
    min-height: auto;
    padding: 14px 0 12px;
  }

  .search-header {
    position: sticky;
  }

  .logo {
    font-size: 1rem;
  }

  .search-input {
    padding: 13px 16px;
  }

  .auth-container {
    gap: 6px;
  }

  .user-info {
    flex-wrap: wrap;
    border-radius: 18px;
    padding-right: 8px;
  }

  .username {
    max-width: 84px;
  }

  .login-btn,
  .logout-btn,
  .favorites-btn,
  .playlists-btn {
    padding: 8px 12px;
  }
}
</style>
