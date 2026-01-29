# Neko云音乐 API 文档

## 概述

Neko云音乐提供完整的 RESTful API，支持音乐搜索、播放、用户认证、收藏等功能。所有 API 都基于 HTTP/HTTPS 协议，使用 JSON 格式进行数据交换。

**基础 URL:** `https://music.cnmsb.xin`

## 目录

- [认证说明](#认证说明)
- [用户相关 API](#用户相关-api)
- [歌单相关 API](#歌单相关-api)
- [音乐相关 API](#音乐相关-api)
- [错误码说明](#错误码说明)

---

## 认证说明

### 用户认证

所有需要用户登录的 API 都需要在请求头中包含用户 Token：

```
Authorization: <token>
```

Token 在用户登录时生成并返回给客户端。

**Token 有效期:** 30 天

---

## 用户相关 API

### 1. 用户注册

**端点:** `POST /api/user/register`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "username": "string",      // 用户名 (必填)
  "password": "string",      // 密码 (必填)
  "email": "string",         // 邮箱 (必填)
  "verificationCode": "string"  // 邮箱验证码 (必填)
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "user": {
      "id": 1,
      "username": "用户名",
      "email": "email@example.com",
      "createdAt": "2024-01-01T00:00:00"
    },
    "token": "64位十六进制字符串"
  }
}
```

### 2. 用户登录

**端点:** `POST /api/user/login`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "username": "string",  // 用户名或邮箱
  "password": "string"   // 密码
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "user": {
      "id": 1,
      "username": "用户名",
      "email": "email@example.com",
      "createdAt": "2024-01-01T00:00:00"
    },
    "token": "64位十六进制字符串"
  }
}
```

### 3. 发送邮箱验证码

**端点:** `POST /api/user/send-verification`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "email": "string"  // 邮箱地址
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "验证码已发送到您的邮箱"
}
```

### 4. 获取用户头像

**端点:** `GET /api/user/avatar/{userId}`

**路径参数:**
- `userId`: 用户 ID

**响应:** 图片文件 (PNG/JPG)

### 5. 获取收藏列表

**端点:** `GET /api/user/favorites`

**请求头:**
```
Authorization: <token>
```

**响应示例:**
```json
{
  "success": true,
  "favorites": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "filename": "song.mp3"
    }
  ]
}
```

### 6. 添加收藏

**端点:** `POST /api/user/favorites`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "musicId": 1  // 音乐 ID
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "收藏成功"
}
```

### 7. 删除收藏

**端点:** `DELETE /api/user/favorites/{musicId}`

**请求头:**
```
Authorization: <token>
```

**路径参数:**
- `musicId`: 音乐 ID

**响应示例:**
```json
{
  "success": true,
  "message": "取消收藏成功"
}
```

### 8. 上传用户头像

**端点:** `POST /api/user/avatar/upload`

**请求头:**
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数:**
- `avatar`: 图片文件 (multipart/form-data)
  - 支持格式：jpg, jpeg, png, gif, webp, bmp
  - 最大文件大小：50MiB

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "头像上传成功",
  "avatarPath": "avatars/1_550e8400-e29b-41d4-a716-446655440000.jpg"
}
```

**响应示例（失败）:**
```json
{
  "error": "未授权访问"
}
```

或

```json
{
  "error": "文件大小超过50MiB限制"
}
```

### 9. 修改用户密码

**端点:** `POST /api/user/password/change`

**请求头:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "oldPassword": "string",  // 原密码（必填）
  "newPassword": "string"   // 新密码（必填，长度不能少于6位）
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "密码修改成功"
}
```

**响应示例（失败）:**
```json
{
  "error": "原密码错误"
}
```

或

```json
{
  "error": "新密码长度不能少于6位"
}
```

或

```json
{
  "error": "新密码不能与原密码相同"
}
```

---

## 歌单相关 API

