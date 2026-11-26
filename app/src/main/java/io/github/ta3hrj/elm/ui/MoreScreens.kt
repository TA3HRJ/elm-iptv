package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import io.github.ta3hrj.elm.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import io.github.ta3hrj.elm.ui.touch.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.ta3hrj.elm.BuildConfig
import io.github.ta3hrj.elm.core.Support
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.RecordingEntity
import io.github.ta3hrj.elm.data.RecordingStatus
import io.github.ta3hrj.elm.data.VodItemEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private val Muted2 = Color(0xFFB8C2D0)

// ---- Recordings --------------------------------------------------------------------------------

@Composable
fun RecordingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val rules by viewModel.recordingRules.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().screenPadding(56.dp, 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx("Kayıtlar", "Recordings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(viewModel.recordingStorage(), style = MaterialTheme.typography.bodySmall, color = Muted2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.focusRequester(first)) { Text(tx("Geri", "Back")) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tx(
                "Kayıt başlatmak için: oynatırken Menü (☰) → Kaydet, ya da TV Rehberi'nde programın üzerinde Menü (☰). Her kayıt en fazla 3 saat. " +
                    "Rehberde \"Dizi kaydı\" seçilirse aynı kanaldaki tüm bölümler, rehber güncellendikçe kendiliğinden planlanır.",
                "To record: while watching press Menu (☰) → Record, or press Menu (☰) on a programme in the TV Guide. Up to 3 hours per recording. " +
                    "With \"Record series\" in the guide, every episode on that channel is planned automatically as the guide updates.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Muted2,
        )
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF7FB8FF)) }
        Spacer(Modifier.height(12.dp))
        if (recordings.isEmpty() && rules.isEmpty()) {
            Text(tx("Henüz kayıt yok.", "No recordings yet."), color = Muted2)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (rules.isNotEmpty()) {
                item { Text(tx("Dizi kayıtları", "Series recordings"), style = MaterialTheme.typography.titleSmall, color = Color(0xFF7FB8FF)) }
                items(rules, key = { "rule${it.id}" }) { rule ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🔁 " + rule.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rule.channelName + "  ·  " + tx("tüm bölümler", "all episodes"), style = MaterialTheme.typography.bodySmall, color = Muted2)
                        }
                        OutlinedButton(onClick = { viewModel.deleteRecordingRule(rule.id) }) { Text(tx("Kapat", "Turn off")) }
                    }
                }
                if (recordings.isNotEmpty()) {
                    item { Text(tx("Kayıtlar", "Recordings"), style = MaterialTheme.typography.titleSmall, color = Color(0xFF7FB8FF)) }
                }
            }
            items(recordings, key = { it.id }) { RecordingRow(it, viewModel) }
        }
    }
}

@Composable
private fun RecordingRow(recording: RecordingEntity, viewModel: MainViewModel) {
    val date = SimpleDateFormat("d MMM HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(Date(recording.start))
    val status = when (recording.status) {
        RecordingStatus.SCHEDULED -> tx("Planlandı", "Scheduled")
        RecordingStatus.RECORDING -> tx("● Kaydediliyor", "● Recording")
        RecordingStatus.DONE -> tx("Tamamlandı", "Done")
        RecordingStatus.CANCELLED -> tx("İptal edildi", "Cancelled")
        else -> tx("Başarısız", "Failed")
    }
    val size = if (recording.sizeBytes > 0) "%.0f MB".format(recording.sizeBytes / (1024.0 * 1024.0)) else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(recording.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(recording.channelName, "$date · ${(recording.stop - recording.start) / 60_000} " + tx("dk", "min"), status, size)
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = Muted2,
            )
            recording.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD18A)) }
        }
        if (recording.status == RecordingStatus.DONE && recording.filePath?.let { File(it).exists() } == true) {
            Button(onClick = { viewModel.playRecording(recording) }) { Text(tx("Oynat", "Play")) }
            Spacer(Modifier.width(8.dp))
        }
        if (recording.status == RecordingStatus.SCHEDULED || recording.status == RecordingStatus.RECORDING) {
            OutlinedButton(onClick = { viewModel.cancelRecording(recording.id) }) {
                Text(if (recording.status == RecordingStatus.RECORDING) tx("Durdur", "Stop") else tx("İptal", "Cancel"))
            }
            Spacer(Modifier.width(8.dp))
        }
        OutlinedButton(onClick = { viewModel.deleteRecording(recording.id) }) { Text(tx("Sil", "Delete")) }
    }
}

