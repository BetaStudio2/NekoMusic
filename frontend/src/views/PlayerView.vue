<template>
  <div class="np-page">
    <!-- 模糊封面底 -->
    <div
      v-if="currentMusic"
      class="np-bg"
      :style="{ backgroundImage: `url(${getCoverUrl(currentMusic.id)})` }"
      aria-hidden="true"
    />
    <div class="np-scrim" aria-hidden="true" />

    <!-- 移动设备下载提示 -->
    <div v-if="isMobile && showBanner" class="np-banner">
      <span>下载 APP 体验更好</span>
      <RouterLink to="/download" class="np-banner__btn">立即下载</RouterLink>
      <button type="button" class="np-banner__close" aria-label="关闭" @click="closeBanner">
        <NIcon name="close" :size="16" />
      </button>
    </div>

    <PageShell width="default">
      <!-- 加载 -->
      <div v-if="!currentMusic" class="np-state">
        <NSpinner :size="28" />
        <p>加载曲目中…</p>
      </div>

      <div v-else class="np" aria-labelledby="track-title">
        <!-- 左：封面 + 信息 + 操作 -->
        <section class="np__aside">
          <div class="np__cover">
            <img
              :src="getCoverUrl(currentMusic.id)"
              :alt="currentMusic.title"
              @error="handleImageError"
            />
            <button
              type="button"
              class="np__play"
              :aria-label="`播放 ${currentMusic.title}`"
              @click="playMusic"
            >
              <NIcon name="play" :size="26" />
            </button>
          </div>

          <!-- 实时频谱（数据来自 GlobalPlayer 的音频元素） -->
          <SpectrumCanvas
            class="np__spectrum"
            :bars="40"
            :height="64"
            :active="isPlaying"
          />

          <div class="np__meta">
            <h1 id="track-title" class="np__title">{{ currentMusic.title }}</h1>
            <p class="np__artist">{{ currentMusic.artist }}</p>
            <p v-if="currentMusic.album" class="np__album">{{ currentMusic.album }}</p>
            <p v-if="currentMusic.duration" class="np__dur">
              <NIcon name="clock" :size="14" />
              {{ formatDuration(currentMusic.duration) }}
            </p>
          </div>

          <div class="np__actions">
            <NButton variant="primary" icon="play" @click="playMusic">播放</NButton>
            <NButton
              :variant="isFavorite(currentMusic?.id) ? 'primary' : 'secondary'"
              icon="heart"
              @click="toggleFavorite"
            >
              {{ isFavorite(currentMusic?.id) ? '已收藏' : '收藏' }}
            </NButton>
            <NButton variant="secondary" icon="download" @click="downloadMusic">下载</NButton>
            <NButton
              variant="outline"
              icon="video"
              :disabled="videoRenderBusy"
              @click="openVideoRenderDialog"
            >
              {{ videoRenderBusy ? '生成中…' : '分享视频' }}
            </NButton>
          </div>

          <p v-if="isLoggedIn()" class="np__hint">
            <NIcon name="sparkles" :size="14" />
            <template v-if="userIsVip">会员：整首横屏成片，无水印、不限次数</template>
            <template v-else>
              免费：30 秒横屏成片（含水印），每日 10 次 ·
              <RouterLink to="/vip">开通会员</RouterLink>
            </template>
          </p>

          <div v-if="videoRenderSubmitted" class="np__notice">
            <NIcon name="circle-check" :size="16" />
            <div>
              <p>已提交渲染，完成后将向注册邮箱发送通知并附下载链接。</p>
              <p v-if="videoRenderRemainingToday != null && !userIsVip" class="np__notice-meta">
                今日剩余免费次数：{{ videoRenderRemainingToday }}
              </p>
            </div>
          </div>

          <div v-if="videoRenderReady" class="np__notice np__notice--ready">
            <NIcon name="circle-check" :size="16" />
            <div class="np__notice-body">
              <p>分享视频已生成，可下载 MP4。</p>
              <NButton size="sm" variant="primary" icon="download" @click="downloadRenderedVideo">
                下载 MP4
              </NButton>
            </div>
          </div>
        </section>

        <!-- 右：歌词 -->
        <section class="np__lyrics" aria-label="歌词">
          <header class="np__lyrics-head">
            <h2 class="np__lyrics-title">歌词</h2>
            <span v-if="parsedLyrics.length" class="np__lyrics-count">{{ parsedLyrics.length }} 行</span>
          </header>

          <div v-if="parsedLyrics.length > 0" ref="lyricsContent" class="np__lyrics-scroll">
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

          <div v-else class="np__lyrics-empty">
            <NIcon name="file-text" :size="26" />
            <p>暂无歌词</p>
            <p class="np__lyrics-empty-hint">播放时可在底栏播放器查看音频进度</p>
          </div>
        </section>
      </div>
    </PageShell>

    <!-- 分享视频弹窗 -->
    <NModal
      v-model="videoModalOpen"
      title="生成分享视频"
      size="md"
      @close="closeVideoModal"
    >
      <p v-if="currentMusic" class="clip-song">{{ currentMusic.title }} · {{ currentMusic.artist }}</p>

      <label class="clip-option" :class="{ 'clip-option--locked': !userIsVip }">
        <input v-model="videoWatermarkChoice" type="checkbox" :disabled="!userIsVip" />
        <span>添加平台水印</span>
      </label>

      <div class="clip-range">
        <div class="clip-range__head">
          <span>成片起始</span>
          <span class="clip-range__value">
            {{ formatClipTime(clipStartSec) }} → {{ formatClipTime(clipEndSec) }}
          </span>
        </div>
        <input
          v-model.number="clipStartSec"
          type="range"
          class="clip-range__slider"
          :min="0"
          :max="maxClipStartSec"
          :step="1"
          :disabled="trackDurationSec <= 0"
          @input="onClipRangeChange"
        />
        <p class="clip-sub">
          <template v-if="userIsVip">
            会员：从所选位置渲染至歌曲结束（约 {{ formatClipTime(clipPreviewDurationSec) }}）
          </template>
          <template v-else>免费：所选范围内固定 30 秒成片（每日 10 次）</template>
        </p>
        <NButton
          size="sm"
          variant="secondary"
          icon="play"
          :disabled="trackDurationSec <= 0 || clipPreviewDurationSec <= 0"
          @click="toggleClipPreview"
        >
          {{ clipPreviewPlaying ? '停止试听' : '试听所选片段' }}
        </NButton>
      </div>

      <p class="clip-sub">
        <template v-if="userIsVip">会员可选是否添加水印，默认无水印</template>
        <template v-else>免费用户须开启水印</template>
      </p>
      <p class="clip-sub">提交后在后台渲染，完成后将邮件通知并附下载链接</p>

      <template #footer>
        <NButton variant="ghost" @click="closeVideoModal">取消</NButton>
        <NButton variant="primary" :loading="videoRenderBusy" @click="confirmVideoRender">
          开始生成
        </NButton>
      </template>
    </NModal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { createVideoRenderJob, fetchVideoRenderStatus, downloadVideoRenderFile } from '@/api/videoRender.js'
