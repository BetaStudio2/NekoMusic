<template>
  <div class="music-detail-view">
    <!-- 移动设备下载提示横幅 -->
    <div v-if="isMobile" class="mobile-download-banner">
      <div class="banner-content">
        <span class="banner-text">下载APP体验更好</span>
        <a href="/download" class="banner-btn">立即下载</a>
        <button @click="closeBanner" class="banner-close">×</button>
      </div>
    </div>

    <div class="detail-container" v-if="currentMusic">
      <div class="content-wrapper">
        <!-- 左侧：音乐详情 -->
        <div class="detail-section">
          <!-- 音乐封面 -->
          <div class="cover-section">
            <img 
              :src="getCoverUrl(currentMusic.id)" 
              :alt="currentMusic.title"
              class="music-cover"
              @error="handleImageError"
            />
          </div>
          
          <!-- 音乐信息 -->
          <div class="music-info">
            <h1 class="music-title">{{ currentMusic.title }}</h1>
            <p class="music-artist">艺术家：{{ currentMusic.artist }}</p>
            <p class="music-album" v-if="currentMusic.album">专辑：{{ currentMusic.album }}</p>
          </div>
          
          <!-- 操作按钮 -->
          <div class="action-buttons">
            <button @click="playMusic" class="play-btn">播放音乐</button>
            <button @click="toggleFavorite" class="favorite-btn" :class="{ 'is-favorite': isFavorite(currentMusic?.id) }">
              {{ isFavorite(currentMusic?.id) ? '❤️ 已收藏' : '🤍 收藏' }}
            </button>
            <button @click="downloadMusic" class="download-btn">
              下载音乐
            </button>
          </div>
        </div>
        
        <!-- 右侧：歌词显示 -->
        <div class="lyrics-section" v-if="parsedLyrics.length > 0">
          <h3>歌词</h3>
          <div class="lyrics-container">
            <div class="lyrics-content" ref="lyricsContent">
              <div 
                v-for="(line, index) in parsedLyrics" 
                :key="index" 
                class="lyric-line"
                :class="getLyricLineClass(index)"
              >
                {{ line.text }}
              </div>
            </div>
          </div>
        </div>
      </div>
      
    </div>
    
    <div v-else class="loading">
      <p>加载音乐详情中...</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
const toast = useToast()

const route = useRoute()
const currentMusic = ref(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const lyrics = ref('')
const parsedLyrics = ref([])
const lyricsContent = ref(null)
const favoriteMusicIds = ref(new Set()) // 存储收藏的音乐ID
const isMobile = ref(false)
const showBanner = ref(true)

// 用于定时器的引用
let timeUpdateInterval = null

// 检测是否是移动设备
const checkMobile = () => {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera
  return /android|ipad|iphone|ipod/i.test(userAgent)
}

// 关闭横幅
const closeBanner = () => {
  showBanner.value = false
}

// 获取音乐详情
const fetchMusicDetail = async (musicId) => {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/info/${musicId}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    })
    
    const data = await response.json()
    if (data.success) {
      currentMusic.value = data.data;
      // 加载歌词
      loadLyrics(musicId)
    } else {
      console.error('获取音乐详情失败:', data.message)
    }
  } catch (error) {
    console.error('请求音乐详情时出错:', error)
  }
}

