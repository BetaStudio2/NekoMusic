# Neko 歌姬计划 · 前端 UI 重构开发文档

> 版本：v1（2026-09）
> 范围：`frontend/`（Vue 3 + Vite）
> 归档基线：Git 标签 `legacy-ui-v1` / 分支 `legacy-ui`
> 配套文档：[`src/FRAMEWORK.md`](../src/FRAMEWORK.md)（框架 API 速查）

---

## 1. 背景与目标

原前端由多人在较长时间内迭代而成，功能完整但视觉与结构存在明显技术债：样式体系多头、页面各自复制背景与变量、装饰手法（尤其是「彩色高亮条」）泛滥且不统一。

本次重构目标：

1. **统一视觉语言** —— 一套设计令牌 + 一套原子组件，消除页面间的视觉漂移。
2. **精简装饰** —— 去除廉价、堆砌的装饰（重点：高亮条、滥用发光、动画渐变），改以排版层次与留白建立秩序。
3. **不改变技术栈** —— 继续 Vue 3 + Vite，**不引入 TypeScript**。
4. **可安全回退** —— 旧实现归档在 `legacy-ui` 分支，随时可对照。
5. **逐页迁移** —— 先立框架，再按页替换，任何时刻线上可用。

---

## 2. 项目现状总览

### 2.1 技术栈

| 层 | 技术 |
|---|---|
| 前端框架 | Vue 3.5（Composition API + `<script setup>`，纯 JS） |
| 构建 | Vite 8，产物输出到 `backend/src/main/resources/site` |
| 路由 | vue-router 5（history 模式，全量路由集中在 `src/router/index.js`） |
| 提示 | vue-toastification |
| 图表 | chart.js（仅管理端统计） |
| 二维码 | qrcode（会员支付） |
| HTTP | 原生 `fetch` 为主，`axios` 少量使用 |
| 图标层（新增） | `morphicons` + `lucide`（数据） |
| 后端 | Java 自研 HTTP handler（同源，`/api/**`） |

### 2.2 代码规模

- 源码合计约 **29,900 行**；`.vue` 45 个，`.js` 14 个。
- 最大文件：`GlobalPlayer.vue` 2,905 行、`AdminMusicView.vue` 2,441 行、`PlayerView.vue` 1,864 行。

### 2.3 目录结构（重构后）

```
frontend/
├── index.html               SEO / 首屏 splash / 无 JS 兜底
├── jsconfig.json            仅配置 @/* 别名（无类型检查）
├── vite.config.js           构建 / 别名 / 手动分块
├── docs/                    ← 本文档所在
└── src/
    ├── main.js              应用入口
    ├── App.vue              顶层壳：顶栏 + RouterView + 播放器 + 页脚
    ├── router/index.js      全部路由 + 移动端重定向 + 标题/SEO 守卫
    ├── assets/main.css      旧全局样式（含 --neko-* 变量、admin 皮肤）
    ├── styles/glassShell.css 旧 .glass-page 页壳变量映射
    ├── config/apiConfig.js  API 基址（同源推导）
    ├── api/                 clientReleases / videoRender / vipPricing
    ├── utils/               nativeAppOpen / userVip / format（新）
    ├── components/          旧共享组件（待迁移）
    ├── views/               页面（含 admin/ 子目录）
    │
    ├── design/              【新】设计令牌 tokens.css + reset.css
    ├── icons/               【新】registry.js + NIcon.vue
    ├── ui/                  【新】NButton/NCard/NInput/NTag/NSpinner/NModal
    ├── composables/         【新】useTheme / useToast
    └── layouts/             【新】AppShell / PageShell / AmbientBackdrop
```

### 2.4 子模块

`Android/`、`pc/`、`Neko歌姬计划文档/` 均为 Git 子模块（外部仓库），本仓库内为空目录，重构不涉及。

---

## 3. 现有 UI 资产盘点

### 3.1 共享组件（`src/components/`）

