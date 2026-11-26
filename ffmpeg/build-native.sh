#!/usr/bin/env bash
# Rebuilds src/main/jniLibs/<abi>/libffmpegJNI.so from public sources:
#   - FFmpeg (LGPL-2.1-or-later build: no --enable-gpl / --enable-nonfree), audio decoders only
#   - Media3 FFmpeg JNI wrapper (Apache-2.0), same tag as the Media3 version the app uses
#
# Usage: ffmpeg/build-native.sh <path to Android NDK>
# Needs git, make, cmake, ninja. Takes ~10 minutes on 4 cores.
set -euo pipefail

FFMPEG_BRANCH="release/6.0"
FFMPEG_COMMIT="573f2b81ebd97da81def2ba762e81b0328547004"   # 6.0.2
MEDIA3_TAG="1.11.1"
API=23
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)
DECODERS=(aac ac3 eac3 dca truehd mlp mp3 mp2 vorbis opus flac alac pcm_mulaw pcm_alaw amrnb amrwb)

NDK="${1:?usage: $0 <android-ndk-path>}"
MODULE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$MODULE/build/native-src}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
JOBS="$(nproc 2>/dev/null || echo 4)"

mkdir -p "$WORK"
cd "$WORK"
if [[ ! -d ffmpeg ]]; then
  git clone --depth 1 --branch "$FFMPEG_BRANCH" https://github.com/FFmpeg/FFmpeg ffmpeg
fi
(cd ffmpeg && test "$(git rev-parse HEAD)" = "$FFMPEG_COMMIT" || { echo "Unexpected FFmpeg commit"; exit 1; })
if [[ ! -d media3 ]]; then
  git clone --depth 1 --branch "$MEDIA3_TAG" --filter=blob:none --sparse https://github.com/androidx/media media3
  (cd media3 && git sparse-checkout set libraries/decoder_ffmpeg)
fi
JNI_SRC="$WORK/media3/libraries/decoder_ffmpeg/src/main/jni"

options=(
  --target-os=android --enable-static --disable-shared --disable-doc --disable-programs
  --disable-everything --disable-avdevice --disable-avformat --disable-swscale --disable-postproc
  --disable-avfilter --disable-symver --enable-swresample --disable-v4l2-m2m --disable-vulkan
  --enable-pic
)
for d in "${DECODERS[@]}"; do options+=("--enable-decoder=$d"); done

for abi in "${ABIS[@]}"; do
  case "$abi" in
    armeabi-v7a) arch=arm;     cpu=armv7-a; triple=armv7a-linux-androideabi; extra=(--extra-cflags="-march=armv7-a -mfloat-abi=softfp" --extra-ldflags="-Wl,--fix-cortex-a8") ;;
    arm64-v8a)   arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android;    extra=() ;;
    x86)         arch=x86;     cpu=i686;    triple=i686-linux-android;       extra=(--disable-asm) ;;
    x86_64)      arch=x86_64;  cpu=x86-64;  triple=x86_64-linux-android;     extra=(--disable-asm) ;;
  esac
  prefix="$WORK/out/$abi"
  (
    cd ffmpeg
    make distclean >/dev/null 2>&1 || true
    ./configure --prefix="$prefix" --arch="$arch" --cpu="$cpu" \
      --cross-prefix="$TOOLCHAIN/$triple$API-" \
      --nm="$TOOLCHAIN/llvm-nm" --ar="$TOOLCHAIN/llvm-ar" --ranlib="$TOOLCHAIN/llvm-ranlib" --strip="$TOOLCHAIN/llvm-strip" \
      "${options[@]}" "${extra[@]}" | tee "$WORK/configure-$abi.log" | grep -E "^License:"
    grep -q "^License: LGPL version 2.1 or later" "$WORK/configure-$abi.log" || { echo "FFmpeg build is not LGPL"; exit 1; }
    make -j"$JOBS" >/dev/null
    make install-libs install-headers >/dev/null
  )

  # The JNI wrapper's CMakeLists expects ffmpeg/ (headers) and ffmpeg/android-libs/<abi>/*.a next to it.
  stage="$WORK/jni-$abi"
  rm -rf "$stage"; mkdir -p "$stage/ffmpeg/android-libs/$abi"
  cp "$JNI_SRC/CMakeLists.txt" "$JNI_SRC/ffmpeg_jni.cc" "$stage/"
  cp -r "$prefix/include/." "$stage/ffmpeg/"
  cp "$prefix"/lib/*.a "$stage/ffmpeg/android-libs/$abi/"
  cmake -S "$stage" -B "$stage/build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$API" -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" >/dev/null
  cmake --build "$stage/build" >/dev/null
  mkdir -p "$MODULE/src/main/jniLibs/$abi"
  "$TOOLCHAIN/llvm-strip" --strip-unneeded -o "$MODULE/src/main/jniLibs/$abi/libffmpegJNI.so" "$stage/build/libffmpegJNI.so"
  echo "$abi: $(du -h "$MODULE/src/main/jniLibs/$abi/libffmpegJNI.so" | cut -f1)"
done
