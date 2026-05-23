# 内嵌 FFmpeg（Linux x86_64，GPU / NVENC）

此目录下的 `ffmpeg` 为 **BtbN FFmpeg-Builds** 的 `linux64-gpl` 包（含 **h264_nvenc**、**CUDA 滤镜** 等），体积较大，**默认不提交到 Git**（见仓库根 `.gitignore`）。

## 获取二进制

在 `backend/` 目录执行：

```bash
chmod +x scripts/fetch-bundled-ffmpeg-gpu.sh
./scripts/fetch-bundled-ffmpeg-gpu.sh
```

或在任意路径执行 `mvn package` / `mvn process-resources` 时，Maven 会在 **Linux x86_64** 上自动运行该脚本（若已存在且含 NVENC/CUDA 则跳过）。

## 许可说明

BtbN 的 **gpl** 构建与 **GPL** 许可一致；分发包含该二进制的 JAR 时请遵守 GPL 义务（提供源码链接等）。若需更宽松许可，可自行换用 `linux64-lgpl` 构建并调整脚本 URL（可能不含部分编码器）。
