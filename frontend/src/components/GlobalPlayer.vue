<template>
  <div class="global-player" :class="{ 'global-player--chrome-dark': chromeDark }">
    <!-- 顶部细进度条：贴住播放条上沿，可点击/拖动跳转 -->
    <div v-if="currentMusic" class="gp-seek">
      <div class="gp-seek__track" aria-hidden="true">
        <div class="gp-seek__fill" :style="{ width: progressPercent + '%' }" />
      </div>
      <input
        type="range"
        class="gp-seek__input"
        :value="progress"
        :max="duration || 0"
        min="0"
        step="0.1"
        aria-label="播放进度"
        @input="onProgressChange"
      />
    </div>

    <div class="gp-grid">
      <!-- 左：封面 + 曲名/歌手 + 收藏 -->
      <div class="gp-track">
        <button
          type="button"
          class="gp-track__cover"
          :aria-label="currentMusic ? `查看 ${currentMusic.title}` : '暂无播放'"
          :disabled="!currentMusic"
          @click="goToDetails"
        >
          <img
            v-if="currentMusic"
            :src="getCoverUrl(currentMusic.id)"
            :alt="currentMusic.title"
            class="gp-track__img"
            @error="handleImageError"
          />
          <span v-else class="gp-track__ph"><NIcon name="music-2" :size="20" /></span>
        </button>

        <div class="gp-track__meta">
          <span class="gp-track__title" :class="{ 'is-placeholder': !currentMusic }">
            {{ currentMusic ? currentMusic.title : '请选择音乐播放' }}
          </span>
          <span class="gp-track__artist" :class="{ 'is-placeholder': !currentMusic }">
            {{ currentMusic ? currentMusic.artist : '—' }}
          </span>
        </div>

        <button
          type="button"
          class="gp-icon"
          :class="{ 'is-on': isFavorite }"
          :disabled="!currentMusic"
          :aria-label="isFavorite ? '取消收藏' : '收藏'"
          :aria-pressed="isFavorite"
          @click="toggleFavorite"
        >
          <NIcon :name="isFavorite ? 'heart' : 'heart-off'" :size="17" />
        </button>
      </div>

      <!-- 中：播放控制 -->
      <div class="gp-controls">
        <button
          type="button"
          class="gp-icon gp-icon--lg"
          title="上一曲"
          aria-label="上一曲"
          :disabled="!currentMusic"
          @click="playPrevious()"
        >
          <NIcon name="skip-back" :size="20" />
        </button>

        <button
          type="button"
          class="gp-icon gp-icon--lg"
          :title="getPlaybackModeTitle()"
          :aria-label="getPlaybackModeTitle()"
          @click="togglePlaybackMode"
        >
          <NIcon :name="modeIcon" :size="18" />
        </button>

        <button
          type="button"
          class="gp-play"
          :disabled="!currentMusic"
          :aria-label="isPlaying && currentMusic ? '暂停' : '播放'"
          :aria-pressed="isPlaying && currentMusic"
          @click="togglePlayPause"
        >
          <NIcon :name="isPlaying && currentMusic ? 'pause' : 'play'" :size="21" />
        </button>

        <button
          type="button"
          class="gp-icon gp-icon--lg"
          title="下一曲"
          aria-label="下一曲"
          :disabled="!currentMusic"
          @click="playNext()"
        >
          <NIcon name="skip-forward" :size="20" />
        </button>
      </div>

      <!-- 右：时间 + 此刻（歌词优先，否则频谱）+ 播放列表
           对齐 ArchoeraMusic 的 _buildRightSection / _BarInfoArea：
           固定宽度列，时间在上，下方 120×12 一块「有歌词显示歌词，
           没歌词显示迷你频谱」。两块都是确定宽度，不会挤压同排控件。 -->
      <div class="gp-right">
        <div class="gp-now">
          <span class="gp-now__time">
            <b>{{ formatTime(currentTime) }}</b>
            <i>/</i>{{ formatTime(duration) }}
          </span>
          <div class="gp-now__viz">
            <span v-if="barLyric" class="gp-now__lyric" :title="barLyric.full">
              {{ barLyric.display }}
            </span>
            <SpectrumCanvas
              v-else-if="currentMusic"
              class="gp-now__spectrum"
              :bars="22"
              :height="12"
              :active="isPlaying"
            />
          </div>
        </div>

        <button
          type="button"
          class="gp-icon gp-icon--lg"
          title="播放列表"
          aria-label="播放列表"
          @click="togglePlaylist"
        >
          <NIcon name="list-music" :size="20" />
        </button>
      </div>
    </div>

    <!-- 音频元素：全站唯一播放源（crossorigin 不可移除，跨域音频接 Web Audio 会静音） -->
    <audio
      v-if="currentMusic"
      ref="audioPlayer"
      :src="`${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.id}`"
      crossorigin="anonymous"
      @ended="onAudioEnded"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
    />

    <!-- 播放列表弹层 -->
    <Transition name="gp-pop">
      <div v-if="showPlaylist" class="gp-pop">
        <header class="gp-pop__head">
          <h3 class="gp-pop__title">
            播放列表
            <span v-if="playlist.length" class="gp-pop__count">{{ playlist.length }} 首</span>
          </h3>
          <div class="gp-pop__head-actions">
            <button
              type="button"
              class="gp-pop__clear"
              :disabled="playlist.length === 0"
              @click="clearPlaylist"
            >
              清空
            </button>
            <button
              type="button"
              class="gp-icon gp-icon--sm"
              aria-label="关闭播放列表"
              @click="togglePlaylist"
            >
              <NIcon name="close" :size="16" />
            </button>
          </div>
        </header>

        <div class="gp-pop__list">
          <p v-if="playlist.length === 0" class="gp-pop__empty">
            列表为空，播放任意曲目后会自动加入此处。
          </p>
          <button
            v-for="(item, index) in playlist"
            :key="item.id"
            type="button"
            class="gp-pop__item"
            :class="{ 'is-current': currentMusic && item.id === currentMusic.id }"
            @click="playFromPlaylist(index)"
          >
            <span class="gp-pop__idx">
              <NIcon
                v-if="currentMusic && item.id === currentMusic.id"
                name="volume-2"
                :size="15"
              />
              <template v-else>{{ index + 1 }}</template>
            </span>
            <span class="gp-pop__info">
              <span class="gp-pop__name">{{ item.title }}</span>
              <span class="gp-pop__artist">{{ item.artist }}</span>
            </span>
          </button>
        </div>
      </div>
    </Transition>
  </div>

  <!-- 清空播放列表确认 -->
  <NModal v-model="showClearConfirm" title="确认清空" size="sm">
    <p>确定要清空播放列表吗？清空后当前曲目仍会继续播放。</p>
    <template #footer>
      <NButton variant="ghost" @click="showClearConfirm = false">取消</NButton>
      <NButton variant="danger" @click="confirmClearPlaylist">确定清空</NButton>
    </template>
  </NModal>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'
import { attachAudioElement, unlockAudioAnalyser } from '@/composables/useAudioAnalyser'
import SpectrumCanvas from '@/components/SpectrumCanvas.vue'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NModal } from '@/ui'

defineProps({
  chromeDark: {
    type: Boolean,
    default: false
  }
})

const toast = useToast()

const router = useRouter()

