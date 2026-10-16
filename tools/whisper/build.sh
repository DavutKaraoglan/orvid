#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
COMMIT=0c7be696371069e0baa2515ed7aa08d5a5473aa6
SRC="$HERE/src"
OUT="$ROOT/libs/native/arm64-v8a"

if [ ! -d "$SRC/.git" ]; then
  git clone --filter=blob:none https://github.com/ggml-org/whisper.cpp "$SRC"
fi
git -C "$SRC" fetch --depth 1 origin "$COMMIT"
git -C "$SRC" checkout -q "$COMMIT"

cmake -S "$SRC" -B "$SRC/build" -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DGGML_OPENMP=OFF \
  -DGGML_NATIVE=OFF -DGGML_CPU_ARM_ARCH=armv8-a -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_TESTS=OFF \
  -DWHISPER_BUILD_SERVER=OFF
cmake --build "$SRC/build" -j2

B="$SRC/build"
clang++ -O2 -std=c++17 -march=armv8-a -I"$SRC/include" -I"$SRC/ggml/include" "$HERE/stream/main.cpp" \
  "$B/src/libwhisper.a" "$B/ggml/src/libggml.a" "$B/ggml/src/libggml-cpu.a" "$B/ggml/src/libggml-base.a" \
  -lm -ldl -Wl,-rpath,'$ORIGIN' -o "$OUT/libwhisper.so"
llvm-strip "$OUT/libwhisper.so"

LIBCXX="${LIBCXX:-${PREFIX:-/usr}/lib/libc++_shared.so}"
cp "$LIBCXX" "$OUT/libc++_shared.so"
llvm-strip "$OUT/libc++_shared.so"

echo "OK -> $OUT/libwhisper.so"
