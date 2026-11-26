package io.github.ta3hrj.elm.desktop.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `what the user chose survives a restart`() {
        val dir = tmp.newFolder("elm")
        val data = AppData(
            playlists = listOf(SavedPlaylist(1, "Ev", SavedPlaylist.TYPE_XTREAM, server = "http://h:8080", username = "u", password = "p")),
            settings = DesktopSettings(language = "tr", activePlaylistId = 1, volume = 55),
            favorites = mapOf(1L to listOf("http://a", "http://b")),
            resume = mapOf("http://film" to 90_000L),
        )
        Storage(dir).save(data)
        assertEquals(data, Storage(dir).load())
        assertTrue(File(dir, "elm-iptv.json").isFile)
        assertTrue("no temporary file left", dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `a damaged file starts the app empty and is kept aside`() {
        val dir = tmp.newFolder("elm")
        File(dir, "elm-iptv.json").writeText("{ not json")
        assertEquals(AppData(), Storage(dir).load())
        assertTrue(File(dir, "elm-iptv.broken.json").isFile)
    }

    @Test
    fun `caches are compressed and read back, and can be cleared`() {
        val storage = Storage(tmp.newFolder("elm"))
        storage.writeCache("p1-x.json.gz", ListSerializer(String.serializer()), listOf("a", "ç", "ğ"))
        assertEquals(listOf("a", "ç", "ğ"), storage.readCache("p1-x.json.gz", ListSerializer(String.serializer())))
        storage.deleteCaches("p1-")
        assertNull(storage.readCache("p1-x.json.gz", ListSerializer(String.serializer())))
    }

    @Test
    fun `newer versions may add fields the older file does not have`() {
        val dir = tmp.newFolder("elm")
        File(dir, "elm-iptv.json").writeText("""{"settings":{"language":"en","futureOption":true},"unknown":1}""")
        assertEquals("en", Storage(dir).load().settings.language)
    }

    @Test
    fun `the data folder follows each system's convention`() {
        val home = tmp.root.path
        val windows = System.getProperty("os.name").startsWith("Windows")
        val dir = Storage.defaultDir(env = { if (it == "APPDATA") "C:\\Users\\x\\AppData\\Roaming" else null }, home = home)
        if (windows) assertEquals(File("C:\\Users\\x\\AppData\\Roaming", "ELM IPTV"), dir) else assertEquals(File(home, ".config/elm-iptv"), dir)
    }

    @Test
    fun `playlists become the shared core sources`() {
        val x = SavedPlaylist(1, "X", SavedPlaylist.TYPE_XTREAM, server = "s", username = "u", password = "p", userAgent = " ", liveFormat = "m3u8").source()
        assertEquals(io.github.ta3hrj.elm.core.PlaylistSource.Xtream("s", "u", "p", null, "m3u8"), x)
        val m = SavedPlaylist(2, "M", SavedPlaylist.TYPE_M3U, url = "http://list").source()
        assertEquals(io.github.ta3hrj.elm.core.PlaylistSource.M3u("http://list", null), m)
    }
}
