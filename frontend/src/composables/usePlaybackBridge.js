/**
 * usePlaybackBridge —— 播放状态/指令桥
 * ------------------------------------------------------------
 * 背景：全站唯一的播放引擎是 GlobalPlayer（`<audio>` + 播放契约都在它内部）。
 * 播放页（全屏）需要「读状态 + 发指令」，但不应该把 GlobalPlayer 的逻辑搬出来，
 * 更不应该去碰既有播放契约（localStorage 键名 / 事件名）。
 *
 * 因此这里做一层极薄的双向桥：
 *  - 读：把 GlobalPlayer 已经广播到 localStorage / window 事件里的状态，
 *        汇集成一个全站共享的 reactive 快照；
 *  - 写：通过 `playerCommand` 自定义事件把指令发回 GlobalPlayer
 *        （GlobalPlayer 侧只加一个监听器做分发，逻辑零改动）。
 *
 * 刻意保持「单向数据流」：本模块不直接改任何播放状态，只发指令。
 * 真正的状态变更一律由 GlobalPlayer 完成后广播回来。
 *
 * 契约（不得破坏）：
 *   存储：currentPlayingMusic / globalPlayerState / globalPlaylist
 *   事件：playerStateChange / playlistUpdated / forcePlay
 */
import { reactive, readonly, onMounted, onUnmounted } from 'vue'

/** 播放模式：与 GlobalPlayer 保持一致 */
export const PLAYBACK_MODES = ['list_repeat', 'single_repeat', 'shuffle']

/** 全站共享的播放快照（模块级单例） */
const state = reactive({
  /** 当前曲目对象 { id, title, artist, album, duration }，无则 null */
  currentMusic: null,
  /** 是否正在播放 */
  isPlaying: false,
  /** 当前播放秒数 */
  currentTime: 0,
  /** 总时长（秒） */
  duration: 0,
  /** 播放模式：list_repeat | single_repeat | shuffle */
  playbackMode: 'list_repeat',
  /** 当前播放列表 */
  playlist: [],
  /** 是否存在当前曲目（决定播放条/播放页是否可用） */
  hasTrack: false,
})

/** 已挂载的消费者数量：归零时解绑监听，避免无谓开销 */
let consumers = 0
let wired = false
/** 上一次已知的曲目 id：用于判断「切歌」并顺带刷新播放列表 */
let lastMusicId = null

function safeParse(raw, fallback = null) {
  if (!raw || raw === 'null' || raw === 'undefined') return fallback
  try {
    return JSON.parse(raw)
  } catch {
    return fallback
  }
}

/** 从 localStorage 汇总当前状态（首次进入 / 跨标签页变更时使用） */
function syncFromStorage() {
  const music = safeParse(localStorage.getItem('currentPlayingMusic'))
  const snapshot = safeParse(localStorage.getItem('globalPlayerState'), {}) || {}

  state.currentMusic = music || null
  state.hasTrack = !!music
  state.isPlaying = !!snapshot.isPlaying
  state.currentTime = Number(snapshot.currentTime) || 0
  state.duration = Number(snapshot.duration) || music?.duration || 0
  if (PLAYBACK_MODES.includes(snapshot.playbackMode)) {
    state.playbackMode = snapshot.playbackMode
  }
  if (music?.id !== lastMusicId) {
    lastMusicId = music?.id ?? null
    syncPlaylist()
  }
}

/** 刷新播放列表快照 */
function syncPlaylist() {
  const list = safeParse(localStorage.getItem('globalPlaylist'), [])
  state.playlist = Array.isArray(list) ? list : []
}

/** GlobalPlayer 广播的播放状态 */
function onPlayerState(event) {
  const detail = event?.detail
  if (!detail) return

  if (detail.currentMusic) {
    state.currentMusic = detail.currentMusic
    state.hasTrack = true
    if (detail.currentMusic.id !== lastMusicId) {
      lastMusicId = detail.currentMusic.id
      syncPlaylist()
    }
  }

  if (typeof detail.isPlaying === 'boolean') state.isPlaying = detail.isPlaying
  if (detail.currentTime != null) state.currentTime = Number(detail.currentTime) || 0
  if (detail.duration != null) state.duration = Number(detail.duration) || 0
  if (PLAYBACK_MODES.includes(detail.playbackMode)) state.playbackMode = detail.playbackMode
}

/** 播放列表被显式更新 */
function onPlaylistUpdated(event) {
  const list = event?.detail?.playlist
  if (Array.isArray(list)) {
    state.playlist = list
    return
  }
  syncPlaylist()
}

/** 强制播放：说明当前曲目刚被设置，重新读取一次存储 */
function onForcePlay() {
  syncFromStorage()
}

/** 跨标签页：另一个标签改了播放状态 */
function onStorage(event) {
  if (!event?.key) return
  if (
    event.key === 'currentPlayingMusic' ||
    event.key === 'globalPlayerState' ||
    event.key === 'globalPlaylist'
  ) {
    syncFromStorage()
  }
}

function wire() {
  if (wired) return
  wired = true
  syncFromStorage()
  syncPlaylist()
  window.addEventListener('playerStateChange', onPlayerState)
  window.addEventListener('playlistUpdated', onPlaylistUpdated)
  window.addEventListener('forcePlay', onForcePlay)
  window.addEventListener('storage', onStorage)
}

function unwire() {
  if (!wired) return
  wired = false
  window.removeEventListener('playerStateChange', onPlayerState)
  window.removeEventListener('playlistUpdated', onPlaylistUpdated)
  window.removeEventListener('forcePlay', onForcePlay)
  window.removeEventListener('storage', onStorage)
}

/**
 * 发送播放指令给 GlobalPlayer。
 * @param {'toggle'|'play'|'pause'|'next'|'prev'|'seek'|'cycleMode'|'playIndex'|'clearPlaylist'} action
 * @param {{ time?: number, index?: number }} [payload]
 */
export function sendPlayerCommand(action, payload = {}) {
  window.dispatchEvent(new CustomEvent('playerCommand', { detail: { action, ...payload } }))
}

/**
 * 订阅播放状态并发送指令。
 * 必须在组件 setup 中调用（内部使用 onMounted / onUnmounted 管理引用计数）。
 */
export function usePlaybackBridge() {
  onMounted(() => {
    consumers += 1
    wire()
    syncFromStorage()
  })

  onUnmounted(() => {
    consumers -= 1
    if (consumers <= 0) {
      consumers = 0
      unwire()
    }
  })

  return {
    /** 只读快照（请勿直接改；所有变更都走 GlobalPlayer） */
    playback: readonly(state),
    sendPlayerCommand,
  }
}
