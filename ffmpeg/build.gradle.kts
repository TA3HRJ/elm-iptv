// Media3's FFmpeg audio decoder (Apache-2.0 Java + JNI wrapper) with an LGPL-only FFmpeg build.
// The native libraries in src/main/jniLibs are prebuilt by build-native.sh; see README.md.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.ta3hrj.elm.ffmpeg"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Upstream Media3 code; it opts into its own unstable APIs.
        disable += setOf("UnsafeOptInUsageError")
    }
}

dependencies {
    api(libs.media3.decoder)
    implementation(libs.media3.exoplayer)
    implementation(libs.androidx.annotation)
    compileOnly(libs.checker.qual)
}
