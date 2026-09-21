#!/usr/bin/env bash
# 下载 MaxMind GeoLite2-City 数据库到 backend/GeoIP/GeoLite2-City.mmdb。
#
# 评论的 IP 归属地完全在本地用 MaxMind GeoIP2 解析（IPv4 / IPv6 共用一份 City 库），
# 不请求任何第三方接口。服务启动时按以下顺序自动找库：
#   GeoIP/GeoLite2-City.mmdb → /app/GeoIP/... → /usr/share/GeoIP/... → JAR 内 /GeoIP/...
# 因此本脚本只是把库文件准备好，不跑脚本也不会影响服务启动（归属地会显示「未知」）。
#
# 环境变量 GEOLITE2_CITY_URL 可换成自建镜像或 MaxMind 官方下载地址（需带 license key 的直链）。
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/GeoIP"
OUT="${OUT_DIR}/GeoLite2-City.mmdb"
URL="${GEOLITE2_CITY_URL:-https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-City.mmdb}"

mkdir -p "${OUT_DIR}"

if [[ -s "${OUT}" ]]; then
  echo "[fetch-geolite2] 已存在，跳过: ${OUT}"
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "[fetch-geolite2] 未找到 curl，跳过；请手动把 GeoLite2-City.mmdb 放到 ${OUT_DIR}/" >&2
  exit 0
fi

TMP="${OUT}.tmp"
echo "[fetch-geolite2] 下载 ${URL}"
if ! curl -fL --retry 3 --connect-timeout 20 --max-time 900 -o "${TMP}" "${URL}"; then
  rm -f "${TMP}"
  echo "[fetch-geolite2] 下载失败；请手动把 GeoLite2-City.mmdb 放到 ${OUT_DIR}/" >&2
  exit 0
fi

# MaxMind DB 魔数标记在文件末尾，用它做一次低成本校验，避免把错误页面当成库文件
if ! tail -c 64 "${TMP}" | command grep -q "MaxMind.com"; then
  rm -f "${TMP}"
  echo "[fetch-geolite2] 文件不是有效的 MaxMind DB，已丢弃；请手动放置 GeoLite2-City.mmdb" >&2
  exit 0
fi

mv "${TMP}" "${OUT}"
echo "[fetch-geolite2] 完成: ${OUT} ($(du -h "${OUT}" | cut -f1))"
