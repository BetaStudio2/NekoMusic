#!/usr/bin/env bash
# 下载评论归属地的免费兜底数据库，写入 src/main/resources 供 JAR 内嵌：
#   GeoLite2-City.mmdb         国外国家 / 国内城市兜底
#   GeoLite2-ASN.mmdb          国内省级运营商 ASN
#   GeoIP/qqwry.dat            国内 IPv4 省市补充
# 需在可访问 GitHub 的网络下执行；mvn package 可自动调用。
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/src/main/resources"
GEOLITE2_BIN="${OUT_DIR}/GeoLite2-City.mmdb"
GEOLITE2_ASN_BIN="${OUT_DIR}/GeoLite2-ASN.mmdb"
GEOIP_DIR="${OUT_DIR}/GeoIP"
QQWRY_BIN="${GEOIP_DIR}/qqwry.dat"
# P3TERX GeoLite.mmdb 镜像
GEOLITE2_URL="https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-City.mmdb"
GEOLITE2_ASN_URL="https://raw.githubusercontent.com/P3TERX/GeoLite.mmdb/download/GeoLite2-ASN.mmdb"
# 纯真数据库按发布版本固定，避免构建期间 latest 变化导致产物不可复现
QQWRY_TAG="20261014"
QQWRY_URL="https://github.com/metowolf/qqwry.dat/releases/download/${QQWRY_TAG}/qqwry.dat"
QQWRY_SHA256="8e45c56a3831b5255687a39c19d806ccf1cfd25afb8d628ef4a9ffe61386c1e9"

mkdir -p "${OUT_DIR}" "${GEOIP_DIR}"

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
download "${GEOLITE2_ASN_URL}" "${GEOLITE2_ASN_BIN}" "GeoLite2-ASN.mmdb"
download "${QQWRY_URL}" "${QQWRY_BIN}" "qqwry.dat" "${QQWRY_SHA256}"

ls -lh "${GEOLITE2_BIN}" "${GEOLITE2_ASN_BIN}" "${QQWRY_BIN}"