// 从localStorage获取当前播放的音乐信息
const currentMusic = ref(JSON.parse(localStorage.getItem('currentPlayingMusic')) || null)
const audioPlayer = ref(null)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const progress = ref(0)
const lyrics = ref('')
const parsedLyrics = ref([])
const lyricsContent = ref(null)

// 播放模式相关状态
const playbackMode = ref('list_repeat') // 'list_repeat', 'single_repeat', 'shuffle'
const playlist = ref([])
const isFavorite = ref(false) // 当前音乐是否已收藏
const showClearConfirm = ref(false) // 是否显示清空确认模态框

/** 当前播放模式对应的图标名（图标注册表语义名） */
const modeIcon = computed(() => {
  if (playbackMode.value === 'single_repeat') return 'repeat-1'
  if (playbackMode.value === 'shuffle') return 'shuffle'
  return 'repeat'
})

/** 进度百分比（顶部细进度条填充宽度） */
const progressPercent = computed(() => {
  const total = duration.value
  if (!total || !Number.isFinite(total) || total <= 0) return 0
  return Math.max(0, Math.min(100, (progress.value / total) * 100))
})

// 记录上一个歌词索引

// 获取用户token
const getToken = () => {
  return localStorage.getItem('userToken')
}

// 切换收藏状态
const toggleFavorite = async () => {
  if (!currentMusic.value) return
  
  const token = getToken()
  if (!token) {
    toast.error('请先登录')
    return
  }
  
  if (isFavorite.value) {
    // 取消收藏
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites/${currentMusic.value.id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': token
        }
      })
      
      const data = await response.json()
      if (data.success) {
        isFavorite.value = false
        toast.success('取消收藏成功')
      } else {
        toast.error('取消收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('取消收藏失败:', error)
      toast.error('取消收藏失败')
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
      })
      
      const data = await response.json()
      if (data.success) {
        isFavorite.value = true
        toast.success('收藏成功')
      } else {
        toast.error('收藏失败: ' + data.message)
      }
    } catch (error) {
      console.error('收藏失败:', error)
      toast.error('收藏失败')
    }
  }
}

// 检查当前音乐是否已收藏
const checkFavoriteStatus = async () => {
  if (!currentMusic.value) return
  
  const token = getToken()
  if (!token) {
    isFavorite.value = false
    return
  }
  
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/user/favorites`, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    })
    
    const data = await response.json()
    if (data.success && data.favorites) {
      isFavorite.value = data.favorites.some(m => m.id === currentMusic.value.id)
    }
  } catch (error) {
    console.error('获取收藏状态失败:', error)
  }
}

// 播放/暂停控制
const togglePlayPause = () => {
  // 播放是明确的手势路径：在此解锁音频分析（建立 AudioContext 并 resume）
  if (!isPlaying.value) unlockAudioAnalyser()

  if (audioPlayer.value && currentMusic.value) {
    if (isPlaying.value) {
      // 暂停：直接暂停，避免重音
      isPlaying.value = false
      updateGlobalPlayerState()
      // 广播播放状态变化
      broadcastPlayerStateChange()
      // 立即暂停音频并静音
      if (audioPlayer.value) {
        audioPlayer.value.volume = 0;
        audioPlayer.value.pause();
      }
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    } else {
      // 播放：立即更新状态，然后淡入播放
      isPlaying.value = true
      updateGlobalPlayerState()
      // 广播播放状态变化
      broadcastPlayerStateChange()
      fadeIn(audioPlayer.value)
      audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    }
  }
}

// 音量淡出效果
const fadeOut = (audioElement) => {
  if (!audioElement) return

  // 为了防止暂停时出现重音，先快速将音量降低到0，然后暂停
  // 使用更快速的淡出效果
  const originalVolume = audioElement.volume;
  
  // 立即设置音量为0以避免重音，然后暂停音频
  audioElement.volume = 0;
  audioElement.pause();
}

// 音量淡入效果
const fadeIn = (audioElement) => {
  if (!audioElement) return
  
  let v = 0
  audioElement.volume = 0

  const tick = () => {
    v += 0.03
    audioElement.volume = Math.min(v, 1)
    if (v < 1) requestAnimationFrame(tick)
  }
  tick()
}

// 音频结束事件
// 音频结束事件 - 现在根据播放模式处理
const onAudioEnded = () => {
  if (playbackMode.value === 'single_repeat') {
    // 单曲循环：重新播放当前歌曲
    if (audioPlayer.value && currentMusic.value) {
      audioPlayer.value.currentTime = 0.2
      audioPlayer.value.play()
      // 更新媒体会话播放状态
      updateMediaSessionPlaybackState()
    }
  } else if (playbackMode.value === 'shuffle' && playlist.value.length > 1) {
    // 随机播放：播放列表中的随机歌曲
    playNextInShuffle(true) // 标记：来自 ended
  } else {
    // 列表循环：播放下一首
    playNext(true) // 标记：来自 ended
  }
  
  updateGlobalPlayerState()
  // 广播播放状态变化
  broadcastPlayerStateChange()
}

// 时间更新事件
const onTimeUpdate = () => {
  if (audioPlayer.value) {
    currentTime.value = audioPlayer.value.currentTime
    progress.value = currentTime.value
    updateGlobalPlayerState()
    
    // 更新媒体会话播放位置
    updateMediaSessionPositionState()
    
    // 歌词高亮由 activeLyricIndex / barLyric 两个 computed 派生，此处无需处理
  }
}

// 音频元数据加载完成
const onLoadedMetadata = () => {
  if (audioPlayer.value) {
    duration.value = audioPlayer.value.duration
    updateGlobalPlayerState()
    // 广播播放状态变化
    broadcastPlayerStateChange()
    
    
    // 加载歌词
    if (currentMusic.value) {
      loadLyrics(currentMusic.value.id)
    }
    
    // 更新媒体会话播放位置
    updateMediaSessionPositionState()
  }
}

// 进度条变化
const onProgressChange = (event) => {
  seekTo(parseFloat(event.target.value))
}

/**
 * 跳转到指定秒数。
 * 底部播放条与全屏播放页（经 playerCommand）共用同一条 seek 路径，
 * 保证 seek 后状态广播、媒体会话位置都与既有行为一致。
 */
const seekTo = (seconds) => {
  if (!audioPlayer.value || !currentMusic.value) return
  const total = duration.value || audioPlayer.value.duration || 0
  const target = Math.max(0, Math.min(Number(seconds) || 0, total || Number(seconds) || 0))

  audioPlayer.value.currentTime = target
  currentTime.value = target
  progress.value = target
  updateGlobalPlayerState()
  // 广播播放状态变化
  broadcastPlayerStateChange()
  // 更新媒体会话播放位置
  updateMediaSessionPositionState()
}

/**
 * 播放指令分发（来自全屏播放页等外部 UI）。
 * 只做「动作 → 既有函数」的映射，不新增任何播放逻辑，
 * 因此不会改变既有契约与行为。
 */
const handlePlayerCommand = (e) => {
  const { action, time, index } = e?.detail || {}
  switch (action) {
    case 'toggle':
      togglePlayPause()
      break
    case 'play':
      if (!isPlaying.value) togglePlayPause()
      break
    case 'pause':
      if (isPlaying.value) togglePlayPause()
      break
    case 'next':
      playNext()
      break
    case 'prev':
      playPrevious()
      break
    case 'seek':
      seekTo(time)
      break
    case 'cycleMode':
      togglePlaybackMode()
      break
    case 'playIndex':
      playFromPlaylist(index)
      break
    case 'clearPlaylist':
      clearPlaylist()
      break
    default:
      break
  }
}

// 更新全局播放器状态
const updateGlobalPlayerState = () => {
  const state = {
    isPlaying: isPlaying.value,
    currentTime: currentTime.value,
    duration: duration.value,
    playbackMode: playbackMode.value
  };
  localStorage.setItem('globalPlayerState', JSON.stringify(state));
}

// 广播播放器状态变化
const broadcastPlayerStateChange = () => {
  // 创建自定义事件来通知播放状态变化
  const event = new CustomEvent('playerStateChange', {
    detail: {
      isPlaying: isPlaying.value,
      currentTime: currentTime.value,
      duration: duration.value,
      playbackMode: playbackMode.value,
      currentMusic: currentMusic.value
    }
  });
  window.dispatchEvent(event);
}

// 格式化时间（秒转分:秒）
const formatTime = (seconds) => {
  if (isNaN(seconds) || seconds < 0) return '0:00'
  
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return `${min}:${sec < 10 ? '0' : ''}${sec}`
}

// 跳转到音乐详情页面
const goToDetails = () => {
  if (currentMusic.value) {
    // 跳转到音乐详情页面
    router.push(`/detail/${currentMusic.value.id}`)
  }
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
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
  let currentLyric = null
  
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

// 获取指定索引的歌词行文本
/**
 * 当前时间对应的歌词行下标（-1 = 前奏，还没到第一句）。
 * 播放条右侧「此刻」区域与全屏播放页共用同一份 parsedLyrics。
 */
const activeLyricIndex = computed(() => {
  const lines = parsedLyrics.value
  for (let i = lines.length - 1; i >= 0; i--) {
    if (currentTime.value >= lines[i].time) return i
  }
  return -1
})

/**
 * 播放条右侧「此刻」区域要显示的歌词。
 * 对齐 ArchoeraMusic 的 _BarInfoArea：有歌词就优先显示歌词，返回 null
 * 时调用方降级为迷你频谱。前奏与空行都返回 null，避免出现一块空白。
 */
const barLyric = computed(() => {
  const idx = activeLyricIndex.value
  if (idx < 0) return null
  const line = parsedLyrics.value[idx]
  const text = (line?.text || '').trim()
  if (!text) return null
  const translation = (line?.translation || '').trim()
  return {
    text,
    translation,
    // 只有 12px 高的一行：正文与翻译并排显示，超出省略；完整内容挂 title
    display: translation ? `${text} · ${translation}` : text,
    full: translation ? `${text} — ${translation}` : text,
  }
})

// 处理封面图片加载错误
const handleImageError = (event) => {
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`
}

