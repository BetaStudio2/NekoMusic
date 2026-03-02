import { app as o, ipcMain as u, shell as k, BrowserWindow as x, nativeImage as b, Tray as v, Menu as L } from "electron";
import d from "path";
import y from "fs";
import { fileURLToPath as T } from "url";
const w = d.dirname(T(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, f;
o.isQuitting = !1;
o.commandLine.appendSwitch("enable-gpu-rasterization");
o.commandLine.appendSwitch("enable-zero-copy");
o.commandLine.appendSwitch("ignore-gpu-blocklist");
o.commandLine.appendSwitch("enable-native-gpu-memory-buffers");
o.commandLine.appendSwitch("enable-gpu-memory-buffer-compositor-resources");
o.commandLine.appendSwitch("disable-dev-shm-usage");
o.commandLine.appendSwitch("disable-background-networking");
o.commandLine.appendSwitch("disable-background-timer-throttling");
o.commandLine.appendSwitch("disable-renderer-backgrounding");
o.commandLine.appendSwitch("disable-breakpad");
o.commandLine.appendSwitch("disable-component-extensions-with-background-pages");
o.commandLine.appendSwitch("disable-domain-reliability");
o.commandLine.appendSwitch("disable-sync");
o.commandLine.appendSwitch("disable-backgrounding-occluded-windows");
o.commandLine.appendSwitch("disable-ipc-flooding-protection");
o.commandLine.appendSwitch("disable-features", "Translate");
o.commandLine.appendSwitch("disable-features", "SpeechSynthesis");
o.commandLine.appendSwitch("disable-features", "Autofill");
const M = o.requestSingleInstanceLock();
M ? o.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), o.quit(), process.exit(0));
function P() {
  console.log("createWindow: 开始创建窗口");
  const p = process.env.NODE_ENV === "development", a = p ? d.join(w, "../public/icon.png") : d.join(o.getAppPath(), "public/icon.png"), c = d.join(w, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", a), console.log("createWindow: preload 路径 =", c), e = new x({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: !1,
    autoHideMenuBar: !0,
    icon: a,
    title: "Neko云音乐",
    webPreferences: {
      preload: c,
      nodeIntegration: !1,
      contextIsolation: !0,
      devTools: !0,
      sandbox: !1
      // 关闭沙箱以允许 localStorage 访问
    },
    backgroundColor: "#667eea"
  }), e.on("maximize", () => {
    e.webContents.send("window-maximized");
  }), e.on("unmaximize", () => {
    e.webContents.send("window-unmaximized");
  }), e.webContents.on("before-input-event", (t, n) => {
    n.control && n.shift && (n.key === "I" || n.key === "i") && t.preventDefault(), n.control && (n.key === "F12" || n.key === "f12") && t.preventDefault(), (n.alt && n.key === "F12" || n.alt && n.key === "f12") && t.preventDefault(), (n.key === "F11" || n.key === "f11") && t.preventDefault();
  }), p || !o.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const t = o.getAppPath(), n = d.join(t, "dist/index.html");
    console.log("生产文件路径:", n), e.loadFile(n);
  }
  e.on("close", (t) => {
    o.isQuitting || (t.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let h = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function z() {
  console.log("createTray: 开始创建托盘");
  const p = (i) => {
    const s = process.resourcesPath ? d.join(process.resourcesPath, i) : null, l = d.join(o.getAppPath(), "public", i), m = d.join(w, "../public", i);
    return s && y.existsSync(s) ? (console.log(`使用resources路径: ${s}`), s) : y.existsSync(l) ? (console.log(`使用app路径: ${l}`), l) : y.existsSync(m) ? (console.log(`使用相对路径: ${m}`), m) : (console.log(`使用默认app路径: ${l}`), l);
  }, a = p("icon.png"), c = b.createFromPath(a);
  console.log("托盘图标路径:", a), console.log("托盘图标是否为空:", c.isEmpty()), f && (f.destroy(), f = null), f = new v(c), f.setToolTip("Neko云音乐"), (() => {
    const i = {};
    return [
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
    ].forEach((l) => {
      try {
        const m = p(`${l}.png`), g = b.createFromPath(m);
        g.resize({ width: 18, height: 18 }), i[l] = g;
      } catch (m) {
        console.warn(`Failed to load icon: ${l}`, m);
      }
    }), i;
  })();
  const n = async () => {
    if (e)
      try {
        const i = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        i ? h.currentMusic = JSON.parse(i) : h.currentMusic = null;
        const s = await e.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        s && (h.isPlaying = s.isPlaying);
      } catch (i) {
        console.error("同步播放状态失败:", i);
      }
  }, r = async () => {
    await n();
    const i = h.currentMusic;
    let s = "暂无播放";
    if (i) {
      const g = i.title || "未知歌曲", S = i.artist || "未知艺术家";
      s = `${g.length > 15 ? g.substring(0, 15) + "..." : g} - ${S}`;
    }
    const l = [
      // 顶部：当前播放信息
      {
        label: s,
        enabled: !1
      },
      { type: "separator" },
      // 显示窗口
      {
        label: "显示窗口",
        click: () => {
          e && (e.show(), e.focus());
        }
      },
      // 隐藏窗口
      {
        label: "隐藏窗口",
        click: () => {
          e && e.hide();
        }
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        click: () => {
          o.isQuitting = !0, o.quit();
        }
      }
    ], m = L.buildFromTemplate(l);
    console.log("托盘菜单已构建，包含", l.length, "个菜单项"), f.setContextMenu(m), console.log("托盘菜单已设置到托盘对象"), f.setToolTip("Neko云音乐"), console.log("托盘工具提示已设置"), i && f.setToolTip(`正在播放: ${i.title} - ${i.artist}`);
  };
  r(), u.on("player-state-changed", (i, s) => {
    s && (h = { ...h, ...s }, r());
  }), u.on("music-play", (i, s) => {
    h.currentMusic = s, h.isPlaying = !0, r();
  }), u.on("play-state-changed", (i, s) => {
    h.isPlaying = s, r();
  }), f.on("click", () => {
    console.log("托盘图标被点击"), e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), f.on("double-click", () => {
    console.log("托盘图标被双击"), e && (e.isVisible() || e.show(), e.focus());
  });
}
u.on("window-minimize", () => {
  e && e.minimize();
});
u.on("window-maximize", () => {
  e && (e.isMaximized() ? e.unmaximize() : e.maximize());
});
u.on("window-close", () => {
  e && e.hide();
});
u.handle("get-path", async (p, a) => o.getPath(a));
u.handle("save-file", async (p, a) => {
  const { fileName: c, fileType: t, suggestedPath: n } = a;
  let r;
  return n && n.includes("NekoMusic") ? (r = o.getPath("downloads"), n && (r = d.join(r, n))) : (r = o.getPath("userData"), n && (r = d.join(r, n))), y.existsSync(r) || y.mkdirSync(r, { recursive: !0 }), d.join(r, c);
});
u.handle("write-file", async (p, a, c) => {
  try {
    const t = Buffer.from(c);
    return y.writeFileSync(a, t), { success: !0, path: a };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
u.handle("open-file", async (p, a) => {
  try {
    return await k.openPath(a), { success: !0 };
  } catch (c) {
    return console.error("打开文件失败:", c), { success: !1, error: c.message };
  }
});
u.handle("http-request", async (p, a, c = {}) => {
  try {
    const t = await fetch(a, {
      ...c,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...c.headers || {}
      }
    }), n = await t.text();
    return {
      success: !0,
      status: t.status,
      data: n,
      headers: Object.fromEntries(t.headers.entries())
    };
  } catch (t) {
    return console.error("HTTP请求失败:", t), { success: !1, error: t.message };
  }
});
o.on("ready", () => {
  if (o.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), P(), z();
});
o.on("will-quit", () => {
});
o.on("window-all-closed", () => {
  process.platform !== "darwin" && o.quit();
});
o.on("activate", () => {
  e === null ? P() : e.show();
});
