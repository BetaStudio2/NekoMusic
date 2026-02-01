<template>
  <div class="local-music-view">
    <div class="header">
      <div class="header-left">
        <h2 class="section-title">本地音乐</h2>
        <div class="header-info">
          <span v-if="musicList.length > 0" class="music-count">{{ musicList.length }} 首歌曲</span>
          <span class="scan-path">扫描目录: {{ defaultScanPath || '加载中...' }}</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn-scan" @click="scanDefaultDirectory" title="扫描下载目录/NekoMusic">
          <svg viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
          </svg>
          <span>扫描默认目录</span>
        </button>
        <button class="btn-primary" @click="importMusic">
          <svg viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"/>
          </svg>
          <span>导入音乐</span>
        </button>
        <button v-if="musicList.length > 0" class="btn-secondary" @click="clearLibrary">
          <svg viewBox="0 0 24 24" width="16" height="16">
            <path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
          </svg>
          <span>清空</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <div v-else-if="musicList.length === 0" class="empty">
      <div class="empty-icon">
        <svg viewBox="0 0 24 24" width="64" height="64">
          <path fill="currentColor" d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"/>
        </svg>
      </div>
      <p>暂无本地音乐</p>
      <p class="hint">点击"扫描默认目录"扫描下载目录/NekoMusic，或点击"导入音乐"手动选择</p>
    </div>

    <div v-else class="music-list-container">
      <div class="list-header-row">
        <span class="col-index">#</span>
        <span class="col-title">标题</span>
        <span class="col-artist">艺术家</span>
        <span class="col-album">专辑</span>
        <span class="col-duration">时长</span>
        <span class="col-actions">操作</span>
      </div>
      <div class="music-items">
        <div 
          v-for="(music, index) in musicList" 
          :key="music.id"
          :class="['music-item', { playing: currentMusic?.id === music.id }]"
          @dblclick="playMusic(music)"
        >
          <span class="col-index">
            <span v-if="currentMusic?.id === music.id" class="playing-icon">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
              </svg>
            </span>
            <span v-else>{{ index + 1 }}</span>
          </span>
          <span class="col-title">
            <div class="cover-wrapper">
              <img :src="getCoverUrl(music)" alt="封面" class="cover" />
              <div class="cover-overlay">
                <svg class="play-overlay-icon" viewBox="0 0 24 24" width="24" height="24">
                  <path fill="currentColor" d="M8 5v14l11-7z"/>
                </svg>
              </div>
            </div>
            <span class="title-text">{{ music.title }}</span>
          </span>
          <span class="col-artist">{{ music.artist }}</span>
          <span class="col-album">{{ music.album }}</span>
          <span class="col-duration">{{ formatDuration(music.duration) }}</span>
          <span class="col-actions">
            <button class="action-btn" @click.stop="playMusic(music)" title="播放">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path fill="currentColor" d="M8 5v14l11-7z"/>
              </svg>
            </button>
            <button class="action-btn" @click.stop="addToPlaylist(music)" title="添加到播放列表">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path fill="currentColor" d="M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z"/>
              </svg>
            </button>
            <button class="action-btn delete-btn" @click.stop="removeMusic(music)" title="删除">
              <svg viewBox="0 0 24 24" width="16" height="16">
                <path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
              </svg>
            </button>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const musicList = ref([])
const loading = ref(false)
const currentMusic = ref(null)
const defaultScanPath = ref('')

// 获取默认扫描目录
const getDefaultScanDirectory = async () => {
  if (!window.electronAPI) {
    return '下载目录/NekoMusic'
  }
  
  try {
    const result = await window.electronAPI.getDefaultScanPath()
    if (result.success) {
      defaultScanPath.value = result.path
      return result.path
    }
  } catch (error) {
    console.error('获取默认扫描目录失败:', error)
  }
  
  return '下载目录/NekoMusic'
}

const loadMusicLibrary = async () => {
  if (!window.electronAPI) {
    console.error('Electron API 不可用')
    return
  }

  loading.value = true
  try {
    const result = await window.electronAPI.getLocalMusicLibrary()
    if (result && result.success) {
      musicList.value = result.music || []
    } else {
      musicList.value = []
    }
  } catch (error) {
    console.error('加载本地音乐库失败:', error)
    musicList.value = []
  } finally {
    loading.value = false
  }
}

const scanDefaultDirectory = async () => {
  if (!window.electronAPI) {
    console.error('Electron API 不可用')
    return
  }

  loading.value = true
  try {
    const result = await window.electronAPI.scanDefaultDirectory()
    if (result.success) {
      musicList.value = await loadMusicLibrary()
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: result.message || '扫描完成', 
          type: 'success' 
        }
      }))
    } else {
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: result.message || '扫描失败', 
          type: 'error' 
        }
      }))
    }
  } catch (error) {
    console.error('扫描默认目录失败:', error)
    window.dispatchEvent(new CustomEvent('show-toast', {
      detail: { 
        message: '扫描失败: ' + error.message, 
        type: 'error' 
      }
    }))
  } finally {
    loading.value = false
  }
}

const importMusic = async () => {
  if (!window.electronAPI) {
    console.error('Electron API 不可用')
    return
  }

  try {
    const result = await window.electronAPI.selectLocalMusicFiles()
    if (result.success) {
      musicList.value = await loadMusicLibrary()
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: `成功导入 ${result.music.length} 首音乐`, 
          type: 'success' 
        }
      }))
    } else {
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: result.message || '导入失败', 
          type: 'error' 
        }
      }))
    }
  } catch (error) {
    console.error('导入音乐失败:', error)
    window.dispatchEvent(new CustomEvent('show-toast', {
      detail: { 
        message: '导入失败: ' + error.message, 
        type: 'error' 
      }
    }))
  }
}

