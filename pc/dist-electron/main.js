import { app as i, ipcMain as d, dialog as F, shell as C, protocol as N, BrowserWindow as z, nativeImage as M, Tray as I, Menu as L } from "electron";
import c from "path";
import m from "fs";
import { fileURLToPath as E } from "url";
const b = c.dirname(E(import.meta.url)), x = c.join(i.getPath("userData"), "music-library.json"), S = c.join(i.getPath("userData"), "music-cache");
m.existsSync(S) || m.mkdirSync(S, { recursive: !0 });
function P() {
  try {
    if (m.existsSync(x)) {
      const e = m.readFileSync(x, "utf-8");
      return JSON.parse(e);
    }
  } catch (e) {
    console.error("读取音乐库失败:", e);
  }
  return [];
}
function v(e) {
  try {
    return m.writeFileSync(x, JSON.stringify(e, null, 2)), !0;
  } catch (n) {
    return console.error("保存音乐库失败:", n), !1;
  }
}
async function D(e) {
  try {
    const n = m.statSync(e), s = c.basename(e, c.extname(e)), t = s.split(" - "), r = t[t.length - 1] || s, l = t.length > 1 ? t[0] : "未知艺术家";
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
  } catch (n) {
    return console.error("获取音频元信息失败:", n), null;
  }
}
function k() {
  const e = i.getPath("downloads");
  return c.join(e, "NekoMusic");
}
d.handle("get-default-scan-path", async () => {
  try {
    return { success: !0, path: k() };
  } catch (e) {
    return console.error("获取默认扫描目录失败:", e), { success: !1, message: e.message };
  }
});
function $(e, n = [".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"]) {
  const s = [];
  try {
    let t = function(r) {
      const l = m.readdirSync(r);
      for (const u of l) {
        const a = c.join(r, u);
        if (m.statSync(a).isDirectory())
          t(a);
        else {
          const y = c.extname(u).toLowerCase();
          n.includes(y) && s.push(a);
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
    const e = k();
    console.log("开始扫描默认目录:", e);
    const n = $(e);
    if (n.length === 0)
      return { success: !0, message: "默认目录中没有找到音频文件", music: [], total: 0 };
    const s = P(), t = [];
    for (const r of n) {
      if (s.some((a) => a.filePath === r))
        continue;
      const u = await D(r);
      u && (s.push(u), t.push(u));
    }
    return t.length > 0 ? (v(s), {
      success: !0,
      message: `成功导入 ${t.length} 首音乐`,
      music: t,
      total: s.length,
      scanned: n.length
    }) : {
      success: !0,
      message: "所有文件已存在于音乐库中",
      music: [],
      total: s.length,
      scanned: n.length
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
    const n = P(), s = [];
    for (const t of e.filePaths) {
      if (n.some((u) => u.filePath === t))
        continue;
      const l = await D(t);
      l && (n.push(l), s.push(l));
    }
    return s.length > 0 ? (v(n), { success: !0, music: s, total: n.length }) : { success: !1, message: "文件已存在或解析失败" };
  } catch (n) {
    return console.error("导入音乐失败:", n), { success: !1, message: n.message };
  }
});
d.handle("get-local-music-library", async () => {
  try {
    return { success: !0, music: P() };
  } catch (e) {
    return console.error("获取音乐库失败:", e), { success: !1, message: e.message };
  }
});
d.handle("remove-local-music", async (e, n) => {
  try {
    const s = P(), t = s.findIndex((r) => r.id === n);
    return t === -1 ? { success: !1, message: "音乐不存在" } : (s.splice(t, 1), v(s), { success: !0, total: s.length });
  } catch (s) {
    return console.error("移除音乐失败:", s), { success: !1, message: s.message };
  }
});
function A() {
  N.registerBufferProtocol("local-file", (e, n) => {
    const s = e.url.replace("local-file:///", "");
    try {
      const t = m.readFileSync(s), r = c.extname(s).toLowerCase(), l = {
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
        ".flac": "audio/flac",
        ".m4a": "audio/mp4",
        ".ogg": "audio/ogg",
        ".aac": "audio/aac"
      }[r] || "application/octet-stream";
      n({
        data: t,
        mimeType: l
      });
    } catch (t) {
      console.error("读取本地文件失败:", t), n({ error: -2 });
    }
  });
}
let o, w;
i.isQuitting = !1;
const W = i.requestSingleInstanceLock();
W ? i.on("second-instance", () => {
  o && (o.isMinimized() && o.restore(), o.focus());
}) : (console.log("已经有实例在运行，退出新实例"), i.quit(), process.exit(0));
function T() {
  console.log("createWindow: 开始创建窗口");
  const e = process.env.NODE_ENV === "development", n = e ? c.join(b, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = c.join(b, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", n), console.log("createWindow: preload 路径 =", s), o = new z({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    frame: !1,
    autoHideMenuBar: !0,
    icon: n,
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
  }), o.on("maximize", () => {
    o.webContents.send("window-maximized");
  }), o.on("unmaximize", () => {
    o.webContents.send("window-unmaximized");
  }), o.webContents.on("before-input-event", (t, r) => {
    r.control && r.shift && (r.key === "I" || r.key === "i") && t.preventDefault(), r.control && (r.key === "F12" || r.key === "f12") && t.preventDefault(), (r.alt && r.key === "F12" || r.alt && r.key === "f12") && t.preventDefault(), (r.key === "F11" || r.key === "f11") && t.preventDefault();
  }), e || !i.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), o.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const t = i.getAppPath(), r = c.join(t, "dist/index.html");
    console.log("生产文件路径:", r), o.loadFile(r);
  }
  o.webContents.openDevTools(), o.on("close", (t) => {
    i.isQuitting || (t.preventDefault(), o.hide());
  }), o.on("closed", () => {
    o = null;
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
  const e = process.env.NODE_ENV === "development", n = e ? c.join(b, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = M.createFromPath(n);
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
    ], y = e ? c.join(b, "../public") : c.join(i.getAppPath(), "public");
    return f.forEach((p) => {
      try {
        const g = M.createFromPath(c.join(y, `${p}.png`));
        g.resize({ width: 18, height: 18 }), a[p] = g;
      } catch (g) {
        console.warn(`Failed to load icon: ${p}`, g);
      }
    }), a;
  })(), l = async () => {
    if (o)
      try {
        const a = await o.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        a ? h.currentMusic = JSON.parse(a) : h.currentMusic = null;
        const f = await o.webContents.executeJavaScript(`
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
          i.isQuitting = !0, i.quit();
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
    o && (o.isVisible() ? o.isFocused() ? o.hide() : o.focus() : (o.show(), o.focus()));
  }), setInterval(u, 5e3);
}
d.on("window-minimize", () => {
  o && o.minimize();
});
d.on("window-maximize", () => {
  o && (o.isMaximized() ? o.unmaximize() : o.maximize());
});
d.on("window-close", () => {
  o && o.hide();
});
d.handle("save-file", async (e, n) => {
  const { fileName: s, fileType: t, suggestedPath: r } = n;
  let l = i.getPath("userData");
  return r && (l = c.join(l, r)), m.existsSync(l) || m.mkdirSync(l, { recursive: !0 }), c.join(l, s);
});
d.handle("write-file", async (e, n, s) => {
  try {
    const t = Buffer.from(s);
    return m.writeFileSync(n, t), { success: !0, path: n };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
d.handle("open-file", async (e, n) => {
  try {
    return await C.openPath(n), { success: !0 };
  } catch (s) {
    return console.error("打开文件失败:", s), { success: !1, error: s.message };
  }
});
d.handle("http-request", async (e, n, s = {}) => {
  try {
    const t = await fetch(n, {
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
i.on("ready", () => {
  if (A(), i.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (o) {
    console.log("窗口已存在，显示窗口"), o.show(), o.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), T(), _();
});
i.on("will-quit", () => {
});
i.on("window-all-closed", () => {
  process.platform !== "darwin" && i.quit();
});
i.on("activate", () => {
  o === null ? T() : o.show();
});
