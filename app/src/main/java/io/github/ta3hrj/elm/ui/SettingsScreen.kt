package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.AppSettings
import io.github.ta3hrj.elm.data.ChannelOrder
import io.github.ta3hrj.elm.data.GroupOrder
import io.github.ta3hrj.elm.data.SubtitleStyle
import io.github.ta3hrj.elm.data.SubtitleStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.People

private val SettingsMuted = Color(0xFFB8C2D0)

private enum class PinStep { None, VerifyForChange, New, Confirm, VerifyForRemove }

/** The parts of Settings, listed on the left. */
private enum class SettingsSection(val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Accounts(Icons.Filled.AccountCircle),
    General(Icons.Filled.Tune),
    Playback(Icons.Filled.PlayCircle),
    Parental(Icons.Filled.Lock),
    Hidden(Icons.Filled.VisibilityOff);

    fun label(): String = when (this) {
        Accounts -> tx("Hesaplar ve sistem", "Accounts and system")
        General -> tx("Genel ve görünüm", "General and look")
        Playback -> tx("Oynatma ve görüntü", "Playback and picture")
        Parental -> tx("Ebeveyn kilidi", "Parental lock")
        Hidden -> tx("Gizlenenler", "Hidden items")
    }
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenErrorLog: () -> Unit,
    onOpenProfiles: () -> Unit,
    onExit: () -> Unit,
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val homeView by viewModel.homeView.collectAsStateWithLifecycle()
    val activity = androidx.activity.compose.LocalActivity.current
    val resume by viewModel.resumeLastChannel.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val flags by viewModel.groupFlags.collectAsStateWithLifecycle()
    val hiddenChannels by viewModel.hiddenChannels.collectAsStateWithLifecycle()
    val playlist by viewModel.currentPlaylist.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val channelOrder by viewModel.channelOrder.collectAsStateWithLifecycle()
    val groupOrder by viewModel.groupOrder.collectAsStateWithLifecycle()
    val autoRefresh by viewModel.autoRefreshHours.collectAsStateWithLifecycle()
    val protectSettings by viewModel.protectSettings.collectAsStateWithLifecycle()
    val subtitle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val adultLock by viewModel.adultLock.collectAsStateWithLifecycle()
    val frameRateMatching by viewModel.frameRateMatching.collectAsStateWithLifecycle()
    val bufferSize by viewModel.bufferSize.collectAsStateWithLifecycle()
    val idleHours by viewModel.idleHours.collectAsStateWithLifecycle()
    val connection by viewModel.connectionResult.collectAsStateWithLifecycle()
    val uiSize by viewModel.uiSize.collectAsStateWithLifecycle()
    val backgroundUpdate by viewModel.backgroundUpdate.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val homePreview by viewModel.homePreview.collectAsStateWithLifecycle()
    val audioProfile by viewModel.audioProfile.collectAsStateWithLifecycle()

    var section by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsSection.Accounts) }
    var step by remember { mutableStateOf(PinStep.None) }
    var newPin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    // A new text size has to be confirmed; otherwise it goes back after a few seconds, so a size that
    // does not suit the TV cannot leave the user stuck.
    var sizeBefore by remember { mutableStateOf<Int?>(null) }
    val chooseSize: (Int) -> Unit = { size ->
        if (size != uiSize) {
            if (sizeBefore == null) sizeBefore = uiSize
            viewModel.setUiSize(size)
            if (size == sizeBefore) sizeBefore = null
        }
    }
    sizeBefore?.let { before ->
        val chosen = uiSize
        var secondsLeft by remember(chosen) { mutableStateOf(SIZE_CONFIRM_SECONDS) }
        LaunchedEffect(chosen) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000)
                secondsLeft--
            }
            viewModel.setUiSize(before)
            sizeBefore = null
        }
        OptionsDialog(
            title = tx("Görünüm böyle kalsın mı?", "Keep this size?"),
            subtitle = tx("$secondsLeft sn içinde önceki boyuta dönülecek.", "Going back to the previous size in $secondsLeft s."),
            options = listOf(
                DialogOption(tx("Kalsın", "Keep")) { viewModel.setUiSize(chosen) },
                DialogOption(tx("Geri al", "Undo")) {},
            ),
            // Back, like "Undo", returns to the previous size; "Keep" sets the new size again afterwards.
            onDismiss = {
                viewModel.setUiSize(before)
                sizeBefore = null
            },
        )
    }

    val hiddenGroups = flags.values.filter { it.hidden }.sortedBy { it.name }
    val lockedGroups = flags.values.filter { it.locked }.sortedBy { it.name }

    Column(Modifier.fillMaxSize().screenPadding(48.dp, 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tx("Ayarlar", "Settings"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            // The message takes the room left over, so a long one (e.g. backup paths) never squeezes the title.
            Spacer(Modifier.width(16.dp))
            Text(message.orEmpty(), color = SettingsMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize()) {
            // Sections on the left: moving onto one shows it, so the remote never has to scroll a long page.
            Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsSection.entries.forEachIndexed { index, s ->
                    io.github.ta3hrj.elm.ui.touch.ListItem(
                        selected = s == section,
                        onClick = { section = s },
                        headlineContent = { Text(s.label(), maxLines = 1) },
                        leadingContent = { androidx.tv.material3.Icon(s.icon, contentDescription = null) },
                        scale = listItemFocusScale(),
                        colors = listItemFocusColors(),
                        border = listItemFocusBorder(),
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onFocusChanged { if (it.isFocused) section = s },
                    )
                }
            }
            Spacer(Modifier.width(28.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
        if (section == SettingsSection.Accounts) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(modifier = Modifier.width(320.dp), onClick = onOpenPlaylists) { IconLabel(Icons.AutoMirrored.Filled.PlaylistPlay, tx("Hesaplar ve listeler", "Accounts and playlists")) }
                Button(modifier = Modifier.width(320.dp), onClick = onOpenProfiles) { IconLabel(Icons.Filled.People, tx("Profiller", "Profiles")) }
                Button(modifier = Modifier.width(320.dp), onClick = onOpenRecordings) { IconLabel(Icons.Filled.FiberManualRecord, tx("Kayıtlar", "Recordings")) }
                Button(modifier = Modifier.width(320.dp), onClick = onOpenBackup) { IconLabel(Icons.Filled.Backup, tx("Yedekleme", "Backup")) }
                Button(modifier = Modifier.width(320.dp), onClick = onOpenErrorLog) { IconLabel(Icons.Filled.BugReport, tx("Hata günlüğü", "Error log")) }
                Button(modifier = Modifier.width(320.dp), onClick = onExit) { IconLabel(Icons.AutoMirrored.Filled.ExitToApp, tx("Çıkış", "Exit")) }
            }
        }
        if (viewModel.updatesEnabled) {
            item { SectionTitle(tx("Uygulama", "App")) }
            item {
                SettingRow(tx("Güncelleme", "Update"), updateStatusText(update, viewModel.updateCheckedAt)) {
                    val available = update as? MainViewModel.UpdateState.Available
                    // One button for both states: when the check finds a version it turns into "Install" without
                    // being replaced, so the focus stays on it.
                    Button(
                        onClick = { if (available != null) viewModel.installUpdate() else viewModel.checkForUpdate(manual = true) },
                        enabled = available != null || update != MainViewModel.UpdateState.Checking,
                    ) {
                        IconLabel(
                            Icons.Filled.SystemUpdate,
                            if (available != null) tx("${available.release.version} sürümünü kur", "Install ${available.release.version}") else tx("Güncellemeleri denetle", "Check for updates"),
                        )
                    }
                }
            }
        }
        }
        if (section == SettingsSection.General) {
        item { SectionTitle(tx("Dil ve görünüm", "Language and look")) }
        item {
            SettingRow(
                tx("Yazı ve öğe boyutu", "Text and item size"),
                tx("Küçük boyutta ekrana daha çok satır sığar; uzaktan okumak için büyütün.", "Smaller fits more rows on screen; make it larger for reading from afar."),
            ) {
                Choices(AppSettings.UI_SCALES.indices.toList(), uiSize, ::uiSizeLabel, chooseSize)
            }
        }
        item {
            SettingRow(tx("Uygulama dili", "App language"), languageLabel(language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in Lang.Choice.entries) {
                        ChoiceButton(languageLabel(choice), selected = choice == language) {
                            if (choice != language) {
                                viewModel.setLanguage(choice)
                                activity?.recreate()
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle(tx("Açılış", "Start")) }
        item {
            SettingRow(
                tx("Açılış ekranı", "Start screen"),
                when (homeView) {
                    MainViewModel.HOME_GUIDE -> tx("Önizlemeli TV rehberi", "TV guide with preview")
                    MainViewModel.HOME_SECTIONS -> tx("Bölümler: Canlı TV, Rehber, Filmler, Diziler", "Sections: Live TV, Guide, Films, Series")
                    else -> tx("Kanal listesi", "Channel list")
                },
            ) {
                Choices(
                    listOf(MainViewModel.HOME_LIST, MainViewModel.HOME_GUIDE, MainViewModel.HOME_SECTIONS),
                    homeView,
                    {
                        when (it) {
                            MainViewModel.HOME_GUIDE -> tx("TV rehberi", "TV guide")
                            MainViewModel.HOME_SECTIONS -> tx("Bölümler", "Sections")
                            else -> tx("Kanal listesi", "Channel list")
                        }
                    },
                    viewModel::setHomeView,
                )
            }
        }
        item {
            SettingRow(tx("Açılışta son izlenen kanaldan devam et", "Continue with the last channel on start"), if (resume) tx("Açık", "On") else tx("Kapalı", "Off")) {
                Button(onClick = { viewModel.setResumeLastChannel(!resume) }) {
                    Text(if (resume) tx("Kapat", "Turn off") else tx("Aç", "Open"))
                }
            }
        }

        item {
            SettingRow(
                tx("Listeyi otomatik güncelle", "Update the playlist automatically"),
                tx("Uygulama açıkken kanallar, rehber, film ve diziler bu aralıkla yenilenir.", "Channels, guide, films and series are refreshed at this interval while the app is open."),
            ) {
                Choices(AppSettings.AUTO_REFRESH_CHOICES, autoRefresh, { if (it == 0) tx("Kapalı", "Off") else tx("$it saat", "$it h") }, viewModel::setAutoRefreshHours)
            }
        }
        item {
            SettingRow(
                tx("Arka planda günlük güncelle", "Update daily in the background"),
                tx("Uygulama açılmasa da kanallar ve rehber günde bir kez güncellenir; dizi kayıtları yeni bölümleri yakalar.",
                    "Channels and the guide are updated once a day even when the app is not opened, so series recordings catch new episodes."),
            ) {
                Button(onClick = { viewModel.setBackgroundUpdate(!backgroundUpdate) }) {
                    Text(if (backgroundUpdate) tx("Açık", "On") else tx("Kapalı", "Off"))
                }
            }
        }

        item { SectionTitle(tx("Sıralama", "Order")) }
        item {
            SettingRow(tx("Kanal sırası", "Channel order"), tx("Liste, rehber ve kanal değiştirme bu sırayı izler.", "The list, the guide and channel up/down follow this order.")) {
                Choices(ChannelOrder.entries, channelOrder, ::channelOrderLabel, viewModel::setChannelOrder)
            }
        }
        item {
            SettingRow(tx("Kategori sırası", "Category order"), "") {
                Choices(GroupOrder.entries, groupOrder, { if (it == GroupOrder.NAME) tx("Ada göre (A–Z)", "By name (A–Z)") else tx("Liste sırası", "Playlist order") }, viewModel::setGroupOrder)
            }
        }
        }
        if (section == SettingsSection.Playback) {
        item { SectionTitle(tx("Oynatma", "Playback")) }
        item {
            SettingRow(
                tx("Tampon", "Buffer"),
                tx("Küçük: kanallar daha hızlı açılır. Büyük: zayıf bağlantıda daha az takılır, açılış biraz uzar.", "Small: channels open faster. Large: fewer stalls on a weak connection, slightly slower start."),
            ) {
                Choices(listOf(0, 1, 2), bufferSize, { when (it) { 0 -> tx("Küçük", "Small"); 2 -> tx("Büyük", "Large"); else -> tx("Normal", "Normal") } }, viewModel::setBufferSize)
            }
        }
        item {
            SettingRow(
                tx("\"Hâlâ izliyor musunuz?\" sorusu", "\"Are you still watching?\""),
                tx("Bu süre boyunca hiç tuşa basılmazsa \"Orada mısınız?\" diye sorar; 2 dakika cevap gelmezse yayını durdurur, bir tuşla kaldığı yerden sürer.", "Asks \"Are you still there?\" after this long without a key press; stops playback if nobody answers within 2 minutes, a key picks it up again."),
            ) {
                Choices(AppSettings.IDLE_CHOICES, idleHours, { if (it == 0) tx("Kapalı", "Off") else tx("$it sa", "$it h") }, viewModel::setIdleHours)
            }
        }
        item {
            SettingRow(
                tx("Bağlantı testi", "Connection test"),
                connection ?: tx("Sunucunun yanıt süresini ve son izlenen kanalın indirme hızını ölçer (yaklaşık 10 sn).", "Measures the server's response time and the download speed of the last channel watched (about 10 s)."),
            ) {
                Button(onClick = viewModel::runConnectionTest, enabled = connection != MainViewModel.RUNNING) {
                    IconLabel(Icons.Filled.Speed, if (connection == MainViewModel.RUNNING) tx("Ölçülüyor…", "Measuring…") else tx("Test et", "Test"))
                }
            }
        }

        item {
            SettingRow(
                tx("Ses profili", "Sound profile"),
                tx(
                    "Genel: sesi içerik türü belirtmeden gönderir. Film: TV ve cihazın film ses işlemesini devreye sokar; bazı TV'lerde ses daha ince ya da farklı duyulabilir. Sesi iki ayarla karşılaştırın; yeni ayar bir sonraki yayında geçerli olur.",
                    "General: sends the sound without a content type. Movie: lets the TV and the device apply their movie sound processing, which on some TVs sounds thinner or different. Compare the two; the new setting applies to the next stream.",
                ),
            ) {
                Choices(listOf(AppSettings.AUDIO_PROFILE_GENERAL, AppSettings.AUDIO_PROFILE_MOVIE), audioProfile, { if (it == AppSettings.AUDIO_PROFILE_MOVIE) tx("Film", "Movie") else tx("Genel", "General") }, viewModel::setAudioProfile)
            }
        }
        item {
            SettingRow(
                tx("Ana ekranda canlı önizleme", "Live preview on the home screen"),
                tx(
                    "Kanal listesinin yanında seçili kanalı küçük pencerede ve yayın bilgisini (çözünürlük, bağlantı kalitesi) gösterir. Kanala OK ile geçince çalan yayın kesintisiz tam ekrana geçer (aynı bağlantı). Kayıt sürerken açılmaz. Yalnızca geniş ekranda (Kompakt ve Normal boyut) görünür.",
                    "Shows the selected channel in a small window beside the channel list, with stream information (picture size, connection quality). Choosing the channel with OK carries the playing stream on to full screen without a break (same connection). Not shown while a recording runs. Only shown on a wide screen (Compact and Normal size).",
                ),
            ) {
                Button(onClick = { viewModel.setHomePreview(!homePreview) }) {
                    Text(if (homePreview) tx("Açık", "On") else tx("Kapalı", "Off"))
                }
            }
        }

        item { SectionTitle(tx("Görüntü", "Picture")) }
        item {
            SettingRow(
                tx("Kare hızı eşleme", "Match frame rate"),
                tx(
                    "TV'nin yenileme hızını yayına göre değiştirir (Avrupa yayınları 50 Hz, filmler 24 Hz); takılmayı azaltır. Geçişte ekran 1–2 saniye kararabilir.",
                    "Switches the TV's refresh rate to suit the stream (European broadcasts 50 Hz, films 24 Hz) for smoother motion. The screen may go dark for 1–2 seconds when it switches.",
                ),
            ) {
                Button(onClick = { viewModel.setFrameRateMatching(!frameRateMatching) }) {
                    Text(if (frameRateMatching) tx("Açık · kapat", "On · turn off") else tx("Kapalı · aç", "Off · turn on"))
                }
            }
        }

        item { SectionTitle(tx("Altyazı görünümü", "Subtitle appearance")) }
        item {
            SettingRow(tx("Boyut", "Size"), "") {
                Choices(listOf(0, 1, 2, 3), subtitle.size, ::subtitleSizeLabel) { viewModel.setSubtitleStyle(subtitle.copy(size = it)) }
            }
        }
        item {
            SettingRow(tx("Yazı rengi", "Text colour"), "") {
                Choices(listOf(0, 1), subtitle.color, { if (it == 1) tx("Sarı", "Yellow") else tx("Beyaz", "White") }) { viewModel.setSubtitleStyle(subtitle.copy(color = it)) }
            }
        }
        item {
            SettingRow(tx("Arka plan", "Background"), "") {
                Choices(listOf(0, 1, 2), subtitle.background, ::subtitleBackgroundLabel) { viewModel.setSubtitleStyle(subtitle.copy(background = it)) }
            }
        }
        item { SubtitlePreview(subtitle) }
        }
        if (section == SettingsSection.Parental) {
        item { SectionTitle(tx("Ebeveyn kilidi", "Parental lock")) }
        item {
            SettingRow(
                "PIN",
                when {
                    !pinSet -> tx("Belirlenmedi. Kategori kilitlemek için önce bir PIN belirleyin.", "Not set. Set a PIN before locking categories.")
                    unlocked -> tx("Belirlendi · kilitli kategoriler şu an açık", "Set · locked categories are open now")
                    else -> tx("Belirlendi · kilitli kategoriler kapalı", "Set · locked categories are closed")
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { step = if (pinSet) PinStep.VerifyForChange else PinStep.New }) {
                        Text(if (pinSet) tx("PIN'i değiştir", "Change PIN") else tx("PIN belirle", "Set PIN"))
                    }
                    if (pinSet) {
                        OutlinedButton(onClick = { step = PinStep.VerifyForRemove }) { Text(tx("PIN'i kaldır", "Remove PIN")) }
                        if (unlocked) OutlinedButton(onClick = viewModel::lockNow) { Text(tx("Şimdi kilitle", "Lock now")) }
                    }
                }
            }
        }
        item {
            Text(
                tx("Bir kategoriyi kilitlemek veya gizlemek için listede kategorinin üzerinde Menü (☰) tuşuna basın ya da OK'a basılı tutun.", "To lock or hide a category, press Menu (☰) on it in the list or hold OK."),
                color = SettingsMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            SettingRow(
                tx("Yetişkin kategorilerini otomatik kilitle", "Lock adult categories automatically"),
                if (pinSet) {
                    tx("+18, XXX, Adult, Yetişkin gibi kategoriler kilitlenir.", "Categories such as +18, XXX, Adult are locked.")
                } else {
                    tx("PIN belirlenmediği için bu kategoriler gizleniyor; PIN belirleyince kilitli olarak görünürler.", "With no PIN set these categories are hidden; once a PIN is set they appear locked.")
                },
            ) {
                Button(onClick = {
                    if (adultLock) askPinThen(viewModel) { viewModel.setAdultLock(false) } else viewModel.setAdultLock(true)
                }) {
                    Text(if (adultLock) tx("Açık · kapat", "On · turn off") else tx("Kapalı · aç", "Off · turn on"))
                }
            }
        }
        if (pinSet) {
            item {
                SettingRow(
                    tx("Ayarları PIN ile koru", "Protect Settings with the PIN"),
                    tx("Ayarlar açılırken PIN sorulur; kilit böylece ayarlardan kaldırılamaz.", "Opening Settings asks for the PIN, so the lock can't be removed there."),
                ) {
                    Button(onClick = { viewModel.setProtectSettings(!protectSettings) }) {
                        Text(if (protectSettings) tx("Açık · kapat", "On · turn off") else tx("Kapalı · aç", "Off · turn on"))
                    }
                }
            }
        }
        if (lockedGroups.isNotEmpty()) {
            item { Text(tx("Kilitli: ", "Locked: ") + lockedGroups.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall) }
        }
        }
        if (section == SettingsSection.Hidden) {
        item { SectionTitle(tx("Gizlenenler", "Hidden items") + (playlist?.let { " (${it.name})" } ?: "")) }
        if (hiddenGroups.isEmpty() && hiddenChannels.isEmpty()) {
            item { Text(tx("Gizlenmiş kategori veya kanal yok.", "No hidden categories or channels."), color = SettingsMuted) }
        }
        items(hiddenGroups, key = { "g|${it.scope}|${it.name}" }) { flag ->
            SettingRow(flag.name, scopeLabel(flag.scope)) {
                OutlinedButton(onClick = { viewModel.setGroupHidden(flag.scope, flag.name, hidden = false) }) { Text(tx("Göster", "Show")) }
            }
        }
        items(hiddenChannels, key = { "c|${it.streamUrl}" }) { channel ->
            SettingRow(channel.name, tx("Kanal", "Channel")) {
                OutlinedButton(onClick = { viewModel.unhideChannel(channel.streamUrl) }) { Text(tx("Göster", "Show")) }
            }
        }
        }
            }
        }
    }

    PendingPin(viewModel)
    when (step) {
        PinStep.None -> Unit
        PinStep.VerifyForChange -> PinDialog(
            tx("Mevcut PIN", "Current PIN"),
            onSubmit = { pin -> viewModel.verifyPin(pin).also { if (it) step = PinStep.New } },
            onDismiss = { if (step == PinStep.VerifyForChange) step = PinStep.None },
        )
        PinStep.New -> PinDialog(
            tx("Yeni PIN", "New PIN"),
            subtitle = tx("4 haneli bir PIN girin", "Enter a 4-digit PIN"),
            onSubmit = { pin -> newPin = pin; step = PinStep.Confirm; true },
            onDismiss = { if (step == PinStep.New) step = PinStep.None },
        )
        PinStep.Confirm -> PinDialog(
            tx("Yeni PIN'i tekrar girin", "Enter the new PIN again"),
            onSubmit = { pin ->
                (pin == newPin).also {
                    if (it) {
                        viewModel.setPin(pin)
                        step = PinStep.None
                    }
                }
            },
            onDismiss = { step = PinStep.None },
        )
        PinStep.VerifyForRemove -> PinDialog(
            tx("PIN'i kaldırmak için mevcut PIN", "Current PIN to remove it"),
            onSubmit = { pin -> viewModel.clearPin(pin).also { if (it) step = PinStep.None } },
            onDismiss = { step = PinStep.None },
        )
    }
}

private const val SIZE_CONFIRM_SECONDS = 15

private fun uiSizeLabel(size: Int): String = when (size) {
    0 -> tx("Kompakt", "Compact")
    1 -> tx("Normal", "Normal")
    2 -> tx("Büyük", "Large")
    else -> tx("Çok büyük", "Extra large")
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF7FB8FF), modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SettingRow(title: String, detail: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = SettingsMuted)
        }
        action()
    }
}

