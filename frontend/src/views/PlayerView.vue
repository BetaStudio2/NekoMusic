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
            <button
              @click="openVideoRenderDialog"
              class="clip-btn"
              :disabled="videoRenderBusy"
            >
              {{ videoRenderBusy ? '生成中…' : '生成分享视频' }}
            </button>
          </div>
          <p v-if="isLoggedIn()" class="clip-hint">
            <template v-if="userIsVip">会员：整首横屏成片，无水印、不限次数</template>
            <template v-else>免费：30 秒横屏成片（含水印），每日 10 次 · <router-link to="/vip">开通会员</router-link></template>
          </p>
          <div v-if="videoRenderSubmitted" class="clip-notice clip-notice--submitted">
            <p>已提交渲染，完成后将向注册邮箱发送通知并附下载链接。</p>
            <p v-if="videoRenderRemainingToday != null && !userIsVip" class="clip-notice-meta">
              今日剩余免费次数：{{ videoRenderRemainingToday }}
            </p>
          </div>
          <div v-if="videoRenderReady" class="clip-notice clip-notice--ready">
            <p>分享视频已生成，可下载 MP4。</p>
            <button type="button" class="clip-download-inline" @click="downloadRenderedVideo">下载 MP4</button>
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
                <div class="lyric-text">{{ line.text }}</div>
                <div class="lyric-translation" v-if="line.translation">{{ line.translation }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      
    </div>
    
    <div v-else class="loading">
      <p>加载音乐详情中...</p>
    </div>

    <!-- 水印确认弹窗（提交后关闭，不占据整页进度） -->
    <div v-if="videoModalOpen" class="clip-modal-backdrop" @click.self="closeVideoModal">
      <div class="clip-modal" role="dialog" aria-labelledby="clip-modal-title">
        <button type="button" class="clip-modal-close" aria-label="关闭" @click="closeVideoModal">×</button>
        <h3 id="clip-modal-title">生成分享视频</h3>
        <p class="clip-modal-song" v-if="currentMusic">{{ currentMusic.title }} · {{ currentMusic.artist }}</p>

        <div class="clip-modal-confirm">
          <label class="clip-watermark-option" :class="{ 'clip-watermark-option--locked': !userIsVip }">
            <input
              v-model="videoWatermarkChoice"
              type="checkbox"
              :disabled="!userIsVip"
            />
            <span>添加平台水印</span>
          </label>
          <div class="clip-range-block">
            <div class="clip-range-head">
              <span>成片起始</span>
              <span class="clip-range-value">{{ formatClipTime(clipStartSec) }} → {{ formatClipTime(clipEndSec) }}</span>
            </div>
            <input
              v-model.number="clipStartSec"
              type="range"
              class="clip-range-slider"
              :min="0"
              :max="maxClipStartSec"
              :step="1"
              :disabled="trackDurationSec <= 0"
              @input="onClipRangeChange"
            />
            <p class="clip-modal-sub clip-range-hint">
              <template v-if="userIsVip">会员：从所选位置渲染至歌曲结束（约 {{ formatClipTime(clipPreviewDurationSec) }}）</template>
              <template v-else>免费：所选范围内固定 30 秒成片（每日 10 次）</template>
            </p>
            <div class="clip-preview-actions">
              <button
                type="button"
                class="clip-preview-btn"
                :disabled="trackDurationSec <= 0 || clipPreviewDurationSec <= 0"
                @click="toggleClipPreview"
              >
                {{ clipPreviewPlaying ? '停止试听' : '试听所选片段' }}
              </button>
            </div>
          </div>
          <p v-if="userIsVip" class="clip-modal-sub">会员可选是否添加水印，默认无水印</p>
          <p v-else class="clip-modal-sub">免费用户须开启水印</p>
          <p class="clip-modal-sub">提交后在后台渲染，完成后将邮件通知并附下载链接</p>
          <div class="clip-modal-actions">
            <button type="button" class="clip-cancel-btn" @click="closeVideoModal">取消</button>
            <button type="button" class="clip-confirm-btn" :disabled="videoRenderBusy" @click="confirmVideoRender">
              {{ videoRenderBusy ? '提交中…' : '开始生成' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { createVideoRenderJob, fetchVideoRenderStatus, downloadVideoRenderFile } from '@/api/videoRender.js'
import { syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'
import { tryOpenMusicDetailInApp } from '@/utils/nativeAppOpen.js'
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
const userIsVip = ref(false)

const videoModalOpen = ref(false)
const videoRenderBusy = ref(false)
const videoRenderSubmitted = ref(false)
const videoRenderReady = ref(false)
const videoRenderJobId = ref('')
const videoRenderRemainingToday = ref(null)
const videoWatermarkChoice = ref(true)
const clipStartSec = ref(0)
const clipPreviewPlaying = ref(false)

const NON_VIP_CLIP_SEC = 30

// 用于定时器的引用
let timeUpdateInterval = null
let clipPreviewAudio = null
/** 试听用 blob URL，同页同曲只 fetch 一次，避免多次 Range 请求 */
const clipPreviewBlobUrlByMusicId = new Map()
let clipPreviewLoading = false

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

const loadUserVipFromStorage = () => {
  try {
    const u = JSON.parse(localStorage.getItem('user') || 'null')
    userIsVip.value = !!u?.isVip
  } catch {
    userIsVip.value = false
  }
}

const handleVipSync = () => {
  loadUserVipFromStorage()
}

const trackDurationSec = computed(() => {
  const d = Number(currentMusic.value?.duration)
  return Number.isFinite(d) && d > 0 ? Math.floor(d) : 0
})

const maxClipStartSec = computed(() => {
  const dur = trackDurationSec.value
  if (dur <= 0) return 0
  if (userIsVip.value) {
    return Math.max(0, dur - 1)
  }
  return Math.max(0, dur - NON_VIP_CLIP_SEC)
})

const clipPreviewDurationSec = computed(() => {
  const dur = trackDurationSec.value
  if (dur <= 0) return 0
  const remain = dur - clipStartSec.value
  if (remain <= 0) return 0
  if (userIsVip.value) return remain
  return Math.min(NON_VIP_CLIP_SEC, remain)
})

const clipEndSec = computed(() => clipStartSec.value + clipPreviewDurationSec.value)

const formatClipTime = (sec) => {
  const s = Math.max(0, Math.floor(Number(sec) || 0))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}

const clampClipStartSec = (value) => {
  const v = Math.floor(Number(value) || 0)
  return Math.min(Math.max(0, v), maxClipStartSec.value)
}

/** 若当前正在播放本页歌曲，从该时间点起剪；否则从 0 秒 */
const getDefaultClipStartSec = () => {
  try {
    const playing = JSON.parse(localStorage.getItem('currentPlayingMusic') || 'null')
    const state = JSON.parse(localStorage.getItem('globalPlayerState') || 'null')
    if (playing && currentMusic.value && playing.id === currentMusic.value.id && state?.currentTime > 0) {
      return Math.floor(state.currentTime)
    }
  } catch {
    /* ignore */
  }
  return 0
}

const revokeClipPreviewBlobs = () => {
  for (const url of clipPreviewBlobUrlByMusicId.values()) {
    URL.revokeObjectURL(url)
  }
  clipPreviewBlobUrlByMusicId.clear()
}

const waitAudioEvent = (audio, eventName, timeoutMs = 15000) => new Promise((resolve, reject) => {
  const timer = setTimeout(() => {
    cleanup()
    reject(new Error(`${eventName} timeout`))
  }, timeoutMs)
  const cleanup = () => {
    clearTimeout(timer)
    audio.removeEventListener(eventName, onOk)
    audio.removeEventListener('error', onErr)
  }
  const onOk = () => {
    cleanup()
    resolve()
  }
  const onErr = () => {
    cleanup()
    reject(new Error('audio error'))
  }
  audio.addEventListener(eventName, onOk, { once: true })
  audio.addEventListener('error', onErr, { once: true })
})

const ensurePreviewBlobUrl = async (musicId) => {
  const cached = clipPreviewBlobUrlByMusicId.get(musicId)
  if (cached) return cached
  const res = await fetch(`${API_CONFIG.BASE_URL}/api/music/file/${musicId}`)
  if (!res.ok) {
    throw new Error(`fetch ${res.status}`)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  clipPreviewBlobUrlByMusicId.set(musicId, url)
  return url
}

const seekPreviewAudio = (audio, startSec) => new Promise((resolve, reject) => {
  const target = Math.min(startSec, Math.max(0, (audio.duration || startSec) - 0.05))
  if (!Number.isFinite(target) || Math.abs(audio.currentTime - target) <= 0.05) {
    resolve()
    return
  }
  const timer = setTimeout(() => {
    cleanup()
    reject(new Error('seek timeout'))
  }, 10000)
  const cleanup = () => {
    clearTimeout(timer)
    audio.removeEventListener('seeked', onSeeked)
    audio.removeEventListener('error', onErr)
  }
  const onSeeked = () => {
    cleanup()
    resolve()
  }
  const onErr = () => {
    cleanup()
    reject(new Error('seek error'))
  }
  audio.addEventListener('seeked', onSeeked, { once: true })
  audio.addEventListener('error', onErr, { once: true })
  try {
    audio.currentTime = target
  } catch (e) {
    cleanup()
    reject(e)
  }
})

const preparePreviewAudio = async (musicId, startSec) => {
  const blobUrl = await ensurePreviewBlobUrl(musicId)
  const audio = new Audio()
  audio.preload = 'auto'
  audio.src = blobUrl
  if (audio.readyState < 1) {
    await waitAudioEvent(audio, 'loadedmetadata')
  }
  await seekPreviewAudio(audio, startSec)
  return audio
}

const stopClipPreview = () => {
  clipPreviewLoading = false
  clipPreviewPlaying.value = false
  if (clipPreviewAudio) {
    clipPreviewAudio.ontimeupdate = null
    clipPreviewAudio.onended = null
    clipPreviewAudio.pause()
    clipPreviewAudio.removeAttribute('src')
    clipPreviewAudio.load()
    clipPreviewAudio = null
  }
}

const onClipRangeChange = () => {
  clipStartSec.value = clampClipStartSec(clipStartSec.value)
  if (clipPreviewPlaying.value) {
    stopClipPreview()
  }
}

const toggleClipPreview = async () => {
  if (clipPreviewLoading) return
  if (clipPreviewPlaying.value) {
    stopClipPreview()
    return
  }
  if (!currentMusic.value || clipPreviewDurationSec.value <= 0) return

  stopClipPreview()
  clipPreviewLoading = true
  const start = clipStartSec.value
  const end = clipEndSec.value
  const musicId = currentMusic.value.id

  try {
    window.dispatchEvent(new Event('pauseGlobalPlayer'))
    const audio = await preparePreviewAudio(musicId, start)
    clipPreviewAudio = audio

    audio.ontimeupdate = () => {
      if (audio.currentTime >= end - 0.05) {
        stopClipPreview()
      }
    }
    audio.onended = () => stopClipPreview()

    clipPreviewPlaying.value = true
    await audio.play()
  } catch (e) {
    console.error('clip preview failed:', e)
    stopClipPreview()
    toast.error('试听失败，请稍后重试')
  } finally {
    clipPreviewLoading = false
  }
}

const closeVideoModal = () => {
  stopClipPreview()
  revokeClipPreviewBlobs()
  videoModalOpen.value = false
}

const openVideoRenderDialog = () => {
  if (!currentMusic.value || videoRenderBusy.value) return
  if (!isLoggedIn()) {
    toast.error('请先登录')
    return
  }
  videoWatermarkChoice.value = !userIsVip.value
  clipStartSec.value = clampClipStartSec(getDefaultClipStartSec())
  stopClipPreview()
  videoModalOpen.value = true
}

const checkVideoJobFromQuery = async (jobId) => {
  if (!jobId || !isLoggedIn()) return
  videoRenderJobId.value = jobId
  try {
    const data = await fetchVideoRenderStatus(jobId)
    if (data.status === 'done') {
      videoRenderReady.value = true
      videoRenderSubmitted.value = false
    } else if (data.status === 'failed') {
      toast.error(data.error || '视频渲染失败')
    } else {
      videoRenderSubmitted.value = true
      toast.info('视频正在后台渲染，完成后将邮件通知并附下载链接')
    }
  } catch (e) {
    toast.error(e.message || '查询渲染状态失败')
  }
}

const confirmVideoRender = async () => {
  if (!currentMusic.value || videoRenderBusy.value) return
  if (!userIsVip.value && !videoWatermarkChoice.value) {
    toast.error('非会员须开启水印才能生成')
    return
  }

  videoRenderBusy.value = true
  videoRenderReady.value = false

  try {
    const startSec = clampClipStartSec(clipStartSec.value)
    if (clipPreviewDurationSec.value <= 0) {
      toast.error('所选范围无效，请调整起始时间')
      return
    }
    const watermarked = userIsVip.value ? videoWatermarkChoice.value : true
    const data = await createVideoRenderJob(currentMusic.value.id, startSec, watermarked)
    videoRenderJobId.value = data.jobId || ''
    if (typeof data.remainingToday === 'number') {
      videoRenderRemainingToday.value = data.remainingToday
    }
    if (!videoRenderJobId.value) {
      throw new Error('未返回任务 ID')
    }
    videoRenderSubmitted.value = true
    videoModalOpen.value = false
    toast.success('任务已提交，完成后将邮件通知并附下载链接')
  } catch (e) {
    toast.error(e.message || '创建任务失败')
  } finally {
    videoRenderBusy.value = false
  }
}

const downloadRenderedVideo = async () => {
  if (!videoRenderJobId.value) return
  try {
    const name = `${currentMusic.value?.title || 'clip'}.mp4`.replace(/[/\\?%*:|"<>]/g, '_')
    await downloadVideoRenderFile(videoRenderJobId.value, name)
    toast.success('已开始下载')
  } catch (e) {
    toast.error(e.message || '下载失败')
  }
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

      // 从 Content-Type 响应头中提取正确的文件扩展名
      const contentType = response.headers.get('Content-Type') || 'audio/mpeg';
      const extension = mapContentTypeToExtension(contentType);

      // 创建下载链接
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = currentMusic.value.filename || `${currentMusic.value.title}.${extension}`;

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
      // 回退时尝试使用 fileFormat，如果没有则默认 mp3
      const extension = currentMusic.value.fileFormat || 'mp3';
      link.download = currentMusic.value.filename || `${currentMusic.value.title}.${extension}`;
      link.target = '_blank'; // 在新标签页中打开，而不是当前页面
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    }
  }
}

// 将 Content-Type 映射到文件扩展名
const mapContentTypeToExtension = (contentType) => {
  const type = contentType.toLowerCase();
  if (type.includes('flac')) return 'flac';
  if (type.includes('wav')) return 'wav';
  if (type.includes('ogg')) return 'ogg';
  if (type.includes('aac')) return 'aac';
  if (type.includes('m4a') || type.includes('mp4')) return 'm4a';
  if (type.includes('wma')) return 'wma';
  if (type.includes('ape')) return 'ape';
  if (type.includes('mpeg') || type.includes('mp3')) return 'mp3';
  console.warn('未知的 Content-Type:', contentType, '使用 mp3');
  return 'mp3';
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
  window.addEventListener(USER_VIP_SYNC_EVENT, handleVipSync)
  loadUserVipFromStorage()
  if (isLoggedIn()) {
    syncUserVipFromPlaylistsApi()
  }

  const musicId = route.params.id
  if (checkMobile() && musicId) {
    tryOpenMusicDetailInApp(musicId)
  }

  if (musicId) {
    await fetchMusicDetail(musicId)
    // 启动定时器以持续更新歌词
    startTimer();
  }

  // 获取收藏列表
  await fetchFavorites();

  const videoJob = route.query.videoJob
  if (videoJob) {
    await checkVideoJobFromQuery(String(videoJob))
  }
})

// 组件卸载时移除事件监听和定时器
onUnmounted(() => {
  stopClipPreview()
  revokeClipPreviewBlobs()
  window.removeEventListener('playerStateChange', handlePlayerStateChange)
  window.removeEventListener(USER_VIP_SYNC_EVENT, handleVipSync)
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
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
}

.clip-hint {
  margin: 14px 0 0;
  text-align: center;
  font-size: 0.85rem;
  color: #887bb0;
  line-height: 1.5;
}

.clip-hint a {
  color: #6a5acd;
  text-decoration: none;
  font-weight: 600;
}

.clip-hint a:hover {
  text-decoration: underline;
}

.play-btn, .download-btn, .clip-btn {
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

.clip-btn {
  background: linear-gradient(135deg, rgba(255, 152, 0, 0.92), rgba(255, 87, 34, 0.92));
  color: white;
  box-shadow: 0 4px 15px rgba(255, 152, 0, 0.35);
}

.clip-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 152, 0, 0.55);
}

.clip-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.clip-notice {
  margin-top: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 0.88rem;
  line-height: 1.5;
  text-align: left;
}

.clip-notice p {
  margin: 0;
}

.clip-notice-meta {
  margin-top: 6px !important;
  font-size: 0.82rem;
  opacity: 0.85;
}

.clip-notice--submitted {
  background: rgba(106, 90, 205, 0.1);
  border: 1px solid rgba(106, 90, 205, 0.25);
  color: #5c4b7b;
}

.clip-notice--ready {
  background: rgba(76, 175, 80, 0.12);
  border: 1px solid rgba(76, 175, 80, 0.35);
  color: #2e6b32;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.clip-download-inline {
  padding: 8px 18px;
  border: none;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
  color: white;
  background: linear-gradient(135deg, #6a5acd, #8a2be2);
}

.clip-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(20, 16, 40, 0.45);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.clip-modal {
  position: relative;
  width: min(420px, 100%);
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 28px 24px 24px;
  box-shadow: 0 16px 48px rgba(60, 40, 120, 0.25);
  text-align: center;
}

.clip-modal-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: none;
  background: none;
  font-size: 1.6rem;
  line-height: 1;
  color: #9988bb;
  cursor: pointer;
}

.clip-modal h3 {
  margin: 0 0 8px;
  color: #5c4b7b;
  font-size: 1.25rem;
}

.clip-modal-song {
  margin: 0 0 20px;
  color: #887bb0;
  font-size: 0.9rem;
}

.clip-modal-confirm {
  text-align: left;
}

.clip-range-block {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 12px;
  background: rgba(106, 90, 205, 0.06);
  border: 1px solid rgba(106, 90, 205, 0.18);
}

.clip-range-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 0.9rem;
  color: #5c4b7b;
  font-weight: 600;
}

.clip-range-value {
  font-weight: 500;
  color: #7c6aad;
  font-variant-numeric: tabular-nums;
}

.clip-range-slider {
  width: 100%;
  accent-color: #6a5acd;
  cursor: pointer;
}

.clip-range-slider:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.clip-range-hint {
  margin-top: 8px !important;
  margin-bottom: 0 !important;
}

.clip-preview-actions {
  margin-top: 12px;
}

.clip-preview-btn {
  padding: 8px 16px;
  border: 1px solid rgba(106, 90, 205, 0.35);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  color: #6a5acd;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}

.clip-preview-btn:hover:not(:disabled) {
  background: rgba(106, 90, 205, 0.12);
}

.clip-preview-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.clip-watermark-option {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(106, 90, 205, 0.08);
  border: 1px solid rgba(106, 90, 205, 0.2);
  color: #5c4b7b;
  font-size: 0.95rem;
  cursor: pointer;
}

.clip-watermark-option--locked {
  cursor: not-allowed;
  opacity: 0.92;
}

.clip-watermark-option input {
  width: 18px;
  height: 18px;
  accent-color: #6a5acd;
}

.clip-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.clip-cancel-btn,
.clip-confirm-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
}

.clip-cancel-btn {
  background: rgba(153, 136, 187, 0.2);
  color: #7766aa;
}

.clip-confirm-btn {
  color: white;
  background: linear-gradient(135deg, #6a5acd, #8a2be2);
}

.clip-modal-status p {
  margin: 0 0 8px;
  color: #5c4b7b;
}

.clip-modal-sub {
  font-size: 0.85rem !important;
  color: #9988bb !important;
}

.clip-spinner {
  width: 40px;
  height: 40px;
  margin: 0 auto 16px;
  border: 3px solid rgba(106, 90, 205, 0.2);
  border-top-color: #6a5acd;
  border-radius: 50%;
  animation: clip-spin 0.8s linear infinite;
}

@keyframes clip-spin {
  to { transform: rotate(360deg); }
}

.clip-download-btn,
.clip-retry-btn {
  margin-top: 12px;
  padding: 10px 24px;
  border: none;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  color: white;
  background: linear-gradient(135deg, #6a5acd, #8a2be2);
}

.clip-retry-btn {
  background: linear-gradient(135deg, #ff9800, #ff5722);
}

.clip-quota {
  margin: 16px 0 0;
  font-size: 0.82rem;
  color: #9988bb;
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
  white-space: normal;
  word-wrap: break-word;
  word-break: break-word;
  z-index: 1;
  width: 100%;
  max-width: 90%;
  flex-shrink: 0;
  display: block;
  line-height: 1.5;
}

.lyric-text {
  display: block;
  margin-bottom: 4px;
}

.lyric-translation {
  display: block;
  font-size: 0.75em;
  opacity: 0.8;
  color: rgba(136, 136, 136, 0.9);
}

.lyric-line.active {
  color: #ffffff;
  font-weight: 700;
  font-size: 1.4rem;
  text-shadow: 0 0 10px rgba(106, 90, 205, 0.8), 0 0 20px rgba(106, 90, 205, 0.6);
  z-index: 10;
  transform: scale(1.1); /* 减小放大比例，避免过长歌词溢出 */
  transition: all 0.3s ease;
}

.lyric-line.active .lyric-text {
  color: #ffffff;
}

.lyric-line.active .lyric-translation {
  color: rgba(255, 255, 255, 0.9);
  opacity: 1;
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

  .clip-btn {
    width: 100%;
    min-width: auto;
  }

  .clip-hint {
    font-size: 0.8rem;
    padding: 0 8px;
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