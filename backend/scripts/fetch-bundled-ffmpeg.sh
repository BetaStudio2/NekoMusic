#!/usr/bin/env bash
# 下载 Linux 静态 FFmpeg → target/classes/native/，随 mvn package 打入 fat JAR。
#
# 跳过：mvn package -Dskip.ffmpeg.bundle=true
# 仅当前 CPU 架构（默认，更快）：mvn package
# 两种架构都打包：mvn package -Dffmpeg.bundle.all=true
# 自定义代理（可选）：export http_proxy=https://... https_proxy=https://...
set -euo pipefail

OUT_BASE="${1:-target/classes}"
BUNDLE_ALL="${FFMPEG_BUNDLE_ALL:-false}"

mkdir -p "$OUT_BASE/native/linux-x86_64" "$OUT_BASE/native/linux-aarch64"

# 源 1：johnvansickle 静态包（约 40–80MB/架构，单文件 ffmpeg）
URL_AMD64_JVS="https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
URL_ARM64_JVS="https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-arm64-static.tar.xz"

# 源 2：GitHub BtbN（备用，包较大 ~120MB+）
URL_AMD64_BTBN="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"
URL_ARM64_BTBN="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"

log() {
  echo "[fetch-bundled-ffmpeg] $*"
}

detect_host_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "linux-x86_64" ;;
    aarch64|arm64) echo "linux-aarch64" ;;
    *) echo "unknown" ;;
  esac
}

download_with_progress() {
  local url="$1"
  local out="$2"
  log "URL: $url"
  log "保存到: $out"
  if command -v curl >/dev/null 2>&1; then
    # --progress-bar 输出到 stderr，Maven 控制台可见
    curl -fL --connect-timeout 30 --max-time 3600 --retry 3 --retry-delay 3 \
      --progress-bar "$url" -o "$out"
    echo ""
    return 0
  fi
  if command -v wget >/dev/null 2>&1; then
    wget --timeout=30 --tries=3 --progress=bar:force:noscroll -O "$out" "$url"
    return 0
  fi
  log "错误: 需要 curl 或 wget" >&2
  return 1
}

extract_ffmpeg() {
  local archive="$1"
  local dest="$2"
  local tmp="$3"
  # 不按文件名后缀判断，直接尝试 tar.xz / tar.gz
  if tar -xJf "$archive" -C "$tmp" 2>/dev/null; then
    :
  elif tar -xzf "$archive" -C "$tmp" 2>/dev/null; then
    :
  else
    log "解压失败（非 tar.xz / tar.gz）: $archive" >&2
    return 1
  fi
  local bin
  bin="$(find "$tmp" -type f -name ffmpeg | head -n 1)"
  if [[ -z "$bin" ]]; then
    log "解压后未找到 ffmpeg 可执行文件" >&2
    return 1
  fi
  cp "$bin" "$dest"
  chmod +x "$dest"
}

fetch_one() {
  local platform="$1"
  shift
  local urls=("$@")
  local dest="$OUT_BASE/native/$platform/ffmpeg"

  if [[ -f "$dest" && -x "$dest" ]]; then
    log "已存在，跳过: $dest ($(du -h "$dest" | cut -f1))"
    return 0
  fi

  log "========== $platform =========="
  local tmp
  tmp="$(mktemp -d)"
  local archive="$tmp/ffmpeg.tar.xz"
  local ok=false

  for url in "${urls[@]}"; do
    log "尝试下载 ($platform) ..."
    rm -f "$archive"
    if download_with_progress "$url" "$archive"; then
      if [[ -s "$archive" ]]; then
        log "下载完成 ($(du -h "$archive" | cut -f1))，解压中..."
        if extract_ffmpeg "$archive" "$dest" "$tmp"; then
          ok=true
          break
        fi
        log "解压失败，尝试下一个源..."
      else
        log "下载文件为空，尝试下一个源..."
      fi
    else
      log "下载失败，尝试下一个源..."
    fi
  done

  rm -rf "$tmp"
  if [[ "$ok" != true ]]; then
    log "错误: $platform 所有下载源均失败" >&2
    return 1
  fi
  log "已写入: $dest ($(du -h "$dest" | cut -f1))"
}

HOST_ARCH="$(detect_host_arch)"
log "本机架构: $HOST_ARCH | 打包全部架构: $BUNDLE_ALL"
if [[ -n "${http_proxy:-}" || -n "${https_proxy:-}" ]]; then
  log "使用代理: http_proxy=${http_proxy:-} https_proxy=${https_proxy:-}"
fi

if [[ "$BUNDLE_ALL" == "true" ]]; then
  fetch_one "linux-x86_64" "$URL_AMD64_JVS" "$URL_AMD64_BTBN"
  fetch_one "linux-aarch64" "$URL_ARM64_JVS" "$URL_ARM64_BTBN"
else
  case "$HOST_ARCH" in
    linux-x86_64)
      fetch_one "linux-x86_64" "$URL_AMD64_JVS" "$URL_AMD64_BTBN"
      ;;
    linux-aarch64)
      fetch_one "linux-aarch64" "$URL_ARM64_JVS" "$URL_ARM64_BTBN"
      ;;
    *)
      log "警告: 未识别架构，改为下载 x86_64 + arm64"
      fetch_one "linux-x86_64" "$URL_AMD64_JVS" "$URL_AMD64_BTBN"
      fetch_one "linux-aarch64" "$URL_ARM64_JVS" "$URL_ARM64_BTBN"
      ;;
  esac
fi

log "完成"
