## 仓库克隆
```bash
git clone --recursive https://github.com/NyaNyagulugulu/NekoMuscis.git
```
# NekoMusic - API 缓存策略

## 概述

本文档说明 NekoMusic 后端 API 的 CDN 缓存策略，帮助优化前端性能和减少服务器负载。

## ✅ 可以被 CDN 缓存的 API

以下 API 是 **只读** 操作，**不包含用户认证信息**，返回内容稳定，可以被 CDN 缓存：

| API 端点 | 方法 | 缓存建议 | 说明 |
|---------|------|---------|------|
| `/api/music/cover/{id}` | GET | 长期缓存 (7-30天) | 音乐封面图片，文件不会变更 |
| `/api/music/file/{id}` | GET | 中期缓存 (1-7天) | 音乐文件，文件不会变更 |
| `/api/music/info/{id}` | GET | 短期缓存 (1-24小时) | 音乐元数据信息 |
| `/api/music/lyrics/{id}` | GET | 短期缓存 (1-24小时) | 歌词内容 |

### CDN 缓存配置建议

对于上述可缓存的 API，建议在 CDN 配置中设置以下响应头：

```http
Cache-Control: public, max-age=<秒数>
ETag: "<文件hash>"
```

**示例：**
- 音乐封面：`Cache-Control: public, max-age=2592000` (30天)
- 音乐文件：`Cache-Control: public, max-age=604800` (7天)
- 音乐信息：`Cache-Control: public, max-age=86400` (1天)
- 歌词：`Cache-Control: public, max-age=86400` (1天)

---

## ❌ 不能被 CDN 缓存的 API

以下 API **不能** 被 CDN 缓存，原因包括：

### 1. 需要用户认证的 API (14个)

| API 端点 | 方法 | 原因 |
|---------|------|------|
| `/api/user/login` | POST | 涉及认证令牌，每次请求结果不同 |
| `/api/user/register` | POST | 创建新用户，有状态变更 |
| `/api/user/password/change` | POST | 修改密码，有状态变更 |
| `/api/user/avatar/upload` | POST | 上传文件，有状态变更 |
| `/api/user/avatar/{userId}` | GET | 用户头像可能更新，不适合长期缓存 |
| `/api/user/favorites` | GET/POST/DELETE | 依赖用户登录状态，结果因用户而异 |
| `/api/user/playlist/create` | POST | 创建资源，有状态变更 |
| `/api/user/playlist/update` | POST | 更新资源，有状态变更 |
| `/api/user/playlist/delete` | POST | 删除资源，有状态变更 |
| `/api/user/playlist/music/{playlistId}` | GET | 依赖用户登录状态 |
| `/api/user/playlist/music/add` | POST | 添加资源，有状态变更 |
| `/api/user/playlist/music/remove` | POST | 删除资源，有状态变更 |
| `/api/user/playlists` | GET | 依赖用户登录状态，结果因用户而异 |
| `/api/users/*` | 所有方法 | 管理员接口，依赖权限 |

### 2. 需要管理员认证的 API (11个)

| API 端点 | 方法 | 原因 |
|---------|------|------|
| `/api/admin/login` | POST | 涉及认证令牌 |
| `/api/admin/stats` | GET | 实时统计数据，内容频繁变化 |
| `/api/admin/chart-data` | GET | 实时图表数据，内容频繁变化 |
| `/api/admin/users/*` | 所有方法 | 管理员操作，依赖权限 |
| `/api/music/upload` | POST | 上传文件，有状态变更 |
| `/api/music/list` | GET | 依赖管理员权限 |
| `/api/music/{id}` | GET/PUT/DELETE | 管理员操作，依赖权限 |
| `/api/music/add` | POST | 添加资源，有状态变更 |
| `/api/music/edit` | PUT | 更新资源，有状态变更 |
| `/api/music/delete/{id}` | DELETE | 删除资源，有状态变更 |
| `/api/music/lyrics/{id}` | POST | 更新资源，有状态变更 |

### 3. 其他不适合缓存的 API (5个)

| API 端点 | 方法 | 原因 |
|---------|------|------|
| `/api/music/search` | POST | 搜索结果频繁变化，且依赖搜索参数 |
| `/api/playlist/{id}` | GET | 歌单详情可能频繁更新，不适合缓存 |
| `/api/playlists/search` | GET | 搜索结果频繁变化，且依赖搜索参数 |
| `/api/artists/search` | GET | 搜索结果频繁变化，且依赖搜索参数 |
| `/api/user/send-verification` | POST | 发送验证码，一次性操作 |

---

**统计：**
- ✅ 可被 CDN 缓存：4 个 API
- ❌ 不能被 CDN 缓存：30 个 API（14 个用户认证 + 11 个管理员认证 + 5 个其他）

### CDN 配置建议

对于上述不可缓存的 API，建议在 CDN 配置中设置：

```http
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Expires: 0
```

---

## 前端集成建议

### 1. 为可缓存的 API 添加版本控制

当音乐文件、封面等资源更新时，可以通过添加查询参数或版本号来绕过 CDN 缓存：

```javascript
// 示例：添加时间戳参数
const coverUrl = `/api/music/cover/${musicId}?t=${new Date().getTime()}`;
```

### 2. 为不可缓存的 API 使用适当的请求策略

```javascript
// 对于用户相关的 API，确保携带认证令牌
fetch('/api/user/favorites', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Cache-Control': 'no-cache'
  }
});
```

### 3. 使用浏览器缓存配合 CDN

对于静态资源（封面、音乐文件），可以在前端添加预加载：

```html
<link rel="preload" as="image" href="/api/music/cover/123">
```

---

## 注意事项

1. **认证相关的 API 绝对不能缓存**，否则会导致安全问题
2. **写操作（POST/PUT/DELETE）不能缓存**，会导致数据不一致
3. **依赖用户权限的 API 不能缓存**，不同用户看到的内容不同
4. **实时数据不能缓存**，会导致数据过期
5. **建议为可缓存的 API 设置 ETag**，支持条件请求，节省带宽
6. **定期清理 CDN 缓存**，当资源更新时需要主动清理对应的缓存