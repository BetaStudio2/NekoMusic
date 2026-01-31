import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage } from 'electron'
import path from 'path'
import { fileURLToPath } from 'url'
import MediaService from 'electron-media-service'

// 在 ES 模块中获取 __dirname 的等效值
const __dirname = path.dirname(fileURLToPath(import.meta.url))

let win
let tray

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: false,
    autoHideMenuBar: true,
    icon: path.join(__dirname, '../public/icon.png'),
    title: 'Neko云音乐',
    webPreferences: {
      preload: path.join(__dirname, '../electron/preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      devTools: process.env.NODE_ENV === 'development',
    },
    backgroundColor: '#667eea',
  })
  
  // 监听窗口最大化事件
  win.on('maximize', () => {
    win.webContents.send('window-maximized')
  })
  
  // 监听窗口还原事件
  win.on('unmaximize', () => {
    win.webContents.send('window-unmaximized')
  })

  // 禁用 DevTools 和全屏快捷键
  win.webContents.on('before-input-event', (event, input) => {
    // 禁用 DevTools 快捷键
    if (input.control && input.shift && (input.key === 'I' || input.key === 'i')) {
      event.preventDefault()
    }
    if (input.control && (input.key === 'F12' || input.key === 'f12')) {
      event.preventDefault()
    }
    if (input.alt && input.key === 'F12' || input.alt && input.key === 'f12') {
      event.preventDefault()
    }
    // 禁用 F11 全屏快捷键
    if (input.key === 'F11' || input.key === 'f11') {
      event.preventDefault()
    }
  })

  // 开发模式加载 Vite 开发服务器
  if (process.env.NODE_ENV === 'development') {
    win.loadURL('http://localhost:5173')
    // 开发模式下打开开发者工具
    win.webContents.openDevTools()
  } else {
    // 生产模式加载打包后的文件
    win.loadFile(path.join(__dirname, '../dist/index.html'))
  }

  win.on('close', (event) => {
    if (!app.isQuitting) {
      event.preventDefault()
      win.hide()
    }
  })

  win.on('closed', () => {
    win = null
  })
}

// 全局状态
let playerState = {
  currentMusic: null,
  isPlaying: false,
  playMode: 'list', // list, single, shuffle
  volume: 80,
  lyricsEnabled: false,
  desktopLyricsEnabled: false
}

