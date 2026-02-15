import { app as l, ipcMain as d, shell as k, BrowserWindow as v, nativeImage as x, Tray as T, Menu as M } from "electron";
import p from "path";
import m from "fs";
import { fileURLToPath as C } from "url";
const w = p.dirname(C(import.meta.url));
process.platform === "linux" && (console.log("Linux平台检测到，检查托盘支持..."), console.log("提示：如果托盘图标不显示或右键无反应，请安装系统库："), console.log("  sudo apt-get install libayatana-appindicator3-1"), console.log("  或者设置环境变量：export XDG_CURRENT_DESKTOP=Unity"));
let e, i;
l.isQuitting = !1;
const S = l.requestSingleInstanceLock();
S ? l.on("second-instance", () => {
  e && (e.isMinimized() && e.restore(), e.focus());
}) : (console.log("已经有实例在运行，退出新实例"), l.quit(), process.exit(0));
function b() {
  console.log("createWindow: 开始创建窗口");
  const g = process.env.NODE_ENV === "development", a = g ? p.join(w, "../public/icon.png") : p.join(l.getAppPath(), "public/icon.png"), r = p.join(w, "./preload.cjs");
  if (console.log("createWindow: 图标路径 =", a), console.log("createWindow: preload 路径 =", r), e = new v({
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
  }), e.webContents.on("before-input-event", (n, t) => {
    t.control && t.shift && (t.key === "I" || t.key === "i") && n.preventDefault(), t.control && (t.key === "F12" || t.key === "f12") && n.preventDefault(), (t.alt && t.key === "F12" || t.alt && t.key === "f12") && n.preventDefault(), (t.key === "F11" || t.key === "f11") && n.preventDefault();
  }), g || !l.isPackaged)
    console.log("createWindow: 加载开发服务器 http://localhost:5173"), e.loadURL("http://localhost:5173");
  else {
    console.log("createWindow: 加载生产文件");
    const n = l.getAppPath(), t = p.join(n, "dist/index.html");
    console.log("生产文件路径:", t), e.loadFile(t);
  }
  e.on("close", (n) => {
    l.isQuitting || (n.preventDefault(), e.hide());
  }), e.on("closed", () => {
    e = null;
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
function L() {
  console.log("createTray: 开始创建托盘");
  const g = (o) => {
    const s = process.resourcesPath ? p.join(process.resourcesPath, o) : null, c = p.join(l.getAppPath(), "public", o), f = p.join(w, "../public", o);
    return s && m.existsSync(s) ? (console.log(`使用resources路径: ${s}`), s) : m.existsSync(c) ? (console.log(`使用app路径: ${c}`), c) : m.existsSync(f) ? (console.log(`使用相对路径: ${f}`), f) : (console.log(`使用默认app路径: ${c}`), c);
  }, a = g("icon.png"), r = x.createFromPath(a);
  console.log("托盘图标路径:", a), console.log("托盘图标是否为空:", r.isEmpty()), i && (i.destroy(), i = null), i = new T(r), i.setToolTip("Neko云音乐"), (() => {
    const o = {};
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
        const f = g(`${c}.png`), y = x.createFromPath(f);
        y.resize({ width: 18, height: 18 }), o[c] = y;
      } catch (f) {
        console.warn(`Failed to load icon: ${c}`, f);
      }
    }), o;
  })();
  const t = async () => {
    if (e)
      try {
        const o = await e.webContents.executeJavaScript('localStorage.getItem("currentMusic")');
        o ? h.currentMusic = JSON.parse(o) : h.currentMusic = null;
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
        s && (h.isPlaying = s.isPlaying);
      } catch (o) {
        console.error("同步播放状态失败:", o);
      }
  }, u = async () => {
    await t();
    const o = h.currentMusic;
    let s = "暂无播放";
    if (o) {
      const y = o.title || "未知歌曲", P = o.artist || "未知艺术家";
      s = `${y.length > 15 ? y.substring(0, 15) + "..." : y} - ${P}`;
    }
    const c = [
      // 顶部：当前播放信息
      {
        label: s,
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
          l.isQuitting = !0, l.quit();
        }
      }
    ], f = M.buildFromTemplate(c);
    console.log("托盘菜单已构建，包含", c.length, "个菜单项"), process.platform === "linux" ? console.log("Linux平台：不设置自动菜单，使用手动弹出方式") : (i.setContextMenu(f), console.log("托盘菜单已设置到托盘对象")), i.setToolTip("Neko云音乐"), console.log("托盘工具提示已设置"), o && i.setToolTip(`正在播放: ${o.title} - ${o.artist}`), process.platform === "linux" && (i.linuxContextMenu = f, console.log("Linux平台：菜单已保存用于手动弹出"));
  };
  u(), d.on("player-state-changed", (o, s) => {
    s && (h = { ...h, ...s }, u());
  }), d.on("music-play", (o, s) => {
    h.currentMusic = s, h.isPlaying = !0, u();
  }), d.on("play-state-changed", (o, s) => {
    h.isPlaying = s, u();
  }), i.on("click", () => {
    console.log("托盘图标被点击"), e && (e.isVisible() ? e.isFocused() ? e.hide() : e.focus() : (e.show(), e.focus()));
  }), i.on("double-click", () => {
    console.log("托盘图标被双击"), e && (e.isVisible() || e.show(), e.focus());
  }), i.on("right-click", (o) => {
    console.log("托盘图标右键被点击，平台:", process.platform), process.platform === "linux" ? i.linuxContextMenu ? (console.log("Linux平台：手动弹出菜单"), i.linuxContextMenu.popup({ window: e })) : console.error("Linux平台：找不到菜单引用") : i.popUpContextMenu();
  }), i.on("mouse-down", (o) => {
    console.log("托盘图标鼠标按下事件:", o, "buttons:", o.buttons), process.platform === "linux" && o.buttons === 2 && (console.log("Linux平台：右键按下，准备弹出菜单"), i.linuxContextMenu && i.linuxContextMenu.popup({ window: e }));
  }), setInterval(u, 5e3);
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
d.handle("save-file", async (g, a) => {
  const { fileName: r, fileType: n, suggestedPath: t } = a;
  let u = l.getPath("userData");
  return t && (u = p.join(u, t)), m.existsSync(u) || m.mkdirSync(u, { recursive: !0 }), p.join(u, r);
});
d.handle("write-file", async (g, a, r) => {
  try {
    const n = Buffer.from(r);
    return m.writeFileSync(a, n), { success: !0, path: a };
  } catch (n) {
    return console.error("写入文件失败:", n), { success: !1, error: n.message };
  }
});
d.handle("open-file", async (g, a) => {
  try {
    return await k.openPath(a), { success: !0 };
  } catch (r) {
    return console.error("打开文件失败:", r), { success: !1, error: r.message };
  }
});
d.handle("http-request", async (g, a, r = {}) => {
  try {
    const n = await fetch(a, {
      ...r,
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        ...r.headers || {}
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
l.on("ready", () => {
  if (l.isQuitting) {
    console.log("应用已退出，跳过窗口创建");
    return;
  }
  if (e) {
    console.log("窗口已存在，显示窗口"), e.show(), e.focus();
    return;
  }
  console.log("创建新窗口，NODE_ENV:", process.env.NODE_ENV), b(), L();
});
l.on("will-quit", () => {
});
l.on("window-all-closed", () => {
  process.platform !== "darwin" && l.quit();
});
l.on("activate", () => {
  e === null ? b() : e.show();
});