// ---- Global search -------------------------------------------------------------------------------

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onPlay: (ChannelEntity, ChannelQuery) -> Unit,
    onOpenVod: (VodItemEntity) -> Unit,
    onBack: () -> Unit,
) {
    val query by viewModel.globalQuery.collectAsStateWithLifecycle()
    val channels by viewModel.searchChannels.collectAsStateWithLifecycle()
    val movies by viewModel.searchMovies.collectAsStateWithLifecycle()
    val series by viewModel.searchSeries.collectAsStateWithLifecycle()
    val programmes by viewModel.searchProgrammes.collectAsStateWithLifecycle()
    val reminderKeys by viewModel.reminderKeys.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    Column(Modifier.fillMaxSize().screenPadding(48.dp, 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tx("Ara", "Search"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
        }
        Spacer(Modifier.height(10.dp))
        TvTextField(
            value = query,
            onValueChange = viewModel::setGlobalQuery,
            placeholder = tx("Kanal, program, film veya dizi ara…", "Search channels, programmes, films or series…"),
            imeAction = ImeAction.Search,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            focusRequester = field,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (query.trim().length >= 2 && channels.isEmpty() && movies.isEmpty() && series.isEmpty() && programmes.isEmpty()) {
            Text(tx("Sonuç yok.", "No results."), color = Muted2)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (programmes.isNotEmpty()) {
                item { SectionLabel(tx("Programlar", "Programmes") + " (${programmes.size})") }
                items(programmes, key = { "p${it.channel.id}|${it.programme.start}" }) { hit ->
                    ProgrammeHitRow(viewModel, hit, reminderKeys, onPlay)
                }
            }
            if (movies.isNotEmpty()) {
                item { SectionLabel(tx("Filmler", "Films") + " (${movies.size})") }
                item { PosterRow(movies, onOpenVod) }
            }
            if (series.isNotEmpty()) {
                item { SectionLabel(tx("Diziler", "Series") + " (${series.size})") }
                item { PosterRow(series, onOpenVod) }
            }
            if (channels.isNotEmpty()) {
                item { SectionLabel(tx("Kanallar", "Channels") + " (${channels.size})") }
                items(channels, key = { "c${it.id}" }) { channel ->
                    val now = nowPlaying[channel.streamUrl]
                    ListItem(
                        selected = false,
                        onClick = { onPlay(channel, ChannelQuery.Group(channel.groupTitle)) },
                        headlineContent = { Text("${channel.number}  ${channel.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(channel.groupTitle, now?.let { "${timeRange(it.start, it.stop)} ${it.title}" }).joinToString("  ·  "),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingContent = { ChannelLogo(channel.name, channel.logo, width = 56.dp, height = 36.dp) },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                        glow = listItemFocusGlow(),
                    )
                }
            }
        }
    }
}

/** A programme found in the guide: OK watches it live, plays it from the archive or sets a reminder. */
@Composable
private fun ProgrammeHitRow(
    viewModel: MainViewModel,
    hit: MainViewModel.ProgrammeHit,
    reminderKeys: Set<String>,
    onPlay: (ChannelEntity, ChannelQuery) -> Unit,
) {
    val now = System.currentTimeMillis()
    val programme = hit.programme
    val channel = hit.channel
    val airing = now in programme.start until programme.stop
    val upcoming = programme.start > now
    val archived = !airing && !upcoming && viewModel.isArchived(channel, programme, now)
    val reminded = io.github.ta3hrj.elm.reminders.Reminders.key(channel.streamUrl, programme.start) in reminderKeys
    ListItem(
        selected = false,
        enabled = airing || upcoming || archived,
        onClick = {
            when {
                airing -> onPlay(channel, ChannelQuery.Group(channel.groupTitle))
                upcoming -> viewModel.toggleReminder(channel, programme)
                archived -> viewModel.playArchive(channel, programme)
            }
        },
        headlineContent = { Text(programme.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                "${dayLabel(programme.start, now)} ${timeRange(programme.start, programme.stop)}  ·  ${channel.name}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = { ChannelLogo(channel.name, channel.logo, width = 56.dp, height = 36.dp) },
        trailingContent = {
            Text(
                when {
                    airing -> tx("● Şimdi · OK: izle", "● Now · OK: watch")
                    upcoming && reminded -> tx("⏰ Hatırlatıcı kurulu", "⏰ Reminder set")
                    upcoming -> tx("OK: hatırlatıcı kur", "OK: set reminder")
                    archived -> tx("⟲ OK: arşivden izle", "⟲ OK: watch from archive")
                    else -> tx("Yayınlandı", "Aired")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (airing) Color(0xFFFF6B6B) else Color(0xFF9AA4B2),
            )
        },
        scale = listItemFocusScale(),
        colors = listItemFocusColors(),
        border = listItemFocusBorder(),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF7FB8FF), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun PosterRow(items: List<VodItemEntity>, onOpen: (VodItemEntity) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
        items(items, key = { it.id }) { item ->
            Column(Modifier.width(120.dp)) {
                Surface(
                    onClick = { onOpen(item) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                    glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Accent.copy(alpha = 0.55f), 12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A212B), focusedContainerColor = Color(0xFF4FA3FF)),
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                ) {
                    Box(Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F2630))) {
                        Text(item.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center).padding(6.dp))
                        if (!item.poster.isNullOrBlank()) {
                            AsyncImage(model = item.poster, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---- Backup -----------------------------------------------------------------------------------

@Composable
fun BackupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf(viewModel.backupFiles()) }
    var url by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<File?>(null) }
    var confirmUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }
    var askBackupPassword by remember { mutableStateOf(false) }
    val needsPassword by viewModel.restoreNeedsPassword.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    LaunchedEffect(message) { files = viewModel.backupFiles() }
    // System file picker: the way to reach a backup that an earlier installation saved in Downloads
    // (after reinstalling, Android no longer lets the app list those files itself).
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirmUri = uri }
    // Android 9–10: reading Downloads/ELM IPTV needs the storage permission.
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { files = viewModel.backupFiles() }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Fire TV has no system file picker: offer the button only where one exists.
    val hasPicker = remember {
        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .resolveActivity(context.packageManager) != null
    }
    // Phone / computer transfer over the home network, running while this screen is open.
    var transferUrl by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        transferUrl = viewModel.startBackupTransfer()
        onDispose { viewModel.stopBackupTransfer() }
    }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT <= 29 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { permission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) }
        }
    }

    LazyColumn(Modifier.fillMaxSize().screenPadding(64.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Yedekleme ve geri yükleme", "Backup and restore"), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
            }
        }
        item {
            Text(
                tx(
                    "Yedek; listeleri (kullanıcı adı ve şifreleriyle), favorileri, gizli ve kilitli kategorileri, gizli kanalları, PIN'i ve ayarları içerir. Dosyayı güvenli bir yerde saklayın ve kimseyle paylaşmayın.",
                    "A backup contains your playlists (with usernames and passwords), favorites, hidden and locked categories, hidden channels, the PIN and settings. Keep the file safe and do not share it.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFD18A),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.exportBackup() }, modifier = Modifier.focusRequester(first)) { Text(tx("Şimdi yedekle", "Back up now")) }
                OutlinedButton(onClick = { askBackupPassword = true }) { Text(tx("Parolayla yedekle…", "Back up with a password…")) }
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted2) } }
        item { SectionLabel(tx("Bu cihazdaki yedekler", "Backups on this device")) }
        if (files.isEmpty()) {
            item {
                Text(
                    tx(
                        "Yedek bulunamadı. Uygulama kaldırılıp yeniden kurulduysa eski yedek İndirilenler/ELM IPTV klasöründe durur; aşağıdaki adresle telefondan gönderebilir ya da varsa \"Dosya seç\" ile açabilirsiniz.",
                        "No backups found. If the app was reinstalled, the earlier backup is in Downloads/ELM IPTV; send it from a phone with the address below or, where available, open it with \"Choose file\".",
                    ),
                    color = Muted2,
                )
            }
        }
        items(files, key = { it.path }) { file ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Text(file.parent.orEmpty(), style = MaterialTheme.typography.labelSmall, color = Muted2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { confirm = file }) { Text(tx("Geri yükle", "Restore")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { confirmDelete = file }) { Text(tx("Sil", "Delete")) }
            }
        }
        item { SectionLabel(tx("Telefon veya bilgisayardan aktar", "Transfer from a phone or computer")) }
        item {
            Column {
                Text(
                    tx(
                        "Aynı ev ağındaki telefon ya da bilgisayarın tarayıcısında bu adresi açın: yedek dosyasını TV'ye gönderebilir (geri yükleme) veya TV'deki yedeği indirebilirsiniz. Adres yalnızca bu ekran açıkken çalışır.",
                        "Open this address in a browser on a phone or computer on the same home network: you can send a backup file to the TV (restore) or download the TV's backup. It only works while this screen is open.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    transferUrl ?: tx("TV ağa bağlı değil.", "The TV is not connected to a network."),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7FB8FF),
                )
            }
        }
        if (hasPicker) item {
            OutlinedButton(onClick = {
                val opened = runCatching { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }.isSuccess
                if (!opened) {
                    viewModel.showMessage(
                        tx(
                            "Bu cihazda dosya seçici yok. Yedeği bir web adresinden yükleyin (aşağıda) ya da adb ile kopyalayın.",
                            "This device has no file picker. Load the backup from a web address (below) or copy it with adb.",
                        ),
                    )
                }
            }) { Text(tx("Dosya seç… (İndirilenler vb.)", "Choose file… (Downloads etc.)")) }
        }
        item { SectionLabel(tx("Adresten geri yükle", "Restore from an address")) }
        item {
            Text(
                tx(
                    "Başka bir cihazdaki yedeği bu cihaza almak için dosyayı bir web adresine koyup buraya yazabilir ya da adb ile yukarıdaki klasöre kopyalayabilirsiniz.",
                    "To bring a backup from another device, put the file at a web address and enter it here, or copy it into the folder above with adb.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Muted2,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://…/elm-iptv-….json",
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = { viewModel.restoreBackup(null, url) }, enabled = url.isNotBlank()) { Text(tx("Yükle", "Load")) }
            }
        }
    }
    if (askBackupPassword) {
        TextInputDialog(
            title = tx("Yedek parolası", "Backup password"),
            subtitle = tx(
                "Yedek bu parolayla şifrelenir; geri yüklerken sorulur. Parolayı unutursanız yedek açılamaz.",
                "The backup is encrypted with this password and asked for when restoring. A forgotten password cannot be recovered.",
            ),
            confirmLabel = tx("Yedekle", "Back up"),
            onSubmit = { viewModel.exportBackup(it) },
            onDismiss = { askBackupPassword = false },
        )
    }
    needsPassword?.let { source ->
        TextInputDialog(
            title = tx("Yedek parolası", "Backup password"),
            subtitle = tx("Bu yedek parolayla korunuyor.", "This backup is protected by a password."),
            confirmLabel = tx("Geri yükle", "Restore"),
            onSubmit = { viewModel.restoreBackup(source.file, source.url, source.uri, password = it) },
            onDismiss = viewModel::cancelRestorePassword,
        )
    }
    confirmDelete?.let { file ->
        OptionsDialog(
            title = tx("Yedek silinsin mi?", "Delete this backup?"),
            subtitle = file.name,
            options = listOf(DialogOption(tx("Sil", "Delete")) { viewModel.deleteBackup(file) }),
            onDismiss = { confirmDelete = null },
        )
    }
    confirmUri?.let { uri ->
        OptionsDialog(
            title = tx("Geri yüklensin mi?", "Restore this backup?"),
            subtitle = tx("Eksik listeler eklenir; mevcut listeler korunur.", "Missing playlists are added; existing ones are kept.") +
                "\n" + (uri.lastPathSegment ?: uri.toString()),
            options = listOf(DialogOption(tx("Geri yükle", "Restore")) { viewModel.restoreBackup(null, null, uri) }),
            onDismiss = { confirmUri = null },
        )
    }
    confirm?.let { file ->
        OptionsDialog(
            title = tx("Geri yüklensin mi?", "Restore this backup?"),
            subtitle = tx("Eksik listeler eklenir; mevcut listeler korunur.", "Missing playlists are added; existing ones are kept.") + "\n" + file.name,
            options = listOf(DialogOption(tx("Geri yükle", "Restore")) { viewModel.restoreBackup(file, null) }),
            onDismiss = { confirm = null },
        )
    }
}

// ---- About ------------------------------------------------------------------------------------

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Each section takes the focus in turn, so the remote's up/down scrolls through the whole text
    // (plain text cannot be focused, and a TV list only scrolls by moving the focus).
    val sections = listOf(
        null to AboutTexts.intro(),
        tx("Desteklenen cihazlar", "Supported devices") to AboutTexts.devices(),
        tx("Geliştirici", "Developer") to AboutTexts.developer(),
        tx("İçerik", "Content") to AboutTexts.content(),
        tx("Gizlilik", "Privacy") to AboutTexts.privacy(),
        tx("Açık kaynak bileşenler", "Open-source components") to AboutTexts.licenses(),
        tx("Marka", "Trademarks") to AboutTexts.trademarks(),
    )
    Row(Modifier.fillMaxSize().screenPadding(64.dp, 28.dp)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.elm_emblem), contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ELM IPTV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Every Live Moment  ·  " + tx("Sürüm ", "Version ") + BuildConfig.VERSION_NAME, color = Muted2)
                    }
                    OutlinedButton(onClick = onBack, modifier = Modifier.focusRequester(first)) { Text(tx("Geri", "Back")) }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
            items(sections.take(3)) { (title, text) -> AboutSection(title, text) }
            item { SupportSection() }
            items(sections.drop(3)) { (title, text) -> AboutSection(title, text) }
            item { Spacer(Modifier.height(24.dp)) }
        }
        Spacer(Modifier.width(8.dp))
        ListScrollbar(listState)
    }
}

