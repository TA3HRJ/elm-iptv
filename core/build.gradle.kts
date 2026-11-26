plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    // XmlPullParser is built into Android; the JVM build only compiles (and tests) against kxml2.
    compileOnly(libs.kxml2)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
    testImplementation(libs.kotlinx.coroutines.core)
}

// core runs on Android down to API 22 (Fire OS 5): fail the build on Java APIs newer than that.
tasks.register("checkAndroidApiUsage") {
    val sources = fileTree("src/main/kotlin") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val banned = Regex("""\.(putIfAbsent|computeIfAbsent|computeIfPresent|getOrDefault|removeIf|stream)\(|java\.time\.|java\.util\.Base64""")
        val hits = sources.files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (banned.containsMatchIn(line)) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }
        // Android's ICU regex engine rejects a bare '}' closing an escaped '\{' (the JVM accepts it).
        fun bareClosingBrace(line: String): Boolean {
            var escapedOpen = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (c == '\\' && i + 1 < line.length) {
                    if (line[i + 1] == '{') escapedOpen = true
                    i += 2
                    continue
                }
                if (c == '{') escapedOpen = false
                if (c == '}' && escapedOpen) return true
                i++
            }
            return false
        }
        val braceHits = sources.files.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (line.contains("Regex(\"\"\"") && bareClosingBrace(line.substringAfter("Regex(\"\"\"").substringBefore("\"\"\""))) "${f.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        val all = hits + braceHits.map { "$it  (escape '}' as '\\}')" }
        if (all.isNotEmpty()) throw GradleException("Code that breaks on Android:\n" + all.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn("checkAndroidApiUsage") }
tasks.named("test") { dependsOn("checkAndroidApiUsage") }
