package io.github.ta3hrj.elm.ui

import io.github.ta3hrj.elm.core.tx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.data.PlaylistEntity
import io.github.ta3hrj.elm.data.PlaylistType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Muted = Color(0xFF9AA4B2)

@Composable
fun AddPlaylistScreen(
    viewModel: MainViewModel,
    canCancel: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    /** An existing playlist to change; null adds a new one. */
    editId: Long? = null,
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var type by rememberSaveable { mutableStateOf(PlaylistType.XTREAM) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var userAgent by rememberSaveable { mutableStateOf("") }
    var epgUrl by rememberSaveable { mutableStateOf("") }
    var hls by rememberSaveable { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val editing = editId?.let { id -> playlists.orEmpty().firstOrNull { it.id == id } }
    var filled by rememberSaveable(editId) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        val p = editing ?: return@LaunchedEffect
        if (filled) return@LaunchedEffect
        filled = true
        type = p.type
        name = p.name
        url = p.url
        username = p.username.orEmpty()
        password = p.password.orEmpty()
        userAgent = p.userAgent.orEmpty()
        epgUrl = p.epgUrl.orEmpty()
        hls = p.liveFormat == "m3u8"
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val isXtream = type == PlaylistType.XTREAM
    val canSave = !busy && url.isNotBlank() && (!isXtream || (username.isNotBlank() && password.isNotBlank()))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.elm_emblem),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text("ELM IPTV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Every Live Moment", style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (editId != null) tx("Listeyi düzenle", "Edit playlist") else tx("Yeni liste ekle", "Add a playlist"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (editId != null) {
                tx(
                    "Sunucu, kullanıcı adı veya şifre değişirse liste yeniden indirilir; favorileriniz, listeleriniz ve hatırlatıcılarınız korunur.",
                    "If the server, user name or password changes, the playlist is downloaded again; your favorites, lists and reminders are kept.",
                )
            } else {
                tx(
                    "IPTV sağlayıcınızın verdiği Xtream Codes bilgilerini ya da M3U bağlantısını girin. ELM IPTV içerik sağlamaz; kanallar sizin sağlayıcınızdan gelir.",
                    "Enter the Xtream Codes details or the M3U link from your IPTV provider. ELM IPTV provides no content; the channels come from your provider.",
                )
            },
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton("Xtream Codes", selected = isXtream, modifier = Modifier.focusRequester(firstFocus)) {
                type = PlaylistType.XTREAM
            }
            ChoiceButton(tx("M3U bağlantısı", "M3U link"), selected = !isXtream) { type = PlaylistType.M3U }
        }
        Spacer(Modifier.height(16.dp))

        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField(tx("Liste adı (isteğe bağlı)", "Playlist name (optional)"), name, { name = it })
            if (isXtream) {
                FormField(tx("Sunucu adresi (ör. http://sunucu.com:8080)", "Server address (e.g. http://server.com:8080)"), url, { url = it }, keyboardType = KeyboardType.Uri)
                FormField(tx("Kullanıcı adı", "Username"), username, { username = it })
                FormField(tx("Şifre", "Password"), password, { password = it }, isPassword = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tx("Yayın biçimi:", "Stream format:"), color = Muted)
                    ChoiceButton(tx("MPEG-TS (önerilen)", "MPEG-TS (recommended)"), selected = !hls) { hls = false }
                    ChoiceButton("HLS (m3u8)", selected = hls) { hls = true }
                }
            } else {
                FormField(tx("M3U adresi (http://…)", "M3U address (http://…)"), url, { url = it }, keyboardType = KeyboardType.Uri)
            }
            FormField(tx("User-Agent (isteğe bağlı, sağlayıcı isterse)", "User-Agent (optional, if your provider requires one)"), userAgent, { userAgent = it })
            FormField(
                tx("TV rehberi adresi (XMLTV, isteğe bağlı; boşsa listeden otomatik alınır)", "TV guide address (XMLTV, optional; taken from the playlist if empty)"),
                epgUrl,
                { epgUrl = it },
                keyboardType = KeyboardType.Uri,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = canSave,
                onClick = {
                    val base = editing ?: PlaylistEntity(name = "", type = type, url = "")
                    val playlist = base.copy(
                        name = name.trim().ifEmpty { defaultName(type, url) },
                        type = type,
                        url = url.trim(),
                        username = username.trim().takeIf { isXtream },
                        password = password.trim().takeIf { isXtream },
                        userAgent = userAgent.trim().ifEmpty { null },
                        liveFormat = if (hls) "m3u8" else "ts",
                        epgUrl = epgUrl.trim().ifEmpty { null },
                    )
                    if (editing != null) viewModel.editPlaylist(playlist, onSuccess = onDone) else viewModel.addPlaylist(playlist, onSuccess = onDone)
                },
            ) { Text(if (busy) tx("Bağlanıyor…", "Connecting…") else if (editId != null) tx("Kaydet", "Save") else tx("Kaydet ve yükle", "Save and load")) }
            if (canCancel) OutlinedButton(onClick = onCancel) { Text(tx("Vazgeç", "Cancel")) }
            message?.let { Text(it, color = if (busy) Muted else Color(0xFFFFB4A9)) }
        }
    }
}

