package io.github.ta3hrj.elm.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException

/** Channels of a downloaded playlist, plus account details for Xtream sources. */
data class LoadedPlaylist(
    val channels: List<Channel>,
    val account: XtreamAccount? = null,
    /** Guide (XMLTV) addresses the source announces: M3U `url-tvg`, or Xtream's `xmltv.php`. */
    val epgUrls: List<String> = emptyList(),
)

/** Downloads a playlist from any supported source and returns its channels. */
class PlaylistLoader(
    private val http: OkHttpClient,
    private val defaultUserAgent: String,
) {
    suspend fun load(source: PlaylistSource): LoadedPlaylist = try {
        when (source) {
            is PlaylistSource.M3u -> loadM3u(source).let { LoadedPlaylist(it.channels, epgUrls = it.epgUrls) }
            is PlaylistSource.Xtream -> XtreamClient(http, source, userAgentFor(source)).run {
                val account = account()
                val channels = liveChannels()
                if (channels.isEmpty()) throw PlaylistException(tx("Hesapta canlı kanal bulunamadı.", "No live channels found in the account."))
                LoadedPlaylist(channels, account, listOf(xmltvUrl()))
            }
        }
    } catch (e: PlaylistException) {
        throw e
    } catch (e: IOException) {
        throw PlaylistException(tx("Bağlantı kurulamadı: ", "Could not connect: ") + (e.message ?: e.javaClass.simpleName), e)
    }

    /**
     * Downloads a guide (XMLTV, optionally gzip-compressed) and hands the body stream to [read].
     * Blocking: call from a background thread.
     */
    fun <T> openGuide(url: String, userAgent: String, read: (java.io.InputStream) -> T): T {
        val httpUrl = url.trim().toHttpUrlOrNull() ?: throw PlaylistException(tx("Geçersiz rehber adresi: ", "Invalid guide address: ") + url)
        val request = Request.Builder().url(httpUrl).header("User-Agent", userAgent).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw PlaylistException(tx("Rehber indirilemedi: HTTP ", "Guide download failed: HTTP ") + response.code)
                val body = response.body ?: throw PlaylistException(tx("Rehber boş.", "The guide is empty."))
                return read(body.byteStream())
            }
        } catch (e: IOException) {
            throw PlaylistException(tx("Rehber indirilemedi: ", "Guide download failed: ") + (e.message ?: e.javaClass.simpleName), e)
        }
    }

    fun userAgentFor(source: PlaylistSource): String = source.userAgent?.ifBlank { null } ?: defaultUserAgent

    private suspend fun loadM3u(source: PlaylistSource.M3u): M3uParser.Result = withContext(Dispatchers.IO) {
        val url = source.url.trim().let { if (it.contains("://")) it else "http://$it" }.toHttpUrlOrNull()
            ?: throw PlaylistException(tx("Geçersiz M3U adresi.", "Invalid M3U address."))
        val request = Request.Builder().url(url).header("User-Agent", userAgentFor(source)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw PlaylistException(tx("Liste indirilemedi: HTTP ", "Playlist download failed: HTTP ") + response.code)
            val body = response.body ?: throw PlaylistException(tx("Liste boş.", "The playlist is empty."))
            val reader = BufferedReader(body.charStream(), 64 * 1024)
            reader.mark(8 * 1024)
            val firstLine = generateSequence { reader.readLine() }.firstOrNull { it.isNotBlank() }.orEmpty()
            reader.reset()
            if (firstLine.trimStart().startsWith("<")) {
                throw PlaylistException(tx("Adres bir M3U listesi yerine web sayfası döndürdü.", "The address returned a web page instead of an M3U playlist."))
            }
            val result = M3uParser.parsePlaylist(reader)
            if (result.channels.isEmpty()) throw PlaylistException(tx("Listede oynatılabilir kanal bulunamadı.", "No playable channels in the playlist."))
            result
        }
    }
}
