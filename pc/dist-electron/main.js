import { app, ipcMain, shell, BrowserWindow, nativeImage, Tray, Menu } from "electron";
import path from "path";
import fs from "fs";
import { fileURLToPath } from "url";
const __dirname$1 = path.dirname(fileURLToPath(import.meta.url));
if (process.platform === "linux") {
  console.log("Linux平台检测到，检查托盘支持...");
  console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库：");
  console.log("  sudo apt-get install libayatana-appindicator3-1");
  console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity");
}
let win;
let tray;
app.isQuitting = false;
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  console.log("已经有实例在运行，退出新实例");
  app.quit();
  process.exit(0);
} else {
  app.on("second-instance", () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });
}
function createWindow() {
  console.log("createWindow: 开始创建窗口");
  const isDev = process.env.NODE_ENV === "development";
  const iconPath = isDev ? path.join(__dirname$1, "../public/icon.png") : path.join(app.getAppPath(), "public/icon.png");
  const preloadPath = path.join(__dirname$1, "./preload.cjs");
  console.log("createWindow: 图标路径 =", iconPath);
  console.log("createWindow: preload 路径 =", preloadPath);
  win = new BrowserWindow({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: false,
    autoHideMenuBar: true,
    icon: iconPath,
    title: "Neko云音乐",
    webPreferences: {
      preload: preloadPath,
      nodeIntegration: false,
      contextIsolation: true,
      devTools: true,
      sandbox: false
      // 关闭沙箱以允许 localStorage 访问
    },
    backgroundColor: "#667eea"
  });
  win.on("maximize", () => {
    win.webContents.send("window-maximized");
  });
  win.on("unmaximize", () => {
    win.webContents.send("window-unmaximized");
  });
  win.webContents.on("before-input-event", (event, input) => {
    if (input.control && input.shift && (input.key === "I" || input.key === "i")) {
      event.preventDefault();
    }
    if (input.control && (input.key === "F12" || input.key === "f12")) {
      event.preventDefault();
    }
    if (input.alt && input.key === "F12" || input.alt && input.key === "f12") {
      event.preventDefault();
    }
    if (input.key === "F11" || input.key === "f11") {
      event.preventDefault();
    }
  });
  if (isDev || !app.isPackaged) {
    console.log("createWindow: 加载开发服务器 http://localhost:5173");
    win.loadURL("http://localhost:5173");
  } else {
    console.log("createWindow: 加载生产文件");
    const appPath = app.getAppPath();
    const prodPath = path.join(appPath, "dist/index.html");
    console.log("生产文件路径:", prodPath);
    win.loadFile(prodPath);
  }
  win.on("close", (event) => {
    if (!app.isQuitting) {
      event.preventDefault();
      win.hide();
    }
  });
  win.on("closed", () => {
    win = null;
  });
}
let playerState = {
  currentMusic: null,
  isPlaying: false,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: false,
  desktopLyricsEnabled: false
};
function createTray() {
  console.log("createTray: 开始创建托盘");
  const getIconPath = (filename) => {
    const resourcesPath = process.resourcesPath ? path.join(process.resourcesPath, filename) : null;
    const appPath = path.join(app.getAppPath(), "public", filename);
    const relativePath = path.join(__dirname$1, "../public", filename);
    if (resourcesPath && fs.existsSync(resourcesPath)) {
      console.log(`使用resources路径: ${resourcesPath}`);
      return resourcesPath;
    } else if (fs.existsSync(appPath)) {
      console.log(`使用app路径: ${appPath}`);
      return appPath;
    } else if (fs.existsSync(relativePath)) {
      console.log(`使用相对路径: ${relativePath}`);
      return relativePath;
    }
    console.log(`使用默认app路径: ${appPath}`);
    return appPath;
  };
  const iconPath = getIconPath("icon.png");
  const trayIcon = nativeImage.createFromPath(iconPath);
  console.log("托盘图标路径:", iconPath);
  console.log("托盘图标是否为空:", trayIcon.isEmpty());
  if (tray) {
    tray.destroy();
    tray = null;
  }
  tray = new Tray(trayIcon);
  tray.setToolTip("Neko云音乐");
  const loadIcons = () => {
    const icons2 = {};
    const iconList = [
      "tray-previous",
      "tray-play",
      "tray-pause",
      "tray-next",
      "tray-favorite",
      "tray-shuffle",
      "tray-setting",
      "tray-list-loop",
      "tray-single-loop",
      "tray-minimize",
      "tray-lyrics",
      "tray-exit"
    ];
    iconList.forEach((name) => {
      try {
        const iconPath2 = getIconPath(`${name}.png`);
        const icon = nativeImage.createFromPath(iconPath2);
        icon.resize({ width: 18, height: 18 });
        icons2[name] = icon;
      } catch (e) {
        console.warn(`Failed to load icon: ${name}`, e);
      }
    });
    return icons2;
  };
  loadIcons();
  const syncPlayerState = async () => {
    if (!win) return;
    try {
      const musicJson = await win.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
      if (musicJson) {
        playerState.currentMusic = JSON.parse(musicJson);
      } else {
        playerState.currentMusic = null;
      }
      const state = await win.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
      if (state) {
        playerState.isPlaying = state.isPlaying;
      }
    } catch (e) {
      console.error("同步播放状态失败:", e);
    }
  };
  const updateContextMenu = async () => {
    await syncPlayerState();
    const music = playerState.currentMusic;
    let playingLabel = "暂无播放";
    if (music) {
      const title = music.title || "未知歌曲";
      const artist = music.artist || "未知艺术家";
      const displayTitle = title.length > 15 ? title.substring(0, 15) + "..." : title;
      playingLabel = `${displayTitle} - ${artist}`;
    }
    const menuTemplate = [
      // 顶部：当前播放信息
      {
        label: playingLabel,
        enabled: false
      },
      { type: "separator" },
      // 显示窗口
      {
        label: "显示窗口",
        click: () => {
          if (win) {
            win.show();
            win.focus();
          }
        }
      },
      // 隐藏窗口
      {
        label: "隐藏窗口",
        click: () => {
          if (win) {
            win.hide();
          }
        }
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        click: () => {
          app.isQuitting = true;
          app.quit();
        }
      }
    ];
    const contextMenu = Menu.buildFromTemplate(menuTemplate);
    console.log("托盘菜单已构建，包含", menuTemplate.length, "个菜单项");
    tray.setContextMenu(contextMenu);
    console.log("托盘菜单已设置到托盘对象");
    tray.setToolTip("Neko云音乐");
    console.log("托盘工具提示已设置");
    if (music) {
      tray.setToolTip(`正在播放: ${music.title} - ${music.artist}`);
    }
  };
  updateContextMenu();
  ipcMain.on("player-state-changed", (event, state) => {
    if (state) {
      playerState = { ...playerState, ...state };
      updateContextMenu();
    }
  });
  ipcMain.on("music-play", (event, music) => {
    playerState.currentMusic = music;
    playerState.isPlaying = true;
    updateContextMenu();
  });
  ipcMain.on("play-state-changed", (event, isPlaying) => {
    playerState.isPlaying = isPlaying;
    updateContextMenu();
  });
  tray.on("click", () => {
    console.log("托盘图标被点击");
    if (win) {
      if (win.isVisible()) {
        if (win.isFocused()) {
          win.hide();
        } else {
          win.focus();
        }
      } else {
        win.show();
        win.focus();
      }
    }
  });
  tray.on("double-click", () => {
    console.log("托盘图标被双击");
    if (win) {
      if (win.isVisible()) {
        win.focus();
      } else {
        win.show();
        win.focus();
      }
    }
  });
  setInterval(updateContextMenu, 5e3);
}
ipcMain.on("window-minimize", () => {
  if (win) win.minimize();
});
ipcMain.on("window-maximize", () => {
  if (win) {
    if (win.isMaximized()) {
      win.unmaximize();
    } else {
      win.maximize();
    }
  }
});
ipcMain.on("window-close", () => {
  if (win) win.hide();
});
ipcMain.handle("save-file", async (event, options) => {
  const { fileName, fileType, suggestedPath } = options;
  let basePath = app.getPath("userData");
  if (suggestedPath) {
    basePath = path.join(basePath, suggestedPath);
  }
  if (!fs.existsSync(basePath)) {
    fs.mkdirSync(basePath, { recursive: true });
  }
  const filePath = path.join(basePath, fileName);
  return filePath;
});
ipcMain.handle("write-file", async (event, filePath, data) => {
  try {
    const buffer = Buffer.from(data);
    fs.writeFileSync(filePath, buffer);
    return { success: true, path: filePath };
  } catch (error) {
    console.error("写入文件失败:", error);
    return { success: false, error: error.message };
  }
});
ipcMain.handle("open-file", async (event, filePath) => {
  try {
    await shell.openPath(filePath);
    return { success: true };
  } catch (error) {
    console.error("打开文件失败:", error);
    return { success: false, error: error.message };
  }
});
ipcMain.handle("http-request", async (event, url, options = {}) => {
  try {
    const response = await fetch(url, {
      ...options,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...options.headers || {}
      }
    });
    const data = await response.text();
    return {
      success: true,
      status: response.status,
      data,
      headers: Object.fromEntries(response.headers.entries())
    };
  } catch (error) {
    console.error("HTTP请求失败:", error);
    return { success: false, error: error.message };
  }
});
app.on("ready", () => {
  if (app.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (win) {
    console.log("窗口已存在，显示窗口");
    win.show();
    win.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV);
  createWindow();
  createTray();
});
app.on("will-quit", () => {
});
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});
app.on("activate", () => {
  if (win === null) {
    createWindow();
  } else {
    win.show();
  }
});