// 获取歌词
const getLyricsUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/lyrics/${musicId}`
}

// 加载歌词
const loadLyrics = async (musicId) => {
  try {
    const response = await fetch(getLyricsUrl(musicId))
    if (response.ok) {
      const data = await response.json()
      if (data.success) {
        lyrics.value = data.data
        parseLrcLyrics(data.data)
      } else {
        lyrics.value = ''
        parsedLyrics.value = []
      }
    } else {
      lyrics.value = ''
      parsedLyrics.value = []
    }
  } catch (error) {
    console.error('加载歌词失败:', error)
    lyrics.value = ''
    parsedLyrics.value = []
  }
}

// 解析LRC歌词格式
const parseLrcLyrics = (lrcText) => {
  if (!lrcText) {
    parsedLyrics.value = []
    return
  }
  
  const lines = lrcText.split('\n')
  const parsed = []
  
  for (const line of lines) {
    // 匹配 [mm:ss.xx] 或 [mm:ss.xxx] 格式的时间标签
    const timeRegex = /\[(\d{2}):(\d{2})\.(\d{2,3})\]/g
    let match
    const text = line.replace(timeRegex, '').trim()
    
    while ((match = timeRegex.exec(line)) !== null) {
      const minutes = parseInt(match[1])
      const seconds = parseInt(match[2])
      const milliseconds = parseInt(match[3])
      // 根据毫秒长度调整（LRC格式可能使用2位或3位毫秒）
      let millisecondsDivisor
      if (milliseconds.toString().length === 2) {
        millisecondsDivisor = 100 // 两位毫秒，如 .25
      } else {
        millisecondsDivisor = 1000 // 三位毫秒，如 .250
      }
      const timeInSeconds = minutes * 60 + seconds + (milliseconds / millisecondsDivisor)
      parsed.push({
        time: timeInSeconds,
        text: text
      })
    }
  }
  
  // 按时间排序
  parsed.sort((a, b) => a.time - b.time)
  parsedLyrics.value = parsed
}

// 监听全局播放器状态变化
const handlePlayerStateChange = (e) => {
  const state = e.detail;
  // 只有在当前音乐是正在播放的音乐时才更新本地状态
  const currentPlayingMusic = JSON.parse(localStorage.getItem('currentPlayingMusic') || 'null');
  if (currentPlayingMusic && currentMusic.value && currentPlayingMusic.id === currentMusic.value.id) {
    isPlaying.value = state.isPlaying;
    currentTime.value = state.currentTime;
    duration.value = state.duration;
    
    // 更新歌词位置
    updateActiveLyric(); // 直接更新歌词高亮状态
  }
}

// 获取当前歌词索引
const getCurrentLyricIndex = () => {
  if (parsedLyrics.value.length === 0) return -1

  for (let i = parsedLyrics.value.length - 1; i >= 0; i--) {
    const lyric = parsedLyrics.value[i]
    if (currentTime.value >= lyric.time) {
      return i
    }
  }

  return -1
}

// 判断当前歌词行是否应该高亮
const isActiveLyric = (index) => {
  const currentIndex = getCurrentLyricIndex()
  return currentIndex === index
}

// 更新当前高亮歌词
const updateActiveLyric = async () => {
  // 确保DOM已更新后再执行滚动
  await nextTick();
  scrollToActiveLyric();
}

// 获取歌词行类型（active, before, after）
const getLyricLineClass = (index) => {
  const currentIndex = getCurrentLyricIndex()
  if (currentIndex === index) {
    return 'active'
  } else if (currentIndex - 1 === index || currentIndex + 1 === index) {
    // 相邻的歌词行
    return 'before'
  } else {
    // 其他歌词行
    return ''
  }
}

// 由于现在使用flex布局，移除原来的绝对定位计算函数
// 现在主要依赖CSS和滚动来定位歌词

// 滚动到当前歌词位置
const scrollToActiveLyric = () => {
  if (!lyricsContent.value) return
  
  // 查找当前激活的歌词元素
  const activeIndex = parsedLyrics.value.findIndex((_, index) => isActiveLyric(index))
  if (activeIndex === -1) return
  
  // 获取所有歌词行元素
  const lyricElements = lyricsContent.value.children
  if (activeIndex >= 0 && activeIndex < lyricElements.length) {
    const activeElement = lyricElements[activeIndex]
    
    // 计算滚动位置，使当前歌词居中
    const container = lyricsContent.value;
    const containerHeight = container.clientHeight;
    const elementHeight = activeElement.offsetHeight;
    
    // 计算容器的滚动高度，使元素居中显示
    // 需要将容器滚动到一个位置，使得当前元素位于容器的垂直中心
    const targetScrollTop = activeElement.offsetTop - (containerHeight / 2) + (elementHeight / 2);
    
    // 平滑滚动到目标位置
    container.scrollTo({
      top: targetScrollTop,
      behavior: 'smooth'
    })
  }
}



// 播放音乐 - 通过全局播放器播放
const playMusic = () => {
  if (currentMusic.value) {
    // 先获取当前播放列表，如果没有则从后端获取
    let playlist = JSON.parse(localStorage.getItem('globalPlaylist') || '[]');
    
    // 检查当前音乐是否已经在播放列表中
    const existingIndex = playlist.findIndex(item => item.id === currentMusic.value.id);
    if (existingIndex === -1) {
      // 如果当前音乐不在播放列表中，则添加到列表中
      playlist.push(currentMusic.value);
      // 保存更新后的播放列表
      localStorage.setItem('globalPlaylist', JSON.stringify(playlist));
      
      // 立即广播播放列表更新事件，确保 GlobalPlayer 组件收到通知
      const playlistEvent = new CustomEvent('playlistUpdated', {
        detail: {
          playlist: playlist
        }
      });
      window.dispatchEvent(playlistEvent);
    }
    
    // 设置当前播放的音乐到localStorage，触发全局播放器
    localStorage.setItem('currentPlayingMusic', JSON.stringify(currentMusic.value));
    
    // 立即更新播放状态为播放，并清零时间（从0.1开始）
    const state = {
      isPlaying: true,
      currentTime: 0.1,
      duration: currentMusic.value.duration || 0
    };
    localStorage.setItem('globalPlayerState', JSON.stringify(state));
    
    // 立即广播播放状态变化
    const event = new CustomEvent('playerStateChange', {
      detail: {
        isPlaying: state.isPlaying,
        currentTime: state.currentTime,
        duration: state.duration,
        currentMusic: currentMusic.value
      }
    });
    window.dispatchEvent(event);
    
    // 立即触发强制播放
    setTimeout(() => {
      window.dispatchEvent(new Event('forcePlay'));
    }, 10);
    
    // 再次确保播放器状态同步
    setTimeout(() => {
      window.dispatchEvent(new Event('forcePlay'));
    }, 100);
  }
}

// 获取用户token
const getToken = () => {
  return localStorage.getItem('userToken');
}

// 检查用户是否登录
const isLoggedIn = () => {
  return !!getToken();
}

// 检查音乐是否已收藏
const isFavorite = (musicId) => {
  return favoriteMusicIds.value.has(musicId);
}

// 切换收藏状态
const toggleFavorite = async () => {
  if (!currentMusic.value) return;
  
  if (!isLoggedIn()) {
    toast.error('请先登录');
    return;
  }
  
  const token = getToken();
  
  if (isFavorite(currentMusic.value.id)) {
    // 取消收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${currentMusic.value.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': token
        }
      });
      
      const data = await response.json();
      if (data.success) {
        favoriteMusicIds.value.delete(currentMusic.value.id);
        toast.success('取消收藏成功');
      } else {
        console.error('取消收藏失败:', data.message);
        toast.error('取消收藏失败: ' + data.message);
      }
    } catch (error) {
      console.error('取消收藏失败:', error);
      toast.error('取消收藏失败');
    }
  } else {
    // 添加收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
        method: 'POST',
        headers: {
          'Authorization': token,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ musicId: currentMusic.value.id })
      });
      
      const data = await response.json();
      if (data.success) {
        favoriteMusicIds.value.add(currentMusic.value.id);
        toast.success('收藏成功');
      } else {
        console.error('收藏失败:', data.message);
        toast.error('收藏失败: ' + data.message);
      }
    } catch (error) {
      console.error('收藏失败:', error);
      toast.error('收藏失败');
    }
  }
}

// 获取收藏列表
const fetchFavorites = async () => {
  if (!isLoggedIn()) {
    return;
  }
  
  try {
    const token = getToken();
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    });
    
    const data = await response.json();
    if (data.success) {
      // 提取所有收藏的音乐ID
      favoriteMusicIds.value = new Set(data.favorites.map(m => m.id));
    }
  } catch (error) {
    console.error('获取收藏列表失败:', error);
  }
}

// 下载音乐
const downloadMusic = async () => {
  if (currentMusic.value) {
    try {
      // 使用fetch API获取音乐文件
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.value.id}`);
      const blob = await response.blob();
      
      // 创建下载链接
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = currentMusic.value.filename || `${currentMusic.value.title}.mp3`;
      
      // 添加到DOM，点击并移除
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      
      // 释放URL对象
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('下载音乐失败:', error);
      
      // 如果fetch方法失败，回退到直接链接方法
      const link = document.createElement('a');
      link.href = `${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.value.id}`;
      link.download = currentMusic.value.filename || `${currentMusic.value.title}.mp3`;
      link.target = '_blank'; // 在新标签页中打开，而不是当前页面
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  }
}

