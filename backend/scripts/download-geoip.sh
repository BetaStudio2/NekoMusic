#!/usr/bin/env bash
# 下载评论归属地数据库，写入 src/main/resources 供 JAR 内嵌：
#   GeoLite2-City.mmdb         国外国家 / 国内城市兜底
#   GeoIP/qqwry.dat            国内 IPv4 省市补充
#   GeoIP/ip2region_v4.xdb     国内 IPv4 城市级兜底
#   GeoIP/ip2region_v6.xdb     国内 IPv6 城市级兜底
# 需在可访问 GitHub 的网络下执行；mvn package 可自动调用。
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/src/main/resources"
GEOLITE2_BIN="${OUT_DIR}/GeoLite2-City.mmdb"
IP2REGION_DIR="${OUT_DIR}/GeoIP"
QQWRY_BIN="${IP2REGION_DIR}/qqwry.dat"
IP2REGION_V4_BIN="${IP2REGION_DIR}/ip2region_v4.xdb"
IP2REGION_V6_BIN="${IP2REGION_DIR}/ip2region_v6.xdb"
# P3TERX GeoLite.mmdb 镜像
GEOLITE2_URL="https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-City.mmdb"
# 固定到 2026-10-14 的数据提交；不要用旧标签，旧数据对运营商 IP 的城市精度更差
IP2REGION_COMMIT="1bf6cabfb63c1a14af0971749c2120a3e5c77d72"
IP2REGION_V4_URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/${IP2REGION_COMMIT}/data/ip2region_v4.xdb"
IP2REGION_V6_URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/${IP2REGION_COMMIT}/data/ip2region_v6.xdb"
IP2REGION_V4_SHA256="27ecba8c9a6d5e3b280aedbe7ee15c65372b179fbd0dad3845d8c608f2fcace1"
IP2REGION_V6_SHA256="939f6b46bd2b8bec3cf7c5ceb8ba782266ae9b1f35b5ba7916700dec0b7506ed"
# 纯真数据库按发布版本固定，避免构建期间 latest 变化导致产物不可复现
QQWRY_TAG="20261014"
QQWRY_URL="https://github.com/metowolf/qqwry.dat/releases/download/${QQWRY_TAG}/qqwry.dat"
QQWRY_SHA256="8e45c56a3831b5255687a39c19d806ccf1cfd25afb8d628ef4a9ffe61386c1e9"

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
download "${QQWRY_URL}" "${QQWRY_BIN}" "qqwry.dat" "${QQWRY_SHA256}"
download "${IP2REGION_V4_URL}" "${IP2REGION_V4_BIN}" "ip2region_v4.xdb" "${IP2REGION_V4_SHA256}"
download "${IP2REGION_V6_URL}" "${IP2REGION_V6_BIN}" "ip2region_v6.xdb" "${IP2REGION_V6_SHA256}"

ls -lh "${GEOLITE2_BIN}" "${QQWRY_BIN}" "${IP2REGION_V4_BIN}" "${IP2REGION_V6_BIN}"