private fun scopeLabel(scope: String) = when (scope) {
    FlagScopes.MOVIE -> tx("Film kategorisi", "Film category")
    FlagScopes.SERIES -> tx("Dizi kategorisi", "Series category")
    else -> tx("Canlı TV kategorisi", "Live TV category")
}

private fun languageLabel(choice: Lang.Choice) = when (choice) {
    Lang.Choice.SYSTEM -> tx("Cihaz dili", "Device language")
    Lang.Choice.TURKISH -> "Türkçe"
    Lang.Choice.ENGLISH -> "English"
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            ChoiceButton(label(option), selected = option == selected) { if (option != selected) onSelect(option) }
        }
    }
}

private fun channelOrderLabel(order: ChannelOrder) = when (order) {
    ChannelOrder.PLAYLIST -> tx("Liste sırası", "Playlist order")
    ChannelOrder.NUMBER -> tx("Numara", "Number")
    ChannelOrder.NAME -> tx("Ad (A–Z)", "Name (A–Z)")
}

private fun subtitleSizeLabel(size: Int) = when (size) {
    0 -> tx("Küçük", "Small")
    2 -> tx("Büyük", "Large")
    3 -> tx("Çok büyük", "Extra large")
    else -> tx("Normal", "Normal")
}

private fun subtitleBackgroundLabel(background: Int) = when (background) {
    0 -> tx("Yok (gölgeli)", "None (shadow)")
    2 -> tx("Siyah", "Black")
    else -> tx("Yarı saydam", "Translucent")
}

