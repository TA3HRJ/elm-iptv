package io.github.ta3hrj.elm.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.ta3hrj.elm.desktop.data.Catalog
import io.github.ta3hrj.elm.desktop.data.Storage
import io.github.ta3hrj.elm.desktop.ui.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

/** The version of this build (written by Gradle from the Android app's versionName). */
val appVersion: String by lazy {
    Thread.currentThread().contextClassLoader?.getResourceAsStream("elm-version.txt")?.use { it.readBytes().decodeToString().trim() } ?: "dev"
}

fun main() {
    val storage = Storage(Storage.defaultDir())
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent") != null) chain.proceed(request)
            else chain.proceed(request.newBuilder().header("User-Agent", Catalog.DEFAULT_USER_AGENT).build())
        }
        .build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val state = AppState(storage, Catalog(http, storage), scope)

    application {
        // Logos and posters: the same HTTP client, with a disk cache beside the other caches.
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
                .diskCache { DiskCache.Builder().directory(File(storage.cacheDir, "images").toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
                .build()
        }
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "ELM IPTV",
            state = windowState,
            icon = painterResource("elm.png"),
            onPreviewKeyEvent = { event ->
                // F5 / Ctrl+R reload the playlist from anywhere.
                if (event.type == KeyEventType.KeyDown && (event.key == Key.F5 || (event.isCtrlPressed && event.key == Key.R)) && state.playing == null) {
                    state.refresh()
                    true
                } else {
                    false
                }
            },
        ) {
            window.minimumSize = java.awt.Dimension(800, 520)
            androidx.compose.runtime.LaunchedEffect(Unit) { state.start() }
            App(state, windowState)
        }
    }
}
