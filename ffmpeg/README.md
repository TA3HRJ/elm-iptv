# FFmpeg audio decoder module

Plays audio formats the TV can't decode itself (AC3, E-AC3, DTS, TrueHD, MP2…). ExoPlayer tries the device decoder
first and falls back to this one (`EXTENSION_RENDERER_MODE_ON`).

| Part | Source | License |
|---|---|---|
| `src/main/java/androidx/media3/decoder/ffmpeg/*` | AndroidX Media3 `1.11.1`, `libraries/decoder_ffmpeg`, unmodified | Apache-2.0 (`LICENSE-media3.txt`) |
| `src/main/jniLibs/*/libffmpegJNI.so` | Media3 `ffmpeg_jni.cc` + FFmpeg `release/6.0` @ `573f2b81ebd97da81def2ba762e81b0328547004` (6.0.2), unmodified | Apache-2.0 + LGPL-2.1-or-later (`LICENSE-ffmpeg-LGPL-2.1.txt`) |

FFmpeg is configured without `--enable-gpl` and `--enable-nonfree`; `build-native.sh` stops if `configure` reports
anything other than "LGPL version 2.1 or later". Only these decoders are enabled:
aac, ac3, eac3, dca, truehd, mlp, mp3, mp2, vorbis, opus, flac, alac, pcm_mulaw, pcm_alaw, amrnb, amrwb.

## Rebuilding / replacing the library (LGPL)

```bash
ffmpeg/build-native.sh <path-to-android-ndk>   # tested with NDK r27c (27.2.12479018)
```

This downloads the exact sources above, builds `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`, and overwrites
`src/main/jniLibs`. A modified FFmpeg can be used the same way; the resulting `libffmpegJNI.so` can be put into the
APK in place of the shipped one. The FFmpeg source used is also available on request: errhan@hotmail.com.
