import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, shell, dialog, protocol } from 'electron'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'

// 在 ES 模块中获取 __dirname 的等效值
const __dirname = path.dirname(fileURLToPath(import.meta.url))

// 本地音乐库数据存储路径
const MUSIC_LIBRARY_PATH = path.join(app.getPath('userData'), 'music-library.json')
const MUSIC_CACHE_DIR = path.join(app.getPath('userData'), 'music-cache')

// 确保缓存目录存在
if (!fs.existsSync(MUSIC_CACHE_DIR)) {
  fs.mkdirSync(MUSIC_CACHE_DIR, { recursive: true })
}

// 读取本地音乐库
function loadMusicLibrary() {
  try {
    if (fs.existsSync(MUSIC_LIBRARY_PATH)) {
      const data = fs.readFileSync(MUSIC_LIBRARY_PATH, 'utf-8')
      return JSON.parse(data)
    }
  } catch (error) {
    console.error('读取音乐库失败:', error)
  }
  return []
}

// 保存本地音乐库
function saveMusicLibrary(library) {
  try {
    fs.writeFileSync(MUSIC_LIBRARY_PATH, JSON.stringify(library, null, 2))
    return true
  } catch (error) {
    console.error('保存音乐库失败:', error)
    return false
  }
}

// 获取音频文件元信息
async function getAudioMetadata(filePath) {
  try {
    const stat = fs.statSync(filePath)
    const basename = path.basename(filePath, path.extname(filePath))
    
    // 简单的文件名解析（实际项目可使用 music-metadata 库）
    const parts = basename.split(' - ')
    const title = parts[parts.length - 1] || basename
    const artist = parts.length > 1 ? parts[0] : '未知艺术家'
    
    return {
      id: `local-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      title: title,
      artist: artist,
      album: '本地音乐',
      duration: 0, // 需要实际解析音频文件获取
      filePath: filePath,
      isLocal: true,
      addedAt: Date.now()
    }
  } catch (error) {
    console.error('获取音频元信息失败:', error)
    return null
  }
}

// 获取默认扫描目录（下载目录/NekoMusic）
function getDefaultScanDirectory() {
  const downloadsPath = app.getPath('downloads')
  const nekoMusicPath = path.join(downloadsPath, 'NekoMusic')
  return nekoMusicPath
}

// 递归扫描目录中的音频文件
function scanDirectoryForMusic(dirPath, supportedExtensions = ['.mp3', '.wav', '.flac', '.m4a', '.ogg', '.aac']) {
  const musicFiles = []
  
  try {
    // 检查目录是否存在
    if (!fs.existsSync(dirPath)) {
      console.log('默认扫描目录不存在:', dirPath)
      return musicFiles
    }
    
    // 递归扫描
    function scan(currentPath) {
      const items = fs.readdirSync(currentPath)
      
      for (const item of items) {
        const itemPath = path.join(currentPath, item)
        const stat = fs.statSync(itemPath)
        
        if (stat.isDirectory()) {
          // 递归扫描子目录
          scan(itemPath)
        } else {
          // 检查文件扩展名
          const ext = path.extname(item).toLowerCase()
          if (supportedExtensions.includes(ext)) {
            musicFiles.push(itemPath)
          }
        }
      }
    }
    
    scan(dirPath)
    console.log(`扫描目录 ${dirPath}，找到 ${musicFiles.length} 个音频文件`)
  } catch (error) {
    console.error('扫描目录失败:', error)
  }
  
  return musicFiles
}

// IPC 处理：扫描默认目录
ipcMain.handle('scan-default-directory', async () => {
  try {
    const scanPath = getDefaultScanDirectory()
    console.log('开始扫描默认目录:', scanPath)
    
    // 扫描目录获取音频文件列表
    const musicFiles = scanDirectoryForMusic(scanPath)
    
    if (musicFiles.length === 0) {
      return { success: true, message: '默认目录中没有找到音频文件', music: [], total: 0 }
    }
    
    const library = loadMusicLibrary()
    const addedMusic = []
    
    // 添加新文件到音乐库
    for (const filePath of musicFiles) {
      // 检查是否已存在
      const exists = library.some(m => m.filePath === filePath)
      if (exists) {
        continue
      }
      
      const metadata = await getAudioMetadata(filePath)
      if (metadata) {
        library.push(metadata)
        addedMusic.push(metadata)
      }
    }
    
    if (addedMusic.length > 0) {
      saveMusicLibrary(library)
      return { 
        success: true, 
        message: `成功导入 ${addedMusic.length} 首音乐`, 
        music: addedMusic, 
        total: library.length,
        scanned: musicFiles.length
      }
    } else {
      return { 
        success: true, 
        message: '所有文件已存在于音乐库中', 
        music: [], 
        total: library.length,
        scanned: musicFiles.length
      }
    }
  } catch (error) {
    console.error('扫描默认目录失败:', error)
    return { success: false, message: error.message }
  }
})

// IPC 处理：选择本地音乐文件
ipcMain.handle('select-local-music-files', async () => {
  const result = await dialog.showOpenDialog({
    properties: ['openFile', 'multiSelections'],
    filters: [
      { name: '音频文件', extensions: ['mp3', 'wav', 'flac', 'm4a', 'ogg', 'aac'] }
    ]
  })
  
  if (result.canceled || result.filePaths.length === 0) {
    return { success: false, message: '未选择文件' }
  }
  
  try {
    const library = loadMusicLibrary()
    const addedMusic = []
    
    for (const filePath of result.filePaths) {
      // 检查是否已存在
      const exists = library.some(m => m.filePath === filePath)
      if (exists) {
        continue
      }
      
      const metadata = await getAudioMetadata(filePath)
      if (metadata) {
        library.push(metadata)
        addedMusic.push(metadata)
      }
    }
    
    if (addedMusic.length > 0) {
      saveMusicLibrary(library)
      return { success: true, music: addedMusic, total: library.length }
    } else {
      return { success: false, message: '文件已存在或解析失败' }
    }
  } catch (error) {
    console.error('导入音乐失败:', error)
    return { success: false, message: error.message }
  }
})

// IPC 处理：获取本地音乐库
ipcMain.handle('get-local-music-library', async () => {
  try {
    const library = loadMusicLibrary()
    return { success: true, music: library }
  } catch (error) {
    console.error('获取音乐库失败:', error)
    return { success: false, message: error.message }
  }
})

// IPC 处理：移除本地音乐
ipcMain.handle('remove-local-music', async (event, musicId) => {
  try {
    const library = loadMusicLibrary()
    const index = library.findIndex(m => m.id === musicId)
    
    if (index === -1) {
      return { success: false, message: '音乐不存在' }
    }
    
    library.splice(index, 1)
    saveMusicLibrary(library)
    
    return { success: true, total: library.length }
  } catch (error) {
    console.error('移除音乐失败:', error)
    return { success: false, message: error.message }
  }
})

// 注册本地文件协议
function registerLocalFileProtocol() {
  protocol.registerBufferProtocol('local-file', (request, callback) => {
    // 从 URL 中提取文件路径
    const filePath = request.url.replace('local-file:///', '')
    
    try {
      // 读取文件
      const fileData = fs.readFileSync(filePath)
      const ext = path.extname(filePath).toLowerCase()
      
      // 根据 MIME 类型设置
      const mimeType = {
        '.mp3': 'audio/mpeg',
        '.wav': 'audio/wav',
        '.flac': 'audio/flac',
        '.m4a': 'audio/mp4',
        '.ogg': 'audio/ogg',
        '.aac': 'audio/aac'
      }[ext] || 'application/octet-stream'
      
      callback({
        data: fileData,
        mimeType: mimeType
      })
    } catch (error) {
      console.error('读取本地文件失败:', error)
      callback({ error: -2 }) // 读取失败
    }
  })
}

let win
let tray
app.isQuitting = false  // 声明退出标志

// 防止多实例运行
const gotTheLock = app.requestSingleInstanceLock()

if (!gotTheLock) {
  console.log('已经有实例在运行，退出新实例')
  app.quit()
  process.exit(0)
} else {
  app.on('second-instance', () => {
    // 如果有第二个实例尝试启动，聚焦到现有窗口
    if (win) {
      if (win.isMinimized()) win.restore()
      win.focus()
    }
  })
}

function createWindow() {
  console.log('createWindow: 开始创建窗口')
  // 打包后图标在 app.asar 的 public 目录中，开发环境在 public 目录
  const isDev = process.env.NODE_ENV === 'development'
  const iconPath = isDev
    ? path.join(__dirname, '../public/icon.png')
    : path.join(app.getAppPath(), 'public/icon.png')
  // 打包后 preload.cjs 在 dist-electron 目录下，使用相对路径
  const preloadPath = path.join(__dirname, './preload.cjs')
  console.log('createWindow: 图标路径 =', iconPath)
  console.log('createWindow: preload 路径 =', preloadPath)
  
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: false,
    autoHideMenuBar: true,
    icon: iconPath,
    title: 'Neko云音乐',
    webPreferences: {
      preload: preloadPath,
      nodeIntegration: false,
      contextIsolation: true,
      devTools: true,
      sandbox: false,  // 关闭沙箱以允许 localStorage 访问
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

  // 根据环境判断加载开发服务器还是生产文件
  if (isDev || !app.isPackaged) {
    console.log('createWindow: 加载开发服务器 http://localhost:5173')
    win.loadURL('http://localhost:5173')
  } else {
    console.log('createWindow: 加载生产文件')
    const appPath = app.getAppPath()
    const prodPath = path.join(appPath, 'dist/index.html')
    console.log('生产文件路径:', prodPath)
    win.loadFile(prodPath)
  }

  // 打开开发者工具（开发和生产模式都打开）
  win.webContents.openDevTools()

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
  const isDev = process.env.NODE_ENV === 'development'
  const iconPath = isDev
    ? path.join(__dirname, '../public/icon.png')
    : path.join(app.getAppPath(), 'public/icon.png')
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

    const iconDir = isDev ? path.join(__dirname, '../public') : path.join(app.getAppPath(), 'public')

    iconList.forEach(name => {
      try {
        const icon = nativeImage.createFromPath(path.join(iconDir, `${name}.png`))
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

// 文件保存 IPC 处理
ipcMain.handle('save-file', async (event, options) => {
  const { fileName, fileType, suggestedPath } = options
  
  // 获取应用运行目录
  let basePath = app.getPath('userData')
  if (suggestedPath) {
    basePath = path.join(basePath, suggestedPath)
  }
  
  // 确保目录存在
  if (!fs.existsSync(basePath)) {
    fs.mkdirSync(basePath, { recursive: true })
  }
  
  // 构建完整文件路径
  const filePath = path.join(basePath, fileName)
  
  return filePath
})

ipcMain.handle('write-file', async (event, filePath, data) => {
  try {
    const buffer = Buffer.from(data)
    fs.writeFileSync(filePath, buffer)
    return { success: true, path: filePath }
  } catch (error) {
    console.error('写入文件失败:', error)
    return { success: false, error: error.message }
  }
})

ipcMain.handle('open-file', async (event, filePath) => {
  try {
    await shell.openPath(filePath)
    return { success: true }
  } catch (error) {
    console.error('打开文件失败:', error)
    return { success: false, error: error.message }
  }
})

ipcMain.handle('http-request', async (event, url, options = {}) => {
  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        ...(options.headers || {})
      }
    })
    
    const data = await response.text()
    return {
      success: true,
      status: response.status,
      data: data,
      headers: Object.fromEntries(response.headers.entries())
    }
  } catch (error) {
    console.error('HTTP请求失败:', error)
    return { success: false, error: error.message }
  }
})

app.on('ready', () => {
  // 注册本地文件协议
  registerLocalFileProtocol()
  
  // 如果已经退出，不创建窗口
  if (app.isQuitting) {
    console.log('应用已退出，跳过窗口创建')
    return
  }
  
  // 如果窗口已存在，不重复创建
  if (win) {
    console.log('窗口已存在，显示窗口')
    win.show()
    win.focus()
    return
  }
  
  console.log('创建新窗口，NODE_ENV:', process.env.NODE_ENV)
  createWindow()
  createTray()
})

app.on('will-quit', () => {
  // 清理资源
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