// 清空播放列表
const clearPlaylist = () => {
  if (playlist.value.length === 0) {
    toast.warning('播放列表已经是空的')
    return
  }
  
  // 显示确认模态框
  showClearConfirm.value = true
}

// 确认清空播放列表
const confirmClearPlaylist = () => {
  // 清空播放列表
  playlist.value = []
  // 清空localStorage中的播放列表
  localStorage.setItem('globalPlaylist', JSON.stringify([]))
  // 如果当前有正在播放的音乐，保留当前音乐在列表中
  if (currentMusic.value) {
    playlist.value = [currentMusic.value]
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value))
  }
  // 广播播放列表更新事件
  const playlistEvent = new CustomEvent('playlistUpdated', {
    detail: {
      playlist: playlist.value
    }
  })
  window.dispatchEvent(playlistEvent)
  toast.success('播放列表已清空')
  // 关闭模态框
  showClearConfirm.value = false
}

// 播放模式切换函数
const togglePlaybackMode = () => {
  if (playbackMode.value === 'list_repeat') {
    playbackMode.value = 'single_repeat'
  } else if (playbackMode.value === 'single_repeat') {
    playbackMode.value = 'shuffle'
  } else {
    playbackMode.value = 'list_repeat'
  }
  // 保存播放模式到localStorage
  localStorage.setItem('playbackMode', playbackMode.value)
}

// 获取播放模式标题
const getPlaybackModeTitle = () => {
  if (playbackMode.value === 'list_repeat') {
    return '列表循环'
  } else if (playbackMode.value === 'single_repeat') {
    return '单曲循环'
  } else {
    return '随机播放'
  }
}

// 播放列表显示状态
const showPlaylist = ref(false)

// 切换播放列表显示
const togglePlaylist = () => {
  showPlaylist.value = !showPlaylist.value
}

// 从播放列表播放
const playFromPlaylist = (index) => {
  if (playlist.value[index]) {
    // 先暂停当前音频
    if (audioPlayer.value && !audioPlayer.value.paused) {
      audioPlayer.value.pause();
    }
    
    // 重置播放时间并立即更新UI（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 设置为当前播放的音乐
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[index]))
    currentMusic.value = playlist.value[index]
    isPlaying.value = true
    
    // 重新加载歌词
    loadLyrics(playlist.value[index].id)
    
    // 确保音频元素重新加载资源
    if (audioPlayer.value) {
      // 先加载音频资源
      audioPlayer.value.load()
      
      // 在音频加载完成后设置时间为0.1
      const onLoadedData = () => {
        audioPlayer.value.currentTime = 0.1
        currentTime.value = 0.1
        progress.value = 0.1
        updateGlobalPlayerState()
        // 只在有有效 duration 时才更新媒体会话播放位置
        if (audioPlayer.value.duration > 0) {
          duration.value = audioPlayer.value.duration
          updateMediaSessionPositionState()
        }
        fadeIn(audioPlayer.value)
        audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
      }
      
      audioPlayer.value.addEventListener('loadeddata', onLoadedData)
    }
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[index])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
  // 关闭播放列表
  showPlaylist.value = false
}

// 从localStorage加载播放模式
const loadPlaybackMode = () => {
  const savedMode = localStorage.getItem('playbackMode')
  if (savedMode && ['list_repeat', 'single_repeat', 'shuffle'].includes(savedMode)) {
    playbackMode.value = savedMode
  } else {
    // 默认设置为列表循环
    playbackMode.value = 'list_repeat'
    localStorage.setItem('playbackMode', 'list_repeat')
  }
}

