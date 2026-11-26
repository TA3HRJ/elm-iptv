plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from environment variables (GitHub Actions secrets, or set them locally).
// Without them, release builds fall back to the debug key so they can still be sideloaded for testing.
val releaseKeystore = System.getenv("ELM_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.isFile }

// Where the release build looks for updates (`latest.json` on our server). Kept out of the repository:
// CI passes it from the ELM_UPDATE_URL variable. Without it the build never checks for updates.
val updateUrl = System.getenv("ELM_UPDATE_URL").orEmpty().trim()
require(updateUrl.isEmpty() || updateUrl.startsWith("https://")) { "ELM_UPDATE_URL must be an https:// address" }

android {
    namespace = "io.github.ta3hrj.elm"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ta3hrj.elm"
        // API 23 (Android 6): required by Media3 1.11 and current AndroidX. Fire OS 6+ devices are API 25-30.
        minSdk = 23
        targetSdk = 35
        versionCode = 52
        versionName = "1.0.0"
        buildConfigField("String", "UPDATE_URL", "\"${updateUrl.replace("\"", "")}\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("ELM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ELM_KEY_ALIAS")
                keyPassword = System.getenv("ELM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the release build on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric tests (database, migrations, repositories) run on the JVM without a device.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Store the FFmpeg native libraries compressed: roughly halves the APK size (they are unpacked at install).
        jniLibs.useLegacyPackaging = true
    }
}

// Robolectric's Android runtime is fetched by Gradle (cached, works behind proxies) instead of by
// Robolectric itself at test time; the tests then run offline.
val robolectricRuntime: Configuration by configurations.creating { isTransitive = false }
val robolectricJarsDir = layout.buildDirectory.dir("robolectric-jars")
val copyRobolectricJars = tasks.register<Copy>("copyRobolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarsDir)
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricJars)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
    // Same locale as CI. On a Turkish Windows, Conscrypt lower-cases "Windows" to "wındows" (dotless ı)
    // and cannot find its native library, so every Robolectric test fails.
    jvmArgs("-Duser.language=en", "-Duser.country=US")
    // Dev aids: screenshots (ShotTest: -Dshots=DIR -Dsize=3 -Dscreen=settings -Dkeys=DDR) and the remote sweep
    // (RemoteReachabilityTest: -Dsweep=1); both are skipped without these properties.
    listOf("shots", "size", "keys", "screen", "listName", "sweep", "perf", "demo", "lang").forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }
}

ksp {
    // Room writes each database version's schema here; migrations are checked against these files.
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK file names: ELM-IPTV-<version>.apk (debug: ELM-IPTV-<version>-debug.apk).
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl ?: return@forEach
            impl.outputFileName.set(impl.versionName.map { "ELM-IPTV-$it.apk" })
        }
    }
}

dependencies {
    implementation(project(":core"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    robolectricRuntime(libs.robolectric.android.all)

    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    // Only the icons actually used remain after R8 shrinking.
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    // Daily guide/channel update in the background.
    implementation(libs.work.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    implementation(libs.coil.compose)
    // QR code for the support link in About (Apache 2.0).
    implementation(libs.zxing.core)
    // FFmpeg audio decoders (LGPL build), used only when the device can't decode a format itself (AC3, E-AC3, DTS…).
    implementation(project(":ffmpeg"))
    implementation(libs.tvprovider)
}