- [创建歌单](#1-创建歌单)
- [获取歌单列表](#2-获取歌单列表)
- [更新歌单](#3-更新歌单)
- [删除歌单](#4-删除歌单)
- [歌单权限说明](#歌单权限说明)

### 1. 创建歌单

**端点:** `POST /api/user/playlist/create`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "name": "string",          // 歌单名称 (必填)
  "description": "string"   // 歌单描述 (可选)
}
```

**参数限制:**
- `name`: 长度不能超过 255 个字符
- `description`: 长度不能超过 500 个字符

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单创建成功",
  "playlist": {
    "id": 1,
    "name": "我的歌单",
    "description": "这是我的歌单描述",
    "musicCount": 0,
    "createdAt": "2026-01-29 12:00:00",
    "updatedAt": "2026-01-29 12:00:00"
  }
}
```

**响应示例（失败）:**
```json
{
  "success": false,
  "message": "歌单名称不能为空"
}
```

### 2. 获取歌单列表

**端点:** `GET /api/user/playlists`

**请求头:**
```
Authorization: <token>
```

**响应示例:**
```json
{
  "success": true,
  "message": "获取歌单列表成功",
  "playlists": [
    {
      "id": 1,
      "name": "我的歌单",
      "description": "这是我的歌单描述",
      "coverPath": null,
      "musicCount": 5,
      "createdAt": "2026-01-29 12:00:00",
      "updatedAt": "2026-01-29 12:05:00"
    }
  ]
}
```

**说明:** 此 API 返回当前用户创建的所有歌单列表，歌单按创建时间倒序排列。

### 3. 更新歌单

**端点:** `POST /api/user/playlist/update`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "id": 1,                   // 歌单 ID (必填)
  "name": "string",          // 歌单名称 (必填)
  "description": "string"   // 歌单描述 (可选)
}
```

**参数限制:**
- `id`: 歌单 ID，必须是当前用户创建的歌单
- `name`: 长度不能超过 255 个字符
- `description`: 长度不能超过 500 个字符

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单更新成功",
  "playlist": {
    "id": 1,
    "name": "更新的歌单名称",
    "description": "更新的描述",
    "musicCount": 5,
    "createdAt": "2026-01-29 12:00:00",
    "updatedAt": "2026-01-29 12:10:00"
  }
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限修改此歌单"
}
```

**说明:** 只有歌单的创建者（user_id 匹配）才能更新歌单信息。

### 4. 删除歌单

**端点:** `POST /api/user/playlist/delete`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "id": 1  // 歌单 ID (必填)
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单删除成功"
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限删除此歌单"
}
```

**说明:** 
- 只有歌单的创建者（user_id 匹配）才能删除歌单
- 删除歌单会级联删除 `playlist_music` 表中的所有关联记录
- 此操作不可恢复

---

## 歌单权限说明

### 权限规则

| 操作 | 权限要求 |
|------|---------|
| 创建歌单 | 任何登录用户 |
| 查看歌单列表 | 任何登录用户（只能查看自己创建的歌单） |
| 更新歌单 | 只有歌单的创建者 |
| 删除歌单 | 只有歌单的创建者 |

### 权限验证

所有修改和删除操作都会验证用户是否是歌单的创建者。如果用户尝试修改或删除不属于自己的歌单，服务器会返回 `403 Forbidden` 状态码和相应的错误信息。

---

## 音乐相关 API

### 1. 搜索音乐

**端点:** `POST /api/music/search`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "query": "string",  // 搜索关键词
  "page": 1,          // 页码 (可选，默认为 1)
  "pageSize": 20      // 每页数量 (可选，默认为 20)
}
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "results": [
      {
        "id": 1,
        "title": "歌曲标题",
        "artist": "艺术家",
        "album": "专辑",
        "duration": 180,
        "coverUrl": "/api/music/cover/1"
      }
    ]
  }
}
```

### 2. 获取音乐信息

**端点:** `GET /api/music/info/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应示例:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "歌曲标题",
    "artist": "艺术家",
    "album": "专辑",
    "duration": 180,
    "coverUrl": "/api/music/cover/1",
    "fileUrl": "/api/music/file/1",
    "lyrics": "歌词内容"
  }
}
```

### 3. 获取音乐文件

**端点:** `GET /api/music/file/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应:** 音频文件 (MP3)

### 4. 获取音乐封面

**端点:** `GET /api/music/cover/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应:** 图片文件 (PNG/JPG)

### 5. 获取歌词

**端点:** `GET /api/music/lyrics/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应示例:**
```json
{
  "success": true,
  "lyrics": "歌词内容"
}
```

---

## 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权，需要登录或 Token 无效 |
| 403 | 禁止访问，权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 错误响应格式

```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 前端集成示例

### 用户登录

```javascript
async function login(username, password) {
  const response = await fetch('https://music.cnmsb.xin/api/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  if (data.success) {
    localStorage.setItem('userToken', data.data.token);
    localStorage.setItem('userInfo', JSON.stringify(data.data.user));
  }
  return data;
}
```

### 搜索音乐

```javascript
async function searchMusic(query, page = 1, pageSize = 20) {
  const response = await fetch('https://music.cnmsb.xin/api/music/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ query, page, pageSize })
  });
  
  return await response.json();
}
```

### 获取收藏列表

```javascript
async function getFavorites() {
  const token = localStorage.getItem('userToken');
  const response = await fetch('https://music.cnmsb.xin/api/user/favorites', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  return await response.json();
}
```

### 上传用户头像

```javascript
async function uploadAvatar(avatarFile) {
  const token = localStorage.getItem('userToken');
  const formData = new FormData();
  formData.append('avatar', avatarFile);
  
  const response = await fetch('https://music.cnmsb.xin/api/user/avatar/upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('头像上传成功:', data.avatarPath);
  } else {
    console.error('头像上传失败:', data.error);
  }
  return data;
}
```

### 创建歌单

```javascript
async function createPlaylist(name, description) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/create', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      name: name,
      description: description
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单创建成功:', data.playlist);
  } else {
    console.error('歌单创建失败:', data.message);
  }
  return data;
}
```

### 获取歌单列表

```javascript
async function getPlaylists() {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlists', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('获取到歌单列表:', data.playlists);
  } else {
    console.error('获取歌单列表失败:', data.message);
  }
  return data;
}
```

### 更新歌单

```javascript
async function updatePlaylist(playlistId, name, description) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/update', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      id: playlistId,
      name: name,
      description: description
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单更新成功:', data.playlist);
  } else if (data.message === '无权限修改此歌单') {
    alert('您没有权限修改此歌单');
  } else {
    console.error('歌单更新失败:', data.message);
  }
  return data;
}
```

### 删除歌单

```javascript
async function deletePlaylist(playlistId) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/delete', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      id: playlistId
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单删除成功');
    // 可以在这里刷新歌单列表
  } else if (data.message === '无权限删除此歌单') {
    alert('您没有权限删除此歌单');
  } else {
    console.error('歌单删除失败:', data.message);
  }
  return data;
}
```

### 修改用户密码

```javascript
async function changePassword(oldPassword, newPassword) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/password/change', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      oldPassword: oldPassword,
      newPassword: newPassword
    })
  });
  
  const data = await response.json();
  if (data.success) {
    alert('密码修改成功！');
  } else {
    alert('密码修改失败：' + data.error);
  }
  return data;
}
```

---

## 注意事项

1. **CORS:** 所有 API 都支持跨域请求
2. **Token 管理:** Token 有效期为 30 天，过期后需要重新登录
3. **错误处理:** 所有 API 都返回统一的 JSON 格式，包含 `success` 和 `message` 字段
4. **速率限制:** 建议客户端实现适当的请求速率限制，避免频繁请求
5. **头像上传:**
   - 支持的图片格式：jpg, jpeg, png, gif, webp, bmp
   - 最大文件大小：50MiB
   - 只允许图片类型文件上传，会严格验证 MIME 类型
   - 头像文件保存在 `avatars/` 目录下
6. **密码修改:**
   - 新密码长度不能少于 6 位
   - 新密码不能与原密码相同
   - 需要提供正确的原密码才能修改
   - 使用 Argon2 算法加密密码
7. **歌单管理:**
   - 每个歌单都有唯一的 ID 和创建者（user_id）
   - 只有歌单的创建者才能更新和删除歌单
   - 删除歌单会级联删除歌单中的所有音乐关联
   - 歌单名称长度限制：255 个字符
   - 歌单描述长度限制：500 个字符
   - `musicCount` 字段会自动更新，无需手动维护
   - 歌单按创建时间倒序排列（最新的在前面）

---

## 联系方式

如有问题或建议，请联系：admin@nekomusic.com