// 播放下一首
const playNext = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  let nextIndex
  
  if (playbackMode.value === 'shuffle') {
    nextIndex = getRandomIndex(currentIndex)
  } else {
    nextIndex = (currentIndex + 1) % playlist.value.length
  }
  
  if (nextIndex !== -1 && playlist.value[nextIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[nextIndex]))
    currentMusic.value = playlist.value[nextIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[nextIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[nextIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI（从0.1开始）
      currentTime.value = 0.1
      duration.value = 0
      progress.value = 0.1
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间（从0.1开始）
        audioPlayer.value.currentTime = 0.1
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0.1
          audioPlayer.value.currentTime = 0.1
          currentTime.value = 0.1
          progress.value = 0.1
          updateGlobalPlayerState()
          
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[nextIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}

// 播放上一首
const playPrevious = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  let prevIndex
  
  if (playbackMode.value === 'shuffle') {
    prevIndex = getRandomIndex(currentIndex)
  } else {
    prevIndex = (currentIndex - 1 + playlist.value.length) % playlist.value.length
  }
  
  if (prevIndex !== -1 && playlist.value[prevIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[prevIndex]))
    currentMusic.value = playlist.value[prevIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[prevIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[prevIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI（从0.1开始）
      currentTime.value = 0.1
      duration.value = 0
      progress.value = 0.1
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间（从0.1开始）
        audioPlayer.value.currentTime = 0.1
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0.1
          audioPlayer.value.currentTime = 0.1
          currentTime.value = 0.1
          progress.value = 0.1
          updateGlobalPlayerState()
          
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[prevIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}

// 获取随机索引（排除当前索引）
const getRandomIndex = (currentIndex) => {
  if (playlist.value.length <= 1) return currentIndex
  
  let randomIndex
  do {
    randomIndex = Math.floor(Math.random() * playlist.value.length)
  } while (randomIndex === currentIndex && playlist.value.length > 1)
  
  return randomIndex
}

// 播放下一首（随机模式）
const playNextInShuffle = (fromEnded = false) => {
  if (!currentMusic.value || playlist.value.length === 0) return
  
  const currentIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id)
  const nextIndex = getRandomIndex(currentIndex)
  
  if (nextIndex !== -1 && playlist.value[nextIndex]) {
    // 设置新音乐到localStorage
    localStorage.setItem('currentPlayingMusic', JSON.stringify(playlist.value[nextIndex]))
    currentMusic.value = playlist.value[nextIndex]
    
    // 加载新音乐的歌词
    loadLyrics(playlist.value[nextIndex].id)
    
    // 关键区别：根据来源参数执行不同逻辑
    if (fromEnded) {
      // 来自 ended 事件：不暂停，直接换源并播放
      if (audioPlayer.value) {
        // 监听 canplay 事件，确保在音频可以播放后再播放
        const onCanPlay = () => {
          // 确保时间从0.2开始
          audioPlayer.value.currentTime = 0.2
          currentTime.value = 0.2
          progress.value = 0.2
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          audioPlayer.value.play()
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay)
        }
        
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${playlist.value[nextIndex].id}`
        // 添加事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay)
      }
    } else {
      // 手动点下一首：允许淡入
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 重置播放时间并立即更新UI
      currentTime.value = 0
      duration.value = 0
      progress.value = 0
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      // 加载新音频资源
      if (audioPlayer.value) {
        // 确保音频元素在加载新资源前已重置时间
        audioPlayer.value.currentTime = 0
        
        // 监听loadeddata事件以确保音频已加载后再操作
        const onLoadedData = () => {
          // 确保音频时间已重置为0
          audioPlayer.value.currentTime = 0
          currentTime.value = 0
          progress.value = 0
          updateGlobalPlayerState()
          updateMediaSessionPositionState()
          fadeIn(audioPlayer.value)
          
          // 移除事件监听器
          audioPlayer.value.removeEventListener('loadeddata', onLoadedData)
        }
        
        audioPlayer.value.addEventListener('loadeddata', onLoadedData)
        audioPlayer.value.load()
      }
    }
    
    // 确保UI立即更新时间轴（从0.1开始）
    currentTime.value = 0.1
    duration.value = 0
    progress.value = 0.1
    updateGlobalPlayerState()
    
    // 更新媒体会话元数据
    updateMediaSessionMetadata(playlist.value[nextIndex])
    // 更新播放状态
    updateMediaSessionPlaybackState()
  }
}



// 监听localStorage变化，响应播放音乐的改变
const handleStorageChange = (e) => {
  if (e.key === 'currentPlayingMusic') {
    // 只有在音乐实际改变时才重置播放器
    const newMusic = e.newValue ? JSON.parse(e.newValue) : null;
    if (newMusic && (!currentMusic.value || newMusic.id !== currentMusic.value.id)) {
      // 先暂停当前音频
      if (audioPlayer.value && !audioPlayer.value.paused) {
        audioPlayer.value.pause();
      }
      
      // 音乐改变了，更新当前音乐并重置播放器
      currentMusic.value = newMusic;
      
      // 检查当前音乐是否在播放列表中，如果不在则添加进去
      if (newMusic && playlist.value) {
        const existingIndex = playlist.value.findIndex(item => item.id === newMusic.id);
        if (existingIndex === -1) {
          // 如果当前音乐不在播放列表中，则添加到列表中
          playlist.value.push(newMusic);
          // 同时保存到 localStorage
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
        }
      }
      
      // 重置播放时间并立即更新UI
      currentTime.value = 0
      duration.value = 0
      progress.value = 0
      updateGlobalPlayerState()
      
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
      
      if (audioPlayer.value) {
        // 设置新的音频源
        audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${newMusic.id}`;
        
        // 检查播放状态，如果应该播放则开始播放
        const storedState = localStorage.getItem('globalPlayerState');
        let shouldPlay = false;
        
        if (storedState) {
          const state = JSON.parse(storedState);
          shouldPlay = state.isPlaying;
        } else {
          // 如果没有播放状态信息，默认播放（因为用户点击了播放按钮）
          shouldPlay = true;
        }
        
        // 监听 canplay 事件，一旦音频可以播放就立即播放
        const onCanPlay = () => {
          audioPlayer.value.currentTime = 0.1; // 确保从0.1开始播放
          currentTime.value = 0.1;
          progress.value = 0.1;
          updateGlobalPlayerState();
          
          if (shouldPlay) {
            // 设置播放状态
            isPlaying.value = true;
            fadeIn(audioPlayer.value);
            audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            broadcastPlayerStateChange(); // 确保其他组件同步状态
          } else {
            // 如果不应该播放，确保播放状态为 false
            isPlaying.value = false;
            broadcastPlayerStateChange();
          }
          // 移除事件监听器
          audioPlayer.value.removeEventListener('canplay', onCanPlay);
        };
        
        // 添加 canplay 事件监听器
        audioPlayer.value.addEventListener('canplay', onCanPlay);
        
        // 调用 load() 来加载新资源
        audioPlayer.value.load();
      }
      
      // 加载新音乐的歌词
      if (newMusic) {
        loadLyrics(newMusic.id)
        // 更新媒体会话元数据
        updateMediaSessionMetadata(newMusic)
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
        // 检查收藏状态
        checkFavoriteStatus()
      } else {
        lyrics.value = ''
        parsedLyrics.value = []
        isFavorite.value = false
        // 清除媒体会话元数据
        if ('mediaSession' in navigator) {
          navigator.mediaSession.metadata = null
        }
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
      }
    } else if (!e.newValue) {
      // 没有音乐了，暂停播放器
      currentMusic.value = null;
      if (audioPlayer.value) {
        audioPlayer.value.pause();
        // 重置播放时间
        currentTime.value = 0;
        progress.value = 0;
        isPlaying.value = false;
        duration.value = 0;
        updateGlobalPlayerState();
        // 广播播放状态变化
        broadcastPlayerStateChange();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
        // 更新媒体会话播放位置
        updateMediaSessionPositionState()
      }
      // 清空歌词
      lyrics.value = ''
      parsedLyrics.value = []
      // 清除媒体会话元数据
      if ('mediaSession' in navigator) {
        navigator.mediaSession.metadata = null
      }
    }
  } else if (e.key === 'globalPlayerState') {
    // 从播放页面接收状态更新
    if (e.newValue) {
      const state = JSON.parse(e.newValue);
      // 更新播放器状态，无论audio元素是否准备好
      const previousIsPlaying = isPlaying.value;
      isPlaying.value = state.isPlaying;
      currentTime.value = state.currentTime;
      duration.value = state.duration;
      progress.value = state.currentTime;
      
      // 如果有audio元素则同步操作
      if (audioPlayer.value && currentMusic.value) {
        // 等待音频加载完成再执行操作
        const updateWhenReady = () => {
          if (audioPlayer.value) {
            audioPlayer.value.currentTime = state.currentTime;
            if (state.isPlaying && !previousIsPlaying) {
              // 如果状态从暂停变为播放，则开始播放音频
              fadeIn(audioPlayer.value);
              audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            } else if (!state.isPlaying) {
              // 如果状态变为暂停，则暂停音频
              audioPlayer.value.volume = 0;
              audioPlayer.value.pause();
            }
          }
        };
        
        if (audioPlayer.value.readyState >= 2) { // HAVE_CURRENT_DATA
          updateWhenReady();
        } else {
          audioPlayer.value.addEventListener('loadeddata', updateWhenReady, { once: true });
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      } else if (currentMusic.value) {
        // 如果audio元素还没准备好，等待并执行操作
        const handleMetadata = () => {
          if (audioPlayer.value) {
            audioPlayer.value.currentTime = state.currentTime;
            if (state.isPlaying && !previousIsPlaying) {
              // 如果状态从暂停变为播放，则开始播放音频
              fadeIn(audioPlayer.value);
              audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
            } else if (!state.isPlaying) {
              // 如果状态变为暂停，则暂停音频
              audioPlayer.value.volume = 0;
              audioPlayer.value.pause();
            }
          }
        };
        
        audioPlayer.value?.addEventListener('loadedmetadata', handleMetadata, { once: true });
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState()
    }
  } else if (e.key === 'globalPlaylist') {
    // 当播放列表更新时，同步更新本地播放列表
    if (e.newValue) {
      try {
        const newPlaylist = JSON.parse(e.newValue);
        playlist.value = newPlaylist;
      } catch (error) {
        console.error('解析播放列表失败:', error);
      }
    }
  }
}

// 强制播放处理函数
const handleForcePlay = () => {
  if (audioPlayer.value && currentMusic.value) {
    // 确保时间从0.1开始
    audioPlayer.value.currentTime = 0.1;
    currentTime.value = 0.1;
    progress.value = 0.1;
    updateGlobalPlayerState();
    
    // 确保播放状态为true
    isPlaying.value = true;
    
    // 确保音频源已经设置为当前音乐
    const expectedSrc = `${API_CONFIG.BASE_URL}/api/music/file/${currentMusic.value.id}`;
    
    if (!audioPlayer.value.src || !audioPlayer.value.src.includes(currentMusic.value.id.toString())) {
      // 如果音频源不是当前音乐，则设置为当前音乐
      audioPlayer.value.src = expectedSrc;
      
      const onCanPlay = () => {
        audioPlayer.value.currentTime = 0.1; // 再次确保从0.1开始
        currentTime.value = 0.1;
        progress.value = 0.1;
        updateGlobalPlayerState();
        
        fadeIn(audioPlayer.value);
        audioPlayer.value.play().catch(e => {
          console.log('播放被阻止:', e);
          // 如果播放失败，重置播放状态
          isPlaying.value = false;
          updateGlobalPlayerState();
          broadcastPlayerStateChange();
        });
        audioPlayer.value.removeEventListener('canplay', onCanPlay);
        
        // 更新播放状态
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
        updateMediaSessionPlaybackState();
      };
      
      // 添加错误处理
      const onError = (e) => {
        console.error('音频加载失败:', e);
        isPlaying.value = false;
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
        audioPlayer.value.removeEventListener('error', onError);
      };
      
      audioPlayer.value.addEventListener('canplay', onCanPlay);
      audioPlayer.value.addEventListener('error', onError);
      audioPlayer.value.load();
    } else {
      // 音频源已经是当前音乐，直接播放
      fadeIn(audioPlayer.value);
      audioPlayer.value.play().catch(e => {
        console.log('播放被阻止:', e);
        // 如果播放失败，重置播放状态
        isPlaying.value = false;
        updateGlobalPlayerState();
        broadcastPlayerStateChange();
      });
      
      // 更新播放状态
      updateGlobalPlayerState();
      broadcastPlayerStateChange();
      updateMediaSessionPlaybackState();
    }
  }
}

// 处理自定义播放状态变化事件
const handlePlayerStateChange = (e) => {
  const state = e.detail;
  
  // 检查是否正在切换到新音乐
  if (currentMusic.value && state.currentMusic && currentMusic.value.id !== state.currentMusic.id) {
    // 如果是切换到新音乐，更新当前音乐并切换音频源
    currentMusic.value = state.currentMusic;
    
    // 重置播放时间（从0.1开始）
    currentTime.value = 0.1;
    duration.value = 0;
    progress.value = 0.1;
    updateGlobalPlayerState();
    updateMediaSessionPositionState();
    
    // 切换音频源
    if (audioPlayer.value) {
      audioPlayer.value.src = `${API_CONFIG.BASE_URL}/api/music/file/${state.currentMusic.id}`;
      
      const onCanPlay = () => {
        audioPlayer.value.currentTime = 0.1;
        currentTime.value = 0.1;
        progress.value = 0.1;
        updateGlobalPlayerState();
        
        if (state.isPlaying) {
          isPlaying.value = true;
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        }
        
        audioPlayer.value.removeEventListener('canplay', onCanPlay);
        updateMediaSessionPlaybackState();
      };
      
      audioPlayer.value.addEventListener('canplay', onCanPlay);
      audioPlayer.value.load();
    }
    
    // 加载新音乐的歌词
    loadLyrics(state.currentMusic.id);
    // 更新媒体会话元数据
    updateMediaSessionMetadata(state.currentMusic);
    
    return; // 处理完音乐切换后直接返回
  }
  
  // 否则是同一首音乐的时间更新
  currentTime.value = state.currentTime;
  duration.value = state.duration;
  progress.value = state.currentTime;

  // 如果有audio元素则同步操作
  if (audioPlayer.value && currentMusic.value && currentMusic.value.id === state.currentMusic?.id) {
    const previousIsPlaying = isPlaying.value;
    isPlaying.value = state.isPlaying;
    
    // 等待音频加载完成再执行操作
    const performStateChange = () => {
      if (audioPlayer.value) {
        audioPlayer.value.currentTime = state.currentTime;
        if (state.isPlaying && !previousIsPlaying) {
          // 如果状态从暂停变为播放，则开始播放音频
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        } else if (!state.isPlaying) {
          // 如果是暂停状态，立即暂停并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState();
    };

    if (audioPlayer.value.readyState >= 2) { // HAVE_CURRENT_DATA
      performStateChange();
    } else {
      audioPlayer.value.addEventListener('loadeddata', performStateChange, { once: true });
    }
  } else if (currentMusic.value && currentMusic.value.id === state.currentMusic?.id) {
    // 如果audio元素还没准备好，等待并执行操作
    const previousIsPlaying = isPlaying.value;
    isPlaying.value = state.isPlaying;
    
    const handleMetadata = () => {
      if (audioPlayer.value) {
        audioPlayer.value.currentTime = state.currentTime;
        if (state.isPlaying && !previousIsPlaying) {
          // 如果状态从暂停变为播放，则开始播放音频
          fadeIn(audioPlayer.value);
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        } else if (!state.isPlaying) {
          // 如果是暂停状态，立即暂停并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
        updateGlobalPlayerState();
        // 更新媒体会话播放状态
        updateMediaSessionPlaybackState();
      }
      // 更新媒体会话播放位置
      updateMediaSessionPositionState();
    };

    audioPlayer.value?.addEventListener('loadedmetadata', handleMetadata, { once: true });
  }
}

// 处理播放列表更新事件
const handlePlaylistUpdated = (e) => {
  if (e.detail && e.detail.playlist) {
    playlist.value = e.detail.playlist;
    // 同时保存到 localStorage 以确保持久化
    localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
  }
}

/** 详情页试听片段时暂停底部播放器，避免双路音频与重复请求 */
const handlePauseGlobalPlayer = () => {
  if (audioPlayer.value && !audioPlayer.value.paused) {
    audioPlayer.value.pause()
    isPlaying.value = false
    updateGlobalPlayerState()
    updateMediaSessionPlaybackState()
  }
}

onMounted(() => {
  // 把音频元素注册给频谱分析（Web Audio AnalyserNode 需挂在同一元素上）
  if (audioPlayer.value) attachAudioElement(audioPlayer.value)

  // audio 是 v-if="currentMusic" 渲染的：首次挂载时通常还不存在，
  // 因此监听 ref，等元素出现后再注册（元素被重建时也会重新注册）
  watch(audioPlayer, (el) => {
    if (el) attachAudioElement(el)
  })

  // 加载播放列表
  loadPlaylist()

  // 监听storage事件，以响应其他标签页的播放变化
  window.addEventListener('storage', handleStorageChange)
  // 监听自定义事件，以响应播放页面的状态变化
  window.addEventListener('playerStateChange', handlePlayerStateChange)
  // 监听强制播放事件
  window.addEventListener('forcePlay', handleForcePlay)
  // 监听播放列表更新事件
  window.addEventListener('playlistUpdated', handlePlaylistUpdated)
  // 监听URL hash变化，处理播放请求
  window.addEventListener('hashchange', handleHashChange)
  window.addEventListener('pauseGlobalPlayer', handlePauseGlobalPlayer)
  // 监听来自全屏播放页的播放指令（见 composables/usePlaybackBridge.js）
  window.addEventListener('playerCommand', handlePlayerCommand)
  // 初始检查hash
  handleHashChange()
  
  // 初始化当前播放音乐
  const storedMusic = localStorage.getItem('currentPlayingMusic')
  if (storedMusic) {
    currentMusic.value = JSON.parse(storedMusic)
    
    // 如果当前音乐不在播放列表中，则添加进去
    if (currentMusic.value && playlist.value) {
      const existingIndex = playlist.value.findIndex(item => item.id === currentMusic.value.id);
      if (existingIndex === -1) {
        // 如果当前音乐不在播放列表中，则添加到列表中
        playlist.value.push(currentMusic.value);
        // 同时保存到 localStorage
        localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
      }
    }
    
    // 初始化时加载歌词
    if (currentMusic.value) {
      loadLyrics(currentMusic.value.id)
      // 初始化媒体会话
      initializeMediaSession(currentMusic.value)
      // 检查收藏状态
      checkFavoriteStatus()
    }
  }
  
  // 初始化时从localStorage获取播放状态
  const storedState = localStorage.getItem('globalPlayerState');
  if (storedState) {
    const state = JSON.parse(storedState);
    currentTime.value = state.currentTime;
    duration.value = state.duration;
    progress.value = state.currentTime;
    
    // 如果全局播放器应该正在播放，则同步播放状态
    isPlaying.value = state.isPlaying;
    
    // 如果当前音乐存在且播放状态为播放，则尝试播放
    if (currentMusic.value && isPlaying.value && audioPlayer.value) {
      setTimeout(() => {
        handleForcePlay();
      }, 100); // 稍微延迟确保组件完全加载
    }
  }
  
  // 加载播放模式
  loadPlaybackMode()
  
  // 初始化媒体会话API
  initializeMediaSession()
})

// 加载播放列表
// 处理URL hash变化，响应播放请求
const handleHashChange = () => {
  const hash = window.location.hash

  if (hash.startsWith('#play=')) {
    // 单曲播放
    try {
      const musicData = JSON.parse(decodeURIComponent(hash.substring(6)))
      currentMusic.value = musicData
      localStorage.setItem('currentPlayingMusic', JSON.stringify(musicData))

      // 确保音乐在播放列表中
      if (playlist.value) {
        const existingIndex = playlist.value.findIndex(item => item.id === musicData.id)
        if (existingIndex === -1) {
          playlist.value.push(musicData)
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value))
        }
      }

      // 加载歌词并开始播放
      loadLyrics(musicData.id)
      if (audioPlayer.value) {
        audioPlayer.value.load()
        audioPlayer.value.play()
      }
      isPlaying.value = true

      // 清除hash
      history.replaceState(null, null, ' ')
    } catch (error) {
      console.error('解析播放数据失败:', error)
    }
  } else if (hash.startsWith('#playlist=')) {
    // 播放列表播放
    try {
      const params = hash.substring(1).split('&')
      const playlistData = JSON.parse(decodeURIComponent(params[0].substring(9)))
      const startIndex = parseInt(params[1].substring(6)) || 0

      // 更新播放列表
      playlist.value = playlistData
      localStorage.setItem('globalPlaylist', JSON.stringify(playlistData))

      // 播放指定索引的音乐
      if (playlistData[startIndex]) {
        currentMusic.value = playlistData[startIndex]
        localStorage.setItem('currentPlayingMusic', JSON.stringify(playlistData[startIndex]))

        loadLyrics(playlistData[startIndex].id)
        if (audioPlayer.value) {
          audioPlayer.value.load()
          audioPlayer.value.play()
        }
        isPlaying.value = true
      }

      // 清除hash
      history.replaceState(null, null, ' ')
    } catch (error) {
      console.error('解析播放列表数据失败:', error)
    }
  }
}

const loadPlaylist = async () => {
  try {
    // 首先尝试从 localStorage 读取播放列表
    const storedPlaylist = localStorage.getItem('globalPlaylist');
    if (storedPlaylist) {
      playlist.value = JSON.parse(storedPlaylist);
    } else {
      // 如果 localStorage 中没有播放列表，则从后端获取
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: '' })
      })
      if (response.ok) {
        const data = await response.json()
        if (data.success) {
          playlist.value = data.results || []
          // 同时保存到 localStorage
          localStorage.setItem('globalPlaylist', JSON.stringify(playlist.value));
        }
      }
    }
  } catch (error) {
    console.error('加载播放列表失败:', error)
    
    // 如果出错，尝试从 localStorage 获取播放列表作为备选
    try {
      const storedPlaylist = localStorage.getItem('globalPlaylist');
      if (storedPlaylist) {
        playlist.value = JSON.parse(storedPlaylist);
      }
    } catch (localStorageError) {
      console.error('从localStorage加载播放列表也失败:', localStorageError);
    }
  }
}

// 初始化媒体会话API
const initializeMediaSession = (music = null) => {
  if ('mediaSession' in navigator) {
    try {
      // 设置媒体操作处理程序
      navigator.mediaSession.setActionHandler('play', () => {
        if (audioPlayer.value && currentMusic.value) {
          isPlaying.value = true
          updateGlobalPlayerState()
          fadeIn(audioPlayer.value)
          // 调用play()来开始播放
          audioPlayer.value.play().catch(e => console.log('播放被阻止:', e));
        }
      })
      
      navigator.mediaSession.setActionHandler('pause', () => {
        if (audioPlayer.value && currentMusic.value) {
          isPlaying.value = false
          updateGlobalPlayerState()
          // 立即暂停音频并静音，避免重音
          if (audioPlayer.value) {
            audioPlayer.value.volume = 0;
            audioPlayer.value.pause();
          }
        }
      })
      
      navigator.mediaSession.setActionHandler('previoustrack', () => {
        playPrevious()
      })
      
      navigator.mediaSession.setActionHandler('nexttrack', () => {
        playNext() // 手动点击，使用完整流程
      })
      
      navigator.mediaSession.setActionHandler('seekbackward', () => {
        if (audioPlayer.value) {
          audioPlayer.value.currentTime = Math.max(audioPlayer.value.currentTime - 10, 0);
        }
      })
      
      navigator.mediaSession.setActionHandler('seekforward', () => {
        if (audioPlayer.value) {
          audioPlayer.value.currentTime = Math.min(audioPlayer.value.currentTime + 10, audioPlayer.value.duration);
        }
      })
      
      // 设置当前播放的音乐元数据
      if (music || currentMusic.value) {
        updateMediaSessionMetadata(music || currentMusic.value)
      }
    } catch (error) {
      console.log('媒体会话API初始化失败:', error)
    }
  }
}

// 更新媒体会话元数据
const updateMediaSessionMetadata = (music) => {
  if ('mediaSession' in navigator && music) {
    try {
      const artwork = [
        { src: getCoverUrl(music.id), sizes: '96x96', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '128x128', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '192x192', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '256x256', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '384x384', type: 'image/jpeg' },
        { src: getCoverUrl(music.id), sizes: '512x512', type: 'image/jpeg' }
      ]
      
      navigator.mediaSession.metadata = new MediaMetadata({
        title: music.title || '未知标题',
        artist: music.artist || '未知艺术家',
        album: music.album || '未知专辑',
        artwork: artwork
      })
      
      // 更新播放状态
      navigator.mediaSession.playbackState = isPlaying.value ? 'playing' : 'paused'
    } catch (error) {
      console.log('更新媒体会话元数据失败:', error)
    }
  }
}

// 更新媒体会话播放状态
const updateMediaSessionPlaybackState = () => {
  if ('mediaSession' in navigator) {
    try {
      navigator.mediaSession.playbackState = isPlaying.value ? 'playing' : 'paused'
    } catch (error) {
      console.log('更新媒体会话播放状态失败:', error)
    }
  }
}

// 更新媒体会话播放位置
const updateMediaSessionPositionState = () => {
  if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
    try {
      // 确保 currentTime 不大于 duration
      const safeCurrentTime = Math.min(currentTime.value, duration.value);
      navigator.mediaSession.setPositionState({
        duration: duration.value,
        playbackRate: 1.0,
        position: safeCurrentTime
      });
    } catch (error) {
      console.log('更新媒体会话播放位置失败:', error)
    }
  }
}

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange)
  window.removeEventListener('playerStateChange', handlePlayerStateChange)
  window.removeEventListener('forcePlay', handleForcePlay)
  window.removeEventListener('playlistUpdated', handlePlaylistUpdated)
  window.removeEventListener('hashchange', handleHashChange)
  window.removeEventListener('pauseGlobalPlayer', handlePauseGlobalPlayer)
  window.removeEventListener('playerCommand', handlePlayerCommand)
  
  // 清除媒体会话
  if ('mediaSession' in navigator) {
    navigator.mediaSession.metadata = null
    navigator.mediaSession.playbackState = 'none'
  }
})
</script>