// 格式化时长为分秒格式
const formatDuration = (duration) => {
  if (!duration || duration < 0) return '0:00'
  
  const minutes = Math.floor(duration / 60)
  const seconds = Math.floor(duration % 60)
  
  return `${minutes}:${seconds < 10 ? '0' : ''}${seconds}`
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  event.target.src = '/src/assets/default-cover.png'; // 使用默认封面
}

// 定期检查播放时间，确保歌词实时更新
const startTimer = () => {
  if (timeUpdateInterval) {
    clearInterval(timeUpdateInterval);
  }
  
  timeUpdateInterval = setInterval(() => {
    // 从localStorage获取当前播放状态
    const storedState = localStorage.getItem('globalPlayerState');
    if (storedState) {
      const state = JSON.parse(storedState);
      
      // 检查当前播放的音乐是否是本页面的音乐
      const currentPlayingMusic = JSON.parse(localStorage.getItem('currentPlayingMusic') || 'null');
      if (currentPlayingMusic && currentMusic.value && currentPlayingMusic.id === currentMusic.value.id) {
        // 更新播放时间
        const previousTime = currentTime.value;
        currentTime.value = state.currentTime;
        duration.value = state.duration;
        isPlaying.value = state.isPlaying;
        
        // 如果时间发生变化，则更新歌词高亮
        if (Math.abs(currentTime.value - previousTime) > 0.1) { // 防止过于频繁的更新
          updateActiveLyric();
        }
      }
    }
  }, 300); // 每300毫秒更新一次，平衡性能和流畅度
};

