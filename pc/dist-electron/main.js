import { app as n, ipcMain as d, shell as v, BrowserWindow as k, nativeImage as P, Tray as T, Menu as M } from "electron";
import u from "path";
import m from "fs";
import { fileURLToPath as S } from "url";
const w = u.dirname(S(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, p;
n.isQuitting = !1;
const z = n.requestSingleInstanceLock();
z ? n.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), n.quit(), process.exit(0));
function b() {
  console.log("createWindow: 开始创建窗口");
  const f = process.env.NODE_ENV === "development", a = f ? u.join(w, "../public/icon.png") : u.join(n.getAppPath(), "public/icon.png"), r = u.join(w, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", a), console.log("createWindow: preload 路径 =", r), e = new k({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: !1,
    autoHideMenuBar: !0,
    icon: a,
    title: "Neko云音乐",
    webPreferences: {
      preload: r,
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
  }), e.webContents.on("before-input-event", (t, o) => {
    o.control && o.shift && (o.key === "I" || o.key === "i") && t.preventDefault(), o.control && (o.key === "F12" || o.key === "f12") && t.preventDefault(), (o.alt && o.key === "F12" || o.alt && o.key === "f12") && t.preventDefault(), (o.key === "F11" || o.key === "f11") && t.preventDefault();
  }), f || !n.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const t = n.getAppPath(), o = u.join(t, "dist/index.html");
    console.log("生产文件路径:", o), e.loadFile(o);
  }
  e.on("close", (t) => {
    n.isQuitting || (t.preventDefault(), e.hide());
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
function N() {
  console.log("createTray: 开始创建托盘");
  const f = (s) => {
    const i = process.resourcesPath ? u.join(process.resourcesPath, s) : null, c = u.join(n.getAppPath(), "public", s), h = u.join(w, "../public", s);
    return i && m.existsSync(i) ? (console.log(`使用resources路径: ${i}`), i) : m.existsSync(c) ? (console.log(`使用app路径: ${c}`), c) : m.existsSync(h) ? (console.log(`使用相对路径: ${h}`), h) : (console.log(`使用默认app路径: ${c}`), c);
  }, a = f("icon.png"), r = P.createFromPath(a);
  console.log("托盘图标路径:", a), console.log("托盘图标是否为空:", r.isEmpty()), p && (p.destroy(), p = null), p = new T(r), p.setToolTip("Neko云音乐"), (() => {
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
    ].forEach((c) => {
      try {
        const h = f(`${c}.png`), y = P.createFromPath(h);
        y.resize({ width: 18, height: 18 }), s[c] = y;
      } catch (h) {
        console.warn(`Failed to load icon: ${c}`, h);
      }
    }), s;
  })();
  const o = async () => {
    if (e)
      try {
        const s = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        s ? g.currentMusic = JSON.parse(s) : g.currentMusic = null;
        const i = await e.webContents.executeJavaScript(`
        (function() {
          const audio = document.querySelector('audio');
          return {
            isPlaying: audio ? !audio.paused : false,
            currentTime: audio ? audio.currentTime : 0,
            duration: audio ? audio.duration : 0
          };
        })()
      `);
        i && (g.isPlaying = i.isPlaying);
      } catch (s) {
        console.error("同步播放状态失败:", s);
      }
  }, l = async () => {
    await o();
    const s = g.currentMusic;
    let i = "暂无播放";
    if (s) {
      const y = s.title || "未知歌曲", x = s.artist || "未知艺术家";
      i = `${y.length > 15 ? y.substring(0, 15) + "..." : y} - ${x}`;
    }
    const c = [
      // 顶部：当前播放信息
      {
        label: i,
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
          n.isQuitting = !0, n.quit();
        }
      }
    ], h = M.buildFromTemplate(c);
    console.log("托盘菜单已构建，包含", c.length, "个菜单项"), p.setContextMenu(h), console.log("托盘菜单已设置到托盘对象"), p.setToolTip("Neko云音乐"), console.log("托盘工具提示已设置"), s && p.setToolTip(`正在播放: ${s.title} - ${s.artist}`);
  };
  l(), d.on("player-state-changed", (s, i) => {
    i && (g = { ...g, ...i }, l());
  }), d.on("music-play", (s, i) => {
    g.currentMusic = i, g.isPlaying = !0, l();
  }), d.on("play-state-changed", (s, i) => {
    g.isPlaying = i, l();
  }), p.on("click", () => {
    console.log("托盘图标被点击"), e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), p.on("double-click", () => {
    console.log("托盘图标被双击"), e && (e.isVisible() || e.show(), e.focus());
  }), setInterval(l, 5e3);
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
d.handle("get-path", async (f, a) => n.getPath(a));
d.handle("save-file", async (f, a) => {
  const { fileName: r, fileType: t, suggestedPath: o } = a;
  let l;
  return o && o.includes("NekoMusic") ? (l = n.getPath("downloads"), o && (l = u.join(l, o))) : (l = n.getPath("userData"), o && (l = u.join(l, o))), m.existsSync(l) || m.mkdirSync(l, { recursive: !0 }), u.join(l, r);
});
d.handle("write-file", async (f, a, r) => {
  try {
    const t = Buffer.from(r);
    return m.writeFileSync(a, t), { success: !0, path: a };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
d.handle("open-file", async (f, a) => {
  try {
    return await v.openPath(a), { success: !0 };
  } catch (r) {
    return console.error("打开文件失败:", r), { success: !1, error: r.message };
  }
});
d.handle("http-request", async (f, a, r = {}) => {
  try {
    const t = await fetch(a, {
      ...r,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...r.headers || {}
      }
    }), o = await t.text();
    return {
      success: !0,
      status: t.status,
      data: o,
      headers: Object.fromEntries(t.headers.entries())
    };
  } catch (t) {
    return console.error("HTTP请求失败:", t), { success: !1, error: t.message };
  }
});
n.on("ready", () => {
  if (n.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), b(), N();
});
n.on("will-quit", () => {
});
n.on("window-all-closed", () => {
  process.platform !== "darwin" && n.quit();
});
n.on("activate", () => {
  e === null ? b() : e.show();
});
