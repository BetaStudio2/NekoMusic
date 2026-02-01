import { app as i, ipcMain as l, shell as T, BrowserWindow as k, nativeImage as v, Tray as M, Menu as z } from "electron";
import c from "path";
import b from "fs";
import { fileURLToPath as D } from "url";
const g = c.dirname(D(import.meta.url));
let e, m;
i.isQuitting = !1;
const j = i.requestSingleInstanceLock();
j ? i.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), i.quit(), process.exit(0));
function P() {
  console.log("createWindow: 开始创建窗口");
  const u = process.env.NODE_ENV === "development", r = u ? c.join(g, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = c.join(g, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", r), console.log("createWindow: preload 路径 =", s), e = new k({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: r,
    title: "Neko云音乐",
    webPreferences: {
      preload: s,
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
  }), e.webContents.on("before-input-event", (o, t) => {
    t.control && t.shift && (t.key === "I" || t.key === "i") && o.preventDefault(), t.control && (t.key === "F12" || t.key === "f12") && o.preventDefault(), (t.alt && t.key === "F12" || t.alt && t.key === "f12") && o.preventDefault(), (t.key === "F11" || t.key === "f11") && o.preventDefault();
  }), u || !i.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const o = i.getAppPath(), t = c.join(o, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.webContents.openDevTools(), e.on("close", (o) => {
    i.isQuitting || (o.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let d = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function N() {
  const u = process.env.NODE_ENV === "development", r = u ? c.join(g, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = v.createFromPath(r);
  s.resize({ width: 16, height: 16 }), m = new M(s);
  const t = (() => {
    const n = {}, a = [
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
    ], w = u ? c.join(g, "../public") : c.join(i.getAppPath(), "public");
    return a.forEach((y) => {
      try {
        const f = v.createFromPath(c.join(w, `${y}.png`));
        f.resize({ width: 18, height: 18 }), n[y] = f;
      } catch (f) {
        console.warn(`Failed to load icon: ${y}`, f);
      }
    }), n;
  })(), p = async () => {
    if (e)
      try {
        const n = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        n ? d.currentMusic = JSON.parse(n) : d.currentMusic = null;
        const a = await e.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        a && (d.isPlaying = a.isPlaying);
      } catch (n) {
        console.error("同步播放状态失败:", n);
      }
  }, h = async () => {
    await p();
    const n = d.currentMusic;
    let a = "暂无播放";
    if (n) {
      const f = n.title || "未知歌曲", x = n.artist || "未知艺术家";
      a = `${f.length > 15 ? f.substring(0, 15) + "..." : f} - ${x}`;
    }
    const w = [
      // 顶部：当前播放信息
      {
        label: a,
        enabled: !1
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        icon: t["tray-exit"],
        click: () => {
          i.isQuitting = !0, i.quit();
        }
      }
    ], y = z.buildFromTemplate(w);
    m.setContextMenu(y), n ? m.setToolTip(`正在播放: ${n.title} - ${n.artist}`) : m.setToolTip("Neko云音乐");
  };
  h(), l.on("player-state-changed", (n, a) => {
    a && (d = { ...d, ...a }, h());
  }), l.on("music-play", (n, a) => {
    d.currentMusic = a, d.isPlaying = !0, h();
  }), l.on("play-state-changed", (n, a) => {
    d.isPlaying = a, h();
  }), m.on("click", () => {
    e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), setInterval(h, 5e3);
}
l.on("window-minimize", () => {
  e && e.minimize();
});
l.on("window-maximize", () => {
  e && (e.isMaximized() ? e.unmaximize() : e.maximize());
});
l.on("window-close", () => {
  e && e.hide();
});
l.handle("save-file", async (u, r) => {
  const { fileName: s, fileType: o, suggestedPath: t } = r;
  let p = i.getPath("userData");
  return t && (p = c.join(p, t)), b.existsSync(p) || b.mkdirSync(p, { recursive: !0 }), c.join(p, s);
});
l.handle("write-file", async (u, r, s) => {
  try {
    const o = Buffer.from(s);
    return b.writeFileSync(r, o), { success: !0, path: r };
  } catch (o) {
    return console.error("写入文件失败:", o), { success: !1, error: o.message };
  }
});
l.handle("open-file", async (u, r) => {
  try {
    return await T.openPath(r), { success: !0 };
  } catch (s) {
    return console.error("打开文件失败:", s), { success: !1, error: s.message };
  }
});
l.handle("http-request", async (u, r, s = {}) => {
  try {
    const o = await fetch(r, {
      ...s,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...s.headers || {}
      }
    }), t = await o.text();
    return {
      success: !0,
      status: o.status,
      data: t,
      headers: Object.fromEntries(o.headers.entries())
    };
  } catch (o) {
    return console.error("HTTP请求失败:", o), { success: !1, error: o.message };
  }
});
i.on("ready", () => {
  if (i.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), P(), N();
});
i.on("will-quit", () => {
});
i.on("window-all-closed", () => {
  process.platform !== "darwin" && i.quit();
});
i.on("activate", () => {
  e === null ? P() : e.show();
});