| 组件 | 行数 | 职责 | 备注 |
|---|---|---|---|
| `GlobalPlayer.vue` | 2905 | 底部全局播放条：播放/进度/模式/歌词/播放列表/收藏/MediaSession | 全站最重，技术债最多（见 §4.4） |
| `SearchHeader.vue` | 697 | 顶栏：Logo + 搜索（防抖下拉）+ 用户区 | `chromeDark` 切换深色皮肤；搜索逻辑内联 |
| `AdminSidebar.vue` | 264 | 管理后台侧栏导航 | 含 3px 高亮条；按角色显示菜单 |
| `Footer.vue` | 160 | 页脚：备案 / 组织 / 链接 | 含下划线动画 |
| `LrcBadge.vue` | 36 | 「有歌词」小徽章 | 内联 SVG，唯一被视图复用的组件 |

### 3.2 用户端页面（19 个）

| 页面 | 行数 | 职责 | 关键接口 |
|---|---|---|---|
| `HomeView` | 742 | 首页发现 + 热门/最新预览 | `GET /api/music/ranking`、`/latest` |
| `PlayerView` | 1864 | 单曲详情：封面/歌词/收藏/下载/分享视频 | `/api/music/info|lyrics|file`、`/api/user/favorites`、`/api/video/render/*` |
| `SearchResultsView` | 1117 | 搜索结果（单曲/歌单/艺人三栏） | `POST /api/music/search`、`/api/playlists/search`、`/api/artists/search` |
| `UploadMusicView` | 1619 | 上传音乐（封面/歌词/表单） | `POST /api/user/upload` |
| `DownloadView` | 1471 | 客户端下载（Android/PC + 歌单迁入） | `GET /version` |
| `PlaylistDetailView` | 1001 | 歌单详情 + 添加音乐 | `/api/user/playlist/music/*`、`/api/playlist/{id}` |
| `UserVipView` | 995 | 会员购买（套餐 + 收款码） | `/api/vip/pricing`、`/api/vip/pay/create` |
| `UserPlaylistsView` | 731 | 我的歌单（卡片 + 编辑/删除） | `/api/user/playlists`、`/api/user/playlist/*` |
| `UserRegisterView` | 1111 | 注册 + 滑块验证码 | `/api/captcha/slider*`、`/api/user/register` |
| `UserProfileView` | 686 | 个人中心（资料/安全） | `/api/user/nickname/change`；**头像/改密未接后端** |
| `ForgotPasswordView` | 469 | 两步找回密码 | `POST /api/user/send-reset-code`、`/reset-password` |
| `UserFavoritesView` | 406 | 我的收藏 | `/api/user/favorites` |
| `LatestView` | 380 | 最新音乐列表 | `/api/music/latest` |
| `RankingView` | 379 | 热门排行榜 | `/api/music/ranking` |
| `CreatePlaylistView` | 358 | 创建歌单 | `POST /api/user/playlist/create` |
| `UserLoginView` | 212 | 登录 | `POST /api/user/login` |
| `ErrorView` | 206 | 404 | — |
| `AboutView` | 200 | 关于我们 | — |
| `PrivacyPolicyView` | 595 | 隐私政策长文 | — |

### 3.3 管理端页面（9 个 + 侧栏）

| 页面 | 行数 | 职责 |
|---|---|---|
| `AdminView` | 692 | 统计概览（数字卡片 + chart.js 折线） |
| `AdminMusicView` | 2441 | 音乐 CRUD（含本地音频元数据解析） |
| `AdminAuditView` | 1439 | 上传审核（试听 + 双语歌词预览） |
| `AdminUsersView` | 1242 | 用户/管理员管理 |
| `AdminLyricsEditorView` | 921 | 歌词文件树 + 编辑器 |
| `AdminReleasesView` | 522 | 客户端版本与安装包发布 |
| `AdminVipPricingView` | 349 | VIP 价目维护 |
| `AdminLoginView` | 282 | 管理员登录 |
| `AdminSettingsView` | 395 | **未被任何路由引用（孤立废弃页）** |

**管理端关键结构问题**：不存在嵌套布局容器。`/admin/*` 每个路由都是平级独立页面，各自复制 `AdminSidebar + 顶栏 + 内容`，靠 `main.css` 里一段 `.admin-layout { … !important }` 全局皮肤统一暗色外观。

### 3.4 反复出现的 UI 模式

