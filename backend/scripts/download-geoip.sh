#!/usr/bin/env bash
# 下载评论归属地数据库，写入 src/main/resources 供 JAR 内嵌：
#   GeoLite2-City.mmdb         国外国家 / 国内城市兜底
#   GeoIP/ip2region_v4.xdb     国内 IPv4 城市级兜底
#   GeoIP/ip2region_v6.xdb     国内 IPv6 城市级兜底
# 需在可访问 GitHub 的网络下执行；mvn package 可自动调用。
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/src/main/resources"
GEOLITE2_BIN="${OUT_DIR}/GeoLite2-City.mmdb"
IP2REGION_DIR="${OUT_DIR}/GeoIP"
IP2REGION_V4_BIN="${IP2REGION_DIR}/ip2region_v4.xdb"
IP2REGION_V6_BIN="${IP2REGION_DIR}/ip2region_v6.xdb"
# P3TERX GeoLite.mmdb 镜像
GEOLITE2_URL="https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-City.mmdb"
# 固定 ip2region 数据版本；校验和与 v3.11.2 的官方数据文件对应
IP2REGION_TAG="v3.11.2"
IP2REGION_V4_URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/${IP2REGION_TAG}/data/ip2region_v4.xdb"
IP2REGION_V6_URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/${IP2REGION_TAG}/data/ip2region_v6.xdb"
IP2REGION_V4_SHA256="5555fd1aab63e06096d9ee2a4187e93b8451e650b77ce4138a26eb9cf4d81469"
IP2REGION_V6_SHA256="2c0ec39095bc224a506657d20dfbb460707dd94a9cfc2242fd5fe2b2e36cbf1c"

mkdir -p "${OUT_DIR}" "${IP2REGION_DIR}"

command -v curl >/dev/null 2>&1 || { echo "需要 curl" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

download() {
  url="$1"
  dest="$2"
  label="$3"
  sha256="${4:-}"
  tmp_file="${TMP}/${label}"

  if [[ -s "${dest}" ]]; then
    if [[ -z "${sha256}" ]] || printf '%s  %s\n' "${sha256}" "${dest}" | sha256sum -c - >/dev/null 2>&1; then
      echo "[fetch-geoip] 已存在，跳过下载: ${dest}"
      return
    fi
    echo "[fetch-geoip] 校验和不匹配，重新下载: ${dest}"
  fi

  echo "[fetch-geoip] 下载 ${label}: ${url}"
  curl -fL --retry 3 --connect-timeout 20 --max-time 600 \
    -o "${tmp_file}" "${url}"

  if [[ ! -s "${tmp_file}" ]]; then
    echo "下载的 ${label} 为空" >&2
    exit 1
  fi
  if [[ -n "${sha256}" ]] && ! printf '%s  %s\n' "${sha256}" "${tmp_file}" | sha256sum -c - >/dev/null 2>&1; then
    echo "下载的 ${label} 校验和不匹配" >&2
    exit 1
  fi

  mv "${tmp_file}" "${dest}"
  echo "[fetch-geoip] 完成: ${dest}"
}

download "${GEOLITE2_URL}" "${GEOLITE2_BIN}" "GeoLite2-City.mmdb"
download "${IP2REGION_V4_URL}" "${IP2REGION_V4_BIN}" "ip2region_v4.xdb" "${IP2REGION_V4_SHA256}"
download "${IP2REGION_V6_URL}" "${IP2REGION_V6_BIN}" "ip2region_v6.xdb" "${IP2REGION_V6_SHA256}"

ls -lh "${GEOLITE2_BIN}" "${IP2REGION_V4_BIN}" "${IP2REGION_V6_BIN}"
