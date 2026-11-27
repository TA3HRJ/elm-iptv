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
        fun js(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")
        val lang = tx("tr", "en")
        val title = tx("ELM IPTV · Yedek aktarımı", "ELM IPTV · Backup transfer")
        val subtitle = tx("Telefondan yedek aktarımı", "Backup transfer from your phone")
        val intro = tx(
            "Bu sayfa televizyonunuzdaki ELM IPTV'ye bağlı. TV'de Yedekleme ekranı açık kaldığı sürece çalışır.",
            "This page is connected to ELM IPTV on your TV. It works for as long as the Backup screen stays open on the TV.",
        )
        val upload = tx("Yedeği TV'ye gönder", "Send a backup to the TV")
        val uploadHint = tx(
            "Telefonunuzdaki yedek dosyasını seçin. TV listeleri, favorileri ve ayarları bu dosyadan geri yükler.",
            "Pick the backup file on your phone. The TV restores playlists, favourites and settings from it.",
        )
        val choose = tx("Yedek dosyası (.json)", "Backup file (.json)")
        val passwordLabel = tx("Parola", "Password")
        val passwordHint = tx("Yedeği parolayla aldıysanız", "Only if the backup has one")
        val send = tx("TV'ye gönder", "Send to TV")
        val download = tx("TV'deki yedeği telefona alın", "Save the TV's backup to your phone")
        val downloadHint = tx(
            "TV'nin şu anki yedeğini indirip saklayın. Yeni bir cihaza geçerken işinize yarar.",
            "Download the TV's current backup and keep it. It comes in handy when you move to a new device.",
        )
        val downloadButton = tx("Yedeği indir", "Download backup")
        val errors = tx("Sorun mu var?", "Something wrong?")
        val errorsHint = tx(
            "Bir sorunu bildirirken bu iki dosyayı da gönderin:",
            "When you report a problem, please attach these two files:",
        )
        val note = tx(
            "Yedek dosyasında IPTV hesaplarınızın kullanıcı adı ve şifresi bulunur. Dosyayı kimseyle paylaşmayın.",
            "The backup file holds the user names and passwords of your IPTV accounts. Don't share it with anyone.",
        )
        val pick = js(tx("Önce yedek dosyasını seçin.", "Pick the backup file first."))
        val sending = js(tx("Gönderiliyor…", "Sending…"))
        val offline = js(tx("TV'ye ulaşılamadı. Yedekleme ekranı hâlâ açık mı?", "Couldn't reach the TV. Is the Backup screen still open?"))
        return """<!doctype html><html lang="$lang"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0a0e16"><title>$title</title><style>
*{box-sizing:border-box}
body{margin:0;background:radial-gradient(600px 320px at 85% -40px,rgba(59,124,255,.22),transparent 70%),#0a0e16;color:#eef2f8;font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
main{max-width:560px;margin:0 auto;padding:24px 16px 40px}
.top{display:flex;align-items:center;gap:12px;margin-bottom:14px}
.top b{display:block;font-size:19px}.top span{display:block;color:#a2aec2;font-size:14px}
.intro{color:#a2aec2;margin:0 0 22px}
.box{background:#131b28;border:1px solid #21304a;border-radius:16px;padding:18px;margin-bottom:14px}
h2{font-size:17px;margin:0 0 4px}
.hint{color:#a2aec2;font-size:14.5px;margin:0 0 14px}
label{display:block;font-size:14px;color:#a2aec2;margin:12px 0 6px}
input{width:100%;font:inherit;color:#eef2f8;background:#0a0e16;border:1px solid #21304a;border-radius:10px;padding:10px 12px}
input:focus{outline:none;border-color:#44d0ff}
input[type=file]{padding:8px}
input::file-selector-button{font:inherit;font-size:14px;color:#eef2f8;background:#172133;border:1px solid #2c3e5e;border-radius:8px;padding:6px 12px;margin-right:10px}
.btn{display:inline-flex;align-items:center;justify-content:center;font:inherit;font-weight:600;border-radius:999px;padding:11px 22px;text-decoration:none;border:1px solid #2c3e5e;color:#eef2f8;background:#172133;margin:4px 8px 0 0;cursor:pointer}
.btn.primary{background:linear-gradient(100deg,#3b7cff,#44d0ff);border-color:transparent;color:#06101f;margin-top:16px}
#r{margin-top:14px;white-space:pre-wrap;color:#eef2f8}#r:empty{display:none}
.note{border:1px solid rgba(255,90,106,.35);background:rgba(255,90,106,.08);border-radius:12px;padding:12px 14px;color:#ffc9ce;font-size:14px;margin-top:18px}
</style></head><body><main>
<div class="top"><svg width="40" height="40" viewBox="0 0 48 48" aria-hidden="true"><defs><linearGradient id="g" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#3b7cff"/><stop offset="1" stop-color="#44d0ff"/></linearGradient></defs><path d="M39 12A19 19 0 1 0 43 24" fill="none" stroke="url(#g)" stroke-width="4.5" stroke-linecap="round"/><circle cx="24" cy="24" r="12" fill="url(#g)"/><path d="M21 18.5v11l9-5.5z" fill="#fff"/><circle cx="37.5" cy="8.5" r="3.2" fill="#ff5a6a"/></svg>
<div><b>ELM IPTV</b><span>$subtitle</span></div></div>
<p class="intro">$intro</p>
<div class="box"><h2>$upload</h2><p class="hint">$uploadHint</p>
<label for="f">$choose</label><input type="file" id="f" accept=".json,application/json,text/plain">
<label for="pw">$passwordLabel</label><input type="password" id="pw" autocomplete="off" placeholder="$passwordHint">
<button class="btn primary" id="s">$send</button><div id="r"></div></div>
<div class="box"><h2>$download</h2><p class="hint">$downloadHint</p><a class="btn" href="/backup.json?k=$key">$downloadButton</a></div>
<div class="box"><h2>$errors</h2><p class="hint">$errorsHint</p><a class="btn" href="/errors.txt?k=$key">errors.txt</a><a class="btn" href="/memory.txt?k=$key">memory.txt</a></div>
<p class="note">$note</p>
</main><script>
document.getElementById('s').onclick=async()=>{const f=document.getElementById('f').files[0];const r=document.getElementById('r');
if(!f){r.textContent='$pick';return}r.textContent='$sending';try{const t=await f.text();
const x=await fetch('/restore?k=$key',{method:'POST',headers:{'Content-Type':'application/json','X-Backup-Password':encodeURIComponent(document.getElementById('pw').value)},body:t});r.textContent=await x.text()}catch(e){r.textContent='$offline'}}
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