@Composable
fun PlaylistsScreen(
    viewModel: MainViewModel,
    onAdd: () -> Unit,
    onSelected: () -> Unit,
    onGuideSettings: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val guideUpdating by viewModel.guideUpdating.collectAsStateWithLifecycle()
    val current by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmDeleteId by remember { mutableLongStateOf(-1L) }
    var accountFor by remember { mutableStateOf<PlaylistEntity?>(null) }
    accountFor?.let { playlist ->
        var rows by remember(playlist.id) { mutableStateOf<List<Pair<String, String>>?>(null) }
        var error by remember(playlist.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(playlist.id) {
            viewModel.accountInfo(playlist).fold(
                { rows = accountRows(it) },
                { error = it.message ?: it.javaClass.simpleName },
            )
        }
        InfoDialog(tx("Hesap bilgisi", "Account details") + " · " + playlist.name, rows, error, onDismiss = { accountFor = null })
    }

    val addFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { addFocus.requestFocus() } }
    LaunchedEffect(confirmDeleteId) {
        if (confirmDeleteId >= 0) {
            delay(4000)
            confirmDeleteId = -1L
        }
    }

    Column(Modifier.fillMaxSize().screenPadding(64.dp, 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tx("Hesaplar ve listeler", "Accounts and playlists"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(16.dp))
            Text(message.orEmpty(), color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Button(onClick = onAdd, modifier = Modifier.focusRequester(addFocus)) { Text(tx("+ Yeni liste", "+ New playlist")) }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(playlists.orEmpty(), key = { it.id }) { playlist ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (playlist.id == current?.id) "● " else "") + playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(describe(playlist), style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text(
                            describeGuide(playlist, updating = playlist.id in guideUpdating),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                        playlist.epgError?.let {
                            Text(tx("Rehber: $it", "Guide: $it"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD18A))
                        }
                        playlist.lastError?.let {
                            Text(tx("Son hata: $it", "Last error: $it"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4A9))
                        }
                    }
                    Button(onClick = { viewModel.selectPlaylist(playlist.id); onSelected() }) { Text(tx("Aç", "Open")) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.refresh(playlist.id) }, enabled = !busy) { Text(tx("Yenile", "Refresh")) }
                    Spacer(Modifier.width(8.dp))
                    if (playlist.type == PlaylistType.XTREAM) {
                        OutlinedButton(onClick = { accountFor = playlist }) { Text(tx("Hesap", "Account")) }
                        Spacer(Modifier.width(8.dp))
                    }
                    OutlinedButton(onClick = { onEdit(playlist.id) }) { Text(tx("Düzenle", "Edit")) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onGuideSettings(playlist.id) }) { Text(tx("Rehber", "Guide")) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (confirmDeleteId == playlist.id) {
                                confirmDeleteId = -1L
                                viewModel.delete(playlist)
                            } else {
                                confirmDeleteId = playlist.id
                            }
                        },
                    ) { Text(if (confirmDeleteId == playlist.id) tx("Emin misiniz? Tekrar basın", "Sure? Press again") else tx("Sil", "Delete")) }
                }
            }
        }
    }
}

