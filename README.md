# NekoMusic

![](https://count.getloli.com/get/@:NekoMusic?theme=moebooru)

在线音乐平台（Web / Android / PC）。完整 API 说明见 [Neko歌姬计划文档/README.md](Neko歌姬计划文档/README.md)。

## 本地听歌识曲（后端）

后端提供 `POST /api/music/recognize`，接收 `multipart/form-data` 的 `audio` 字段。服务端使用本地 FFmpeg 将录音转换为 PCM，并用本站 `Music/music/{id}.*` 曲库建立声纹索引；不会调用第三方识曲 API，也不会把音频转发到外部服务。首次请求会按需构建索引，索引缓存位于 `Music/.fingerprints/`，音频文件变更后自动失效。

示例：

```bash
curl -sS -F 'audio=@sample.m4a' https://music.example.com/api/music/recognize
```

识别结果在 `data` 中返回歌曲 ID、标题、歌手、专辑、置信度和匹配偏移；未匹配本站曲库时返回 `matched: false`。相关大小、时长、并发和限流参数可在 `backend/src/main/resources/config.yml` 的 `music_recognition` 节配置。

## 违禁词检测 API

将违禁词校验从上传、注册等业务中拆出，供前端在提交前预检标题、用户名、歌单名等文案。词表位于 `backend/src/main/resources/违禁词/`（`主词表.txt`、`英文词表.txt`、`白名单.txt`），与线上一致。

**基础 URL：** 与站点 API 相同，例如 `https://music.cnmsb.xin`

**端点：** `POST /api/sensitive-word/check`

**认证：** 无需登录

**请求头：**

```
Content-Type: application/json
```

### 单条检测

**请求体：**

```json
{
  "text": "爱上你"
}
```

**响应示例（通过）：**

```json
{
  "success": true,
  "message": "检测完成",
  "contains": false,
  "data": {
    "text": "爱上你",
    "contains": false,
    "words": []
  }
}
```

**响应示例（命中）：**

```json
{
  "success": true,
  "message": "检测完成",
  "contains": true,
  "data": {
    "text": "示例文本",
    "contains": true,
    "words": ["命中词1"]
  }
}
```

`words` 为命中的违禁词片段列表（与上传接口拦截逻辑相同）。

### 批量检测

**请求体：**

```json
{
  "texts": ["歌曲标题", "歌手名", "专辑名"]
}
```

**响应示例：**

```json
{
  "success": true,
  "message": "检测完成",
  "contains": true,
  "data": [
    {
      "text": "歌曲标题",
      "contains": false,
      "words": []
    },
    {
      "text": "歌手名",
      "contains": true,
      "words": ["示例"]
    }
  ]
}
```

顶层 `contains` 表示是否**任意一条**命中；`data` 中每项结构同单条检测。

### 限制与错误

| 项 | 说明 |
|----|------|
| 单条长度 | 最多 2000 字符 |
| 批量条数 | 最多 20 条 |
| 空白 | 检测前会去除空格与零宽字符（与后端其它校验一致） |

**错误响应：**

```json
{
  "message": "请提供 text 或 texts 字段"
}
```

HTTP 状态码：`400` 参数错误。

### curl 示例

```bash
curl -s -X POST 'https://music.cnmsb.xin/api/sensitive-word/check' \
  -H 'Content-Type: application/json' \
  -d '{"text":"爱上你"}'
```

```bash
curl -s -X POST 'https://music.cnmsb.xin/api/sensitive-word/check' \
  -H 'Content-Type: application/json' \
  -d '{"texts":["标题","歌手"]}'
```

### 说明

- 本接口仅做检测，不写库；实际上传、注册、歌单等接口仍会再次校验。
- 修改词表后需重新部署后端 jar 后生效。

## 每日推荐（AI + Redis）

后端已接入每日推荐能力，按用户收藏风格生成推荐列表，默认使用 OpenAI 兼容接口做重排。

### 核心行为

- 每天 **UTC+8 00:00** 自动执行全量日更任务（服务启动后注册定时器）。
- 推荐结果只存 **Redis**（Redis-only），不依赖 MySQL 推荐结果表。
- **已收藏歌曲硬过滤**：推荐列表不会出现 `user_favorites` 中的曲目。
- **歌单内歌曲降权**：用户自建歌单、已收藏歌单中的曲目仍可能入选，但规则打分降低并在排序中靠后。
- 若当天缓存不存在，接口会按需即时生成并写回 Redis。

### 配置项（`backend/src/main/resources/config.yml`）

`recommendation_ai` 主要字段：

- `enabled`: 是否启用 AI 重排
- `base_url`: OpenAI 兼容网关地址（例如 `https://api.openai.com/v1`）
- `api_key`: API Key
- `model`: 模型名
- `temperature` / `top_p` / `max_tokens`
- `timeout_seconds`: 单次调用超时
- `daily_limit`: 每用户每天返回数量（默认 30）
- `fallback_to_rule`: AI 失败是否回退规则排序

### 存储设计（Redis）

- Key：`daily_reco:{yyyy-MM-dd}:{userId}`
- Value：JSON 数组（包含 `rank/musicId/title/artist/album/language/tags/score/source/reason`）
- TTL：72 小时（跨日容错）

### 接口

- `GET /api/user/recommendations/daily`
  - 需要用户登录 token（`Authorization`）
  - 返回当天推荐结果
- `GET /api/user/recommendations/daily?refresh=true`
  - 强制重算当前用户当天推荐并覆盖 Redis

### 返回示例

```json
{
  "success": true,
  "date": "2026-05-28",
  "count": 30,
  "data": [
    {
      "rank": 1,
      "musicId": 13751,
      "title": "天使ロード中…^_−☆",
      "artist": "三Z-STUDIO&HOYO-MiX",
      "album": "绝区零-天使加载中…^_−☆",
      "language": "日语",
      "tags": "二次元，日语，游戏",
      "score": 4.93,
      "source": "ai",
      "reason": "与近期收藏艺人与标签更匹配"
    }
  ]
}
```

### 运维说明

- Redis 需开启持久化（RDB 或 AOF），避免重启后丢失当日推荐缓存。
- `daily_limit` 提升会线性增加 token 消耗与响应体大小。
- 若发现推荐理由异常，可先用 `refresh=true` 观察重算结果，再检查 `base_url/model/prompt` 配置。