// 初始化
onMounted(async () => {
  // 检测是否是移动设备
  isMobile.value = checkMobile()

  // 监听自定义事件，以响应全局播放器的状态变化
  window.addEventListener('playerStateChange', handlePlayerStateChange)

  const musicId = route.params.id
  if (musicId) {
    await fetchMusicDetail(musicId)
    // 启动定时器以持续更新歌词
    startTimer();
  }

  // 获取收藏列表
  await fetchFavorites();
})

// 组件卸载时移除事件监听和定时器
onUnmounted(() => {
  window.removeEventListener('playerStateChange', handlePlayerStateChange)
  if (timeUpdateInterval) {
    clearInterval(timeUpdateInterval);
    timeUpdateInterval = null;
  }
})
</script>

<style scoped>
.music-detail-view {
  max-width: 1200px;
  margin: 40px auto;
  padding: 20px;
}

/* 移动设备下载横幅 */
.mobile-download-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  animation: slideDown 0.3s ease;
}

@keyframes slideDown {
  from {
    transform: translateY(-100%);
  }
  to {
    transform: translateY(0);
  }
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.banner-text {
  color: white;
  font-weight: bold;
  font-size: 1rem;
  margin-right: 15px;
}

.banner-btn {
  background: white;
  color: #667eea;
  text-decoration: none;
  padding: 8px 20px;
  border-radius: 20px;
  font-weight: bold;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.banner-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.banner-close {
  background: none;
  border: none;
  color: white;
  font-size: 1.5rem;
  font-weight: bold;
  cursor: pointer;
  padding: 0;
  margin-left: 15px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.banner-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.detail-container {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.content-wrapper {
  display: flex;
  gap: 30px;
}

.detail-section {
  flex: 1;
  min-width: 0; /* 防止flex item溢出 */
}

.cover-section {
  margin-bottom: 25px;
  text-align: center;
}

.music-cover {
  width: 250px;
  height: 250px;
  object-fit: cover;
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  border: 1px solid rgba(255, 255, 255, 0.18);
  margin: 0 auto;
}

.music-info {
  margin: 25px 0;
  text-align: left;
}

.music-title {
  font-size: 1.8rem;
  color: #5c4b7b;
  margin: 0 0 15px 0;
  font-weight: bold;
}

.music-artist,
.music-album,
.music-duration {
  font-size: 1.1rem;
  color: #6a5acd;
  margin: 8px 0;
  text-align: left;
}

.action-buttons {
  margin-top: 30px;
  display: flex;
  justify-content: center;
  gap: 20px;
}

.play-btn, .download-btn {
  padding: 12px 24px;
  border-radius: 25px;
  border: none;
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 120px;
}

.play-btn {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.9), rgba(138, 43, 226, 0.9));
  color: white;
  box-shadow: 0 4px 15px rgba(106, 90, 205, 0.4);
}

.play-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(106, 90, 205, 0.6);
}

.download-btn {
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.9), rgba(25, 118, 210, 0.9));
  color: white;
  box-shadow: 0 4px 15px rgba(76, 175, 80, 0.4);
}

.download-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(76, 175, 80, 0.6);
}

.favorite-btn {
  padding: 12px 24px;
  border-radius: 25px;
  border: none;
  font-size: 1rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 120px;
  background: linear-gradient(135deg, rgba(255, 105, 180, 0.9), rgba(255, 20, 147, 0.9));
  color: white;
  box-shadow: 0 4px 15px rgba(255, 105, 180, 0.4);
}

