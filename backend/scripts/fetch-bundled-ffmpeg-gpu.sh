#!/usr/bin/env bash
# 下载 BtbN FFmpeg 静态构建（linux64-gpl，含 h264_nvenc / CUDA 滤镜），写入
# src/main/resources/native/linux-x86_64/ffmpeg 供 JAR 内嵌。
# 许可：GPL（与 BtbN 构建一致）。需在可访问 GitHub 的网络下执行；mvn package 会自动调用。
set -eu

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/src/main/resources/native/linux-x86_64"
OUT_BIN="${OUT_DIR}/ffmpeg"
# 稳定别名 URL，重定向到当前 autobuild（见 https://github.com/BtbN/FFmpeg-Builds ）
DOWNLOAD_URL="https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"

if [[ "$(uname -s)" != "Linux" ]] || ! uname -m | command grep -Eq '^(x86_64|amd64)$'; then
  echo "[fetch-bundled-ffmpeg-gpu] 跳过：仅在本机为 Linux x86_64 时下载内嵌 FFmpeg。"
  exit 0
fi

mkdir -p "${OUT_DIR}"

if [[ -f "${OUT_BIN}" && -x "${OUT_BIN}" ]]; then
  if "${OUT_BIN}" -hide_banner -encoders 2>/dev/null | command grep -q h264_nvenc \
      && "${OUT_BIN}" -hide_banner -filters 2>/dev/null | command grep -q scale_cuda; then
    echo "[fetch-bundled-ffmpeg-gpu] 已存在 GPU 能力 FFmpeg，跳过: ${OUT_BIN}"
    exit 0
  fi
  echo "[fetch-bundled-ffmpeg-gpu] 现有二进制缺少 NVENC/CUDA 滤镜，将重新下载。"
  rm -f "${OUT_BIN}"
fi

command -v curl >/dev/null 2>&1 || { echo "需要 curl" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "需要 tar" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

echo "[fetch-bundled-ffmpeg-gpu] 下载 ${DOWNLOAD_URL}"
curl -fL --retry 3 --connect-timeout 20 --max-time 600 \
  -o "${TMP}/ffmpeg.txz" "${DOWNLOAD_URL}"

echo "[fetch-bundled-ffmpeg-gpu] 解压…"
tar -xJf "${TMP}/ffmpeg.txz" -C "${TMP}"

shopt -s nullglob
tops=( "${TMP}"/ffmpeg-*-linux64-gpl )
if [[ ${#tops[@]} -eq 0 ]]; then
  echo "解压后未找到 ffmpeg-*-linux64-gpl 目录" >&2
  exit 1
fi
BIN="${tops[0]}/bin/ffmpeg"
if [[ ! -f "${BIN}" ]]; then
  echo "未找到 ${BIN}" >&2
  exit 1
fi

cp -f "${BIN}" "${OUT_BIN}"
chmod a+x "${OUT_BIN}"

if ! "${OUT_BIN}" -hide_banner -encoders 2>/dev/null | command grep -q h264_nvenc; then
  rm -f "${OUT_BIN}"
  echo "下载的 FFmpeg 未包含 h264_nvenc" >&2
  exit 1
fi
if ! "${OUT_BIN}" -hide_banner -filters 2>/dev/null | command grep -q scale_cuda; then
  rm -f "${OUT_BIN}"
  echo "下载的 FFmpeg 未包含 scale_cuda（CUDA 滤镜）" >&2
  exit 1
fi

echo "[fetch-bundled-ffmpeg-gpu] 完成: ${OUT_BIN}"
"${OUT_BIN}" -hide_banner -version | head -3