<style scoped>
/* ============================================================================
   底部播放条（停靠式）
   ----------------------------------------------------------------------------
   结构：顶部细进度线 + [ 封面/曲名/收藏 | 播放控制 | 时间/迷你频谱 ]
   形状：圆角矩形（不使用胶囊高亮、不使用侧边彩色条）
   主题：黑偏青，全部走 --n-* 令牌
   ========================================================================== */
.global-player {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: var(--n-z-player);
  height: var(--n-player-height);
  color: var(--n-text);
  user-select: none;
  background:
    linear-gradient(180deg, rgba(9, 24, 29, 0.88), rgba(4, 12, 15, 0.97));
  backdrop-filter: blur(var(--n-blur)) saturate(140%);
  -webkit-backdrop-filter: blur(var(--n-blur)) saturate(140%);
  border-top: 1px solid var(--n-line);
}

.global-player--chrome-dark {
  background:
    linear-gradient(180deg, rgba(6, 17, 21, 0.92), rgba(2, 7, 9, 0.98));
}

/* ===== 顶部细进度线 ===== */
.gp-seek {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 14px;
  transform: translateY(-7px);
  z-index: 2;
}

.gp-seek__track {
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 3px;
  margin-top: -1.5px;
  border-radius: var(--n-radius-pill);
  background: var(--n-line-strong);
  overflow: hidden;
  transition: height var(--n-duration-fast) var(--n-ease),
    margin-top var(--n-duration-fast) var(--n-ease);
}