/** One About section; focusable (with a faint highlight) so the list can be scrolled with the remote. */
@Composable
private fun AboutSection(title: String?, text: String) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0x221F4E86)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (title != null) SectionLabel(title)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6DCE4))
        }
    }
}

/**
 * Support for the app (GitHub Sponsors), after the developer. A TV usually has no browser, so the link is a QR
 * code for the phone; where a browser exists (phones, tablets, some TVs) choosing the section opens the page.
 */
@Composable
private fun SupportSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val qr = remember { qrImage(Support.URL) }
    Surface(
        onClick = {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Support.URL))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0x221F4E86)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel(tx("Destek ol", "Support ELM IPTV"))
                Text(AboutTexts.support(), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6DCE4))
                Spacer(Modifier.height(6.dp))
                Text(Support.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF7FB8FF))
            }
            Spacer(Modifier.width(16.dp))
            Image(
                qr,
                contentDescription = tx("QR kod: ", "QR code: ") + Support.label,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                modifier = Modifier.size(132.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

/** Texts of the About screen, kept together so they are easy to edit. */
object AboutTexts {
    fun intro() = tx(
        "ELM IPTV, Fire TV ve Android TV için kumandayla rahat kullanılan bir IPTV oynatıcıdır. Xtream Codes ve M3U listelerini, TV rehberini, film ve dizileri, geriye dönük izlemeyi ve kaydı destekler.",
        "ELM IPTV is an IPTV player for Fire TV and Android TV, built for the remote control. It supports Xtream Codes and M3U playlists, the TV guide, films and series, catch-up and recording.",
    )

    fun devices() = tx(
        "Çalışır: Fire OS 6, 7 veya 8 kullanan Fire TV cihazları (Fire TV Stick 2. nesil ve sonrası, Stick Lite, Stick 4K, 4K Max, Cube, Fire TV'li televizyonlar) ve Android 6 veya üzeri Android TV / Google TV cihazları (Nvidia Shield, Chromecast with Google TV, TV kutuları, Android TV'li televizyonlar).\n" +
            "Çalışmaz: Vega OS kullanan yeni Fire TV cihazları (Fire TV Stick 4K Select ve sonrası; dışarıdan uygulama kurulamaz) ve Fire OS 5 kullanan 2014–2016 Fire TV'ler.\n" +
            "Telefon ve tablet: Android 6 veya üzeri; dokunmayla kullanılır (dokun: seç, basılı tut: menü, oynatıcıda kaydır: kanal değiştir). Arayüz önce kumanda için tasarlandı.\n" +
            "Cihazınızın Fire OS mu Vega OS mu kullandığını Fire TV Ayarlar → Cihazım (My Fire TV) → Hakkında'da görebilirsiniz.",
        "Works on: Fire TV devices with Fire OS 6, 7 or 8 (Fire TV Stick 2nd gen and later, Stick Lite, Stick 4K, 4K Max, Cube, Fire TV Edition TVs) and Android TV / Google TV devices with Android 6 or later (Nvidia Shield, Chromecast with Google TV, TV boxes, Android TV sets).\n" +
            "Does not work on: new Fire TV devices with Vega OS (Fire TV Stick 4K Select and later; apps cannot be sideloaded) and 2014–2016 Fire TVs with Fire OS 5.\n" +
            "Phones and tablets: Android 6 or later, used by touch (tap: select, hold: menu, swipe in the player: change channel). The interface is designed for the remote first.\n" +
            "Whether your device runs Fire OS or Vega OS is shown under Fire TV Settings → My Fire TV → About.",
    )

    fun developer() = tx(
        "Hüseyin Erhan Özkan\nİletişim: errhan@hotmail.com\n© 2026 Hüseyin Erhan Özkan. GNU GPL 3.0 veya sonrası lisansıyla açık kaynak.\nKaynak kodu: github.com/TA3HRJ/elm-iptv",
        "Hüseyin Erhan Özkan\nContact: errhan@hotmail.com\n© 2026 Hüseyin Erhan Özkan. Open source under the GNU GPL 3.0 or later.\nSource code: github.com/TA3HRJ/elm-iptv",
    )

    fun support() = tx(
        "ELM IPTV reklamsız ve ücretsizdir. Geliştirmeyi desteklemek isterseniz telefonunuzla QR kodu okutun; GitHub Sponsors üzerinden tek seferlik ya da aylık bağış yapabilirsiniz. Bağış isteğe bağlıdır; uygulamanın hiçbir özelliği buna bağlı değildir.",
        "ELM IPTV is free and has no ads. If you would like to support its development, scan the QR code with your phone to give once or monthly through GitHub Sponsors. Giving is optional; no feature of the app depends on it.",
    )

    fun content() = tx(
        "ELM IPTV yalnızca bir oynatıcıdır; kanal, film veya dizi içermez ve satmaz. İzlediğiniz içerik, kendi eklediğiniz IPTV sağlayıcısından gelir. Yayın haklarına sahip, yasal bir sağlayıcı kullanmak kullanıcının sorumluluğundadır.",
        "ELM IPTV is only a player; it contains and sells no channels, films or series. What you watch comes from the IPTV provider you add yourself. Using a lawful provider that holds the broadcasting rights is the user's responsibility.",
    )

    fun privacy() = tx(
        "Uygulama analiz, reklam veya çökme raporu toplamaz; hiçbir veriyi geliştiriciye göndermez. Listeleriniz, şifreleriniz ve ayarlarınız yalnızca bu cihazda saklanır. Bağlantılar yalnızca sizin eklediğiniz sağlayıcıya ve rehber adreslerine yapılır.",
        "The app collects no analytics, ads or crash reports and sends no data to the developer. Your playlists, passwords and settings are stored only on this device. It connects only to the providers and guide addresses you add.",
    )

    fun licenses() = tx(
        "AndroidX Media3 / ExoPlayer, Jetpack Compose, Room, OkHttp, Coil, kotlinx, ZXing (Apache 2.0) · FFmpeg 6.0.2 ses çözücüleri (LGPL 2.1+, değiştirilmemiş kaynak: ffmpeg.org; libffmpegJNI.so kütüphanesi değiştirilebilir) · kXML (MIT) · Inter yazı tipi (SIL OFL).",
        "AndroidX Media3 / ExoPlayer, Jetpack Compose, Room, OkHttp, Coil, kotlinx, ZXing (Apache 2.0) · FFmpeg 6.0.2 audio decoders (LGPL 2.1+, unmodified source: ffmpeg.org; the libffmpegJNI.so library may be replaced) · kXML (MIT) · Inter typeface (SIL OFL).",
    )

    fun trademarks() = tx(
        "Fire TV, Amazon.com, Inc. veya iştiraklerinin; Android TV ve Google TV, Google LLC'nin ticari markasıdır. ELM IPTV bu şirketlerle bağlantılı değildir ve onlar tarafından onaylanmamıştır. \"ELM IPTV\" adı ve logosu GPL lisansının kapsamında değildir; kodu değiştirerek dağıtanlar başka ad ve logo kullanmalıdır.",
        "Fire TV is a trademark of Amazon.com, Inc. or its affiliates; Android TV and Google TV are trademarks of Google LLC. ELM IPTV is not affiliated with or endorsed by these companies. The \"ELM IPTV\" name and logo are not covered by the GPL; modified versions must use a different name and logo.",
    )
}
