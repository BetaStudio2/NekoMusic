#!/usr/bin/env bash
set -euo pipefail

native_dir="$(cd "$(dirname "$0")" && pwd)"
build_dir="$native_dir/build"
cmake -S "$native_dir" -B "$build_dir" -DCMAKE_BUILD_TYPE=Release
cmake --build "$build_dir" --parallel
cp "$build_dir/libneko_audio_quality.so" "$native_dir/libneko_audio_quality.so"
resource_dir="$native_dir/../src/main/resources/native/linux-x86_64"
mkdir -p "$resource_dir"
cp "$build_dir/libneko_audio_quality.so" "$resource_dir/libneko_audio_quality.so"