- **页壳**：`glass-page` + `ambient`（blob 光斑 + grid 网格）。约 7 个页面（Home/Search/Player/PlaylistDetail/UserPlaylists/Download/CreatePlaylist）**自行复制**了一套 blob+grid 与关键帧，未复用 `glassShell.css`。
- **列表行**：编号列表（Latest/Ranking）、集合行（Search/PlaylistDetail/Favorites）、卡片（UserPlaylists `pl-card`、UserVip `plan-card`）。
- **表单**：登录/注册/忘记密码/创建歌单/上传共用 `form-group + input + btn-submit`，focus 统一 `box-shadow: 0 0 0 3px rgba(105,200,223,.12)`。
- **弹窗**：`modal-overlay + panel + ×`，均就地内联，**无公共 Modal 组件**。
- **加载/空态**：`state__spinner`（多页重复定义 `@keyframes spin`）、`state--empty`。
- **主按钮**：`linear-gradient(135deg, #9beaff, var(--accent2))` + 青色发光 + 胶囊圆角，多页同源复制。
- **左侧高亮条**：见 §4.2 专项清单。
- **管理端表格**：`.table-container + *-table + 分页`，各页实现不一。

### 3.5 必须保留的全局契约

重构**不得破坏**以下跨组件/跨页面约定：

**localStorage keys**

| key | 含义 |
|---|---|
| `userToken` | 用户令牌（用户接口鉴权，部分用裸 token 而非 Bearer） |
| `user` | 用户信息（含 `isVip`、`vipExpiresAt`） |
| `adminToken` / `adminInfo` / `isAdminLoggedIn` | 管理员鉴权（用 `Bearer`） |
| `currentPlayingMusic` | 当前播放曲目对象 |
| `globalPlayerState` | `{ isPlaying, currentTime, duration, playbackMode }` |
| `globalPlaylist` | 全局播放列表数组 |
| `playbackMode` | `list_repeat` / `single_repeat` / `shuffle` |
| `mobileDownloadBannerClosed` | 移动下载横幅关闭标记 |

**window 自定义事件**

| 事件 | 语义 |
|---|---|
| `playerStateChange` | 播放器状态变更广播 |
| `forcePlay` | 强制开始播放 |
| `playlistUpdated` | 播放列表变更 |
| `pauseGlobalPlayer` | 请求暂停全局播放 |
| `neko-user-vip-sync` | VIP 状态同步 |

**URL 通道**：hash `#play=<json>`、`#playlist=<json>&index=N`；query `nekoweb=1`（原生 App 回退标记）。

**原生 Scheme**：`nekomusic://player/:id`、`nekomusic://playlist/:id`；Android 包名 `com.neko.music`。

> 迁移阶段这些契约**先原样保留**。若要与新的 `usePlayer` composable 整合，应在完成页面迁移后单独立项，避免边改界面边改协议。

---

## 4. 现有实现的问题

### 4.1 样式体系

1. **多头变量**：`:root --neko-*`（main.css）→ `.glass-page` 映射为 `--text/--muted/...`（glassShell.css）→ 多个页面再各自定义 `--bg0/--accent/--radius/...`。同一个青色在不同文件里被重复硬编码为 `rgba(105,200,223,x)` 数十次。
2. **`--accent-strong` 未定义**：`UserVipView` 等处引用了只在 `.glass-page` 内间接存在的 `--accent-strong`，脱离该页壳后颜色会回退。
3. **登录页无 blob**：`UploadMusicView`、`UserVipView` 没有 ambient 背景，与其余页面不一致。
4. **紫色残留**：`UploadMusicView` 仍用 `#667eea/#764ba2` 紫色渐变，靠 main.css 的 `.upload-view` 补丁强纠，是唯一脱离青色系的页面。
5. **关键帧复制**：`blobFloat`、`gridBreathe`、`spin`、`statePulse` 在多文件重复定义。

### 4.2 「高亮条」专项清单

这是本次要重点治理的视觉问题。全项目清单：

