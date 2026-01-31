import { contextBridge, ipcRenderer } from 'electron'

// 暴露安全的 API 给渲染进程
contextBridge.exposeInMainWorld('electronAPI', {
  // 窗口控制
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),
  
  // 其他可以暴露的 API
  platform: process.platform,
  arch: process.arch,
})

// 监听主进程事件
window.addEventListener('DOMContentLoaded', () => {
  console.log('Electron preload script loaded')
})