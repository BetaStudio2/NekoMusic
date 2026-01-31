import { contextBridge, ipcRenderer } from 'electron'

// 暴露安全的 API 给渲染进程
contextBridge.exposeInMainWorld('electronAPI', {
  // 窗口控制
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),
  
  // 播放器状态通知
  notifyPlayerState: (state) => ipcRenderer.send('player-state-changed', state),
  notifyMusicPlay: (music) => ipcRenderer.send('music-play', music),
  notifyPlayState: (isPlaying) => ipcRenderer.send('play-state-changed', isPlaying),
  
  // 其他可以暴露的 API
  platform: process.platform,
  arch: process.arch,
})

// 监听主进程事件
ipcRenderer.on('tray-previous', () => {
  window.dispatchEvent(new CustomEvent('tray-previous'))
})

ipcRenderer.on('tray-play-pause', () => {
  window.dispatchEvent(new CustomEvent('tray-play-pause'))
})

ipcRenderer.on('tray-next', () => {
  window.dispatchEvent(new CustomEvent('tray-next'))
})

ipcRenderer.on('tray-favorite', () => {
  window.dispatchEvent(new CustomEvent('tray-favorite'))
})

ipcRenderer.on('tray-set-repeat', (event, mode) => {
  window.dispatchEvent(new CustomEvent('tray-set-repeat', { detail: mode }))
})

ipcRenderer.on('tray-toggle-shuffle', (event, isShuffle) => {
  window.dispatchEvent(new CustomEvent('tray-toggle-shuffle', { detail: isShuffle }))
})

ipcRenderer.on('tray-toggle-desktop-lyrics', (event, enabled) => {
  window.dispatchEvent(new CustomEvent('tray-toggle-desktop-lyrics', { detail: enabled }))
})

ipcRenderer.on('navigate-to-settings', () => {
  window.dispatchEvent(new CustomEvent('navigate-to-settings'))
})

// 监听主进程事件
window.addEventListener('DOMContentLoaded', () => {
  console.log('Electron preload script loaded')
})