@Composable
fun GuideSettingsScreen(
    viewModel: MainViewModel,
    playlistId: Long,
    onDone: () -> Unit,
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val updating by viewModel.guideUpdating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val playlist = playlists.orEmpty().firstOrNull { it.id == playlistId }
    if (playlist == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    var url by rememberSaveable(playlistId) { mutableStateOf(playlist.epgUrl.orEmpty()) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Text(tx("TV rehberi", "TV guide"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(playlist.name, color = Muted)
        Spacer(Modifier.height(16.dp))
        Text(describeGuide(playlist, updating = playlist.id in updating), style = MaterialTheme.typography.bodyMedium)
        playlist.epgError?.let { Text(it, color = Color(0xFFFFD18A), style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(16.dp))

        val auto = playlist.epgAutoUrls?.lines()?.filter { it.isNotBlank() }.orEmpty()
        Text(tx("Listenin bildirdiği rehber adresi:", "Guide address announced by the playlist:"), color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(
            if (auto.isEmpty()) tx("— (liste bir rehber adresi bildirmiyor)", "— (the playlist announces no guide address)") else auto.joinToString("\n") { maskCredentials(it) },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.widthIn(max = 760.dp)) {
            FormField(tx("Kendi XMLTV adresim (boş bırakılırsa yukarıdaki kullanılır)", "My own XMLTV address (empty uses the one above)"), url, { url = it }, keyboardType = KeyboardType.Uri)
            Text(
                tx("Birden fazla adresi virgülle ayırabilirsiniz. .xml ve .xml.gz desteklenir.", "Separate several addresses with commas. .xml and .xml.gz are supported."),
                color = Muted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.saveGuideUrl(playlist.id, url, updateNow = true) },
                enabled = playlist.id !in updating,
                modifier = Modifier.focusRequester(firstFocus),
            ) { Text(if (playlist.id in updating) tx("Güncelleniyor…", "Updating…") else tx("Kaydet ve rehberi güncelle", "Save and update guide")) }
            OutlinedButton(onClick = onDone) { Text(tx("Geri", "Back")) }
            message?.let { Text(it, color = Muted) }
        }

        Spacer(Modifier.height(28.dp))
        Text(tx("Saat kaydırma", "Time offset"), style = MaterialTheme.typography.titleMedium)
        Text(
            tx(
                "Rehberdeki saatler yayından ileri veya geri kaymışsa düzeltin. Değişiklik rehberi yeniden indirir.",
                "Fix guide times that run ahead of or behind the broadcast. Changing it downloads the guide again.",
            ),
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        var offset by rememberSaveable(playlistId) { mutableIntStateOf(viewModel.epgOffsetMinutes(playlistId)) }
        val limit = io.github.ta3hrj.elm.data.AppSettings.EPG_OFFSET_LIMIT_MINUTES
        val change = { delta: Int ->
            offset = (offset + delta).coerceIn(-limit, limit)
            viewModel.setEpgOffsetMinutes(playlistId, offset)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { change(-30) }, enabled = offset > -limit) { Text(tx("− 30 dk", "− 30 min")) }
            Text(formatOffset(offset), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 120.dp))
            OutlinedButton(onClick = { change(+30) }, enabled = offset < limit) { Text(tx("+ 30 dk", "+ 30 min")) }
            if (offset != 0) OutlinedButton(onClick = { change(-offset) }) { Text(tx("Sıfırla", "Reset")) }
        }
    }
}

private fun formatOffset(minutes: Int): String {
    if (minutes == 0) return tx("Kaydırma yok", "No offset")
    val sign = if (minutes > 0) "+" else "−"
    val abs = kotlin.math.abs(minutes)
    val h = abs / 60
    val m = abs % 60
    return sign + when {
        h > 0 && m > 0 -> tx("$h sa $m dk", "$h h $m min")
        h > 0 -> tx("$h sa", "$h h")
        else -> tx("$m dk", "$m min")
    }
}

/** Hides logins in guide links on screen: query parameters, `user:pass@` and Xtream-style paths. */
internal fun maskCredentials(url: String): String = io.github.ta3hrj.elm.core.Redact.text(url)

private fun describeGuide(playlist: PlaylistEntity, updating: Boolean): String = when {
    updating -> tx("Rehber: güncelleniyor…", "Guide: updating…")
    playlist.epgUpdatedAt == 0L && playlist.guideUrls().isEmpty() -> tx("Rehber: adres yok", "Guide: no address")
    playlist.epgUpdatedAt == 0L -> tx("Rehber: henüz indirilmedi", "Guide: not downloaded yet")
    else -> tx("Rehber: ${playlist.epgLinkedChannels}/${playlist.channelCount} kanal eşleşti · ${playlist.programmeCount} program · ", "Guide: ${playlist.epgLinkedChannels}/${playlist.channelCount} channels matched · ${playlist.programmeCount} programmes · ") +
        SimpleDateFormat("dd.MM.yyyy HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(playlist.epgUpdatedAt))
}



@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val focusManager = LocalFocusManager.current
    TvTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isPassword = isPassword,
        keyboardType = keyboardType,
        imeAction = ImeAction.Next,
        autoCorrect = false,
        // "Next" on the keyboard selects the next field; OK there opens the keyboard again.
        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun defaultName(type: String, url: String): String {
    val host = url.trim().substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { tx("liste", "playlist") }
    return if (type == PlaylistType.XTREAM) "Xtream – $host" else "M3U – $host"
}

private fun describe(playlist: PlaylistEntity): String {
    val parts = mutableListOf(
        if (playlist.type == PlaylistType.XTREAM) "Xtream Codes" else "M3U",
        tx("${playlist.channelCount} kanal", "${playlist.channelCount} channels"),
    )
    if (playlist.updatedAt > 0) {
        parts += tx("güncelleme ", "updated ") + SimpleDateFormat("dd.MM.yyyy HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(playlist.updatedAt))
    }
    playlist.expiresAt?.takeIf { it > 0 }?.let {
        parts += tx("bitiş ", "ends ") + SimpleDateFormat("dd.MM.yyyy", io.github.ta3hrj.elm.core.Lang.locale).format(Date(it * 1000))
    }
    return parts.joinToString("  ·  ")
}

private fun accountRows(a: io.github.ta3hrj.elm.core.XtreamAccount): List<Pair<String, String>> {
    val date = SimpleDateFormat("d MMMM yyyy", io.github.ta3hrj.elm.core.Lang.locale)
    val active = a.status?.equals("Active", ignoreCase = true) == true
    return listOfNotNull(
        tx("Kullanıcı", "User") to a.username,
        tx("Durum", "Status") to when {
            active && a.trial -> tx("● Aktif (deneme)", "● Active (trial)")
            active -> tx("● Aktif", "● Active")
            a.status.isNullOrBlank() -> "—"
            else -> "● " + a.status
        },
        tx("Bitiş", "Expires") to (a.expiresAt?.let { date.format(Date(it * 1000)) } ?: tx("Sınırsız", "Unlimited")),
        a.createdAt?.let { tx("Oluşturulma", "Created") to date.format(Date(it * 1000)) },
        a.maxConnections?.let {
            tx("En fazla bağlantı", "Max connections") to (it.toString() + (a.activeConnections?.let { n -> tx(" (şu an $n kullanımda)", " ($n in use now)") } ?: "") +
                if (it <= 1) tx(" · çoklu ekranda yalnızca 1 pencere açılır", " · multi-view can open only 1 window") else "")
        },
        a.message?.let { tx("Sağlayıcı mesajı", "Provider message") to it },
        a.serverTimezone?.let { tx("Sunucu saat dilimi", "Server time zone") to it },
    )
}
