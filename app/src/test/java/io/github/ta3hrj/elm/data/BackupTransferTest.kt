package io.github.ta3hrj.elm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** The home-network page must survive browsers that drop or reset their connection (0.9.12 crash). */
@RunWith(RobolectricTestRunner::class)
class BackupTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val errors = CopyOnWriteArrayList<Throwable>()
    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var transfer: BackupTransfer
    private var port = 0
    private var key = ""

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> errors += e })
        transfer = BackupTransfer(context, Backup(context, db, OkHttpClient()), scope)
        val url = transfer.start()
        assumeNotNull(url) // no network interface on this machine
        port = Regex(":(\\d+)/").find(url!!)!!.groupValues[1].toInt()
        key = url.substringAfter("k=")
    }

    @After
    fun tearDown() {
        transfer.stop()
        scope.cancel()
        db.close()
    }

    private fun get(path: String): String = Socket().use { s ->
        s.connect(InetSocketAddress("127.0.0.1", port), 3_000)
        s.soTimeout = 5_000
        s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: tv\r\n\r\n".toByteArray())
        s.getInputStream().readBytes().decodeToString()
    }

    /** Sends half a request, then resets the connection (RST), as a closing browser tab can. */
    private fun resetMidRequest() = Socket().use { s ->
        s.connect(InetSocketAddress("127.0.0.1", port), 3_000)
        s.getOutputStream().write("GET /?k=$key HTTP/1.1\r\nHo".toByteArray())
        s.getOutputStream().flush()
        Thread.sleep(100)
        s.setSoLinger(true, 0)
    }

    /** Opens a connection and resets it at once, without sending anything (a dropped preconnect). */
    private fun resetSilently() = Socket().use { s ->
        s.connect(InetSocketAddress("127.0.0.1", port), 3_000)
        Thread.sleep(150)
        s.setSoLinger(true, 0)
    }

    @Test
    fun resetConnectionsDoNotCrashAndTheServerKeepsAnswering() {
        repeat(3) { resetMidRequest() }
        repeat(3) { resetSilently() }
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 3_000) } // connect and close without a word
        Thread.sleep(500)
        assertTrue(get("/?k=$key").startsWith("HTTP/1.1 200"))
        assertTrue(get("/errors.txt?k=$key").startsWith("HTTP/1.1 200"))
        assertTrue(get("/memory.txt?k=$key").startsWith("HTTP/1.1 200"))
        assertTrue(get("/memory.txt?k=wrong").startsWith("HTTP/1.1 403"))
        assertTrue(get("/?k=wrong").startsWith("HTTP/1.1 403"))
        assertEquals(emptyList<Throwable>(), errors.toList())
    }

    @Test
    fun tooManyWrongKeysLockEveryoneOutForAWhile() {
        repeat(10) { assertTrue(get("/?k=wrong$it").startsWith("HTTP/1.1 403")) }
        // The right key is refused too until the lock runs out.
        assertTrue(get("/?k=$key").startsWith("HTTP/1.1 429"))
    }

    @Test
    fun answersCarrySecurityHeaders() {
        val response = get("/?k=$key")
        assertTrue(response.contains("X-Content-Type-Options: nosniff"))
        assertTrue(response.contains("Content-Security-Policy: default-src 'none'"))
        assertTrue(response.contains("Referrer-Policy: no-referrer"))
    }

    @Test
    fun theKeyIsEightCharacters() {
        assertEquals(8, key.length)
    }
}
