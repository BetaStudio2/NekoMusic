import { app as n, ipcMain as f, shell as W, BrowserWindow as j, nativeImage as N, Tray as I, screen as O, Menu as U } from "electron";
import h from "path";
import y from "fs";
import { fileURLToPath as R } from "url";
const S = h.dirname(R(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let o, e, w;
n.isQuitting = !1;
n.commandLine.appendSwitch("enable-gpu-rasterization");
n.commandLine.appendSwitch("enable-zero-copy");
n.commandLine.appendSwitch("ignore-gpu-blocklist");
n.commandLine.appendSwitch("enable-native-gpu-memory-buffers");
n.commandLine.appendSwitch("enable-gpu-memory-buffer-compositor-resources");
n.commandLine.appendSwitch("disable-dev-shm-usage");
n.commandLine.appendSwitch("disable-background-networking");
n.commandLine.appendSwitch("disable-background-timer-throttling");
n.commandLine.appendSwitch("disable-renderer-backgrounding");
n.commandLine.appendSwitch("disable-breakpad");
n.commandLine.appendSwitch("disable-component-extensions-with-background-pages");
n.commandLine.appendSwitch("disable-domain-reliability");
n.commandLine.appendSwitch("disable-sync");
n.commandLine.appendSwitch("disable-backgrounding-occluded-windows");
n.commandLine.appendSwitch("disable-ipc-flooding-protection");
n.commandLine.appendSwitch("disable-features", "Translate");
n.commandLine.appendSwitch("disable-features", "SpeechSynthesis");
n.commandLine.appendSwitch("disable-features", "Autofill");
const _ = n.requestSingleInstanceLock();
_ ? n.on("second-instance", () => {
  o && (o.isMinimized() && o.restore(), o.focus());
}) : (console.log("已经有实例在运行，退出新实例"), n.quit(), process.exit(0));
function C() {
  console.log("createWindow: 开始创建窗口");
  const l = process.env.NODE_ENV === "development", s = l ? h.join(S, "../public/icon.png") : h.join(n.getAppPath(), "public/icon.png"), r = h.join(S, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", s), console.log("createWindow: preload 路径 =", r), o = new j({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: !1,
    autoHideMenuBar: !0,
    icon: s,
    title: "Neko云音乐",
    webPreferences: {
      preload: r,
      nodeIntegration: !1,
      contextIsolation: !0,
      devTools: !1,
      sandbox: !1,
      // 关闭沙箱以允许 localStorage 访问
      nativeWindowOpen: !0
      // 允许使用原生 window.open
    },
    backgroundColor: "#667eea"
  }), o.on("maximize", () => {
    o.webContents.send("window-maximized");
  }), o.on("unmaximize", () => {
    o.webContents.send("window-unmaximized");
  }), o.webContents.on("before-input-event", (t, i) => {
    i.control && i.shift && (i.key === "I" || i.key === "i") && t.preventDefault(), i.control && (i.key === "F12" || i.key === "f12") && t.preventDefault(), (i.alt && i.key === "F12" || i.alt && i.key === "f12") && t.preventDefault(), (i.key === "F11" || i.key === "f11") && t.preventDefault();
  }), o.webContents.setWindowOpenHandler(({ url: t }) => (W.openExternal(t).catch((i) => {
    console.error("Failed to open external URL:", i);
  }), { action: "deny" })), l || !n.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), o.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const t = n.getAppPath(), i = h.join(t, "dist/index.html");
    console.log("生产文件路径:", i), o.loadFile(i);
  }
  (l || !n.isPackaged) && o.webContents.openDevTools(), o.on("close", (t) => {
    n.isQuitting || (t.preventDefault(), o.hide());
  }), o.on("closed", () => {
    o = null;
  });
}
function A() {
  if (e) {
    console.log("歌词窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("createLyricsWindow: 开始创建歌词窗口");
  const l = process.env.NODE_ENV === "development" || !n.isPackaged;
  let s = h.join(S, "./lyrics.html");
  if (console.log("歌词HTML路径:", s), !y.existsSync(s)) {
    console.error("歌词HTML文件不存在:", s);
    const c = h.join(S, "../../dist-electron/lyrics.html");
    if (y.existsSync(c))
      console.log("使用备用路径:", c), s = c;
    else {
      console.error("无法找到歌词HTML文件");
      return;
    }
  }
  try {
    e = new j({
      width: 500,
      height: 300,
      // 增加高度以适应最大字体和控件
      frame: !1,
      transparent: !0,
      alwaysOnTop: !0,
      skipTaskbar: !0,
      resizable: !1,
      movable: !1,
      webPreferences: {
        nodeIntegration: !0,
        contextIsolation: !1,
        devTools: l
        // 只在开发环境开启DevTools
      },
      show: !1,
      backgroundColor: "#00000000"
    });
  } catch (c) {
    console.error("创建歌词窗口失败:", c), e = null;
    return;
  }
  const { width: r, height: t } = e.getBounds(), { workAreaSize: i } = O.getPrimaryDisplay(), p = Math.floor((i.width - r) / 2), a = Math.floor((i.height - t) / 2);
  e.setPosition(p, a), console.log("歌词窗口位置:", { x: p, y: a }), e.loadFile(s), e.webContents.on("did-fail-load", (c, u, g) => {
    console.error("歌词窗口加载失败:", u, g), e && (e.close(), e = null);
  }), e.on("unresponsive", () => {
    console.warn("歌词窗口无响应，尝试恢复"), e && e.reload();
  }), e.on("responsive", () => {
    console.log("歌词窗口已恢复响应");
  }), e.webContents.on("did-finish-load", () => {
    console.log("歌词窗口加载完成，准备显示");
    const c = h.join(n.getPath("userData"), "lyrics-window-data.json");
    if (y.existsSync(c))
      try {
        const u = JSON.parse(y.readFileSync(c, "utf-8"));
        u.fontSize && e.webContents.send("update-font-size", u.fontSize), u.position && e.setPosition(u.position.x, u.position.y);
      } catch (u) {
        console.error("解析歌词窗口数据失败:", u);
      }
    setTimeout(() => {
      e && !e.isDestroyed() && (e.show(), e.focus(), console.log("歌词窗口已显示"));
    }, 100);
  }), e.on("closed", () => {
    console.log("歌词窗口已关闭"), e = null;
  });
  let d = null;
  e.on("moved", () => {
    d && clearTimeout(d), d = setTimeout(() => {
      try {
        if (e && !e.isDestroyed()) {
          const [c, u] = e.getPosition();
          z({ position: { x: c, y: u } }), console.log("歌词窗口移动到:", { x: c, y: u });
        }
      } catch (c) {
        console.error("保存歌词窗口位置失败:", c);
      }
    }, 500);
  }), console.log("歌词窗口创建成功");
}
function z(l) {
  const s = h.join(n.getPath("userData"), "lyrics-window-data.json");
  try {
    let r = {};
    y.existsSync(s) && (r = JSON.parse(y.readFileSync(s, "utf-8")));
    const t = { ...r, ...l };
    y.writeFileSync(s, JSON.stringify(t));
  } catch (r) {
    console.error("保存歌词窗口数据失败:", r);
  }
}
function F() {
  if (console.log("showLyricsWindow: 被调用"), !e) {
    console.log("showLyricsWindow: 创建新窗口"), A();
    return;
  }
  console.log("showLyricsWindow: 显示窗口"), e.show(), e.focus(), console.log("showLyricsWindow: 窗口已显示");
}
function M() {
  e && e.hide();
}
let m = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function H() {
  console.log("createTray: 开始创建托盘");
  const l = (a) => {
    const d = process.resourcesPath ? h.join(process.resourcesPath, a) : null, c = h.join(n.getAppPath(), "public", a), u = h.join(S, "../public", a);
    return d && y.existsSync(d) ? (console.log(`使用resources路径: ${d}`), d) : y.existsSync(c) ? (console.log(`使用app路径: ${c}`), c) : y.existsSync(u) ? (console.log(`使用相对路径: ${u}`), u) : (console.log(`使用默认app路径: ${c}`), c);
  }, s = l("icon.png"), r = N.createFromPath(s);
  console.log("托盘图标路径:", s), console.log("托盘图标是否为空:", r.isEmpty()), w && (w.destroy(), w = null), w = new I(r), w.setToolTip("Neko云音乐"), (() => {
    const a = {};
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
    ].forEach((c) => {
      try {
        const u = l(`${c}.png`), g = N.createFromPath(u);
        g.resize({ width: 18, height: 18 }), a[c] = g;
      } catch (u) {
        console.warn(`Failed to load icon: ${c}`, u);
      }
    }), a;
  })();
  const i = async () => {
    if (o)
      try {
        const a = await o.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        a ? m.currentMusic = JSON.parse(a) : m.currentMusic = null;
        const d = await o.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        d && (m.isPlaying = d.isPlaying);
      } catch (a) {
        console.error("同步播放状态失败:", a);
      }
  }, p = async () => {
    await i();
    const a = m.currentMusic;
    let d = "暂无播放";
    if (a) {
      const g = a.title || "未知歌曲", b = a.artist || "未知艺术家";
      d = `${g.length > 15 ? g.substring(0, 15) + "..." : g} - ${b}`;
    }
    const c = [
      // 顶部：当前播放信息
      {
        label: d,
        enabled: !1
      },
      { type: "separator" },
      // 显示窗口
      {
        label: "显示窗口",
        click: () => {
          o && (o.show(), o.focus());
        }
      },
      // 隐藏窗口
      {
        label: "隐藏窗口",
        click: () => {
          o && o.hide();
        }
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        click: () => {
          n.isQuitting = !0, n.quit();
        }
      }
    ], u = U.buildFromTemplate(c);
    console.log("托盘菜单已构建，包含", c.length, "个菜单项"), w.setContextMenu(u), console.log("托盘菜单已设置到托盘对象"), w.setToolTip("Neko云音乐"), console.log("托盘工具提示已设置"), a && w.setToolTip(`正在播放: ${a.title} - ${a.artist}`);
  };
  p(), f.on("player-state-changed", (a, d) => {
    d && (m = { ...m, ...d }, p());
  }), f.on("music-play", (a, d) => {
    m.currentMusic = d, m.isPlaying = !0, p();
  }), f.on("play-state-changed", (a, d) => {
    m.isPlaying = d, p();
  }), w.on("click", () => {
    console.log("托盘图标被点击"), o && (o.isVisible() ? o.isFocused() ? o.hide() : o.focus() : (o.show(), o.focus()));
  }), w.on("double-click", () => {
    console.log("托盘图标被双击"), o && (o.isVisible() || o.show(), o.focus());
  });
}
f.on("window-minimize", () => {
  o && o.minimize();
});
f.on("window-maximize", () => {
  o && (o.isMaximized() ? o.unmaximize() : o.maximize());
});
f.on("window-close", () => {
  o && o.hide();
});
f.on("show-lyrics-window", () => {
  console.log("IPC: 收到 show-lyrics-window 请求"), F();
});
f.on("hide-lyrics-window", () => {
  M();
});
f.on("toggle-lyrics-window", () => {
  e && e.isVisible() ? M() : F();
});
f.on("update-lyrics", (l, { lyric: s, translation: r }) => {
  try {
    console.log("主进程: 收到歌词更新请求", { lyric: s, translation: r, lyricsWindowExists: !!e }), e && !e.isDestroyed() ? (console.log("主进程: 发送歌词到窗口"), e.webContents.send("update-lyrics", { lyric: s, translation: r })) : console.log("主进程: 歌词窗口不存在或已销毁");
  } catch (t) {
    console.error("更新歌词失败:", t);
  }
});
f.on("lyrics-window-move", (l, { x: s, y: r }) => {
  try {
    if (e && !e.isDestroyed()) {
      const t = Math.round(Number(s)), i = Math.round(Number(r));
      !isNaN(t) && !isNaN(i) && e.setPosition(t, i);
    }
  } catch (t) {
    console.error("移动歌词窗口失败:", t);
  }
});
f.on("lyrics-window-reset-position", () => {
  if (e) {
    const l = { x: 500, y: 100 };
    e.setPosition(l.x, l.y), z({ position: l });
  }
});
f.on("lyrics-window-toggle-minimize", () => {
  e && e.webContents.send("toggle-minimize");
});
f.on("lyrics-window-update-font-size", (l, s) => {
  e && (e.webContents.send("update-font-size", s), z({ fontSize: s }));
});
f.on("lyrics-window-hide", () => {
  M(), o && o.webContents.send("lyrics-window-hidden");
});
f.on("lyrics-window-open-devtools", () => {
  e && e.webContents.openDevTools();
});
f.handle("get-path", async (l, s) => n.getPath(s));
f.handle("open-external", async (l, s) => {
  const { shell: r } = await import("electron");
  try {
    return await r.openExternal(s), { success: !0 };
  } catch (t) {
    return console.error("Failed to open external URL:", t), { success: !1, error: t.message };
  }
});
f.handle("save-file", async (l, s) => {
  const { fileName: r, fileType: t, suggestedPath: i } = s;
  let p;
  return i && i.includes("NekoMusic") ? (p = n.getPath("downloads"), i && (p = h.join(p, i))) : (p = n.getPath("userData"), i && (p = h.join(p, i))), y.existsSync(p) || y.mkdirSync(p, { recursive: !0 }), h.join(p, r);
});
f.handle("write-file", async (l, s, r) => {
  try {
    const t = Buffer.from(r);
    return y.writeFileSync(s, t), { success: !0, path: s };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
f.handle("open-file", async (l, s) => {
  try {
    return await W.openPath(s), { success: !0 };
  } catch (r) {
    return console.error("打开文件失败:", r), { success: !1, error: r.message };
  }
});
f.handle("http-request", async (l, s, r = {}) => {
  try {
    const t = await fetch(s, {
      ...r,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...r.headers || {}
      }
    }), i = await t.text();
    return {
      success: !0,
      status: t.status,
      data: i,
      headers: Object.fromEntries(t.headers.entries())
    };
  } catch (t) {
    return console.error("HTTP请求失败:", t), { success: !1, error: t.message };
  }
});
f.handle("download-music-with-lyrics", async (l, s) => {
  console.log("下载音乐和歌词 - 接收到的数据:", s);
  try {
    const { id: r, title: t, artist: i, fileFormat: p, baseUrl: a } = s;
    if (!r || !t || !i || !a)
      return console.error("缺少必要参数:", { id: r, title: t, artist: i, baseUrl: a }), { success: !1, error: "缺少必要参数" };
    const d = n.getPath("downloads"), c = h.join(d, "NekoMusic");
    console.log("下载目录:", c), y.existsSync(c) || (y.mkdirSync(c, { recursive: !0 }), console.log("创建下载目录成功"));
    const u = t.replace(/[<>:"/\\|?*]/g, "_"), b = `${i.replace(/[<>:"/\\|?*]/g, "_")} - ${u}`;
    console.log("文件名:", b);
    const T = `${a}/api/music/file/${r}`;
    console.log("音乐URL:", T);
    const v = await fetch(T);
    if (console.log("音乐响应状态:", v.status), !v.ok)
      throw new Error(`下载音乐文件失败: ${v.status}`);
    const $ = await v.arrayBuffer(), E = p || "mp3", x = h.join(c, `${b}.${E}`);
    y.writeFileSync(x, Buffer.from($)), console.log("音乐文件保存成功:", x);
    let P = null;
    try {
      const k = `${a}/api/music/lyrics/${r}`;
      console.log("歌词URL:", k);
      const D = await fetch(k);
      if (console.log("歌词响应状态:", D.status), D.ok) {
        const L = await D.json();
        console.log("歌词数据:", L), L.success && L.data ? (P = h.join(c, `${b}.lrc`), y.writeFileSync(P, L.data, "utf-8"), console.log("歌词文件保存成功:", P)) : console.log("没有歌词数据");
      } else
        console.log("歌词不可用");
    } catch (k) {
      console.warn("下载歌词失败:", k);
    }
    return console.log("下载完成:", { musicPath: x, lyricsPath: P }), {
      success: !0,
      musicPath: x,
      lyricsPath: P
    };
  } catch (r) {
    return console.error("下载音乐和歌词失败:", r), { success: !1, error: r.message };
  }
});
n.on("ready", () => {
  if (n.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (o) {
    console.log("窗口已存在，显示窗口"), o.show(), o.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), C(), H();
});
n.on("will-quit", () => {
  e && e.close();
});
n.on("window-all-closed", () => {
  process.platform !== "darwin" && n.quit();
});
n.on("activate", () => {
  o === null ? C() : o.show();
});
