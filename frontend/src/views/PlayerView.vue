<template>
  <div class="player-page" :class="{ 'player-page--has-banner': isMobile && showBanner }">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <!-- 移动设备下载提示横幅 -->
    <div v-if="isMobile && showBanner" class="mobile-download-banner">
      <div class="banner-content">
        <span class="banner-text">下载 APP 体验更好</span>
        <a href="/download" class="banner-btn">立即下载</a>
        <button type="button" class="banner-close" aria-label="关闭" @click="closeBanner">×</button>
      </div>
    </div>

    <div v-if="!currentMusic" class="shell shell--state" aria-busy="true">
      <div class="state state--loading">
        <div class="state__spinner" aria-hidden="true" />
        <p class="state__text">加载曲目中…</p>
      </div>
    </div>

    <main v-else class="shell" aria-labelledby="track-title">
      <div class="layout">
        <section class="panel panel--meta" aria-label="曲目信息">
          <div class="meta-card">
            <div class="cover-wrap">
              <img
                :src="getCoverUrl(currentMusic.id)"
                :alt="currentMusic.title"
                class="cover"
                width="320"
                height="320"
                @error="handleImageError"
              />
            </div>

            <div class="track-head">
              <h1 id="track-title" class="track-title">{{ currentMusic.title }}</h1>
              <p class="track-artist">{{ currentMusic.artist }}</p>
              <p v-if="currentMusic.album" class="track-album">{{ currentMusic.album }}</p>
              <p v-if="currentMusic.duration" class="track-duration">
                时长 {{ formatDuration(currentMusic.duration) }}
              </p>
            </div>

            <div class="actions-primary">
              <button type="button" class="btn btn--play" @click="playMusic">
                播放
              </button>
            </div>

            <div class="actions-row">
              <button
                type="button"
                class="btn btn--ghost btn--hide-sm"
                :class="{ 'btn--on': isFavorite(currentMusic?.id) }"
                @click="toggleFavorite"
              >
                {{ isFavorite(currentMusic?.id) ? '已收藏' : '收藏' }}
              </button>
              <button type="button" class="btn btn--ghost btn--hide-sm" @click="downloadMusic">下载</button>
              <button
                type="button"
                class="btn btn--ghost btn--accent"
                :disabled="videoRenderBusy"
                @click="openVideoRenderDialog"
              >
                {{ videoRenderBusy ? '生成中…' : '分享视频' }}
              </button>
            </div>

            <p v-if="isLoggedIn()" class="clip-hint">
              <template v-if="userIsVip">会员：整首横屏成片，无水印、不限次数</template>
              <template v-else>
                免费：30 秒横屏成片（含水印），每日 10 次 ·
                <router-link to="/vip">开通会员</router-link>
              </template>
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
        </section>

        <section class="panel panel--lyrics" aria-label="歌词">
          <header class="lyrics-head">
            <h2 class="lyrics-title">歌词</h2>
            <p v-if="parsedLyrics.length" class="lyrics-sub">{{ parsedLyrics.length }} 行</p>
          </header>

          <div v-if="parsedLyrics.length > 0" class="lyrics-shell">
            <div class="lyrics-scroll" ref="lyricsContent">
              <div
                v-for="(line, index) in parsedLyrics"
                :key="index"
                class="lyric-line"
                :class="getLyricLineClass(index)"
              >
                <div class="lyric-text">{{ line.text }}</div>
                <div v-if="line.translation" class="lyric-translation">{{ line.translation }}</div>
              </div>
            </div>
          </div>
          <div v-else class="lyrics-empty">
            <p>暂无歌词</p>
            <p class="lyrics-empty-hint">播放时可在底栏播放器查看音频进度</p>
          </div>
        </section>
      </div>
    </main>

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
      currentMusic.value = data.data
      syncPlayStateFromStorage()
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

/** 进入详情或外部切歌后，与 localStorage 对齐本页「是否正在播当前曲」 */
const syncPlayStateFromStorage = () => {
  try {
    const playing = JSON.parse(localStorage.getItem('currentPlayingMusic') || 'null')
    const state = JSON.parse(localStorage.getItem('globalPlayerState') || 'null')
    if (playing && currentMusic.value && playing.id === currentMusic.value.id && state) {
      isPlaying.value = !!state.isPlaying
      currentTime.value = state.currentTime ?? 0
      duration.value = state.duration ?? currentMusic.value.duration ?? 0
    } else {
      isPlaying.value = false
      currentTime.value = 0
      duration.value = currentMusic.value?.duration || 0
    }
  } catch {
    isPlaying.value = false
  }
}

