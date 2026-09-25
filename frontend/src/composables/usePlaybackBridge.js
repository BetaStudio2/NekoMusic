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
  } else if ('currentMusic' in detail) {
    // 明确广播为「无曲目」（如清空播放列表）→ 跟着收回
    state.currentMusic = null
    state.hasTrack = false
    lastMusicId = null
    syncPlaylist()
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

/** 读取当前播放列表（容错，损坏时回退空数组） */
function readPlaylist() {
  const list = safeParse(localStorage.getItem('globalPlaylist'), [])
  return Array.isArray(list) ? list : []
}

/** 写入播放列表并广播，确保 GlobalPlayer 与桥接层同步 */
function writePlaylist(list) {
  localStorage.setItem('globalPlaylist', JSON.stringify(list))
  window.dispatchEvent(new CustomEvent('playlistUpdated', { detail: { playlist: list } }))
}

/**
 * 统一「开始播放某首曲目」入口。
 * ------------------------------------------------------------
 * 背景：此前各页面各自写 localStorage + 事件（甚至只写 localStorage 就跳路由），
 * 与 GlobalPlayer 的响应式状态互相覆盖，会出现「点新歌跳回上一首」。
 * 现在所有页面都调用这里：状态由本模块写入，真正的切歌/起播交给
 * GlobalPlayer 的 `playTrack` 指令（唯一所有权），避免多路写状态竞态。
 *
 * @param {object} track 曲目对象（至少含 id；title/artist/album/duration 用于展示）
 */
export function playTrack(track) {
  if (!track || track.id == null) return

  const list = readPlaylist()
  const existing = list.findIndex((item) => String(item?.id) === String(track.id))
  let index = existing
  if (existing === -1) {
    list.push(track)
    index = list.length - 1
  } else {
    // 用最新元数据覆盖旧条目（标题 / 时长可能已更新）
    list[existing] = track
  }
  writePlaylist(list)

  localStorage.setItem('currentPlayingMusic', JSON.stringify(track))
  localStorage.setItem(
    'globalPlayerState',
    JSON.stringify({ isPlaying: true, currentTime: 0.1, duration: track.duration || 0 }),
  )

  window.dispatchEvent(
    new CustomEvent('playerCommand', { detail: { action: 'playTrack', track, index } }),
  )
}

/**
 * 从「当前列表」起播指定曲目：整份列表成为播放队列，并从该曲目开始。
 * ------------------------------------------------------------
 * 列表页点单曲的通用语义：队列 = 当前列表。这样「上一首 / 下一首」能在
 * 该列表内前后切换；若只把单曲塞进队列，会出现「点了一首却无法下一首」。
 * 列表里找不到该曲目时退回单曲播放，保证仍能出声。
 *
 * @param {object} track 被点的曲目（至少含 id）
 * @param {object[]} list 该曲目所在的列表
 */
export function playTrackInList(track, list) {
  if (!track || track.id == null) return
  const tracks = Array.isArray(list) ? list : []
  const index = tracks.findIndex((item) => String(item?.id) === String(track.id))
  if (index >= 0) {
    playTracks(tracks, index)
  } else {
    playTrack(track)
  }
}

/**
 * 统一「开始播放整个列表」入口。
 *
 * @param {object[]} tracks 完整播放列表（会整体替换 globalPlaylist）
 * @param {number} [startIndex=0] 起始下标
 */
export function playTracks(tracks, startIndex = 0) {
  if (!Array.isArray(tracks) || tracks.length === 0) return

  const list = tracks.slice()
  const index = Math.max(0, Math.min(Number(startIndex) || 0, list.length - 1))
  const track = list[index]
  if (!track || track.id == null) return

  writePlaylist(list)

  localStorage.setItem('currentPlayingMusic', JSON.stringify(track))
  localStorage.setItem(
    'globalPlayerState',
    JSON.stringify({ isPlaying: true, currentTime: 0.1, duration: track.duration || 0 }),
  )

  window.dispatchEvent(
    new CustomEvent('playerCommand', { detail: { action: 'playTrack', track, index } }),
  )
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
