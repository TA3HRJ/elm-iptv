# Third-party components

| Component | License | Notes |
|---|---|---|
| AndroidX Media3 / ExoPlayer, Jetpack Compose, Compose for TV, Room, Activity, Lifecycle, TV Provider | Apache License 2.0 | Google LLC |
| OkHttp | Apache License 2.0 | Square, Inc. |
| Coil | Apache License 2.0 | Coil Contributors |
| ZXing core (QR code for the support link in About; Android only) | Apache License 2.0 | ZXing authors |
| kotlinx.coroutines, kotlinx.serialization, Kotlin standard library | Apache License 2.0 | JetBrains s.r.o. |
| kXML 2 (Android: tests only, the system provides the parser; Windows/Linux: the guide parser) | MIT | |
| Inter typeface (launcher banner artwork) | SIL Open Font License 1.1 | Rasmus Andersson |
| Media3 FFmpeg decoder module (Java + JNI wrapper, `ffmpeg/`) | Apache License 2.0 | Google LLC; `ffmpeg/LICENSE-media3.txt` |
| FFmpeg 6.0.2 (audio decoders only, inside `libffmpegJNI.so`) | **LGPL-2.1-or-later** | Built without `--enable-gpl`/`--enable-nonfree`; unmodified source and build script in `ffmpeg/` (see `ffmpeg/README.md`); `ffmpeg/LICENSE-ffmpeg-LGPL-2.1.txt` |
| Compose Multiplatform (desktop UI, Material 3, icons) | Apache License 2.0 | JetBrains s.r.o. and Google LLC; Windows/Linux builds only |
| Coil 3 | Apache License 2.0 | Coil Contributors; Windows/Linux builds only |
| vlcj 4 (Java binding to the computer's VLC; Windows/Linux builds only) | **GPL-3.0** | Caprica Software Limited. The desktop builds link it, so they may only be given out under GPL-3.0 (planned for 1.0) or with a commercial vlcj licence. Not part of the Android app. |
| VLC / libVLC (not shipped; the user's own installation is used) | LGPL-2.1-or-later | VideoLAN |

Apache 2.0 and MIT components require keeping their copyright notices; the About screen lists them.

LGPL obligations for FFmpeg when distributing the APK: keep the license text and notice (About screen + this file),
offer the exact FFmpeg source used (FFmpeg `release/6.0`, commit `573f2b81`) and the build script, and allow the
user to replace `libffmpegJNI.so`. No ELM IPTV code is linked into that library; it is built only from FFmpeg and
Media3's Apache-2.0 JNI wrapper, so it can be rebuilt and swapped from public sources.
