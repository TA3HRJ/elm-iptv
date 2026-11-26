import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

// One version for every platform: the desktop build takes the Android app's versionName.
val appVersion: String = Regex("""versionName\s*=\s*"([^"]+)"""")
    .find(rootProject.file("app/build.gradle.kts").readText())
    ?.groupValues?.get(1)
    ?: error("versionName not found in app/build.gradle.kts")
version = appVersion

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.cmp.material3)
    implementation(libs.cmp.material.icons.extended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    // The guide parser (XmlPullParser) is built into Android; on the desktop kxml2 provides it.
    implementation(libs.kxml2)
    // Playback through the VLC installed on the computer (libvlc 3).
    implementation(libs.vlcj)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    // Same as the Android tests: the app's texts and dates are checked in a fixed language.
    jvmArgs("-Duser.language=en", "-Duser.country=US")
}

// The version shown in the app (About) comes from the build.
val writeVersion = tasks.register("writeVersion") {
    val out = layout.buildDirectory.dir("generated/version")
    val v = appVersion
    inputs.property("version", v)
    outputs.dir(out)
    doLast {
        val file = out.get().file("elm-version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(v)
    }
}
sourceSets.main { resources.srcDir(writeVersion) }

compose.desktop {
    application {
        mainClass = "io.github.ta3hrj.elm.desktop.MainKt"
        // Lists of tens of thousands of films and a week of guide fit easily; the video itself is decoded by VLC.
        jvmArgs += listOf("-Xmx1g", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            // Windows installer (WiX is downloaded by the Compose plugin) and the Linux AppImage folder
            // (CI turns it into a single .AppImage file with appimagetool, see desktop/packaging).
            targetFormats(TargetFormat.Msi, TargetFormat.AppImage)
            packageName = "ELM-IPTV"
            packageVersion = appVersion.substringBefore('-')
            description = "IPTV player"
            vendor = "ELM IPTV"
            copyright = "ELM IPTV contributors"
            // What OkHttp (TLS), the coroutines and VLC's binding need from the Java runtime.
            modules("java.naming", "java.net.http", "java.sql", "jdk.crypto.ec", "jdk.charsets", "jdk.unsupported", "java.management")

            windows {
                menuGroup = "ELM IPTV"
                // Without administrator rights, into the user's own programs folder.
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                // Fixed for good: a newer installer replaces the older version instead of installing beside it.
                upgradeUuid = "7f4a3e2c-6d1b-4c8e-9a52-3b7e1d0c9f64"
                iconFile.set(project.file("packaging/elm.ico"))
            }
            linux {
                iconFile.set(project.file("packaging/elm.png"))
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo"
            }
        }
    }
}
