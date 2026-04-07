import { app as o, ipcMain as u, shell as F, BrowserWindow as j, nativeImage as $, Tray as N, Menu as D } from "electron";
import d from "path";
import h from "fs";
import { fileURLToPath as W } from "url";
const T = d.dirname(W(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, m;
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
const C = o.requestSingleInstanceLock();
C ? o.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), o.quit(), process.exit(0));
function M() {
  console.log("createWindow: 开始创建窗口");
  const p = process.env.NODE_ENV === "development", a = p ? d.join(T, "../public/icon.png") : d.join(o.getAppPath(), "public/icon.png"), i = d.join(T, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", a), console.log("createWindow: preload 路径 =", i), e = new j({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: !1,
    autoHideMenuBar: !0,
    icon: a,
    title: "Neko云音乐",
    webPreferences: {
      preload: i,
      nodeIntegration: !1,
      contextIsolation: !0,
      devTools: !1,
      sandbox: !1,
      // 关闭沙箱以允许 localStorage 访问
      nativeWindowOpen: !0
      // 允许使用原生 window.open
    },
    backgroundColor: "#667eea"
  }), e.on("maximize", () => {
    e.webContents.send("window-maximized");
  }), e.on("unmaximize", () => {
    e.webContents.send("window-unmaximized");
  }), e.webContents.on("before-input-event", (n, t) => {
    t.control && t.shift && (t.key === "I" || t.key === "i") && n.preventDefault(), t.control && (t.key === "F12" || t.key === "f12") && n.preventDefault(), (t.alt && t.key === "F12" || t.alt && t.key === "f12") && n.preventDefault(), (t.key === "F11" || t.key === "f11") && n.preventDefault();
  }), e.webContents.setWindowOpenHandler(({ url: n }) => {
    const { shell: t } = require("electron");
    return t.openExternal(n).catch((r) => {
      console.error("Failed to open external URL:", r);
    }), { action: "deny" };
  }), p || !o.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const n = o.getAppPath(), t = d.join(n, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.webContents.openDevTools(), e.on("close", (n) => {
    o.isQuitting || (n.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let g = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function U() {
  console.log("createTray: 开始创建托盘");
  const p = (s) => {
    const c = process.resourcesPath ? d.join(process.resourcesPath, s) : null, l = d.join(o.getAppPath(), "public", s), f = d.join(T, "../public", s);
    return c && h.existsSync(c) ? (console.log(`使用resources路径: ${c}`), c) : h.existsSync(l) ? (console.log(`使用app路径: ${l}`), l) : h.existsSync(f) ? (console.log(`使用相对路径: ${f}`), f) : (console.log(`使用默认app路径: ${l}`), l);
  }, a = p("icon.png"), i = $.createFromPath(a);
  console.log("托盘图标路径:", a), console.log("托盘图标是否为空:", i.isEmpty()), m && (m.destroy(), m = null), m = new N(i), m.setToolTip("Neko云音乐"), (() => {
    const s = {};
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
        const f = p(`${l}.png`), y = $.createFromPath(f);
        y.resize({ width: 18, height: 18 }), s[l] = y;
      } catch (f) {
        console.warn(`Failed to load icon: ${l}`, f);
      }
    }), s;
  })();
  const t = async () => {
    if (e)
      try {
        const s = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        s ? g.currentMusic = JSON.parse(s) : g.currentMusic = null;
        const c = await e.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        c && (g.isPlaying = c.isPlaying);
      } catch (s) {
        console.error("同步播放状态失败:", s);
      }
  }, r = async () => {
    await t();
    const s = g.currentMusic;
    let c = "暂无播放";
    if (s) {
      const y = s.title || "未知歌曲", w = s.artist || "未知艺术家";
      c = `${y.length > 15 ? y.substring(0, 15) + "..." : y} - ${w}`;
    }
    const l = [
      // 顶部：当前播放信息
      {
        label: c,
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
    ], f = D.buildFromTemplate(l);
    console.log("托盘菜单已构建，包含", l.length, "个菜单项"), m.setContextMenu(f), console.log("托盘菜单已设置到托盘对象"), m.setToolTip("Neko云音乐"), console.log("托盘工具提示已设置"), s && m.setToolTip(`正在播放: ${s.title} - ${s.artist}`);
  };
  r(), u.on("player-state-changed", (s, c) => {
    c && (g = { ...g, ...c }, r());
  }), u.on("music-play", (s, c) => {
    g.currentMusic = c, g.isPlaying = !0, r();
  }), u.on("play-state-changed", (s, c) => {
    g.isPlaying = c, r();
  }), m.on("click", () => {
    console.log("托盘图标被点击"), e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), m.on("double-click", () => {
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
u.handle("open-external", async (p, a) => {
  const { shell: i } = await import("electron");
  try {
    return await i.openExternal(a), { success: !0 };
  } catch (n) {
    return console.error("Failed to open external URL:", n), { success: !1, error: n.message };
  }
});
u.handle("save-file", async (p, a) => {
  const { fileName: i, fileType: n, suggestedPath: t } = a;
  let r;
  return t && t.includes("NekoMusic") ? (r = o.getPath("downloads"), t && (r = d.join(r, t))) : (r = o.getPath("userData"), t && (r = d.join(r, t))), h.existsSync(r) || h.mkdirSync(r, { recursive: !0 }), d.join(r, i);
});
u.handle("write-file", async (p, a, i) => {
  try {
    const n = Buffer.from(i);
    return h.writeFileSync(a, n), { success: !0, path: a };
  } catch (n) {
    return console.error("写入文件失败:", n), { success: !1, error: n.message };
  }
});
u.handle("open-file", async (p, a) => {
  try {
    return await F.openPath(a), { success: !0 };
  } catch (i) {
    return console.error("打开文件失败:", i), { success: !1, error: i.message };
  }
});
u.handle("http-request", async (p, a, i = {}) => {
  try {
    const n = await fetch(a, {
      ...i,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...i.headers || {}
      }
    }), t = await n.text();
    return {
      success: !0,
      status: n.status,
      data: t,
      headers: Object.fromEntries(n.headers.entries())
    };
  } catch (n) {
    return console.error("HTTP请求失败:", n), { success: !1, error: n.message };
  }
});
u.handle("download-music-with-lyrics", async (p, a) => {
  console.log("下载音乐和歌词 - 接收到的数据:", a);
  try {
    const { id: i, title: n, artist: t, fileFormat: r, baseUrl: s } = a;
    if (!i || !n || !t || !s)
      return console.error("缺少必要参数:", { id: i, title: n, artist: t, baseUrl: s }), { success: !1, error: "缺少必要参数" };
    const c = o.getPath("downloads"), l = d.join(c, "NekoMusic");
    console.log("下载目录:", l), h.existsSync(l) || (h.mkdirSync(l, { recursive: !0 }), console.log("创建下载目录成功"));
    const f = n.replace(/[<>:"/\\|?*]/g, "_"), w = `${t.replace(/[<>:"/\\|?*]/g, "_")} - ${f}`;
    console.log("文件名:", w);
    const v = `${s}/api/music/file/${i}`;
    console.log("音乐URL:", v);
    const P = await fetch(v);
    if (console.log("音乐响应状态:", P.status), !P.ok)
      throw new Error(`下载音乐文件失败: ${P.status}`);
    const z = await P.arrayBuffer(), E = r || "mp3", S = d.join(l, `${w}.${E}`);
    h.writeFileSync(S, Buffer.from(z)), console.log("音乐文件保存成功:", S);
    let b = null;
    try {
      const k = `${s}/api/music/lyrics/${i}`;
      console.log("歌词URL:", k);
      const L = await fetch(k);
      if (console.log("歌词响应状态:", L.status), L.ok) {
        const x = await L.json();
        console.log("歌词数据:", x), x.success && x.data ? (b = d.join(l, `${w}.lrc`), h.writeFileSync(b, x.data, "utf-8"), console.log("歌词文件保存成功:", b)) : console.log("没有歌词数据");
      } else
        console.log("歌词不可用");
    } catch (k) {
      console.warn("下载歌词失败:", k);
    }
    return console.log("下载完成:", { musicPath: S, lyricsPath: b }), {
      success: !0,
      musicPath: S,
      lyricsPath: b
    };
  } catch (i) {
    return console.error("下载音乐和歌词失败:", i), { success: !1, error: i.message };
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
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), M(), U();
});
o.on("will-quit", () => {
});
o.on("window-all-closed", () => {
  process.platform !== "darwin" && o.quit();
});
o.on("activate", () => {
  e === null ? M() : e.show();
});
