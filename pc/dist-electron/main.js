import { app as n, ipcMain as f, shell as T, BrowserWindow as k, nativeImage as b, Tray as M, Menu as S } from "electron";
import u from "path";
import w from "fs";
import { fileURLToPath as z } from "url";
const P = u.dirname(z(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, c;
n.isQuitting = !1;
const E = n.requestSingleInstanceLock();
E ? n.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), n.quit(), process.exit(0));
function x() {
  console.log("createWindow: 开始创建窗口");
  const d = process.env.NODE_ENV === "development", r = d ? u.join(P, "../public/icon.png") : u.join(n.getAppPath(), "public/icon.png"), a = u.join(P, "./preload.cjs");
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
  }), e.webContents.on("before-input-event", (t, o) => {
    o.control && o.shift && (o.key === "I" || o.key === "i") && t.preventDefault(), o.control && (o.key === "F12" || o.key === "f12") && t.preventDefault(), (o.alt && o.key === "F12" || o.alt && o.key === "f12") && t.preventDefault(), (o.key === "F11" || o.key === "f11") && t.preventDefault();
  }), d || !n.isPackaged)
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
let y = {
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
  const d = (s) => {
    const i = process.resourcesPath ? u.join(process.resourcesPath, s) : null, l = u.join(n.getAppPath(), "public", s), p = u.join(P, "../public", s);
    return i && w.existsSync(i) ? (console.log(`使用resources路径: ${i}`), i) : w.existsSync(l) ? (console.log(`使用app路径: ${l}`), l) : w.existsSync(p) ? (console.log(`使用相对路径: ${p}`), p) : (console.log(`使用默认app路径: ${l}`), l);
  }, r = d("icon.png"), a = b.createFromPath(r);
  console.log("托盘图标路径:", r), console.log("托盘图标是否为空:", a.isEmpty()), c && (c.destroy(), c = null), c = new M(a), c.setToolTip("Neko云音乐");
  const o = (() => {
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
        const p = d(`${l}.png`), m = b.createFromPath(p);
        m.resize({ width: 18, height: 18 }), s[l] = m;
      } catch (p) {
        console.warn(`Failed to load icon: ${l}`, p);
      }
    }), s;
  })(), h = async () => {
    if (e)
      try {
        const s = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        s ? y.currentMusic = JSON.parse(s) : y.currentMusic = null;
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
        i && (y.isPlaying = i.isPlaying);
      } catch (s) {
        console.error("同步播放状态失败:", s);
      }
  }, g = async () => {
    await h();
    const s = y.currentMusic;
    let i = "暂无播放";
    if (s) {
      const m = s.title || "未知歌曲", v = s.artist || "未知艺术家";
      i = `${m.length > 15 ? m.substring(0, 15) + "..." : m} - ${v}`;
    }
    const l = [
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
        icon: o["tray-exit"],
        click: () => {
          n.isQuitting = !0, n.quit();
        }
      }
    ], p = S.buildFromTemplate(l);
    c.setContextMenu(p), c.setToolTip("Neko云音乐"), s && c.setToolTip(`正在播放: ${s.title} - ${s.artist}`);
  };
  g(), f.on("player-state-changed", (s, i) => {
    i && (y = { ...y, ...i }, g());
  }), f.on("music-play", (s, i) => {
    y.currentMusic = i, y.isPlaying = !0, g();
  }), f.on("play-state-changed", (s, i) => {
    y.isPlaying = i, g();
  }), c.on("click", () => {
    console.log("托盘图标被点击"), e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), c.on("double-click", () => {
    console.log("托盘图标被双击"), e && (e.isVisible() || e.show(), e.focus());
  }), setInterval(g, 5e3);
}
f.on("window-minimize", () => {
  e && e.minimize();
});
f.on("window-maximize", () => {
  e && (e.isMaximized() ? e.unmaximize() : e.maximize());
});
f.on("window-close", () => {
  e && e.hide();
});
f.handle("save-file", async (d, r) => {
  const { fileName: a, fileType: t, suggestedPath: o } = r;
  let h = n.getPath("userData");
  return o && (h = u.join(h, o)), w.existsSync(h) || w.mkdirSync(h, { recursive: !0 }), u.join(h, a);
});
f.handle("write-file", async (d, r, a) => {
  try {
    const t = Buffer.from(a);
    return w.writeFileSync(r, t), { success: !0, path: r };
  } catch (t) {
    return console.error("写入文件失败:", t), { success: !1, error: t.message };
  }
});
f.handle("open-file", async (d, r) => {
  try {
    return await T.openPath(r), { success: !0 };
  } catch (a) {
    return console.error("打开文件失败:", a), { success: !1, error: a.message };
  }
});
f.handle("http-request", async (d, r, a = {}) => {
  try {
    const t = await fetch(r, {
      ...a,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...a.headers || {}
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
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), x(), N();
});
n.on("will-quit", () => {
});
n.on("window-all-closed", () => {
  process.platform !== "darwin" && n.quit();
});
n.on("activate", () => {
  e === null ? x() : e.show();
});