// 监听全局播放器状态变化
const handlePlayerStateChange = (e) => {
  const state = e.detail
  const currentPlayingMusic = JSON.parse(localStorage.getItem('currentPlayingMusic') || 'null')
  if (!currentMusic.value) return
  if (currentPlayingMusic && currentPlayingMusic.id === currentMusic.value.id) {
    isPlaying.value = state.isPlaying
    currentTime.value = state.currentTime
    duration.value = state.duration
    updateActiveLyric()
  } else {
    isPlaying.value = false
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
      } else if (currentMusic.value) {
        isPlaying.value = false
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
/* Appended by refactor script — will be merged into PlayerView.vue */
.player-page {
  --text: rgba(255, 255, 255, 0.92);
  --muted: rgba(255, 255, 255, 0.62);
  --faint: rgba(255, 255, 255, 0.42);
  --line: rgba(255, 255, 255, 0.1);
  --card: rgba(255, 255, 255, 0.06);
  --card2: rgba(255, 255, 255, 0.09);
  --accent: #8b5cf6;
  --accent2: #22d3ee;
  --accent3: #34d399;
  --radius: 18px;
  --radius-lg: 22px;
  --ease: cubic-bezier(0.22, 1, 0.36, 1);
  --shadow: 0 24px 80px rgba(0, 0, 0, 0.45);

  position: relative;
  min-height: 100vh;
  padding-top: env(safe-area-inset-top, 0px);
  color: var(--text);
  background: transparent;
}

.ambient {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.ambient__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.5;
  animation: blobFloat 22s var(--ease) infinite;
}

.ambient__blob--a {
  width: 420px;
  height: 420px;
  background: rgba(139, 92, 246, 0.42);
  top: -140px;
  left: -120px;
}

.ambient__blob--b {
  width: 360px;
  height: 360px;
  background: rgba(34, 211, 238, 0.24);
  bottom: -80px;
  right: -100px;
  animation-delay: -7s;
}

.ambient__blob--c {
  width: 280px;
  height: 280px;
  background: rgba(52, 211, 153, 0.18);
  top: 42%;
  left: 38%;
  animation-delay: -12s;
}

.ambient__grid {
  position: absolute;
  inset: 0;
  opacity: 0.32;
  background-image: linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 80% 60% at 50% 20%, black, transparent);
  animation: gridBreathe 10s ease-in-out infinite;
}

@keyframes gridBreathe {
  0%,
  100% {
    opacity: 0.26;
  }
  50% {
    opacity: 0.4;
  }
}

@keyframes blobFloat {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(20px, -14px) scale(1.04);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ambient__blob,
  .ambient__grid {
    animation: none;
  }
}

.shell {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: clamp(16px, 3vw, 28px) clamp(14px, 3.5vw, 28px) clamp(28px, 5vw, 56px);
}

.shell--state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 36px 28px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: var(--card);
  box-shadow: var(--shadow);
}

.state--loading {
  animation: statePulse 2.4s ease-in-out infinite;
}

@keyframes statePulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(34, 211, 238, 0);
  }
  50% {
    box-shadow: 0 0 40px 2px rgba(34, 211, 238, 0.08);
  }
}

@media (prefers-reduced-motion: reduce) {
  .state--loading {
    animation: none;
  }
}

.state__spinner {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--accent2);
  animation: spin 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .state__spinner {
    animation: none;
    border-color: rgba(34, 211, 238, 0.35);
  }
}

.state__text {
  margin: 0;
  font-size: 0.95rem;
  color: var(--muted);
}

.layout {
  display: grid;
  grid-template-columns: minmax(280px, 400px) minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: clamp(18px, 3vw, 28px);
  align-items: start;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  overflow: hidden;
}

.panel--lyrics {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.panel--meta {
  min-height: 0;
}

.meta-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  padding: clamp(20px, 3vw, 28px);
}

.cover-wrap {
  position: relative;
  width: fit-content;
  max-width: 100%;
  margin: 0 auto 20px;
}

.cover {
  display: block;
  width: min(280px, 100%);
  height: auto;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--radius);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.45);
}

.track-head {
  text-align: center;
  margin-bottom: 20px;
}