.favorite-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 105, 180, 0.6);
}

.favorite-btn.is-favorite {
  background: linear-gradient(135deg, rgba(255, 69, 0, 0.9), rgba(220, 20, 60, 0.9));
  box-shadow: 0 4px 15px rgba(255, 69, 0, 0.4);
}

.favorite-btn.is-favorite:hover {
  box-shadow: 0 6px 20px rgba(255, 69, 0, 0.6);
}

/* 歌词显示区域 */
.lyrics-section {
  flex: 1;
  min-width: 0; /* 防止flex item溢出 */
  display: flex;
  flex-direction: column;
}

.lyrics-section h3 {
  color: #6a5acd;
  margin-bottom: 15px;
  font-size: 1.2rem;
  align-self: center;
}

.lyrics-container {
  flex: 1;
  max-height: 500px; /* 限高 */
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow-y: hidden;
  overflow-x: hidden;
}

.lyrics-content {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  overflow-y: auto;
  position: relative;
  gap: 20px;
  padding: 20px 0;
}

.lyric-line {
  color: rgba(136, 136, 136, 0.7);
  font-size: 0.8rem;
  padding: 8px 10px;
  text-align: center;
  transition: all 0.3s ease;
  white-space: nowrap;
  z-index: 1;
  width: auto;
  max-width: 90%;
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 0;
  display: block;
  line-height: 1.5;
}

.lyric-line.active {
  color: #ffffff;
  font-weight: 700;
  font-size: 1.4rem;
  text-shadow: 0 0 10px rgba(106, 90, 205, 0.8), 0 0 20px rgba(106, 90, 205, 0.6);
  z-index: 10;
  transform: scale(1.3); /* 只放大，无其他变换 */
  transition: all 0.3s ease;
}

.lyric-line.before {
  transform: scale(0.95); /* 轻微放大 */
  opacity: 0.7;
  transition: all 0.3s ease;
}

.lyric-line.after {
  transform: scale(0.95); /* 轻微放大 */
  opacity: 0.7;
  transition: all 0.3s ease;
}

.loading {
  text-align: center;
  padding: 40px;
  color: #887bb0;
  font-size: 1.2rem;
}

/* 隐藏的音频元素 */
audio {
  display: none;
}

@media (max-width: 768px) {
  .music-detail-view {
    padding: 100px 10px 20px; /* 为横幅留出更多空间 */
    margin: 0;
  }

  .detail-container {
    padding: 15px;
    margin: 0;
    border-radius: 15px;
  }

  .content-wrapper {
    flex-direction: column;
    gap: 20px;
  }

  .detail-section {
    width: 100%;
  }

  .cover-section {
    margin-bottom: 20px;
  }

  .music-cover {
    width: 280px;
    height: 280px;
    border-radius: 12px;
  }

  .music-info {
    margin: 20px 0;
    text-align: center;
  }

  .music-title {
    font-size: 1.6rem;
    margin-bottom: 10px;
  }

  .music-artist,
  .music-album {
    font-size: 1rem;
    margin: 6px 0;
  }

  .action-buttons {
    margin-top: 25px;
    flex-direction: column;
    align-items: center;
    gap: 15px;
  }

  .play-btn {
    width: 100%;
    padding: 16px 24px;
    font-size: 1.1rem;
    min-width: auto;
  }

  /* 隐藏收藏和下载按钮 */
  .favorite-btn,
  .download-btn {
    display: none !important;
  }

  /* 歌词区域 */
  .lyrics-section {
    flex: 1;
  }

  .lyrics-section h3 {
    font-size: 1.1rem;
    margin-bottom: 10px;
  }

  .lyrics-container {
    padding: 15px;
    max-height: 350px;
  }

  .lyrics-content {
    gap: 15px;
    padding: 15px 0;
  }

  .lyric-line {
    font-size: 0.9rem;
    padding: 6px 8px;
  }

  .lyric-line.active {
    font-size: 1.1rem;
  }

  /* 下载横幅优化 */
  .mobile-download-banner {
    padding: 10px 0;
  }

  .banner-content {
    padding: 10px 15px;
  }

  .banner-text {
    font-size: 0.9rem;
    margin-right: 10px;
  }

  .banner-btn {
    padding: 6px 16px;
    font-size: 0.85rem;
  }

  .banner-close {
    width: 28px;
    height: 28px;
    font-size: 1.3rem;
    margin-left: 10px;
  }
}
</style>