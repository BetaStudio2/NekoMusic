import { app as n, ipcMain as u, shell as T, BrowserWindow as k, nativeImage as x, Tray as M, Menu as S } from "electron";
import l from "path";
import w from "fs";
import { fileURLToPath as z } from "url";
const P = l.dirname(z(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, m;
n.isQuitting = !1;
const E = n.requestSingleInstanceLock();
E ? n.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), n.quit(), process.exit(0));
function b() {
  console.log("createWindow: 开始创建窗口");
  const f = process.env.NODE_ENV === "development", r = f ? l.join(P, "../public/icon.png") : l.join(n.getAppPath(), "public/icon.png"), a = l.join(P, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", r), console.log("createWindow: preload 路径 =", a), e = new k({
    width: 1280,
    height: 720,
    minWidth: 1280,
    minHeight: 720,
    frame: !1,
    autoHideMenuBar: !0,
    icon: r,
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
  }), e.webContents.on("before-input-event", (o, t) => {
    t.control && t.shift && (t.key === "I" || t.key === "i") && o.preventDefault(), t.control && (t.key === "F12" || t.key === "f12") && o.preventDefault(), (t.alt && t.key === "F12" || t.alt && t.key === "f12") && o.preventDefault(), (t.key === "F11" || t.key === "f11") && o.preventDefault();
  }), f || !n.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const o = n.getAppPath(), t = l.join(o, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.on("close", (o) => {
    n.isQuitting || (o.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
  });
}
let p = {
  currentMusic: null,
  isPlaying: !1,
  playMode: "list",
  // list, single, shuffle
  volume: 80,
  lyricsEnabled: !1,
  desktopLyricsEnabled: !1
};
function D() {
  console.log("createTray: 开始创建托盘");
  const f = (s) => {
    const i = process.resourcesPath ? l.join(process.resourcesPath, s) : null, c = l.join(n.getAppPath(), "public", s), d = l.join(P, "../public", s);
    return i && w.existsSync(i) ? (console.log(`使用resources路径: ${i}`), i) : w.existsSync(c) ? (console.log(`使用app路径: ${c}`), c) : w.existsSync(d) ? (console.log(`使用相对路径: ${d}`), d) : (console.log(`使用默认app路径: ${c}`), c);
  }, r = f("icon.png"), a = x.createFromPath(r);
  process.platform !== "linux" && a.resize({ width: 16, height: 16 }), console.log("托盘图标路径:", r), console.log("托盘图标是否为空:", a.isEmpty()), m = new M(a);
  const t = (() => {
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
        const d = f(`${c}.png`), g = x.createFromPath(d);
        g.resize({ width: 18, height: 18 }), s[c] = g;
      } catch (d) {
        console.warn(`Failed to load icon: ${c}`, d);
      }
    }), s;
  })(), h = async () => {
    if (e)
      try {
        const s = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        s ? p.currentMusic = JSON.parse(s) : p.currentMusic = null;
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
        i && (p.isPlaying = i.isPlaying);
      } catch (s) {
        console.error("同步播放状态失败:", s);
      }
  }, y = async () => {
    await h();
    const s = p.currentMusic;
    let i = "暂无播放";
    if (s) {
      const g = s.title || "未知歌曲", v = s.artist || "未知艺术家";
      i = `${g.length > 15 ? g.substring(0, 15) + "..." : g} - ${v}`;
    }
    const c = [
      // 顶部：当前播放信息
      {
        label: i,
        enabled: !1
      },
      { type: "separator" },
      // 显示/隐藏窗口
      {
        label: e && e.isVisible() ? "隐藏窗口" : "显示窗口",
        click: () => {
          e && (e.isVisible() ? e.hide() : (e.show(), e.focus()));
        }
      },
      { type: "separator" },
      // 退出
      {
        label: "退出",
        icon: t["tray-exit"],
        click: () => {
          n.isQuitting = !0, n.quit();
        }
      }
    ], d = S.buildFromTemplate(c);
    m.setContextMenu(d), m.setToolTip("Neko云音乐"), s && m.setToolTip(`正在播放: ${s.title} - ${s.artist}`);
  };
  y(), u.on("player-state-changed", (s, i) => {
    i && (p = { ...p, ...i }, y());
  }), u.on("music-play", (s, i) => {
    p.currentMusic = i, p.isPlaying = !0, y();
  }), u.on("play-state-changed", (s, i) => {
    p.isPlaying = i, y();
  }), m.on("click", () => {
    process.platform === "linux" ? m.popUpContextMenu() : e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
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
u.handle("save-file", async (f, r) => {
  const { fileName: a, fileType: o, suggestedPath: t } = r;
  let h = n.getPath("userData");
  return t && (h = l.join(h, t)), w.existsSync(h) || w.mkdirSync(h, { recursive: !0 }), l.join(h, a);
});
u.handle("write-file", async (f, r, a) => {
  try {
    const o = Buffer.from(a);
    return w.writeFileSync(r, o), { success: !0, path: r };
  } catch (o) {
    return console.error("写入文件失败:", o), { success: !1, error: o.message };
  }
});
u.handle("open-file", async (f, r) => {
  try {
    return await T.openPath(r), { success: !0 };
  } catch (a) {
    return console.error("打开文件失败:", a), { success: !1, error: a.message };
  }
});
u.handle("http-request", async (f, r, a = {}) => {
  try {
    const o = await fetch(r, {
      ...a,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...a.headers || {}
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
n.on("ready", () => {
  if (n.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), b(), D();
});
n.on("will-quit", () => {
});
n.on("window-all-closed", () => {
  process.platform !== "darwin" && n.quit();
});
n.on("activate", () => {
  e === null ? b() : e.show();
});
