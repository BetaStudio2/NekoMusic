# NekoMusic

在线音乐平台（Web / Android / PC）。完整 API 说明见 [Neko云音乐文档/README.md](Neko云音乐文档/README.md)。

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