.track-title {
  margin: 0 0 8px;
  font-size: clamp(1.35rem, 3vw, 1.75rem);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.2;
  color: var(--text);
}

.track-artist {
  margin: 0;
  font-size: 1rem;
  color: var(--accent2);
  font-weight: 600;
}

.track-album,
.track-duration {
  margin: 8px 0 0;
  font-size: 0.88rem;
  color: var(--muted);
}

.actions-primary {
  margin-bottom: 14px;
}

.actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.btn {
  font-family: inherit;
  border: none;
  cursor: pointer;
  transition:
    transform 0.2s var(--ease),
    box-shadow 0.2s var(--ease),
    background 0.2s var(--ease),
    border-color 0.2s var(--ease);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--play {
  width: 100%;
  padding: 14px 22px;
  border-radius: 999px;
  font-size: 1rem;
  font-weight: 700;
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
  box-shadow: 0 8px 28px rgba(139, 92, 246, 0.35);
}

@media (hover: hover) {
  .btn--play:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 12px 36px rgba(34, 211, 238, 0.25);
  }
}

.btn--ghost {
  padding: 10px 16px;
  border-radius: 999px;
  font-size: 0.86rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

@media (hover: hover) {
  .btn--ghost:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(139, 92, 246, 0.35);
  }
}

.btn--accent {
  border-color: rgba(251, 191, 36, 0.35);
  color: #fde68a;
}

.btn--on {
  border-color: rgba(244, 114, 182, 0.45);
  color: #fbcfe8;
  background: rgba(244, 114, 182, 0.12);
}

.clip-hint {
  margin: 16px 0 0;
  text-align: center;
  font-size: 0.82rem;
  line-height: 1.55;
  color: var(--faint);
}

.clip-hint a {
  color: var(--accent2);
  text-decoration: none;
  font-weight: 600;
}

.clip-hint a:hover {
  text-decoration: underline;
}

.clip-notice {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--radius);
  font-size: 0.85rem;
  line-height: 1.5;
}

.clip-notice p {
  margin: 0;
}

.clip-notice-meta {
  margin-top: 6px !important;
  font-size: 0.8rem;
  opacity: 0.9;
}

.clip-notice--submitted {
  background: rgba(139, 92, 246, 0.12);
  border: 1px solid rgba(139, 92, 246, 0.28);
  color: rgba(255, 255, 255, 0.88);
}

.clip-notice--ready {
  background: rgba(52, 211, 153, 0.12);
  border: 1px solid rgba(52, 211, 153, 0.35);
  color: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.clip-download-inline {
  padding: 8px 16px;
  border: none;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.82rem;
  cursor: pointer;
  color: #0c0a14;
  background: linear-gradient(135deg, var(--accent2), var(--accent3));
}

.lyrics-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--line);
}

.lyrics-title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.lyrics-sub {
  margin: 0;
  font-size: 0.78rem;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}

.lyrics-shell {
  flex: 1;
  min-height: 0;
  padding: 8px 12px 16px;
}

.lyrics-scroll {
  height: min(630vh, 560px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 12px 24px;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: rgba(139, 92, 246, 0.5) rgba(255, 255, 255, 0.06);
  mask-image: linear-gradient(180deg, transparent, black 12px, black calc(100% - 12px), transparent);
}

.lyrics-scroll::-webkit-scrollbar {
  width: 8px;
}

.lyrics-scroll::-webkit-scrollbar-track {
  margin: 10px 0;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 999px;
}

.lyrics-scroll::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, rgba(139, 92, 246, 0.55), rgba(34, 211, 238, 0.4));
  border-radius: 999px;
  border: 2px solid rgba(12, 10, 20, 0.4);
  background-clip: padding-box;
}

.lyrics-scroll::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, rgba(167, 139, 250, 0.7), rgba(34, 211, 238, 0.55));
  background-clip: padding-box;
}


.lyrics-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 24px;
  color: var(--muted);
  text-align: center;
}

.lyrics-empty p {
  margin: 0;
}

.lyrics-empty-hint {
  font-size: 0.82rem;
  color: var(--faint);
}

.lyric-line {
  color: rgba(255, 255, 255, 0.38);
  font-size: 0.82rem;
  padding: 10px 8px;
  text-align: center;
  transition:
    color 0.25s var(--ease),
    transform 0.25s var(--ease),
    opacity 0.25s var(--ease);
  line-height: 1.55;
  max-width: 42rem;
  margin: 0 auto;
}