| 位置 | 形式 | 问题 |
|---|---|---|
| `HomeView .browse__rail` | 左侧 5px，`linear-gradient` + `railFlow` **无限动画** | 最典型的廉价装饰：动态彩条与内容无关，抢注意力 |
| `DownloadView .netease-panel__rail` | 左侧静态 红→青 渐变条 | 同上，且红色与品牌青不协 |
| `DownloadView .android__rail` | 左侧青色系 + `railFlow` 动画 | 同上 |
| `AboutView` 列表项 | `border-left: 3px solid rgba(105,200,223,.55)` | 用色条代替排版层次 |
| `PrivacyPolicyView` 提示/条目 | `border-left: 3px`（青/紫两种） | 同页出现两种颜色的条，更乱 |
| `UserProfileView` 信息项 | `border-left: 3px` | 同上 |
| `AdminSidebar .nav-link` | `border-left: 3px`，hover/active 变青 | 侧栏激活态靠色条，是旧式后台观感 |
| `AdminSettingsView .tab-btn.active` | `border-bottom: 2px` | 底部色条切页 |
| `PlayerView` 歌词激活行 | 渐变色条/左侧高亮 | 可在歌词场景保留，但需统一为单一手法 |

**为什么不好看**：高亮条是「用一条彩色边代替真正的信息层级」的取巧做法。它
① 与内容无关、纯装饰，视觉噪音高；
② 厚度/颜色/是否动画各不相同，跨页不一致；
③ 动画彩条（`railFlow`）尤其有 2010 年代后台的陈旧感；
④ 真正的重点（标题、CTA、激活项）反而被稀释。

### 4.3 结构与可维护性

1. **无公共组件**：Modal、Pagination、Table、Empty、Skeleton 全部就地重复实现。
2. **管理端无布局容器**：每页复制侧栏 + 顶栏，切页整页重挂。
3. **鉴权三处校验**：路由 `adminGuard` → 侧栏 `hasPermission()` → 页面内 role 判断。
4. **接口调用散落**：多数页面内联 `fetch`，仅 3 个模块抽到 `api/`。

### 4.4 GlobalPlayer 技术债（单独列出）

- `playNext` / `playPrevious` / `playNextInShuffle` 三函数约 90% 重复；`currentTime=0.1; duration=0; …updateGlobalPlayerState()` 组合在文件内重复 30+ 次。
- 双通道状态同步（localStorage + CustomEvent + hash）职责重叠，易产生竞态与回声。
- 歌词模板硬编码只渲染 2 行，第二行动画永不触发；LRC 解析不支持 offset/元数据。
- 样式叠三层皮肤，末层引用的 `--text/--accent` 等变量在组件挂载位置（`#app` 下）**未定义**，存在颜色回退 bug。
- 巨额内联 SVG path 应以 `NIcon` 取代。
- 收藏接口用裸 token，与管理端 Bearer 不一致。

---

## 5. 设计方向

### 5.1 核心原则

1. **层次靠排版，不靠色块** —— 用字号、字重、颜色明度与留白建立主次；不用彩色边条。
2. **一个视图一个强调点** —— 每屏最多一处使用品牌青作为「主角」（通常是唯一主 CTA）。
3. **装饰让位于内容** —— 封面图、标题、数据是主角；背景只做极淡的氛围。
4. **状态用「底 + 字」表达** —— 激活/选中项用表面色变化 + 文字提亮，不用边条。
5. **动效克制且有因** —— 只用于反馈（hover/focus/加载/图标变形），不做无意义的常驻循环动画。
6. **一切取值来自令牌** —— 颜色/间距/圆角/时长只引用 `--n-*`。

### 5.2 高亮条的替代方案

| 原手法 | 替代 |
|---|---|
| 区块左侧动态彩条（`browse__rail`） | **直接删除**；区块靠卡片表面与标题层级自证 |
| 列表项 `border-left` 色条 | 中性 1px 分隔线（`--n-line-subtle`）或仅用间距分组 |
| 提示块色条 | 用 `NTag` / 图标 + 淡色表面底（`--n-info-soft`） |
| 侧栏激活色条 | 激活项用胶囊形表面底 + 文字/图标变强调色 |
| Tab 底部色条 | 激活 Tab 用表面底胶囊 / 或仅文字提亮 |
| 标题强调 | 渐变文字（已有 `--n-gradient-text`）或纯色加粗 |
| 静态发光边 | 交互时才出现的 `--n-shadow-glow`（focus/hover） |

**示例：侧栏激活态**

```css
/* 旧：靠左侧色条 */
.nav-link.active { border-left: 3px solid var(--neko-accent); }

/* 新：胶囊底 + 提亮 */
.nav-link.active {
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}
```