import { syncUserVipFromPlaylistsApi, USER_VIP_SYNC_EVENT } from '@/utils/userVip.js'
import { tryOpenMusicDetailInApp } from '@/utils/nativeAppOpen.js'
import { useToast } from '@/composables/useToast'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NModal, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'
import SpectrumCanvas from '@/components/SpectrumCanvas.vue'
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
/* ==================== 页面 & 背景 ==================== */
.np-page {
  position: relative;
  min-height: 100dvh;
}

.np-bg {
  position: fixed;
  inset: -12%;
  background-size: cover;
  background-position: center;
  filter: blur(72px) saturate(1.35);
  opacity: 0.24;
  z-index: 0;
  pointer-events: none;
}

.np-scrim {
  position: fixed;
  inset: 0;
  background: linear-gradient(180deg, rgba(4, 9, 11, 0.72), rgba(4, 9, 11, 0.88));
  z-index: 0;
  pointer-events: none;
}

/* ==================== 移动下载横幅 ==================== */
.np-banner {
  position: relative;
  z-index: var(--n-z-sticky);
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-3) var(--n-content-gutter);
  background: var(--n-accent-soft);
  border-bottom: 1px solid var(--n-accent-line);
  color: var(--n-text);
  font-size: var(--n-text-sm);
}

.np-banner__btn {
  margin-left: auto;
  padding: 6px 14px;
  border-radius: var(--n-radius-control);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  font-weight: var(--n-weight-semibold);
  font-size: var(--n-text-sm);
}

.np-banner__close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--n-radius-xs);
  color: var(--n-text-muted);
}

/* ==================== 加载 ==================== */
.np-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-4);
  min-height: min(60vh, 520px);
  color: var(--n-text-muted);
}

/* ==================== 主体两栏 ==================== */
.np {
  display: grid;
  grid-template-columns: minmax(0, 360px) minmax(0, 1fr);
  gap: clamp(28px, 4vw, 56px);
  align-items: start;
}

/* ---- 左：封面 + 信息 + 操作 ---- */
.np__aside {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.np__cover {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  border-radius: var(--n-radius-xl);
  overflow: hidden;
  border: 1px solid var(--n-line-strong);
  background: var(--n-surface-soft);
  box-shadow: var(--n-shadow-lg);
}

.np__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.np__play {
  position: absolute;
  right: var(--n-space-4);
  bottom: var(--n-space-4);
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius-lg);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
  transition: transform var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .np__play:hover {
    transform: translateY(-2px) scale(1.04);
    background: var(--n-accent-strong);
  }
}

