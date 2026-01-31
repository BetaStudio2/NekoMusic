import { app as t, ipcMain as r, BrowserWindow as x, nativeImage as w, Tray as k, Menu as T } from "electron";
import a from "path";
import { fileURLToPath as M } from "url";
const g = a.dirname(M(import.meta.url));
let e, m;
t.isQuitting = !1;
const z = t.requestSingleInstanceLock();
z ? t.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), t.quit(), process.exit(0));
function b() {
  console.log("createWindow: 开始创建窗口");
  const u = process.env.NODE_ENV === "development", y = u ? a.join(g, "../public/icon.png") : a.join(t.getAppPath(), "public/icon.png"), d = a.join(g, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", y), console.log("createWindow: preload 路径 =", d), e = new x({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: y,
    title: "Neko云音乐",
    webPreferences: {
      preload: d,
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
  }), e.webContents.on("before-input-event", (s, o) => {
    o.control && o.shift && (o.key === "I" || o.key === "i") && s.preventDefault(), o.control && (o.key === "F12" || o.key === "f12") && s.preventDefault(), (o.alt && o.key === "F12" || o.alt && o.key === "f12") && s.preventDefault(), (o.key === "F11" || o.key === "f11") && s.preventDefault();
  }), u || !t.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const s = t.getAppPath(), o = a.join(s, "dist/index.html");
    console.log("生产文件路径:", o), e.loadFile(o);
  }
  e.webContents.openDevTools(), e.on("close", (s) => {
    t.isQuitting || (s.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let l = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function D() {
  const u = process.env.NODE_ENV === "development", y = u ? a.join(g, "../public/icon.png") : a.join(t.getAppPath(), "public/icon.png"), d = w.createFromPath(y);
  d.resize({ width: 16, height: 16 }), m = new k(d);
  const o = (() => {
    const i = {}, n = [
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
    ], h = u ? a.join(g, "../public") : a.join(t.getAppPath(), "public");
    return n.forEach((f) => {
      try {
        const c = w.createFromPath(a.join(h, `${f}.png`));
        c.resize({ width: 18, height: 18 }), i[f] = c;
      } catch (c) {
        console.warn(`Failed to load icon: ${f}`, c);
      }
    }), i;
  })(), v = async () => {
    if (e)
      try {
        const i = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        i ? l.currentMusic = JSON.parse(i) : l.currentMusic = null;
        const n = await e.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        n && (l.isPlaying = n.isPlaying);
      } catch (i) {
        console.error("同步播放状态失败:", i);
      }
  }, p = async () => {
    await v();
    const i = l.currentMusic;
    let n = "暂无播放";
    if (i) {
      const c = i.title || "未知歌曲", P = i.artist || "未知艺术家";
      n = `${c.length > 15 ? c.substring(0, 15) + "..." : c} - ${P}`;
    }
    const h = [
      // 顶部：当前播放信息
      {
        label: n,
        enabled: !1
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        icon: o["tray-exit"],
        click: () => {
          t.isQuitting = !0, t.quit();
        }
      }
    ], f = T.buildFromTemplate(h);
    m.setContextMenu(f), i ? m.setToolTip(`正在播放: ${i.title} - ${i.artist}`) : m.setToolTip("Neko云音乐");
  };
  p(), r.on("player-state-changed", (i, n) => {
    n && (l = { ...l, ...n }, p());
  }), r.on("music-play", (i, n) => {
    l.currentMusic = n, l.isPlaying = !0, p();
  }), r.on("play-state-changed", (i, n) => {
    l.isPlaying = n, p();
  }), m.on("click", () => {
    e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), setInterval(p, 5e3);
}
r.on("window-minimize", () => {
  e && e.minimize();
});
r.on("window-maximize", () => {
  e && (e.isMaximized() ? e.unmaximize() : e.maximize());
});
r.on("window-close", () => {
  e && e.hide();
});
t.on("ready", () => {
  if (t.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), b(), D();
});
t.on("will-quit", () => {
});
t.on("window-all-closed", () => {
  process.platform !== "darwin" && t.quit();
});
t.on("activate", () => {
  e === null ? b() : e.show();
});