.gp-seek__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--n-accent-deep), var(--n-accent));
  transition: width 120ms linear;
}

/* 原生 range 铺满并透明化：保留键盘/拖动/无障碍，视觉完全自绘 */
.gp-seek__input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  opacity: 0;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  background: transparent;
}

.gp-seek:hover .gp-seek__track,
.gp-seek:focus-within .gp-seek__track {
  height: 6px;
  margin-top: -3px;
}

.gp-seek:focus-within .gp-seek__track {
  box-shadow: 0 0 0 3px var(--n-accent-soft);
}

/* ===== 主体三栏 ===== */
.gp-grid {
  height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: var(--n-space-4);
  padding: 0 var(--n-space-5);
}

/* ===== 左：封面 + 信息 + 收藏 ===== */
.gp-track {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  min-width: 0;
}

.gp-track__cover {
  flex: none;
  width: 52px;
  height: 52px;
  padding: 0;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-sunken);
  overflow: hidden;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--n-text-faint);
  transition: transform var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease);
}

.gp-track__cover:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--n-accent-line);
}

.gp-track__cover:disabled {
  cursor: default;
}

.gp-track__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.gp-track__ph {
  display: grid;
  place-items: center;
}

.gp-track__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.gp-track__title {
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
  line-height: var(--n-leading-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gp-track__artist {
  font-size: var(--n-text-xs);
  color: var(--n-text-faint);
  line-height: var(--n-leading-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.is-placeholder {
  color: var(--n-text-faint);
  font-weight: var(--n-weight-normal);
}

/* ===== 中：播放控制 ===== */
.gp-controls {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
}

.gp-icon {
  flex: none;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--n-radius-control);
  background: transparent;
  color: var(--n-text-muted);
  cursor: pointer;
  transition: background var(--n-duration-fast) var(--n-ease),
    color var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease);
}

.gp-icon--lg {
  width: 40px;
  height: 40px;
}

.gp-icon--sm {
  width: 30px;
  height: 30px;
}

.gp-icon:hover:not(:disabled) {
  background: var(--n-surface-hover);
  color: var(--n-text);
}

.gp-icon:active:not(:disabled) {
  background: var(--n-surface-active);
}

.gp-icon:disabled {
  opacity: 0.36;
  cursor: default;
}

.gp-icon.is-on {
  color: var(--n-accent);
}

.gp-icon:focus-visible,
.gp-play:focus-visible,
.gp-track__cover:focus-visible,
.gp-pop__item:focus-visible,
.gp-pop__clear:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--n-accent-soft);
}

