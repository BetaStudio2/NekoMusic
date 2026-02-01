import { app as i, ipcMain as d, dialog as j, shell as F, protocol as C, BrowserWindow as N, nativeImage as M, Tray as z, Menu as I } from "electron";
import c from "path";
import m from "fs";
import { fileURLToPath as L } from "url";
const b = c.dirname(L(import.meta.url)), P = c.join(i.getPath("userData"), "music-library.json"), S = c.join(i.getPath("userData"), "music-cache");
m.existsSync(S) || m.mkdirSync(S, { recursive: !0 });
function x() {
  try {
    if (m.existsSync(P)) {
      const t = m.readFileSync(P, "utf-8");
      return JSON.parse(t);
    }
  } catch (t) {
    console.error("读取音乐库失败:", t);
  }
  return [];
}
function v(t) {
  try {
    return m.writeFileSync(P, JSON.stringify(t, null, 2)), !0;
  } catch (n) {
    return console.error("保存音乐库失败:", n), !1;
  }
}
async function D(t) {
  try {
    const n = m.statSync(t), s = c.basename(t, c.extname(t)), e = s.split(" - "), r = e[e.length - 1] || s, l = e.length > 1 ? e[0] : "未知艺术家";
    return {
      id: `local-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      title: r,
      artist: l,
      album: "本地音乐",
      duration: 0,
      // 需要实际解析音频文件获取
      filePath: t,
      isLocal: !0,
      addedAt: Date.now()
    };
  } catch (n) {
    return console.error("获取音频元信息失败:", n), null;
  }
}
function E() {
  const t = i.getPath("downloads");
  return c.join(t, "NekoMusic");
}
function $(t, n = [".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"]) {
  const s = [];
  try {
    let e = function(r) {
      const l = m.readdirSync(r);
      for (const u of l) {
        const a = c.join(r, u);
        if (m.statSync(a).isDirectory())
          e(a);
        else {
          const y = c.extname(u).toLowerCase();
          n.includes(y) && s.push(a);
        }
      }
    };
    if (!m.existsSync(t))
      return console.log("默认扫描目录不存在:", t), s;
    e(t), console.log(`扫描目录 ${t}，找到 ${s.length} 个音频文件`);
  } catch (e) {
    console.error("扫描目录失败:", e);
  }
  return s;
}
d.handle("scan-default-directory", async () => {
  try {
    const t = E();
    console.log("开始扫描默认目录:", t);
    const n = $(t);
    if (n.length === 0)
      return { success: !0, message: "默认目录中没有找到音频文件", music: [], total: 0 };
    const s = x(), e = [];
    for (const r of n) {
      if (s.some((a) => a.filePath === r))
        continue;
      const u = await D(r);
      u && (s.push(u), e.push(u));
    }
    return e.length > 0 ? (v(s), {
      success: !0,
      message: `成功导入 ${e.length} 首音乐`,
      music: e,
      total: s.length,
      scanned: n.length
    }) : {
      success: !0,
      message: "所有文件已存在于音乐库中",
      music: [],
      total: s.length,
      scanned: n.length
    };
  } catch (t) {
    return console.error("扫描默认目录失败:", t), { success: !1, message: t.message };
  }
});
d.handle("select-local-music-files", async () => {
  const t = await j.showOpenDialog({
    properties: ["openFile", "multiSelections"],
    filters: [
      { name: "音频文件", extensions: ["mp3", "wav", "flac", "m4a", "ogg", "aac"] }
    ]
  });
  if (t.canceled || t.filePaths.length === 0)
    return { success: !1, message: "未选择文件" };
  try {
    const n = x(), s = [];
    for (const e of t.filePaths) {
      if (n.some((u) => u.filePath === e))
        continue;
      const l = await D(e);
      l && (n.push(l), s.push(l));
    }
    return s.length > 0 ? (v(n), { success: !0, music: s, total: n.length }) : { success: !1, message: "文件已存在或解析失败" };
  } catch (n) {
    return console.error("导入音乐失败:", n), { success: !1, message: n.message };
  }
});
d.handle("get-local-music-library", async () => {
  try {
    return { success: !0, music: x() };
  } catch (t) {
    return console.error("获取音乐库失败:", t), { success: !1, message: t.message };
  }
});
d.handle("remove-local-music", async (t, n) => {
  try {
    const s = x(), e = s.findIndex((r) => r.id === n);
    return e === -1 ? { success: !1, message: "音乐不存在" } : (s.splice(e, 1), v(s), { success: !0, total: s.length });
  } catch (s) {
    return console.error("移除音乐失败:", s), { success: !1, message: s.message };
  }
});
function A() {
  C.registerBufferProtocol("local-file", (t, n) => {
    const s = t.url.replace("local-file:///", "");
    try {
      const e = m.readFileSync(s), r = c.extname(s).toLowerCase(), l = {
        ".mp3": "audio/mpeg",
        ".wav": "audio/wav",
        ".flac": "audio/flac",
        ".m4a": "audio/mp4",
        ".ogg": "audio/ogg",
        ".aac": "audio/aac"
      }[r] || "application/octet-stream";
      n({
        data: e,
        mimeType: l
      });
    } catch (e) {
      console.error("读取本地文件失败:", e), n({ error: -2 });
    }
  });
}
let o, w;
i.isQuitting = !1;
const W = i.requestSingleInstanceLock();
W ? i.on("second-instance", () => {
  o && (o.isMinimized() && o.restore(), o.focus());
}) : (console.log("已经有实例在运行，退出新实例"), i.quit(), process.exit(0));
function k() {
  console.log("createWindow: 开始创建窗口");
  const t = process.env.NODE_ENV === "development", n = t ? c.join(b, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = c.join(b, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", n), console.log("createWindow: preload 路径 =", s), o = new N({
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
  }), o.webContents.on("before-input-event", (e, r) => {
    r.control && r.shift && (r.key === "I" || r.key === "i") && e.preventDefault(), r.control && (r.key === "F12" || r.key === "f12") && e.preventDefault(), (r.alt && r.key === "F12" || r.alt && r.key === "f12") && e.preventDefault(), (r.key === "F11" || r.key === "f11") && e.preventDefault();
  }), t || !i.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), o.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const e = i.getAppPath(), r = c.join(e, "dist/index.html");
    console.log("生产文件路径:", r), o.loadFile(r);
  }
  o.webContents.openDevTools(), o.on("close", (e) => {
    i.isQuitting || (e.preventDefault(), o.hide());
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
  const t = process.env.NODE_ENV === "development", n = t ? c.join(b, "../public/icon.png") : c.join(i.getAppPath(), "public/icon.png"), s = M.createFromPath(n);
  s.resize({ width: 16, height: 16 }), w = new z(s);
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
    ], y = t ? c.join(b, "../public") : c.join(i.getAppPath(), "public");
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
      const g = a.title || "未知歌曲", T = a.artist || "未知艺术家";
      f = `${g.length > 15 ? g.substring(0, 15) + "..." : g} - ${T}`;
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
    ], p = I.buildFromTemplate(y);
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
d.handle("save-file", async (t, n) => {
  const { fileName: s, fileType: e, suggestedPath: r } = n;
  let l = i.getPath("userData");
  return r && (l = c.join(l, r)), m.existsSync(l) || m.mkdirSync(l, { recursive: !0 }), c.join(l, s);
});
d.handle("write-file", async (t, n, s) => {
  try {
    const e = Buffer.from(s);
    return m.writeFileSync(n, e), { success: !0, path: n };
  } catch (e) {
    return console.error("写入文件失败:", e), { success: !1, error: e.message };
  }
});
d.handle("open-file", async (t, n) => {
  try {
    return await F.openPath(n), { success: !0 };
  } catch (s) {
    return console.error("打开文件失败:", s), { success: !1, error: s.message };
  }
});
d.handle("http-request", async (t, n, s = {}) => {
  try {
    const e = await fetch(n, {
      ...s,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...s.headers || {}
      }
    }), r = await e.text();
    return {
      success: !0,
      status: e.status,
      data: r,
      headers: Object.fromEntries(e.headers.entries())
    };
  } catch (e) {
    return console.error("HTTP请求失败:", e), { success: !1, error: e.message };
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
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), k(), _();
});
i.on("will-quit", () => {
});
i.on("window-all-closed", () => {
  process.platform !== "darwin" && i.quit();
});
i.on("activate", () => {
  o === null ? k() : o.show();
});
