# NekoMusic

# 用户收藏API文档

## 概述

用户收藏API允许用户收藏、取消收藏音乐，并获取收藏列表。所有API都需要用户登录认证。

## 认证

所有API请求都需要在HTTP头中包含用户token：

```
Authorization: <token>
```

Token在用户登录时生成并返回给客户端。

## API端点

### 1. 获取收藏列表

**请求:**
```
GET /api/user/favorites
Authorization: <token>
```

**响应:**
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

### 2. 添加收藏

**请求:**
```
POST /api/user/favorites
Authorization: <token>
Content-Type: application/json

{
  "musicId": 1
}
```

**响应:**
```json
{
  "success": true,
  "message": "收藏成功"
}
```

### 3. 删除收藏

**请求:**
```
DELETE /api/user/favorites/{musicId}
Authorization: <token>
```

**响应:**
```json
{
  "success": true,
  "message": "取消收藏成功"
}
```

## 错误响应

### 401 Unauthorized
```json
{
  "success": false,
  "message": "未提供认证令牌"
}
```

或

```json
{
  "success": false,
  "message": "无效的认证令牌"
}
```

### 400 Bad Request
```json
{
  "success": false,
  "message": "收藏失败或已存在"
}
```

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "服务器内部错误"
}
```

## Token管理

### 获取Token
用户登录时会自动生成token并返回：

```
POST /api/user/login
Content-Type: application/json

{
  "username": "用户名",
  "password": "密码"
}
```

**响应:**
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
    "token": "随机生成的64位十六进制字符串"
  }
}
```

### Token有效期
- Token有效期为30天
- 过期后需要重新登录获取新token

### 存储Token
客户端应该将token存储在浏览器的localStorage或cookie中，并在每次请求时添加到Authorization头中。

## 数据库表结构

### user_favorites表
```sql
CREATE TABLE IF NOT EXISTS user_favorites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    music_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_user_music (user_id, music_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (music_id) REFERENCES music(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_music_id (music_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### user_tokens表
```sql
CREATE TABLE IF NOT EXISTS user_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_token (token),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 前端集成示例

### 登录并存储Token
```javascript
async function login(username, password) {
  const response = await fetch('http://localhost:8080/api/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  if (data.success) {
    // 存储token到localStorage
    localStorage.setItem('userToken', data.data.token);
    localStorage.setItem('userInfo', JSON.stringify(data.data.user));
  }
  return data;
}
```

### 获取收藏列表
```javascript
async function getFavorites() {
  const token = localStorage.getItem('userToken');
  const response = await fetch('http://localhost:8080/api/user/favorites', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  return await response.json();
}
```

### 添加收藏
```javascript
async function addFavorite(musicId) {
  const token = localStorage.getItem('userToken');
  const response = await fetch('http://localhost:8080/api/user/favorites', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ musicId })
  });
  
  return await response.json();
}
```

### 删除收藏
```javascript
async function removeFavorite(musicId) {
  const token = localStorage.getItem('userToken');
  const response = await fetch(`http://localhost:8080/api/user/favorites/${musicId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': token
    }
  });
  
  return await response.json();
}
```

# 用户头像API文档

## 概述

用户头像API允许用户上传和管理个人头像。上传头像需要用户登录认证，获取头像无需认证。

## 认证

上传头像API需要在HTTP头中包含用户token：

```
Authorization: Bearer <token>
```

Token在用户登录时生成并返回给客户端。

## API端点

### 1. 上传头像

**请求:**
```
POST /api/user/avatar/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