function createTray() {
  const iconPath = path.join(__dirname, '../public/icon.png')
  const trayIcon = nativeImage.createFromPath(iconPath)
  trayIcon.resize({ width: 16, height: 16 })
  
  tray = new Tray(trayIcon)
  
  // 加载图标
  const loadIcons = () => {
    const icons = {}
    const iconList = [
      'tray-previous',
      'tray-play',
      'tray-pause',
      'tray-next',
      'tray-favorite',
      'tray-shuffle',
      'tray-setting',
      'tray-list-loop',
      'tray-single-loop',
      'tray-minimize',
      'tray-lyrics',
      'tray-exit'
    ]
    
    iconList.forEach(name => {
      try {
        const icon = nativeImage.createFromPath(path.join(__dirname, `../public/${name}.png`))
        icon.resize({ width: 18, height: 18 })
        icons[name] = icon
      } catch (e) {
        console.warn(`Failed to load icon: ${name}`, e)
      }
    })
    
    return icons
  }
  
  const icons = loadIcons()
  
  // 获取播放器状态
  const syncPlayerState = async () => {
    if (!win) return
    
    try {
      const musicJson = await win.webContents.executeJavaScript('localStorage.getItem("currentMusic")')
      if (musicJson) {
        playerState.currentMusic = JSON.parse(musicJson)
      } else {
        playerState.currentMusic = null
      }
      
      // 同步播放状态（通过 window 对象获取实时状态）
      const state = await win.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `)
      
      if (state) {
        playerState.isPlaying = state.isPlaying
      }
    } catch (e) {
      console.error('同步播放状态失败:', e)
    }
  }
  
  // 更新托盘菜单
  const updateContextMenu = async () => {
    await syncPlayerState()
    
    const music = playerState.currentMusic
    
    // 构建当前播放信息标题
    let playingLabel = '暂无播放'
    if (music) {
      const title = music.title || '未知歌曲'
      const artist = music.artist || '未知艺术家'
      // 截断过长文本
      const displayTitle = title.length > 15 ? title.substring(0, 15) + '...' : title
      playingLabel = `${displayTitle} - ${artist}`
    }
    
    const menuTemplate = [
      // 顶部：当前播放信息
      {
        label: playingLabel,
        enabled: false
      },
      { type: 'separator' },
      
      // 退出
      {
        label: '退出',
        icon: icons['tray-exit'],
        click: () => {
          app.isQuitting = true
          app.quit()
        }
      }
    ]
    
    const contextMenu = Menu.buildFromTemplate(menuTemplate)
    tray.setContextMenu(contextMenu)
    
    // 更新提示信息
    if (music) {
      tray.setToolTip(`正在播放: ${music.title} - ${music.artist}`)
    } else {
      tray.setToolTip('Neko云音乐')
    }
  }
  
  // 初始化菜单
  updateContextMenu()
  
  // 监听渲染进程状态变化
  ipcMain.on('player-state-changed', (event, state) => {
    if (state) {
      playerState = { ...playerState, ...state }
      updateContextMenu()
    }
  })
  
  // 监听音乐播放事件
  ipcMain.on('music-play', (event, music) => {
    playerState.currentMusic = music
    playerState.isPlaying = true
    updateContextMenu()
  })
  
  // 监听播放状态变化
  ipcMain.on('play-state-changed', (event, isPlaying) => {
    playerState.isPlaying = isPlaying
    updateContextMenu()
  })
  
  // 点击托盘图标
  tray.on('click', () => {
    if (win) {
      if (win.isVisible()) {
        if (win.isFocused()) {
          win.hide()
        } else {
          win.focus()
        }
      } else {
        win.show()
        win.focus()
      }
    }
  })
  
  // 定期同步状态（可选）
  setInterval(updateContextMenu, 5000)
}

// 窗口控制 IPC 处理
ipcMain.on('window-minimize', () => {
  if (win) win.minimize()
})

ipcMain.on('window-maximize', () => {
  if (win) {
    if (win.isMaximized()) {
      win.unmaximize()
    } else {
      win.maximize()
    }
  }
})

ipcMain.on('window-close', () => {
  if (win) win.hide()
})

// 更新媒体信息
ipcMain.on('update-media-info', (event, { music, isPlaying, currentTime, duration }) => {
  if (!mediaService) return
  
  if (music) {
    mediaService.setMetadata({
      title: music.title || '未知歌曲',
      artist: music.artist || '未知艺术家',
      album: music.album || '',
      cover: `https://music.cnmsb.xin/api/music/cover/${music.id}`,
      duration: duration || 0
    })
  }
  
  mediaService.setPlaybackState(isPlaying ? 'playing' : 'paused', currentTime || 0)
})

app.on('ready', () => {
  createWindow()
  createTray()
  
  // 初始化媒体服务
  initMediaService()
})

// 媒体服务实例
let mediaService = null

// 初始化媒体服务
function initMediaService() {
  mediaService = new MediaService({
    name: 'Neko云音乐',
    controls: ['play', 'pause', 'next', 'previous'],
  })
  
  // 播放/暂停
  mediaService.on('play', () => {
    if (win) win.webContents.send('media-play-pause')
  })
  
  mediaService.on('pause', () => {
    if (win) win.webContents.send('media-play-pause')
  })
  
  // 下一首
  mediaService.on('next', () => {
    if (win) win.webContents.send('media-next')
  })
  
  // 上一首
  mediaService.on('previous', () => {
    if (win) win.webContents.send('media-previous')
  })
  
  console.log('媒体服务已初始化')
}

app.on('will-quit', () => {
  if (mediaService) {
    mediaService.destroy()
    mediaService = null
  }
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('activate', () => {
  if (win === null) {
    createWindow()
  } else {
    win.show()
  }
})