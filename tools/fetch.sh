#!/usr/bin/env bash
set -euo pipefail

TOOLS="$(cd "$(dirname "$0")" && pwd)"
R8_URL=https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.13.25/r8-8.13.25.jar
R8_SHA=0d4f2f68c4fcbc9434eff122f41feb6fb1d98efd2faa530cec1abacab1cea03d
PLATFORM_URL=https://dl.google.com/android/repository/platform-33_r02.zip
ANDROID_SHA=14261d2817d558e7fbbf9848216c4f6ec37c134353325eab230acf2af0c25fe7

check() {
  echo "$2  $1" | sha256sum -c --quiet - || { echo "checksum mismatch: $1" >&2; rm -f "$1"; exit 1; }
}

if [ ! -f "$TOOLS/r8.jar" ]; then
  curl -fL -o "$TOOLS/r8.jar.part" "$R8_URL"
  check "$TOOLS/r8.jar.part" "$R8_SHA"
  mv "$TOOLS/r8.jar.part" "$TOOLS/r8.jar"
fi

if [ ! -f "$TOOLS/android33.jar" ]; then
  tmp="$(mktemp -d)"
  curl -fL -o "$tmp/platform.zip" "$PLATFORM_URL"
  unzip -p "$tmp/platform.zip" "*/android.jar" > "$TOOLS/android33.jar.part"
  rm -rf "$tmp"
  check "$TOOLS/android33.jar.part" "$ANDROID_SHA"
  mv "$TOOLS/android33.jar.part" "$TOOLS/android33.jar"
fi

echo "OK -> $TOOLS/r8.jar, $TOOLS/android33.jar"