const removeMusic = async (music) => {
  if (!window.electronAPI) {
    return
  }

  if (!confirm(`确定要删除"${music.title}"吗？`)) {
    return
  }

  try {
    const result = await window.electronAPI.removeLocalMusic(music.id)
    if (result.success) {
      musicList.value = musicList.value.filter(m => m.id !== music.id)
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: '删除成功', 
          type: 'success' 
        }
      }))
    } else {
      window.dispatchEvent(new CustomEvent('show-toast', {
        detail: { 
          message: result.message || '删除失败', 
          type: 'error' 
        }
      }))
    }
  } catch (error) {
    console.error('删除音乐失败:', error)
    window.dispatchEvent(new CustomEvent('show-toast', {
      detail: { 
        message: '删除失败: ' + error.message, 
        type: 'error' 
      }
    }))
  }
}

const clearLibrary = async () => {
  if (!confirm('确定要清空所有本地音乐吗？此操作不可恢复。')) {
    return
  }

  for (const music of musicList.value) {
    await window.electronAPI.removeLocalMusic(music.id)
  }
  
  musicList.value = []
  window.dispatchEvent(new CustomEvent('show-toast', {
    detail: { 
      message: '已清空本地音乐库', 
      type: 'success' 
    }
  }))
}

const playMusic = (music) => {
  currentMusic.value = music
  localStorage.setItem('currentMusic', JSON.stringify(music))
  
  window.dispatchEvent(new CustomEvent('add-to-playlist', { detail: music }))
  window.dispatchEvent(new CustomEvent('music-play', { detail: music }))
}

const addToPlaylist = (music) => {
  window.dispatchEvent(new CustomEvent('add-to-playlist', { detail: music }))
  window.dispatchEvent(new CustomEvent('show-toast', {
    detail: { 
      message: '已添加到播放列表', 
      type: 'success' 
    }
  }))
}

const getCoverUrl = (music) => {
  if (music.isLocal) {
    return 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44"><defs><linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:%23667eea;stop-opacity:1"/><stop offset="100%" style="stop-color:%23764ba2;stop-opacity:1"/></linearGradient></defs><rect width="44" height="44" fill="url(%23grad)" rx="8"/><text x="22" y="28" font-family="Arial" font-size="16" fill="white" text-anchor="middle" font-weight="bold">🎵</text></svg>'
  }
  return `https://music.cnmsb.xin/api/music/cover/${music.id}`
}

const formatDuration = (seconds) => {
  if (!seconds || seconds === 0) return '--:--'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

onMounted(() => {
  const savedMusic = localStorage.getItem('currentMusic')
  if (savedMusic) {
    try {
      currentMusic.value = JSON.parse(savedMusic)
    } catch (e) {
      console.error('解析当前音乐失败:', e)
    }
  }

  getDefaultScanDirectory()
  loadMusicLibrary()
})
</script>

<style scoped>
.local-music-view {
  padding: 24px;
  height: 100%;
  overflow-y: auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.music-count {
  font-size: 14px;
  color: var(--text-secondary);
  background: rgba(102, 126, 234, 0.1);
  padding: 4px 12px;
  border-radius: 12px;
}

.scan-path {
  font-size: 13px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.scan-path::before {
  content: '📁';
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.btn-scan {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  background: rgba(76, 175, 80, 0.1);
  color: #4CAF50;
  border: 1px solid rgba(76, 175, 80, 0.2);
}

.btn-scan:hover {
  background: rgba(76, 175, 80, 0.2);
  transform: translateY(-1px);
}

.btn-primary, .btn-secondary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.btn-secondary {
  background: rgba(255, 59, 48, 0.1);
  color: #ff3b30;
  border: 1px solid rgba(255, 59, 48, 0.2);
}

.btn-secondary:hover {
  background: rgba(255, 59, 48, 0.2);
  transform: translateY(-1px);
}

.loading, .empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(102, 126, 234, 0.1);
  border-top: 4px solid var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: var(--primary);
}

.hint {
  font-size: 13px;
  margin-top: 8px;
  opacity: 0.7;
}

.music-list-container {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.list-header-row {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 600;
  border-bottom: 1px solid var(--border-light);
}

.music-items {
  max-height: calc(100vh - 300px);
  overflow-y: auto;
}

.music-item {
  display: grid;
  grid-template-columns: 50px 1fr 1fr 1fr 80px 120px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.2s;
  align-items: center;
}

.music-item:last-child {
  border-bottom: none;
}

.music-item:hover {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.03) 0%, rgba(118, 75, 162, 0.03) 100%);
}

.music-item:hover .cover-wrapper .cover-overlay {
  opacity: 1;
}

.music-item.playing {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
}

.music-item.playing .title-text {
  color: var(--primary);
  font-weight: 600;
}

.music-item.playing .cover {
  box-shadow: 0 0 20px rgba(102, 126, 234, 0.3);
}

.col-index {
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
}

.playing-icon {
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-title {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.cover-wrapper {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: 8px;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: all 0.2s;
}

.cover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
}

.play-overlay-icon {
  color: white;
  transform: scale(0.8);
  transition: all 0.2s;
}

.cover-wrapper:hover .play-overlay-icon {
  transform: scale(1);
}

.title-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

.col-artist, .col-album {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-duration {
  font-size: 13px;
  color: var(--text-muted);
  text-align: center;
  font-weight: 500;
}

.col-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.action-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary);
  transform: scale(1.1);
}

.action-btn.delete-btn:hover {
  background: rgba(255, 59, 48, 0.1);
  color: #ff3b30;
}

.music-items::-webkit-scrollbar {
  width: 6px;
}

.music-items::-webkit-scrollbar-track {
  background: transparent;
}

.music-items::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.music-items::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.2);
}
</style>