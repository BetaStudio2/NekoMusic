# AGENTS.md

面向在本仓库中工作的编码代理/协作者的约定。**改动前先读本文件**；更深层目录若另有
`AGENTS.md`，以更深层为准。项目说明、部署步骤、API 细节见 `README.md` 与
`Neko歌姬计划文档/README.md`。

> **首要约束（违反即视为未完成）**
> 1. 新增 API 必须显式设置 `Cache-Control`（见「HTTP 缓存策略」）。
> 2. 新增/修改/删除后端 API 必须同步更新 API 文档（见「API 文档同步」）。
> 3. 不提交密钥/本地配置、构建产物，以及与任务无关的子模块指针变动。

## 项目结构

```
backend/                Java 后端（Jetty 12 + Servlet，Maven，Java 27），Web 与 API 的唯一服务端
frontend/               Vue 3 + Vite 前端（构建产物打进后端 classpath 的 site/）
Android/                Android 客户端（git submodule，独立仓库）
pc/                     桌面客户端 Qt/C++（git submodule，独立仓库）
Neko歌姬计划文档/        API/产品文档（git submodule，独立仓库）
pc/ 与 Android/        是子模块：除非任务明确要求，不要改动、不要顺手提交其指针变动
```

- 浏览器/客户端访问的是**同一个后端**（默认端口 `65535`）；前端 dev server 通过代理与后端同源
  （`frontend/.env.development` 的 `VITE_DEV_PROXY_TARGET`），因此**不需要**为浏览器放开 CORS。
- 站内图片、音频、安装包等由后端直接提供（见 `handlers/`、`util/HttpResourceCache.java`）。

## 构建与运行

### 后端
```bash
# 推荐：Docker（首次需 cp src/main/resources/config.yml config.yml 并填好 MySQL/Redis/jwt 等）
cd backend && docker compose build && docker compose up -d && docker compose logs -f neko-music

# 本地 Maven（Java 27）
cd backend && mvn -B package -DskipTests     # 打包
cd backend && mvn test                       # 跑单元测试（JUnit 5）
```
- 入口 `com.neko.music.Main`；路由集中在 `handlers/ServletRegistrar.java`。
- 生产配置来自运行目录的 `backend/config.yml`（由 `src/main/resources/config.yml` 复制并填写）。

### 前端
```bash
cd frontend && npm install
cd frontend && npm run dev      # http://localhost:5173，/api、/version 代理到本地后端
cd frontend && npm run build    # 产物输出到 ../backend/src/main/resources/site（该目录已 gitignore）
```
- 前端产物是**构建物**，不要提交 `backend/src/main/resources/site/*`；部署/打包前必须跑一次 `npm run build`。
- 前后端改动互相依赖时，先 `npm run build` 让后端能提供最新前端，再验证。

### 客户端（子模块）
`Android/`、`pc/` 各自有独立仓库与构建脚本，参见其 `README.md`；本仓库只固定子模块指针。

## 测试

- 后端：`cd backend && mvn test`（现有用例在 `backend/src/test/java/...`，覆盖 handler、util、service）。
- 前端：未配置测试框架/`lint` 脚本；改完至少 `npm run build` 保证可编译。
- 无格式化/lint 门禁，但请保持与相邻代码一致的风格。

## 代码约定

- **路由必须注册在 `ServletRegistrar`**：嵌入式 Jetty 不处理 `@WebServlet` 注解。
  映射遵循 Servlet 规范：精确路径 > 最长前缀 > 扩展名 > 默认 `/`，因此 `/api/music/latest`
  会胜过通配的 `/api/music/*`。新增接口用清晰的最小前缀，避免与既有通配产生歧义。
- HTTP JSON 处理器继承 `handlers/ApiServlet`，统一用 `sendSuccessResponse` / `sendErrorResponse` /
  `writeJson` 等，不要各自复制响应样板；错误体保持 `{"success":false,"message":"..."}` 契约。
- 注释、日志、提交信息用中文；提交信息用 Conventional Commits 中文描述，例如
  `fix(frontend): 修复播放页手势拦截控件触摸与两处布局偏移`。
- 前端统一使用 `@/ui` 组件（`NButton`、`NIcon`…）与 `design/tokens.css` 的 CSS 变量，
  不要新造一套按钮/颜色；图标走图标注册表（`NIcon` 的 `name`）。