/* 主播放键：圆角矩形实底，不用圆形 */
.gp-play {
  flex: none;
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  padding: 0;
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius);
  background: linear-gradient(160deg, var(--n-accent), var(--n-accent-deep));
  color: var(--n-text-inverse);
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(47, 159, 178, 0.28);
  transition: transform var(--n-duration-fast) var(--n-ease),
    box-shadow var(--n-duration-fast) var(--n-ease), filter var(--n-duration-fast) var(--n-ease);
}

.gp-play:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.08);
  box-shadow: 0 10px 24px rgba(47, 159, 178, 0.38);
}

.gp-play:active:not(:disabled) {
  transform: translateY(0) scale(0.97);
}

.gp-play:disabled {
  background: var(--n-surface-active);
  border-color: var(--n-line);
  color: var(--n-text-faint);
  box-shadow: none;
  cursor: default;
}

/* ===== 右：时间 + 此刻（歌词优先，否则迷你频谱）+ 播放列表 ===== */
.gp-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--n-space-3);
  min-width: 0;
}

/* 固定宽度列：时间在上，「此刻」在下（对齐 ArchoeraMusic 的 150px 列） */
.gp-now {
  flex: none;
  width: 150px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
  min-width: 0;
}

.gp-now__time {
  font-size: var(--n-text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--n-text-faint);
  white-space: nowrap;
}