**示例：区块标题**

```html
<!-- 旧：<section class="browse"><div class="browse__rail"/>…  -->
<!-- 新： -->
<section class="section">
  <header class="section__head">
    <h2 class="section__title">热门与最新</h2>
    <p class="section__sub">排行榜与最新上架封面预览</p>
  </header>
  …
</section>
```

### 5.3 视觉语言规范

> 主题已于 2026-09 定为「**黑偏青 + 圆角矩形**」，全站统一。

- **基调**：黑偏青 —— 近黑为底（青相 ~190°），青为唯一强调色。
- **背景**：`--n-bg` 渐变 + 极淡 ambient（统一由 `AppShell` 提供，页面不再自建）。
- **表面**：玻璃卡片 `NCard variant="glass"`，圆角 `--n-radius-lg`（18px），呼吸感来自阴影而非彩边。
- **文字**：主 `--n-text`、次 `--n-text-muted`、弱 `--n-text-faint`；标题可用渐变文字。
- **强调**：`--n-accent(#5fd0e0)` 仅用于主 CTA、链接、图标激活、focus 环。
- **形状**：**圆角矩形语言，不使用胶囊**。
  - 按钮 / 输入框：`--n-radius-control`（12px）
  - 标签 / 小徽章：`--n-radius-xs`（8px）
  - 卡片：`--n-radius-lg`（18px）；封面：`--n-radius`（14px）
  - 大面板 / Hero：`--n-radius-xl`（24px）
  - `--n-radius-circle` 仅用于头像、加载环、关闭按钮等确需圆形处。
- **动效**：`--n-duration-fast`（160ms）用于交互反馈，`--n-duration`（240ms）用于位移/展开；禁止常驻循环动画（ambient 除外且需尊重 `prefers-reduced-motion`）。
- **图标**：统一 `NIcon`；状态切换（播放/暂停、菜单/关闭、收藏）用 morphicons 变形，替代生硬的显隐切换。

### 5.4 播放条形态（决策）

参考 ArchoeraMusic 的实现，确定采用**停靠式 + 空闲隐藏**，**不做悬浮**：

- **位置**：始终停靠页面底部（搜索栏始终在顶部），保持现有 chrome 层次。
- **空闲隐藏**：无当前曲目时整体下移并淡出，**不占用底部空间**；有曲目（含暂停的恢复会话）时从底部滑入 + 淡入（约 300ms，`ease-out`，尊重 `prefers-reduced-motion`）。
- **不可卸载**：`GlobalPlayer` 挂载着 hash / 自定义事件 / MediaSession 监听，空闲时只能**视觉隐藏**，不能 `v-if` 卸载，否则无法接收新的播放指令。
- **底栏联动**：页脚为播放器预留的高度需在隐藏时同步收起，避免空白。
- **落地批次**：随布局壳批次（批次 1）实现外壳与过渡；`GlobalPlayer` 本体重构仍在批次 6。

---

## 6. 新框架（已交付）

> API 速查见 [`src/FRAMEWORK.md`](../src/FRAMEWORK.md)，此处仅列要点。

- **设计令牌** `src/design/tokens.css`：`--n-*` 全量变量；`reset.css` 元素重置与全局环境。
- **图标层** `src/icons/`：`registry.js` 集中登记 lucide 数据（唯一 import 处，保证 tree-shaking）；`NIcon.vue` 支持静态 / 自动变形 / 受控变形。
- **原子组件** `src/ui/`：`NButton` `NCard` `NInput` `NTag` `NSpinner` `NModal`。
- **组合式函数** `src/composables/`：`useTheme`、`useToast`；`src/utils/format.js` 格式化。
- **布局壳** `src/layouts/`：`AppShell`（环境光 + 顶栏 + 内容 + 播放器 + 页脚）、`PageShell`（宽度 + 标题区）、`AmbientBackdrop`。
- **自检页** `/__kit`：开发环境可视化验证全部基础层（`KitView.vue`）。
  **决策：迁移期间全程保留**，作为组件用法与视觉效果的参考样板；**待全部页面迁移完成后再移除**（见 §8.2 批次 7）。

新框架与旧代码当前**并存**：`main.js` 额外引入两份 CSS，旧页面不受影响。

