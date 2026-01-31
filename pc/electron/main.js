import { app, BrowserWindow } from 'electron'
import path from 'path'
import { fileURLToPath } from 'url'

// 在 ES 模块中获取 __dirname 的等效值
const __dirname = path.dirname(fileURLToPath(import.meta.url))

let win

function createWindow() {
  win = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    icon: path.join(__dirname, '../public/icon.png'),
    title: 'NekoMusic',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
    },
    backgroundColor: '#667eea',
  })

  // 开发模式加载 Vite 开发服务器
  if (process.env.NODE_ENV === 'development') {
    win.loadURL('http://localhost:5173')
    win.webContents.openDevTools()
  } else {
    // 生产模式加载打包后的文件
    win.loadFile(path.join(__dirname, '../dist/index.html'))
  }

  win.on('closed', () => {
    win = null
  })
}

app.on('ready', createWindow)

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('activate', () => {
  if (win === null) {
    createWindow()
  }
})