.gp-now__time b {
  font-weight: var(--n-weight-medium);
  color: var(--n-text-muted);
}

.gp-now__time i {
  font-style: normal;
  margin: 0 4px;
  opacity: 0.5;
}

/* 120×12 的「此刻」槽位：有歌词显示歌词，否则显示频谱。
   ★ 两个子项都必须是确定宽度，否则尺寸会落到 <canvas> 的固有宽度上，
     把整条播放条挤爆（见 SpectrumCanvas 内的说明）。 */
.gp-now__viz {
  flex: none;
  width: 120px;
  height: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  overflow: hidden;
}

.gp-now__lyric {
  max-width: 100%;
  font-size: var(--n-text-xs);
  line-height: 1.1;
  color: var(--n-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: right;
}

.gp-now__spectrum {
  flex: none;
  width: 120px;
}

/* ===== 播放列表弹层 ===== */
.gp-pop {
  position: absolute;
  right: var(--n-space-5);
  bottom: calc(100% + 12px);
  width: min(370px, calc(100vw - 32px));
  max-height: min(54vh, 470px);
  display: flex;
  flex-direction: column;
  background: var(--n-surface-strong);
  backdrop-filter: blur(var(--n-blur-lg)) saturate(150%);
  -webkit-backdrop-filter: blur(var(--n-blur-lg)) saturate(150%);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  box-shadow: var(--n-shadow);
  overflow: hidden;
}

.gp-pop__head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-3);
  padding: var(--n-space-3) var(--n-space-3) var(--n-space-3) var(--n-space-4);
  border-bottom: 1px solid var(--n-line-subtle);
}

.gp-pop__title {
  margin: 0;
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  color: var(--n-text);
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
}

.gp-pop__count {
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-normal);
  color: var(--n-text-faint);
}

.gp-pop__head-actions {
  display: flex;
  align-items: center;
  gap: var(--n-space-1);
}

.gp-pop__clear {
  padding: 5px 10px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xs);
  background: transparent;
  color: var(--n-text-muted);
  font-size: var(--n-text-xs);
  cursor: pointer;
  transition: color var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease);
}

.gp-pop__clear:hover:not(:disabled) {
  color: var(--n-danger);
  border-color: var(--n-danger);
  background: var(--n-danger-soft);
}

.gp-pop__clear:disabled {
  opacity: 0.4;
  cursor: default;
}

.gp-pop__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--n-space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gp-pop__empty {
  margin: 0;
  padding: var(--n-space-6) var(--n-space-4);
  text-align: center;
  font-size: var(--n-text-sm);
  color: var(--n-text-faint);
}

.gp-pop__item {
  display: flex;
  align-items: center;
  gap: var(--n-space-3);
  width: 100%;
  padding: var(--n-space-2) var(--n-space-3);
  border: 1px solid transparent;
  border-radius: var(--n-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--n-duration-fast) var(--n-ease),
    border-color var(--n-duration-fast) var(--n-ease);
}

.gp-pop__item:hover {
  background: var(--n-surface-hover);
}

.gp-pop__item.is-current {
  background: var(--n-accent-soft);
  border-color: var(--n-accent-line);
}

.gp-pop__idx {
  flex: none;
  width: 22px;
  text-align: center;
  font-size: var(--n-text-xs);
  font-variant-numeric: tabular-nums;
  color: var(--n-text-faint);
  display: grid;
  place-items: center;
}

.gp-pop__item.is-current .gp-pop__idx {
  color: var(--n-accent);
}

.gp-pop__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.gp-pop__name {
  font-size: var(--n-text-sm);
  color: var(--n-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gp-pop__item.is-current .gp-pop__name {
  color: var(--n-accent-strong);
}

.gp-pop__artist {
  font-size: var(--n-text-xs);
  color: var(--n-text-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 弹层动效：自下轻微上浮 */
.gp-pop-enter-active,
.gp-pop-leave-active {
  transition: opacity var(--n-duration) var(--n-ease),
    transform var(--n-duration) var(--n-ease);
}

.gp-pop-enter-from,
.gp-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

/* ===== 响应式 ===== */
@media (max-width: 1080px) {
  .gp-now {
    width: 112px;
  }

  .gp-now__viz,
  .gp-now__spectrum {
    width: 92px;
  }
}

@media (max-width: 860px) {
  .gp-grid {
    gap: var(--n-space-2);
    padding: 0 var(--n-space-3);
  }

  .gp-track__artist,
  .gp-now {
    display: none;
  }
}

@media (max-width: 620px) {
  .gp-track__meta {
    display: none;
  }

  .gp-track__cover {
    width: 44px;
    height: 44px;
  }

  .gp-right {
    gap: var(--n-space-2);
  }
}

@media (prefers-reduced-motion: reduce) {
  .gp-seek__track,
  .gp-seek__fill,
  .gp-icon,
  .gp-play,
  .gp-track__cover,
  .gp-pop,
  .gp-pop__item {
    transition: none;
  }
}
</style>
