import { app as o, ipcMain as d, BrowserWindow as k, nativeImage as v, Tray as T, Menu as M } from "electron";
import a from "path";
import b from "fs";
import { fileURLToPath as z } from "url";
const h = a.dirname(z(import.meta.url));
let e, g;
o.isQuitting = !1;
const D = o.requestSingleInstanceLock();
D ? o.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), o.quit(), process.exit(0));
function P() {
  console.log("createWindow: 开始创建窗口");
  const f = process.env.NODE_ENV === "development", r = f ? a.join(h, "../public/icon.png") : a.join(o.getAppPath(), "public/icon.png"), l = a.join(h, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", r), console.log("createWindow: preload 路径 =", l), e = new k({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: r,
    title: "Neko云音乐",
    webPreferences: {
      preload: l,
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
  }), e.webContents.on("before-input-event", (n, t) => {
    t.control && t.shift && (t.key === "I" || t.key === "i") && n.preventDefault(), t.control && (t.key === "F12" || t.key === "f12") && n.preventDefault(), (t.alt && t.key === "F12" || t.alt && t.key === "f12") && n.preventDefault(), (t.key === "F11" || t.key === "f11") && n.preventDefault();
  }), f || !o.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const n = o.getAppPath(), t = a.join(n, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.webContents.openDevTools(), e.on("close", (n) => {
    o.isQuitting || (n.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let c = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function j() {
  const f = process.env.NODE_ENV === "development", r = f ? a.join(h, "../public/icon.png") : a.join(o.getAppPath(), "public/icon.png"), l = v.createFromPath(r);
  l.resize({ width: 16, height: 16 }), g = new T(l);
  const t = (() => {
    const i = {}, s = [
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
    ], w = f ? a.join(h, "../public") : a.join(o.getAppPath(), "public");
    return s.forEach((m) => {
      try {
        const u = v.createFromPath(a.join(w, `${m}.png`));
        u.resize({ width: 18, height: 18 }), i[m] = u;
      } catch (u) {
        console.warn(`Failed to load icon: ${m}`, u);
      }
    }), i;
  })(), p = async () => {
    if (e)
      try {
        const i = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        i ? c.currentMusic = JSON.parse(i) : c.currentMusic = null;
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
        s && (c.isPlaying = s.isPlaying);
      } catch (i) {
        console.error("同步播放状态失败:", i);
      }
  }, y = async () => {
    await p();
    const i = c.currentMusic;
    let s = "暂无播放";
    if (i) {
      const u = i.title || "未知歌曲", x = i.artist || "未知艺术家";
      s = `${u.length > 15 ? u.substring(0, 15) + "..." : u} - ${x}`;
    }
    const w = [
      // 顶部：当前播放信息
      {
        label: s,
        enabled: !1
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        icon: t["tray-exit"],
        click: () => {
          o.isQuitting = !0, o.quit();
        }
      }
    ], m = M.buildFromTemplate(w);
    g.setContextMenu(m), i ? g.setToolTip(`正在播放: ${i.title} - ${i.artist}`) : g.setToolTip("Neko云音乐");
  };
  y(), d.on("player-state-changed", (i, s) => {
    s && (c = { ...c, ...s }, y());
  }), d.on("music-play", (i, s) => {
    c.currentMusic = s, c.isPlaying = !0, y();
  }), d.on("play-state-changed", (i, s) => {
    c.isPlaying = s, y();
  }), g.on("click", () => {
    e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), setInterval(y, 5e3);
}
d.on("window-minimize", () => {
  e && e.minimize();
});
d.on("window-maximize", () => {
  e && (e.isMaximized() ? e.unmaximize() : e.maximize());
});
d.on("window-close", () => {
  e && e.hide();
});
d.handle("save-file", async (f, r) => {
  const { fileName: l, fileType: n, suggestedPath: t } = r;
  let p = o.getPath("userData");
  return t && (p = a.join(p, t)), b.existsSync(p) || b.mkdirSync(p, { recursive: !0 }), a.join(p, l);
});
d.handle("write-file", async (f, r, l) => {
  try {
    const n = Buffer.from(l);
    return b.writeFileSync(r, n), { success: !0, path: r };
  } catch (n) {
    return console.error("写入文件失败:", n), { success: !1, error: n.message };
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
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), P(), j();
});
o.on("will-quit", () => {
});
o.on("window-all-closed", () => {
  process.platform !== "darwin" && o.quit();
});
o.on("activate", () => {
  e === null ? P() : e.show();
});