- 涉及 MySQL/Redis 等外部依赖的改动，保持失败可降级或明确报错，不要静默吞异常。
- **改后端 API 必须同步改 API 文档**（见下节）。

## API 文档同步（强约束）

**后端 API 的新增、修改、删除，必须在同一次改动中同步更新 API 文档；只改代码不改文档视为未完成。**
包括但不限于：路由/方法变更、请求参数或字段变更、响应结构变更、鉴权/权限变化、状态码变化、接口下线。

- 主文档：`Neko歌姬计划文档/README.md`（标题为「Neko歌姬计划 API 文档」，含 `## 目录`）。
- 专项文档：如 `Neko歌姬计划文档/API-滑块与人机验证.md`；涉及对应流程时一并更新。
  新专项主题可新增独立 `.md`，并在主文档 `## 目录` 里加链接。
- 文档在 **git submodule** `Neko歌姬计划文档/`（独立仓库 `FantasyNetworkCN/NekoMusicDocs`）。
  改文档要提交到该子模块仓库；若需要在本仓库固定新版本，再单独更新子模块指针，不要夹带无关指针变动。

每个端点按现有格式补齐，字段缺一不可：

- 分节序号 + 标题（如 `### 21. 扫码登录`），并同步更新 `## 目录` 中对应条目/链接。
- `**端点:**` `METHOD /path`。
- `**请求头:**`（鉴权使用 `Authorization: Bearer <token>`，公开接口要注明「无需登录」）。
- 请求体 / 查询参数说明及示例。
- `**响应示例（成功）**` 与 `**响应示例（失败/未登录）**`。
- `**状态码:**` 及各码含义。

破坏性变更（改路径、改字段名/类型、删除接口）要在文档中显式标注，并说明兼容/迁移方式。
改动若也影响 `README.md` 的功能描述或示例，记得一并更新。

## HTTP 缓存策略（强约束）

**所有新增 API 必须显式声明 `Cache-Control`。** 动态接口由
`filter/CacheControlFilter.java` 兜底为 `private, no-store`（作用于 `/api/*`、`/loser/*`、`/detail/*`），
需要公开缓存的接口在处理器内 `setHeader` 覆盖。时长一律复用
`util/HttpResourceCache.java` 的常量，**不要写死数字**。

| 类别 | 响应头 | 说明 |
| --- | --- | --- |
| 动态 API、登录、注册、头像、个人数据、后台 | `private, no-store` | 不缓存，每次回源 |
| `/api/music/file/{id}`（音质解析跳转） | `private, no-store` | 每次重新解析 |
| `/api/music/latest`、`/api/music/ranking` | `public, max-age=1800` | 半小时 |
| `/sitemap.xml`、`.txt`（robots/llms 等） | `public, max-age=86400` | 一天 |
| 静态固定资源（png/ico/svg/js/css/字体/webmanifest/安装包 .exe/.pak/.deb 等） | `public, max-age=15552000` | 六个月 |
| 带内容哈希的 `/assets/*` | `public, max-age=15552000, immutable` | 六个月 + immutable |
| 音频、封面、客户端安装包（磁盘文件） | `HttpResourceCache.applyFileCachingHeaders(...)` | 六个月 + `must-revalidate` + ETag/Last-Modified |
| `index.html`、`sw.js` | `no-cache` + ETag/Last-Modified | 必须能及时发版，靠 304 再校验 |

- 新增磁盘媒体/文件响应时，优先复用 `HttpResourceCache`（自动带 ETag、Last-Modified、`If-None-Match → 304`、
  `Accept-Ranges`），不要手写头部。
- 给「同一 URL 内容可能被替换」的资源别加 `immutable`；入口 HTML / Service Worker 保持可再校验，
  否则用户拿不到新版本。
- 需要 `Vary` 的响应（如按 User-Agent 区分爬虫/浏览器）要显式设置，避免共享缓存串味。

## 不要提交

- `backend/config.yml` 等含密码/密钥的本地配置；样例模板提交在 `backend/src/main/resources/config.yml`。
- 构建产物：`backend/src/main/resources/site/*`、`target/`、`frontend/node_modules/`、
  `backend/Music/`、`backend/releases/`、`dist/`。
- 与任务无关的 `Android/`、`pc/` 子模块指针变动（`git status` 里常见，属他人/历史遗留，别顺手带上）。