---

## 7. HomeView 重构方案（样板页）

### 7.1 现状

页面由三个区块组成，主要问题集中在装饰而非结构：

```
home-page（自建 ambient + 自建变量集）
└─ main.shell
   ├─ section.intro         标题 + 说明 + 5 个胶囊入口（底边框分隔）
   ├─ section.migrate-strip 迁入说明（青色渐变底 + 渐变 CTA）
   └─ section.browse        热门/最新（左侧 5px 动画彩条 + 动画渐变底 + 两枚 mosaic 卡片）
```

关键数据：`GET /api/music/ranking`、`GET /api/music/latest?limit=300`；封面 `/api/music/cover/{id}`；登录态取自 `localStorage.userToken`。

### 7.2 设计问题

1. `browse__rail` 动态彩条 —— §4.2 已述，**首要删除目标**。
2. `migrate-strip` 用一整块青色渐变 + 渐变按钮，强调过度，与「一个视图一个强调点」冲突。
3. `.intro` 用底边框分隔，是「用线代替留白」。
4. 两枚卡片是唯一内容，信息密度低，首页显得空。
5. 页面自建 ambient 与变量，未复用框架。

### 7.3 最终排布（参考主流音乐 App）

参考 ArchoeraMusic 首页范式（`页头 → Hero 横幅 → 动作卡 → 横向封面栏`），最终实现：

**① 页头**

- `首页` 标题 + 按时段问候（早上好 / 下午好 / 晚上好 / 夜深了），登录后带昵称。

**② Hero 横幅（双态）**

- 一张圆角横幅卡片：**模糊封面底** + 压暗渐变，保证文字可读。
- **已登录且取到每日推荐** → 展示「每日推荐」：2×2 封面拼图、`播放推荐`、说明「每天 00:00 更新，共 N 首」。
- **未登录 / 推荐不可用** → 回退品牌态：热门第一首封面、`播放热门`、品牌说明。
- 数据：`GET /api/user/recommendations/daily`，`Authorization: <裸 userToken>`；
  请求失败或未登录一律**静默回退**，不弹错。
- 左侧：强调标签 → 渐变标题 → 说明 → CTA（`播放推荐/热门` 为唯一主强调，`歌单迁入` 为次级）。

**③ 动作卡**

- 4 张：热门排行 / 最新上架 / 歌单迁入 / 我的收藏（未登录）或 上传音乐（已登录）。
- 图标（青调圆角方块）+ 标题 + 副标题 + `chevron-right`。

**④ 横向封面栏 ×2**

- `热门音乐`（带序号角标）与 `最新上架`，均为**横向滑动栏**（`scroll-snap`），各 12 张。
- 区块头采用「标题 + 副标题 + `更多 ›`」范式。
- 封面悬停：图片微放大 + 边框提亮 + 播放键浮现（触屏常显）。

**⑤ 约定**

- 播放复用 `#play` / `#playlist` 契约，与列表页一致。
- 全局仅 `播放热门` 一处主强调；无侧边高亮条、无区块级动画渐变。
- 页壳使用 `PageShell` + `AmbientBackdrop`，不自建变量与关键帧。

### 7.4 最终结构

```html
<PageShell width="default">
  <header class="home-head">标题 + 问候</header>

  <section class="hero">
    <div class="hero__bg" />            <!-- 模糊封面底 -->
    <div class="hero__scrim" />          <!-- 压暗渐变 -->
    <div class="hero__copy"> 标签 / 标题 / 说明 / CTA </div>
    <button class="hero__feature"> 主打封面 + 播放 </button>
  </section>

  <section class="quick"> 4 × NCard 动作卡 </section>

  <section class="section">
    <header class="section__head">标题 + 副标题 + 更多 ›</header>
    <div class="rail"> 12 × 封面卡 </div>
  </section>
  <section class="section"> … 最新上架 rail … </section>
</PageShell>
```

> 说明：顶栏 / 播放条 / 页脚由 `App.vue` 统一提供（见批次 1），页面自身不渲染 chrome。

---

## 8. 迁移计划

### 8.1 总原则

