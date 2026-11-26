package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom

/**
 * Moves backups between the TV and a phone or computer on the same home network, for devices without a
 * file picker (Fire TV): while the Backup screen is open, a small web page is served at the address shown
 * on screen. It can upload a backup file (restore) or download the current backup.
 *
 * Every request must carry the random key that is part of the address, so other devices on the network
 * cannot read the backup (it contains logins). The server stops when the screen is left.
 */
class BackupTransfer(private val context: android.content.Context, private val backup: Backup, private val scope: CoroutineScope) {
    private var server: ServerSocket? = null
    private var job: Job? = null
    private var key = ""
    private var limiter = AttemptLimiter()
    private val connections = java.util.concurrent.Semaphore(MAX_CONNECTIONS)

    /** Called after a backup was restored from the web page, with the result text. */
    var onRestored: ((String) -> Unit)? = null

    /** Starts serving and returns the address to open, or null when the TV has no network address. */
    fun start(): String? {
        stop()
        val ip = localAddress() ?: return null
        val socket = PORTS.firstNotNullOfOrNull { port ->
            runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(port)) } }.getOrNull()
        } ?: runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = socket
        key = randomKey()
        limiter = AttemptLimiter()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                // Too many connections at once (a flood on the home network): close this one at once.
                if (!connections.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                launch(Dispatchers.IO) {
                    // A browser may drop or reset its connection at any point (closed tab, preconnect,
                    // flaky Wi-Fi); that only ends this request, never the app.
                    try {
                        client.use { handle(it) }
                    } catch (e: java.io.IOException) {
                        // Connection ended by the other side.
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        CrashLog.record(context, e)
                    } finally {
                        connections.release()
                    }
                }
            }
        }
        return "http://${ip}:${socket.localPort}/?k=$key"
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { server?.close() }
        server = null
    }

    private suspend fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: return
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2) return
        val method = parts[0]
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').associate { pair ->
            val k = pair.substringBefore('=')
            val v = runCatching { URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8") }.getOrElse { "" }
            k to v
        }
        val out = socket.getOutputStream()
        fun respond(code: Int, type: String, body: ByteArray, extra: String = "") {
            val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; 413 -> "Payload Too Large"; 429 -> "Too Many Requests"; else -> "Bad Request" }
            out.write(
                ("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
                    "Cache-Control: no-store\r\nConnection: close\r\n" +
                    // The page needs only its own inline script and style and talks only to this TV.
                    "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\n" +
                    "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'\r\n" +
                    "$extra\r\n").toByteArray(Charsets.ISO_8859_1),
            )
            out.write(body)
            out.flush()
        }
        if (!limiter.allowed()) {
            respond(429, "text/plain; charset=utf-8", tx("Çok fazla yanlış deneme. Bir dakika sonra tekrar deneyin.", "Too many wrong tries. Try again in a minute.").toByteArray())
            return
        }
        // Compared without stopping at the first different character.
        val given = query["k"].orEmpty().toByteArray()
        if (!java.security.MessageDigest.isEqual(given, key.toByteArray())) {
            limiter.failed()
            respond(403, "text/plain; charset=utf-8", tx("Geçersiz anahtar. TV ekranındaki adresi kullanın.", "Invalid key. Use the address shown on the TV.").toByteArray())
            return
        }
        limiter.succeeded()
        when {
            method == "GET" && path == "/" -> respond(200, "text/html; charset=utf-8", page().toByteArray())
            method == "GET" && path == "/backup.json" -> {
                val json = backup.exportJson().toByteArray()
                respond(200, "application/json; charset=utf-8", json, "Content-Disposition: attachment; filename=\"elm-iptv-backup.json\"\r\n")
            }
            method == "GET" && path == "/errors.txt" -> {
                val text = CrashLog.entries(context).joinToString("\n\n" + "=".repeat(60) + "\n\n") { runCatching { io.github.ta3hrj.elm.core.Redact.text(it.file.readText()) }.getOrElse { "" } }
                    .ifEmpty { tx("Kayıtlı hata yok.", "No errors recorded.") }
                respond(200, "text/plain; charset=utf-8", text.toByteArray(), "Content-Disposition: attachment; filename=\"elm-iptv-errors.txt\"\r\n")
            }
            method == "GET" && path == "/memory.txt" -> {
                val text = MemoryLog.text(context).ifEmpty { tx("Henüz bellek kaydı yok.", "No memory samples yet.") }
                respond(200, "text/plain; charset=utf-8", text.toByteArray(), "Content-Disposition: attachment; filename=\"elm-iptv-memory.txt\"\r\n")
            }
            method == "POST" && path == "/restore" -> {
                val length = headers["content-length"]?.toIntOrNull() ?: -1
                if (length !in 1..MAX_UPLOAD) {
                    respond(413, "text/plain; charset=utf-8", tx("Dosya boş ya da çok büyük.", "The file is empty or too large.").toByteArray())
                    return
                }
                val body = readExactly(input, length) ?: return
                val password = headers["x-backup-password"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.ifEmpty { null }
                val result = runCatching { backup.restoreText(body.decodeToString(), password) }
                    .fold(
                        { it },
                        {
                            if (it is BackupPasswordNeeded) {
                                tx("Bu yedek parolalı: parolayı yazıp yeniden gönderin.", "This backup has a password: enter it and send again.")
                            } else {
                                tx("Geri yüklenemedi: ", "Restore failed: ") + (it.message ?: it.javaClass.simpleName)
                            }
                        },
                    )
                onRestored?.invoke(result)
                respond(200, "text/plain; charset=utf-8", result.toByteArray())
            }
            else -> respond(404, "text/plain; charset=utf-8", "Not found".toByteArray())
        }
    }

    private fun page(): String {
        val title = tx("ELM IPTV yedek aktarımı", "ELM IPTV backup transfer")
        val upload = tx("Yedeği TV'ye yükle (geri yükle)", "Send a backup to the TV (restore)")
        val choose = tx("Yedek dosyasını seçin (.json)", "Choose the backup file (.json)")
        val send = tx("Gönder", "Send")
        val passwordLabel = tx("Yedek parolası (varsa)", "Backup password (if any)")
        val download = tx("TV'deki güncel yedeği indir", "Download the TV's current backup")
        val errors = tx("Hata günlüğü (sorun bildirmek için)", "Error log (for reporting a problem)")
        val note = tx(
            "Yedek, listelerin kullanıcı adı ve şifrelerini içerir. Dosyayı kimseyle paylaşmayın.",
            "A backup contains the playlists' user names and passwords. Do not share the file.",
        )
        val sending = tx("Gönderiliyor…", "Sending…")
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title><style>
body{font-family:system-ui,sans-serif;background:#0E1116;color:#E8ECF2;margin:0;padding:20px;max-width:560px}
h1{font-size:20px}h2{font-size:16px;color:#7FB8FF;margin-top:28px}
.box{background:#161B22;border-radius:12px;padding:16px}
button,a.btn{display:inline-block;background:#4FA3FF;color:#0E1116;border:0;border-radius:8px;padding:10px 18px;font-size:15px;text-decoration:none;margin-top:12px}
#r{margin-top:12px;white-space:pre-wrap}.note{color:#FFD18A;font-size:13px}
</style></head><body><h1>$title</h1><p class="note">$note</p>
<h2>$upload</h2><div class="box"><label>$choose<br><input type="file" id="f" accept=".json,application/json,text/plain"></label><br>
<label>$passwordLabel<br><input type="text" id="pw" autocomplete="off"></label><br>
<button id="s">$send</button><div id="r"></div></div>
<h2>$download</h2><div class="box"><a class="btn" href="/backup.json?k=$key">backup.json</a></div>
<h2>$errors</h2><div class="box"><a class="btn" href="/errors.txt?k=$key">errors.txt</a> <a class="btn" href="/memory.txt?k=$key">memory.txt</a></div>
<script>
document.getElementById('s').onclick=async()=>{const f=document.getElementById('f').files[0];const r=document.getElementById('r');
if(!f){return}r.textContent='$sending';try{const t=await f.text();
const x=await fetch('/restore?k=$key',{method:'POST',headers:{'Content-Type':'application/json','X-Backup-Password':encodeURIComponent(document.getElementById('pw').value)},body:t});r.textContent=await x.text()}catch(e){r.textContent=String(e)}}
</script></body></html>"""
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
            if (buffer.size() > 8192) return null
        }
        return buffer.toString("ISO-8859-1")
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) return null
            read += n
        }
        return data
    }

    private fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .sortedByDescending { it.isSiteLocalAddress }
            .firstOrNull()?.hostAddress
    }.getOrNull()

    private fun randomKey(): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        // 8 characters of 31 (about 40 bits); with the lock-out below, guessing it is not practical.
        return (1..KEY_LENGTH).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private companion object {
        val PORTS = 8090..8099
        const val MAX_UPLOAD = 5 * 1024 * 1024
        const val KEY_LENGTH = 8
        /** At most this many requests are served at the same time; more are dropped (nobody needs more). */
        const val MAX_CONNECTIONS = 6
    }
}