/** A sample line drawn the way the player will show subtitles. */
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    val scale = SubtitleStyles.scale(style.size)
    // Takes the focus (nothing happens on OK) so the remote can move down to it and bring it on screen.
    io.github.ta3hrj.elm.ui.touch.Surface(
        onClick = {},
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = surfaceFocusBorder(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
    Box(
        Modifier.fillMaxWidth().height(90.dp).background(
            androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF3A5F7D), Color(0xFF6B4E71), Color(0xFF8C6A3F))),
            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            tx("Altyazı böyle görünecek", "Subtitles will look like this"),
            color = Color(SubtitleStyles.foreground(style)),
            fontSize = (20 * scale).sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = if (style.background == 0) androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 6f) else null,
            ),
            modifier = Modifier.padding(bottom = 12.dp)
                .background(Color(SubtitleStyles.background(style)))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
    }
}

/** Crashes saved on this device, newest first; OK shows the full report. */
@Composable
fun ErrorLogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(viewModel.crashEntries()) }
    var open by remember { mutableStateOf<io.github.ta3hrj.elm.data.CrashLog.Entry?>(null) }
    // The same home-network page as Backup: the log can be downloaded on a phone and sent on.
    var transferUrl by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        transferUrl = viewModel.startBackupTransfer()
        onDispose { viewModel.stopBackupTransfer() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val selected = open
    if (selected != null) {
        val text = remember(selected) { runCatching { io.github.ta3hrj.elm.core.Redact.text(selected.file.readText()) }.getOrDefault("") }
        val back = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { back.requestFocus() } }
        androidx.activity.compose.BackHandler { open = null }
        Column(Modifier.fillMaxSize().screenPadding(48.dp, 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Hata ayrıntısı", "Error details"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { open = null }, modifier = Modifier.focusRequester(back)) { Text(tx("Geri", "Back")) }
            }
            Spacer(Modifier.height(8.dp))
            // Scrolls with up/down: each line is focusable on a TV, so the text is split into rows.
            LazyColumn(Modifier.fillMaxSize()) {
                items(text.lines()) { line ->
                    io.github.ta3hrj.elm.ui.touch.Surface(
                        onClick = {},
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0xFF1F4E86)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(line, style = MaterialTheme.typography.bodySmall, fontSize = 13.sp, color = Color(0xFFE0E6EE), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().screenPadding(64.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Hata günlüğü", "Error log"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    OutlinedButton(onClick = { viewModel.clearCrashes(); entries = emptyList() }) { Text(tx("Tümünü sil", "Delete all")) }
                    Spacer(Modifier.width(12.dp))
                }
                OutlinedButton(onClick = onBack, modifier = if (entries.isEmpty()) Modifier.focusRequester(first) else Modifier) { Text(tx("Geri", "Back")) }
            }
        }
        item {
            Text(
                tx(
                    "Uygulama beklenmedik şekilde kapanırsa ayrıntılar burada saklanır (son 10 kayıt). Sorun bildirmek için aynı ev ağındaki telefonda aşağıdaki adresi açıp \"errors.txt\" dosyasını indirin ve gönderin. Uzun izlemede yavaşlama ya da kapanma olduysa \"memory.txt\" dosyasını da gönderin: uygulama 5 dakikada bir bellek kullanımını not eder.",
                    "If the app closes unexpectedly, the details are kept here (last 10). To report a problem, open the address below on a phone on the same home network, download \"errors.txt\" and send it. If it slowed down or closed after long watching, send \"memory.txt\" too: the app notes its memory use every 5 minutes.",
                ),
                color = SettingsMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text(
                transferUrl ?: tx("TV ağa bağlı değil.", "The TV is not connected to a network."),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7FB8FF),
            )
        }
        item {
            val memory = remember { viewModel.memorySummary() }
            if (memory.lastPssMb != null) {
                Text(
                    tx(
                        "Bellek (bu açılıştan beri): şimdi ${memory.lastPssMb} MB · en yüksek ${memory.peakPssMb} MB · başta ${memory.firstPssMb} MB · ${memory.samples} ölçüm",
                        "Memory (since this start): now ${memory.lastPssMb} MB · highest ${memory.peakPssMb} MB · at start ${memory.firstPssMb} MB · ${memory.samples} samples",
                    ),
                    color = SettingsMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (entries.isEmpty()) {
            item { Text(tx("Kayıtlı hata yok.", "No errors recorded."), color = SettingsMuted) }
        }
        items(entries.size) { i ->
            val entry = entries[i]
            io.github.ta3hrj.elm.ui.touch.ListItem(
                selected = false,
                onClick = { open = entry },
                headlineContent = {
                    Text(
                        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", io.github.ta3hrj.elm.core.Lang.locale).format(java.util.Date(entry.time)) +
                            (entry.version?.let { tx("  ·  sürüm $it", "  ·  version $it") } ?: "") +
                            if (entry.fromOlderVersion) tx("  ·  eski sürüm, düzeltilmiş olabilir", "  ·  older version, may be fixed") else "",
                        color = if (entry.fromOlderVersion) SettingsMuted else Color.Unspecified,
                    )
                },
                supportingContent = { Text(entry.summary, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = if (entry.fromOlderVersion) SettingsMuted else Color.Unspecified) },
                colors = listItemFocusColors(),
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}
