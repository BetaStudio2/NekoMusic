#!/usr/bin/env bash
# 下载 Linux x86_64 静态 FFmpeg → target/classes/native/linux-x86_64/，随 mvn package 打入 fat JAR。
#
# 跳过：mvn package -Dskip.ffmpeg.bundle=true
# 自定义代理（可选）：export http_proxy=https://... https_proxy=https://...
set -euo pipefail

OUT_BASE="${1:-target/classes}"
PLATFORM="linux-x86_64"

# 源 1：johnvansickle 静态包（约 40MB）
URL_JVS="https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz"
# 源 2：GitHub BtbN（备用，包较大 ~135MB）
URL_BTBN="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"

log() {
  echo "[fetch-bundled-ffmpeg] $*"
}

download_with_progress() {
  local url="$1"
  local out="$2"
  log "URL: $url"
  log "保存到: $out"
  if command -v curl >/dev/null 2>&1; then
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

dest="$OUT_BASE/native/$PLATFORM/ffmpeg"
mkdir -p "$OUT_BASE/native/$PLATFORM"

if [[ -f "$dest" && -x "$dest" ]]; then
  log "已存在，跳过: $dest ($(du -h "$dest" | cut -f1))"
  log "完成"
  exit 0
fi

log "========== $PLATFORM (amd64) =========="
if [[ -n "${http_proxy:-}" || -n "${https_proxy:-}" ]]; then
  log "使用代理: http_proxy=${http_proxy:-} https_proxy=${https_proxy:-}"
fi

tmp="$(mktemp -d)"
archive="$tmp/ffmpeg.tar.xz"
ok=false

for url in "$URL_JVS" "$URL_BTBN"; do
  log "尝试下载..."
  rm -f "$archive"
  if download_with_progress "$url" "$archive" && [[ -s "$archive" ]]; then
    log "下载完成 ($(du -h "$archive" | cut -f1))，解压中..."
    if extract_ffmpeg "$archive" "$dest" "$tmp"; then
      ok=true
      break
    fi
    log "解压失败，尝试下一个源..."
  else
    log "下载失败，尝试下一个源..."
  fi
done

rm -rf "$tmp"

if [[ "$ok" != true ]]; then
  log "错误: linux-x86_64 FFmpeg 所有下载源均失败" >&2
  exit 1
fi

log "已写入: $dest ($(du -h "$dest" | cut -f1))"
log "完成"
