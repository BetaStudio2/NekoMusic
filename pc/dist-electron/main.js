import { app as c, ipcMain as d, dialog as F, shell as N, protocol as C, BrowserWindow as z, nativeImage as S, Tray as I, Menu as L } from "electron";
import i from "path";
import m from "fs";
import { fileURLToPath as E } from "url";
const b = i.dirname(E(import.meta.url)), P = i.join(c.getPath("userData"), "music-library.json"), M = i.join(c.getPath("userData"), "music-cache");
m.existsSync(M) || m.mkdirSync(M, { recursive: !0 });
function x() {
  try {
    if (m.existsSync(P)) {
      const e = m.readFileSync(P, "utf-8");
      return JSON.parse(e);
    }
  } catch (e) {
    console.error("读取音乐库失败:", e);
  }
  return [];
}
function v(e) {
  try {
    return m.writeFileSync(P, JSON.stringify(e, null, 2)), !0;
  } catch (o) {
    return console.error("保存音乐库失败:", o), !1;
  }
}
async function k(e) {
  try {
    const o = m.statSync(e), s = i.basename(e, i.extname(e)), t = s.split(" - "), r = t[t.length - 1] || s, l = t.length > 1 ? t[0] : "未知艺术家";
    return {
      id: `local-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      title: r,
      artist: l,
      album: "本地音乐",
      duration: 0,
      // 需要实际解析音频文件获取
      filePath: e,
      isLocal: !0,
      addedAt: Date.now()
    };
  } catch (o) {
    return console.error("获取音频元信息失败:", o), null;
  }
}
function D() {
  const e = c.getPath("downloads"), o = i.join(e, "NekoMusic");
  if (!m.existsSync(o))
    try {
      m.mkdirSync(o, { recursive: !0 }), console.log("已自动创建 NekoMusic 目录:", o);
    } catch (s) {
      console.error("创建 NekoMusic 目录失败:", s);
    }
  return o;
}
d.handle("get-default-scan-path", async () => {
  try {
    return { success: !0, path: D() };
  } catch (e) {
    return console.error("获取默认扫描目录失败:", e), { success: !1, message: e.message };
  }
});
function $(e, o = [".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"]) {
  const s = [];
  try {
    let t = function(r) {
      const l = m.readdirSync(r);
      for (const u of l) {
        const a = i.join(r, u);
        if (m.statSync(a).isDirectory())
          t(a);
        else {
          const y = i.extname(u).toLowerCase();
          o.includes(y) && s.push(a);
        }
      }
    };
    if (!m.existsSync(e))
      return console.log("默认扫描目录不存在:", e), s;
    t(e), console.log(`扫描目录 ${e}，找到 ${s.length} 个音频文件`);
  } catch (t) {
    console.error("扫描目录失败:", t);
  }
  return s;
}
d.handle("scan-default-directory", async () => {
  try {
    const e = D();
    console.log("开始扫描默认目录:", e);
    const o = $(e);
    if (o.length === 0)
      return { success: !0, message: "默认目录中没有找到音频文件", music: [], total: 0 };
    const s = x(), t = [];
    for (const r of o) {
      if (s.some((a) => a.filePath === r))
        continue;
      const u = await k(r);
      u && (s.push(u), t.push(u));
    }
    return t.length > 0 ? (v(s), {
      success: !0,
      message: `成功导入 ${t.length} 首音乐`,
      music: t,
      total: s.length,
      scanned: o.length
    }) : {
      success: !0,
      message: "所有文件已存在于音乐库中",
      music: [],
      total: s.length,
      scanned: o.length
    };
  } catch (e) {
    return console.error("扫描默认目录失败:", e), { success: !1, message: e.message };
  }
});
d.handle("select-local-music-files", async () => {
  const e = await F.showOpenDialog({
    properties: ["openFile", "multiSelections"],
    filters: [
      { name: "音频文件", extensions: ["mp3", "wav", "flac", "m4a", "ogg", "aac"] }
    ]
  });
  if (e.canceled || e.filePaths.length === 0)
    return { success: !1, message: "未选择文件" };
  try {
    const o = x(), s = [];
    for (const t of e.filePaths) {
      if (o.some((u) => u.filePath === t))
        continue;
      const l = await k(t);
      l && (o.push(l), s.push(l));
    }
    return s.length > 0 ? (v(o), { success: !0, music: s, total: o.length }) : { success: !1, message: "文件已存在或解析失败" };
  } catch (o) {
    return console.error("导入音乐失败:", o), { success: !1, message: o.message };
  }
});
d.handle("get-local-music-library", async () => {
  try {
    return { success: !0, music: x() };
  } catch (e) {
    return console.error("获取音乐库失败:", e), { success: !1, message: e.message };
  }
});
d.handle("remove-local-music", async (e, o) => {
  try {
    const s = x(), t = s.findIndex((r) => r.id === o);
    return t === -1 ? { success: !1, message: "音乐不存在" } : (s.splice(t, 1), v(s), { success: !0, total: s.length });
  } catch (s) {
    return console.error("移除音乐失败:", s), { success: !1, message: s.message };
  }
});
function A() {
  C.registerBufferProtocol("local-file", (e, o) => {
    const s = e.url.replace("local-file:///", "");
    try {
      const t = m.readFileSync(s), r = i.extname(s).toLowerCase(), l = {
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
        ".flac": "audio/flac",
        ".m4a": "audio/mp4",
        ".ogg": "audio/ogg",
        ".aac": "audio/aac"
      }[r] || "application/octet-stream";
      o({
        data: t,
        mimeType: l
      });
    } catch (t) {
      console.error("读取本地文件失败:", t), o({ error: -2 });
    }
  });
}
let n, w;
c.isQuitting = !1;
const W = c.requestSingleInstanceLock();
W ? c.on("second-instance", () => {
  n && (n.isMinimized() && n.restore(), n.focus());
}) : (console.log("已经有实例在运行，退出新实例"), c.quit(), process.exit(0));
function T() {
  console.log("createWindow: 开始创建窗口");
  const e = process.env.NODE_ENV === "development", o = e ? i.join(b, "../public/icon.png") : i.join(c.getAppPath(), "public/icon.png"), s = i.join(b, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", o), console.log("createWindow: preload 路径 =", s), n = new z({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: o,
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
  }), n.on("maximize", () => {
    n.webContents.send("window-maximized");
  }), n.on("unmaximize", () => {
    n.webContents.send("window-unmaximized");
  }), n.webContents.on("before-input-event", (t, r) => {
    r.control && r.shift && (r.key === "I" || r.key === "i") && t.preventDefault(), r.control && (r.key === "F12" || r.key === "f12") && t.preventDefault(), (r.alt && r.key === "F12" || r.alt && r.key === "f12") && t.preventDefault(), (r.key === "F11" || r.key === "f11") && t.preventDefault();
  }), e || !c.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), n.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const t = c.getAppPath(), r = i.join(t, "dist/index.html");
    console.log("生产文件路径:", r), n.loadFile(r);
  }
  n.webContents.openDevTools(), n.on("close", (t) => {
    c.isQuitting || (t.preventDefault(), n.hide());
  }), n.on("closed", () => {
    n = null;
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
function _() {
  const e = process.env.NODE_ENV === "development", o = e ? i.join(b, "../public/icon.png") : i.join(c.getAppPath(), "public/icon.png"), s = S.createFromPath(o);
  s.resize({ width: 16, height: 16 }), w = new I(s);
  const r = (() => {
    const a = {}, f = [
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
    ], y = e ? i.join(b, "../public") : i.join(c.getAppPath(), "public");
    return f.forEach((p) => {
      try {
        const g = S.createFromPath(i.join(y, `${p}.png`));
        g.resize({ width: 18, height: 18 }), a[p] = g;
      } catch (g) {
        console.warn(`Failed to load icon: ${p}`, g);
      }
    }), a;
  })(), l = async () => {
    if (n)
      try {
        const a = await n.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        a ? h.currentMusic = JSON.parse(a) : h.currentMusic = null;
        const f = await n.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        f && (h.isPlaying = f.isPlaying);
      } catch (a) {
        console.error("同步播放状态失败:", a);
      }
  }, u = async () => {
    await l();
    const a = h.currentMusic;
    let f = "暂无播放";
    if (a) {
      const g = a.title || "未知歌曲", j = a.artist || "未知艺术家";
      f = `${g.length > 15 ? g.substring(0, 15) + "..." : g} - ${j}`;
    }
    const y = [
      // 顶部：当前播放信息
      {
        label: f,
        enabled: !1
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        icon: r["tray-exit"],
        click: () => {
          c.isQuitting = !0, c.quit();
        }
      }
    ], p = L.buildFromTemplate(y);
    w.setContextMenu(p), a ? w.setToolTip(`正在播放: ${a.title} - ${a.artist}`) : w.setToolTip("Neko云音乐");
  };
  u(), d.on("player-state-changed", (a, f) => {
    f && (h = { ...h, ...f }, u());
  }), d.on("music-play", (a, f) => {
    h.currentMusic = f, h.isPlaying = !0, u();
  }), d.on("play-state-changed", (a, f) => {
    h.isPlaying = f, u();
  }), w.on("click", () => {
    n && (n.isVisible() ? n.isFocused() ? n.hide() : n.focus() : (n.show(), n.focus()));
  }), setInterval(u, 5e3);
}
d.on("window-minimize", () => {
  n && n.minimize();
});
d.on("window-maximize", () => {
  n && (n.isMaximized() ? n.unmaximize() : n.maximize());
});
d.on("window-close", () => {
  n && n.hide();
});
d.handle("save-file", async (e, o) => {
  const { fileName: s, fileType: t, suggestedPath: r } = o;
  let l = c.getPath("userData");
  return r && (l = i.join(l, r)), m.existsSync(l) || m.mkdirSync(l, { recursive: !0 }), i.join(l, s);
});
d.handle("write-file", async (e, o, s) => {
  try {
    const t = Buffer.from(s);
    return m.writeFileSync(o, t), { success: !0, path: o };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
d.handle("open-file", async (e, o) => {
  try {
    return await N.openPath(o), { success: !0 };
  } catch (s) {
    return console.error("打开文件失败:", s), { success: !1, error: s.message };
  }
});
d.handle("http-request", async (e, o, s = {}) => {
  try {
    const t = await fetch(o, {
      ...s,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...s.headers || {}
      }
    }), r = await t.text();
    return {
      success: !0,
      status: t.status,
      data: r,
      headers: Object.fromEntries(t.headers.entries())
    };
  } catch (t) {
    return console.error("HTTP请求失败:", t), { success: !1, error: t.message };
  }
});
c.on("ready", () => {
  if (A(), c.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (n) {
    console.log("窗口已存在，显示窗口"), n.show(), n.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), T(), _();
});
c.on("will-quit", () => {
});
c.on("window-all-closed", () => {
  process.platform !== "darwin" && c.quit();
});
c.on("activate", () => {
  n === null ? T() : n.show();
});
