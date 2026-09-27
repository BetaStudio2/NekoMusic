#!/usr/bin/env python3
"""Recalculate original audio quality metadata for every music row."""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path


def find_root() -> Path:
    configured = os.environ.get("NEKO_MUSIC_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    candidates = [
        Path(__file__).resolve().parent / "projet" / "NekoMusic",
        Path.cwd(),
        Path(__file__).resolve().parents[2],
    ]
    for candidate in candidates:
        if (candidate / "backend").is_dir() and (candidate / "config.yml").exists():
            return candidate
    return Path.cwd()


def mysql_config(text: str) -> dict[str, str]:
    lines = text.splitlines()
    mysql_lines = []
    in_mysql = False
    for line in lines:
        if line.strip() == "mysql:":
            in_mysql = True
            continue
        if in_mysql and line and not line[0].isspace() and not line.lstrip().startswith("#"):
            break
        if in_mysql:
            mysql_lines.append(line)
    body = "\n".join(mysql_lines)

    def value(key: str, default: str) -> str:
        match = re.search(r"^\s{2,}" + re.escape(key) + r"\s*:\s*(?:['\"]([^'\"]*)['\"]|([^#\r\n]*))", body, re.MULTILINE)
        if not match:
            return default
        return (match.group(1) or match.group(2) or "").strip()

    return {
        "host": os.environ.get("MYSQL_HOST", value("host", "localhost")),
        "port": os.environ.get("MYSQL_PORT", value("port", "3306")),
        "database": os.environ.get("MYSQL_DATABASE", value("database", "nek_music")),
        "user": os.environ.get("MYSQL_USER", value("username", "root")),
        "password": os.environ.get("MYSQL_PASSWORD", value("password", "")),
    }


def run_mysql(config: dict[str, str], query: str) -> str:
    env = os.environ.copy()
    env["MYSQL_PWD"] = config["password"]
    command = [
        "mysql", f"--host={config['host']}", f"--port={config['port']}",
        f"--user={config['user']}", "--batch", "--skip-column-names",
        config["database"], "-e", query,
    ]
    result = subprocess.run(command, capture_output=True, text=True, env=env)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"MySQL 连接或查询失败 ({config['host']}:{config['port']}/{config['database']}): {detail}")
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--music-id", type=int)
    args = parser.parse_args()

    root = find_root()
    config_path = root / "config.yml"
    if not config_path.exists():
        print(f"找不到配置文件: {config_path}，可设置 NEKO_MUSIC_ROOT", file=sys.stderr)
        return 2
    config = mysql_config(config_path.read_text(encoding="utf-8"))
    query = "SELECT id FROM music ORDER BY id"
    if args.music_id:
        query = f"SELECT id FROM music WHERE id = {args.music_id}"
    rows = run_mysql(config, query).splitlines()

    jar_override = os.environ.get("NEKO_BACKEND_JAR")
    if jar_override:
        classpath = Path(jar_override).expanduser().resolve()
    else:
        jar_candidates = sorted(root.glob("*.jar")) + sorted((root / "target").glob("*.jar"))
        classpath = next((path for path in jar_candidates if path.is_file() and "original" not in path.name), None)
    classes = root / "backend" / "target" / "classes"
    if classpath is None and not classes.exists():
        print("找不到后端 JAR。请先构建后端，或设置 NEKO_BACKEND_JAR=/path/to/backend.jar", file=sys.stderr)
        return 2
    classpath = classpath if classpath is not None else classes

    for row in rows:
        music_id = int(row)
        candidates = [p for p in sorted((root / "Music" / "music").glob(f"{music_id}.*")) if p.is_file()]
        if not candidates:
            print(f"SKIP {music_id}: 音频文件不存在")
            continue
        result = subprocess.run(
            ["java", "-cp", str(classpath),
             "com.neko.music.scripts.NativeQualityProbe", str(candidates[0])],
            capture_output=True, text=True,
        )
        if result.returncode != 0:
            print(f"SKIP {music_id}: {result.stderr.strip()}")
            continue
        quality, bitrate, sample_rate, bits, channels = result.stdout.strip().split(",")
        print(f"{music_id}: {quality} {bitrate}bps {sample_rate}Hz {bits}bit {channels}ch")
        if not args.dry_run:
            update = (
                "UPDATE music SET max_quality='" + quality + "', bitrate_bps=" + bitrate
                + ", sample_rate_hz=" + sample_rate + ", bits_per_sample=" + bits
                + ", channels=" + channels + " WHERE id=" + str(music_id) + ";"
            )
            run_mysql(config, update)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