.lyric-text {
  display: block;
}

.lyric-translation {
  display: block;
  margin-top: 4px;
  font-size: 0.78em;
  opacity: 0.85;
}

.lyric-line.before {
  opacity: 0.55;
  transform: scale(0.98);
}

.lyric-line.active {
  color: #fff;
  font-weight: 700;
  font-size: 1.15rem;
  opacity: 1;
  transform: scale(1.02);
  text-shadow:
    0 0 20px rgba(34, 211, 238, 0.45),
    0 0 36px rgba(139, 92, 246, 0.35);
}

.lyric-line.active .lyric-translation {
  color: rgba(255, 255, 255, 0.82);
}

.mobile-download-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: linear-gradient(135deg, rgba(30, 27, 50, 0.95), rgba(15, 16, 32, 0.98));
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}

.banner-content {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 11px 16px;
  max-width: 1180px;
  margin: 0 auto;
  gap: 10px;
}

.banner-text {
  color: rgba(255, 255, 255, 0.88);
  font-weight: 600;
  font-size: 0.88rem;
}

.banner-btn {
  background: linear-gradient(135deg, var(--accent), #6366f1);
  color: #fff;
  text-decoration: none;
  padding: 7px 16px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 0.82rem;
  white-space: nowrap;
}

.banner-close {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.75);
  font-size: 1.25rem;
  line-height: 1;
  cursor: pointer;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.clip-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(6, 5, 12, 0.72);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.clip-modal {
  position: relative;
  width: min(440px, 100%);
  background: linear-gradient(165deg, rgba(30, 28, 48, 0.98), rgba(18, 17, 28, 0.99));
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-lg);
  padding: 26px 22px 22px;
  box-shadow: var(--shadow);
  color: var(--text);
}

.clip-modal-close {
  position: absolute;
  top: 10px;
  right: 12px;
  border: none;
  background: none;
  font-size: 1.5rem;
  line-height: 1;
  color: var(--faint);
  cursor: pointer;
}

.clip-modal h3 {
  margin: 0 0 8px;
  color: var(--text);
  font-size: 1.15rem;
  text-align: center;
}

.clip-modal-song {
  margin: 0 0 18px;
  color: var(--muted);
  font-size: 0.88rem;
  text-align: center;
}

.clip-modal-confirm {
  text-align: left;
}

.clip-range-block {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--line);
}

.clip-range-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 0.88rem;
  color: var(--muted);
  font-weight: 600;
}

.clip-range-value {
  font-weight: 500;
  color: var(--accent2);
  font-variant-numeric: tabular-nums;
}

.clip-range-slider {
  width: 100%;
  accent-color: var(--accent);
  cursor: pointer;
}

.clip-range-slider:disabled {
  opacity: 0.45;
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
  border: 1px solid rgba(139, 92, 246, 0.4);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--accent2);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
}

.clip-preview-btn:hover:not(:disabled) {
  background: rgba(139, 92, 246, 0.15);
}

.clip-preview-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.clip-watermark-option {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 0.9rem;
  cursor: pointer;
}

.clip-watermark-option--locked {
  cursor: not-allowed;
  opacity: 0.85;
}

.clip-watermark-option input {
  width: 18px;
  height: 18px;
  accent-color: var(--accent);
}

.clip-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.clip-cancel-btn,
.clip-confirm-btn {
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  font-size: 0.88rem;
}

.clip-cancel-btn {
  background: rgba(255, 255, 255, 0.08);
  color: var(--muted);
}

.clip-confirm-btn {
  color: #0c0a14;
  background: linear-gradient(135deg, #c4b5fd, var(--accent2));
}

.clip-modal-sub {
  font-size: 0.82rem !important;
  color: var(--faint) !important;
}

audio {
  display: none;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    height: auto;
    max-height: none;
  }

  .panel--lyrics {
    min-height: min(48vh, 420px);
    max-height: min(58vh, 520px);
    overflow: hidden;
  }
}

@media (max-width: 768px) {
  .player-page--has-banner .shell {
    padding-top: 52px;
  }

  .btn--hide-sm {
    display: none !important;
  }

  .btn--play {
    padding: 16px 22px;
    font-size: 1.05rem;
  }

  .actions-row {
    flex-direction: column;
  }

  .actions-row .btn--accent {
    width: 100%;
  }
}

</style>
