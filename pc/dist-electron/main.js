import { app as o, ipcMain as u, shell as k, BrowserWindow as T, nativeImage as b, Tray as M, Menu as z } from "electron";
import r from "path";
import v from "fs";
import { fileURLToPath as D } from "url";
const g = r.dirname(D(import.meta.url));
let e, h;
o.isQuitting = !1;
const j = o.requestSingleInstanceLock();
j ? o.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), o.quit(), process.exit(0));
function P() {
  console.log("createWindow: 开始创建窗口");
  const d = process.env.NODE_ENV === "development", c = d ? r.join(g, "../public/icon.png") : r.join(o.getAppPath(), "public/icon.png"), a = r.join(g, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", c), console.log("createWindow: preload 路径 =", a), e = new T({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: c,
    title: "Neko云音乐",
    webPreferences: {
      preload: a,
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
  }), d || !o.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const n = o.getAppPath(), t = r.join(n, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.webContents.openDevTools(), e.on("close", (n) => {
    o.isQuitting || (n.preventDefault(), e.hide());
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
function N() {
  const d = process.env.NODE_ENV === "development", c = d ? r.join(g, "../public/icon.png") : r.join(o.getAppPath(), "public/icon.png"), a = b.createFromPath(c);
  a.resize({ width: 16, height: 16 }), h = new M(a);
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
    ], w = d ? r.join(g, "../public") : r.join(o.getAppPath(), "public");
    return s.forEach((m) => {
      try {
        const f = b.createFromPath(r.join(w, `${m}.png`));
        f.resize({ width: 18, height: 18 }), i[m] = f;
      } catch (f) {
        console.warn(`Failed to load icon: ${m}`, f);
      }
    }), i;
  })(), p = async () => {
    if (e)
      try {
        const i = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        i ? l.currentMusic = JSON.parse(i) : l.currentMusic = null;
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
        s && (l.isPlaying = s.isPlaying);
      } catch (i) {
        console.error("同步播放状态失败:", i);
      }
  }, y = async () => {
    await p();
    const i = l.currentMusic;
    let s = "暂无播放";
    if (i) {
      const f = i.title || "未知歌曲", x = i.artist || "未知艺术家";
      s = `${f.length > 15 ? f.substring(0, 15) + "..." : f} - ${x}`;
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
    ], m = z.buildFromTemplate(w);
    h.setContextMenu(m), i ? h.setToolTip(`正在播放: ${i.title} - ${i.artist}`) : h.setToolTip("Neko云音乐");
  };
  y(), u.on("player-state-changed", (i, s) => {
    s && (l = { ...l, ...s }, y());
  }), u.on("music-play", (i, s) => {
    l.currentMusic = s, l.isPlaying = !0, y();
  }), u.on("play-state-changed", (i, s) => {
    l.isPlaying = s, y();
  }), h.on("click", () => {
    e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), setInterval(y, 5e3);
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
u.handle("save-file", async (d, c) => {
  const { fileName: a, fileType: n, suggestedPath: t } = c;
  let p = o.getPath("userData");
  return t && (p = r.join(p, t)), v.existsSync(p) || v.mkdirSync(p, { recursive: !0 }), r.join(p, a);
});
u.handle("write-file", async (d, c, a) => {
  try {
    const n = Buffer.from(a);
    return v.writeFileSync(c, n), { success: !0, path: c };
  } catch (n) {
    return console.error("写入文件失败:", n), { success: !1, error: n.message };
  }
});
u.handle("open-file", async (d, c) => {
  try {
    return await k.openPath(c), { success: !0 };
  } catch (a) {
    return console.error("打开文件失败:", a), { success: !1, error: a.message };
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
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), P(), N();
});
o.on("will-quit", () => {
});
o.on("window-all-closed", () => {
  process.platform !== "darwin" && o.quit();
});
o.on("activate", () => {
  e === null ? P() : e.show();
});
