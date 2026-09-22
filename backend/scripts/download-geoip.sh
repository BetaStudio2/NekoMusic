#!/usr/bin/env bash
# 下载 GeoLite2-City.mmdb，写入
# src/main/resources/GeoLite2-City.mmdb 供 JAR 内嵌。
# 需在可访问 GitHub 的网络下执行；mvn package 可自动调用。
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/src/main/resources"
OUT_BIN="${OUT_DIR}/GeoLite2-City.mmdb"
# P3TERX GeoLite.mmdb 镜像
DOWNLOAD_URL="https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-City.mmdb"

mkdir -p "${OUT_DIR}"

if [[ -f "${OUT_BIN}" ]]; then
  echo "[fetch-geolite2-city] 已存在，跳过下载: ${OUT_BIN}"
  exit 0
fi

command -v curl >/dev/null 2>&1 || { echo "需要 curl" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

echo "[fetch-geolite2-city] 下载 ${DOWNLOAD_URL}"
curl -fL --retry 3 --connect-timeout 20 --max-time 600 \
  -o "${TMP}/GeoLite2-City.mmdb" "${DOWNLOAD_URL}"

if [[ ! -s "${TMP}/GeoLite2-City.mmdb" ]]; then
  echo "下载的 GeoLite2-City.mmdb 为空" >&2
  exit 1
fi

echo "[fetch-geolite2-city] 安装 GeoLite2-City.mmdb"
mv "${TMP}/GeoLite2-City.mmdb" "${OUT_BIN}"

echo "[fetch-geolite2-city] 完成: ${OUT_BIN}"
ls -lh "${OUT_BIN}"