.np__spectrum {
  margin-top: var(--n-space-5);
}

.np__meta {
  margin-top: var(--n-space-6);
}

.np__title {
  margin: 0;
  font-size: clamp(1.4rem, 2.6vw, 1.9rem);
  font-weight: var(--n-weight-bold);
  letter-spacing: -0.03em;
  line-height: 1.15;
  overflow-wrap: anywhere;
}

.np__artist {
  margin: var(--n-space-2) 0 0;
  color: var(--n-accent-strong);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-medium);
}

.np__album {
  margin: var(--n-space-1) 0 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.np__dur {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin: var(--n-space-3) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  font-variant-numeric: tabular-nums;
}

.np__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-3);
  margin-top: var(--n-space-6);
}

.np__hint {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-2);
  margin: var(--n-space-5) 0 0;
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius);
  background: var(--n-surface-soft);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.np__hint :deep(.n-icon) {
  margin-top: 2px;
  color: var(--n-accent);
}

.np__notice {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-3);
  margin-top: var(--n-space-4);
  padding: var(--n-space-4);
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.np__notice :deep(.n-icon) {
  flex: none;
  margin-top: 1px;
  color: var(--n-accent-strong);
}

.np__notice p {
  margin: 0;
}

.np__notice-body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--n-space-3);
}

.np__notice-meta {
  margin-top: var(--n-space-1) !important;
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
}

/* ---- 右：歌词 ---- */
.np__lyrics {
  min-width: 0;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
  padding: var(--n-space-6);
}

.np__lyrics-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--n-space-4);
  padding-bottom: var(--n-space-4);
  margin-bottom: var(--n-space-4);
  border-bottom: 1px solid var(--n-line-subtle);
}

.np__lyrics-title {
  margin: 0;
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.np__lyrics-count {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.np__lyrics-scroll {
  max-height: min(62vh, 620px);
  overflow-y: auto;
  padding-right: var(--n-space-2);
  scroll-behavior: smooth;
}

.np__lyrics-scroll::-webkit-scrollbar {
  width: 8px;
}

.np__lyrics-scroll::-webkit-scrollbar-thumb {
  background: rgba(95, 208, 224, 0.24);
  border-radius: var(--n-radius-pill);
}

@media (hover: hover) {
  .np__lyrics-scroll::-webkit-scrollbar-thumb:hover {
    background: rgba(95, 208, 224, 0.4);
  }
}

.np__lyrics-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--n-space-2);
  min-height: 240px;
  color: var(--n-text-muted);
  text-align: center;
}

.np__lyrics-empty :deep(.n-icon) {
  color: var(--n-text-faint);
}

.np__lyrics-empty p {
  margin: 0;
}

.np__lyrics-empty-hint {
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
}

/* ==================== 歌词行 ==================== */
.lyric-line {
  padding: var(--n-space-2) var(--n-space-3);
  border-radius: var(--n-radius-xs);
  color: var(--n-text-faint);
  font-size: var(--n-text-base);
  line-height: var(--n-leading-normal);
  transition: color var(--n-duration) var(--n-ease), background var(--n-duration) var(--n-ease);
}

.lyric-line.before {
  color: var(--n-text-muted);
}

.lyric-line.active {
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
  font-weight: var(--n-weight-semibold);
}

.lyric-text {
  overflow-wrap: anywhere;
}

.lyric-translation {
  margin-top: 2px;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.lyric-line.active .lyric-translation {
  color: var(--n-accent);
}

/* ==================== 分享视频弹窗 ==================== */
.clip-song {
  margin: 0 0 var(--n-space-4);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.clip-option {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  cursor: pointer;
  font-size: var(--n-text-base);
}

.clip-option--locked {
  opacity: 0.6;
  cursor: not-allowed;
}

.clip-range {
  margin-top: var(--n-space-5);
  padding: var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius);
  background: var(--n-surface-soft);
}

.clip-range__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--n-space-3);
  margin-bottom: var(--n-space-3);
  font-size: var(--n-text-sm);
  color: var(--n-text-muted);
}

.clip-range__value {
  color: var(--n-accent-strong);
  font-variant-numeric: tabular-nums;
  font-weight: var(--n-weight-semibold);
}

.clip-range__slider {
  width: 100%;
  accent-color: var(--n-accent);
  cursor: pointer;
}

.clip-sub {
  margin: var(--n-space-3) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .np {
    grid-template-columns: 1fr;
    gap: var(--n-space-8);
  }

  .np__aside {
    max-width: 420px;
    margin: 0 auto;
    width: 100%;
  }

  .np__lyrics-scroll {
    max-height: min(50vh, 460px);
  }
}

@media (max-width: 560px) {
  .np__actions :deep(.n-btn) {
    flex: 1 1 calc(50% - var(--n-space-3));
    justify-content: center;
  }
}
</style>