avatar: <图片文件>
```

**请求参数:**
- `avatar`: 图片文件（multipart/form-data）
  - 支持格式：jpg, jpeg, png, gif, webp, bmp
  - 最大文件大小：50MiB
  - 会严格验证文件MIME类型，只允许图片类型

**响应（成功）:**
```json
{
  "success": true,
  "message": "头像上传成功",
  "avatarPath": "avatars/1_550e8400-e29b-41d4-a716-446655440000.jpg"
}
```

**响应（失败）:**
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

或

```json
{
  "error": "只支持图片文件（jpg, jpeg, png, gif, webp, bmp）"
}
```

### 2. 获取用户头像

**请求:**
```
GET /api/user/avatar/{userId}
```

**参数:**
- `userId`: 用户ID（路径参数）

**响应:**
- 如果用户有头像，返回头像图片文件
- 如果用户没有头像，返回默认头像图片

## 错误响应

### 401 Unauthorized
```json
{
  "error": "未授权访问"
}
```

或

```json
{
  "error": "无效的Token"
}
```

### 400 Bad Request
```json
{
  "error": "未上传头像文件"
}
```

或

```json
{
  "error": "文件大小超过50MiB限制"
}
```

或

```json
{
  "error": "只支持图片文件（jpg, jpeg, png, gif, webp, bmp）"
}
```

### 500 Internal Server Error
```json
{
  "error": "上传头像失败: <错误信息>"
}
```

## 目录结构

头像文件保存在 `avatars/` 目录下，文件名格式为：`{userId}_{uuid}.{extension}`

例如：`avatars/1_550e8400-e29b-41d4-a716-446655440000.jpg`

## 注意事项

1. 每次上传新头像时，旧头像文件会被自动删除
2. 头像文件名使用 UUID 确保唯一性
3. 支持的图片格式：jpg, jpeg, png, gif, webp, bmp
4. 最大文件大小限制：50MiB
5. 只允许图片类型文件上传，会严格验证 MIME 类型
6. 如果用户没有头像，系统会返回默认头像

## 数据库表结构

### users表（需要添加avatar字段）

```sql
ALTER TABLE users ADD COLUMN avatar VARCHAR(255) DEFAULT NULL COMMENT '用户头像路径';
```

## 前端集成示例

### 上传头像
```javascript
async function uploadAvatar(avatarFile) {
  const token = localStorage.getItem('userToken');
  const formData = new FormData();
  formData.append('avatar', avatarFile);
  
  const response = await fetch('http://localhost:8080/api/user/avatar/upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('头像上传成功:', data.avatarPath);
    // 更新用户头像显示
    updateAvatarDisplay(data.avatarPath);
  } else {
    console.error('头像上传失败:', data.error);
  }
  return data;
}
```

### 获取头像URL
```javascript
function getAvatarUrl(userId) {
  return `http://localhost:8080/api/user/avatar/${userId}`;
}
```

### 在React组件中使用
```jsx
import React, { useState } from 'react';

function AvatarUpload({ userId }) {
  const [avatarUrl, setAvatarUrl] = useState(getAvatarUrl(userId));
  
  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    
    // 验证文件类型
    if (!file.type.startsWith('image/')) {
      alert('请选择图片文件');
      return;
    }
    
    // 验证文件大小（50MiB = 50 * 1024 * 1024 bytes）
    if (file.size > 50 * 1024 * 1024) {
      alert('文件大小不能超过50MiB');
      return;
    }
    
    const result = await uploadAvatar(file);
    if (result.success) {
      // 刷新头像
      setAvatarUrl(getAvatarUrl(userId) + '?t=' + Date.now());
    }
  };
  
  return (
    <div>
      <img 
        src={avatarUrl} 
        alt="用户头像" 
        style={{ width: 100, height: 100, borderRadius: '50%' }}
      />
      <input 
        type="file" 
        accept="image/*"
        onChange={handleFileChange}
      />
    </div>
  );
}
```

### 在Vue组件中使用
```vue
<template>
  <div>
    <img 
      :src="avatarUrl" 
      alt="用户头像" 
      style="width: 100px; height: 100px; border-radius: 50%;"
    />
    <input 
      type="file" 
      accept="image/*"
      @change="handleFileChange"
    />
  </div>
</template>

<script>
export default {
  props: ['userId'],
  data() {
    return {
      avatarUrl: this.getAvatarUrl(this.userId)
    };
  },
  methods: {
    getAvatarUrl(userId) {
      return `http://localhost:8080/api/user/avatar/${userId}`;
    },
    async handleFileChange(e) {
      const file = e.target.files[0];
      if (!file) return;
      
      // 验证文件类型
      if (!file.type.startsWith('image/')) {
        alert('请选择图片文件');
        return;
      }
      
      // 验证文件大小
      if (file.size > 50 * 1024 * 1024) {
        alert('文件大小不能超过50MiB');
        return;
      }
      
      const result = await this.uploadAvatar(file);
      if (result.success) {
        // 刷新头像
        this.avatarUrl = this.getAvatarUrl(this.userId) + '?t=' + Date.now();
      }
    },
    async uploadAvatar(avatarFile) {
      const token = localStorage.getItem('userToken');
      const formData = new FormData();
      formData.append('avatar', avatarFile);
      
      const response = await fetch('http://localhost:8080/api/user/avatar/upload', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`
        },
        body: formData
      });
      
      return await response.json();
    }
  }
};
</script>
```