- **先框架后页面**；每个页面独立可发布，迁移即替换该路由的组件文件。
- 迁移一个页面就清理其**独占的旧样式**；共享样式（main.css / glassShell.css）在最后阶段统一收敛。
- 旧组件在对应新组件完全替代后再删除。

### 8.2 建议批次

| 批次 | 内容 | 说明 |
|---|---|---|
| 0 | ✅ 框架 + HomeView 样板 | 框架与首页已完成（`design/` `icons/` `ui/` `composables/` `layouts/`） |
| 1 | ✅ 布局壳落地 | 已抽出 `SiteHeader`/`SiteFooter`/`AdminLayout`（含嵌套路由）；播放条已改为「停靠式 + 空闲隐藏」 |
| 2 | ✅ 简单页 | About / Privacy / Error / CreatePlaylist / UserLogin / ForgotPassword / UserFavorites 已迁移 |
| 3 | ✅ 列表页 | Latest / Ranking / SearchResults / PlaylistDetail / UserPlaylists 已迁移；搜索页改为标签页，播放页重排为「封面 + 歌词」双栏 |
| 4 | ✅ 复杂页 | PlayerView / UploadMusicView / UserVipView / UserProfileView 已迁移；**登录与注册合并为单卡片切换**（`layouts/AuthPanel.vue`，含高度过渡 + 淡入淡出 + 滑动指示块）；DownloadView 一并迁移（用户端已全部完成） |
| 5 | ✅ 管理端 | 全部 7 页 + 管理登录已迁移；`AdminLayout`/`AdminSidebar` 重制（分组式圆角导航）；`.admin-layout` 皮肤改为令牌版。**待收尾**：删除 `main.css` 的 `!important` 皮肤与各页遗留的浅色 scoped 样式 |
| 6 | 播放器 | GlobalPlayer 重构 + 抽出 `usePlayer`/`useLyrics`/`useMediaSession`；收敛全局契约 |
| 7 | 清理 | 删除 `main.css` 废弃段与旧组件（`glassShell.css` 此前已随用户端完成而删除）；**此时**移除 `/__kit` 与 `KitView.vue`（迁移期间一直保留） |

### 8.3 每页迁移检查清单

- [ ] 使用 `AppShell` + `PageShell`，不再自建 ambient/变量/关键帧
- [ ] 颜色/间距/圆角/动效全部引用 `--n-*`
- [ ] 图标走 `NIcon` + `registry`，无内联 `<svg>`
- [ ] 提示走 `useToast`
- [ ] 无高亮条、无常驻动画渐变、无多余发光
- [ ] 弹窗用 `NModal`，加载用 `NSpinner`，标签用 `NTag`
- [ ] 不破坏 §3.5 的全局契约
- [ ] 键盘可达 + `prefers-reduced-motion` 生效

---

## 9. 风险与注意事项

1. **契约不可轻动**：播放器靠 localStorage + 事件 + hash 三通道驱动，改界面时**不要顺带改这些 key/事件名**。
2. **鉴权不统一**：用户接口多为裸 `Authorization: <token>`，管理端为 `Bearer <token>`；迁移时保持现状，勿「顺手统一」。
3. **移动端重定向**：`router.beforeEach` 会把移动设备强制导向 `/download`（详情/歌单/账户/VIP/管理页除外），改路由需同步考虑此逻辑。
4. **构建产物直出后端**：`vite build` 输出到 `backend/src/main/resources/site`，勿手动改动该目录。
5. **SEO 与首屏**：`index.html` 含 JSON-LD、splash、`#app` 内 SEO 正文；改动挂载点需谨慎。
6. **未完成功能**：`UserProfileView` 的头像上传/改密、`AdminSettingsView`（孤立页）均为 stub，重构时不要误当作正常功能。
7. **框架并存期**：旧 `--neko-*` 与新 `--n-*` 会同时存在，新页面只用 `--n-*`，避免混用导致漂移。

---

## 附录 A：术语

| 术语 | 含义 |
|---|---|
| 高亮条 / rail | 页面上装饰性的彩色竖条或横条（多在区块/条目左侧），本次重构的主要治理对象 |
| 令牌 / tokens | `--n-*` 设计变量 |
| 页壳 / shell | 页面的外层容器与背景体系 |
| 契约 | localStorage key、window 事件、URL 通道等跨组件约定 |
