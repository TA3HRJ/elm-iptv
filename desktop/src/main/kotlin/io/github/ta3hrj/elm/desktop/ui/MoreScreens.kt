package io.github.ta3hrj.elm.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ta3hrj.elm.core.Redact
import io.github.ta3hrj.elm.core.VodItem
import io.github.ta3hrj.elm.core.VodKind
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.desktop.AppState
import io.github.ta3hrj.elm.desktop.appVersion
import io.github.ta3hrj.elm.desktop.data.SavedPlaylist
import io.github.ta3hrj.elm.desktop.player.Vlc

/** One search over channels, films and series. Films and series that were not opened yet are loaded first. */
@Composable
fun SearchScreen(state: AppState) {
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<VodItem?>(null) }
    androidx.compose.runtime.LaunchedEffect(state.content) {
        state.loadVod(VodKind.MOVIE)
        state.loadVod(VodKind.SERIES)
    }
    open?.let { item ->
        VodDetail(state, item) { open = null }
        return
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(tx("Kanal, film veya dizi ara…", "Search channels, films or series…")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        if (query.trim().length < 2) {
            EmptyNote(tx("En az iki harf yazın.", "Type at least two letters."))
            return@Column
        }
        val channels = AppState.filter(state.visibleChannels(), query) { it.name }.take(100)
        val movies = AppState.filter(state.movies.orEmpty(), query) { it.name }.take(60)
        val series = AppState.filter(state.series.orEmpty(), query) { it.name }.take(60)
        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            EmptyNote(tx("Sonuç yok.", "No results."))
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (channels.isNotEmpty()) item { Section(tx("Kanallar", "Channels")) }
            items(channels, key = { "c" + it.streamUrl }) { c ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { state.playChannel(c, channels) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(c.logo, c.name, Modifier.size(width = 48.dp, height = 32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, modifier = Modifier.weight(1f))
                    Text(c.group, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            for ((title, list) in listOf(tx("Filmler", "Films") to movies, tx("Diziler", "Series") to series)) {
                if (list.isNotEmpty()) item { Section(title) }
                items(list, key = { "v" + it.kind + it.providerId }) { v ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { open = v }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Logo(v.poster, v.name, Modifier.size(width = 32.dp, height = 48.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(v.name, modifier = Modifier.weight(1f))
                        Text(v.category, color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun PlaylistsScreen(state: AppState, welcome: Boolean) {
    var editing by remember { mutableStateOf<SavedPlaylist?>(null) }
    var removing by remember { mutableStateOf<SavedPlaylist?>(null) }
    editing?.let { p ->
        PlaylistForm(p, onSave = { state.addOrUpdatePlaylist(it); editing = null; state.screen = io.github.ta3hrj.elm.desktop.Screen.Live }, onCancel = { editing = null })
        return
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        if (welcome) {
            Text(tx("ELM IPTV'ye hoş geldiniz", "Welcome to ELM IPTV"), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(tx("Başlamak için sağlayıcınızın verdiği M3U adresini veya Xtream bilgilerini ekleyin.", "To start, add the M3U address or Xtream details your provider gave you."), color = Muted)
            Spacer(Modifier.height(16.dp))
        } else {
            Text(tx("Listeler", "Playlists"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { editing = SavedPlaylist(0, tx("Listem", "My playlist"), SavedPlaylist.TYPE_XTREAM) }) { Text(tx("+ Xtream hesabı", "+ Xtream account")) }
            OutlinedButton(onClick = { editing = SavedPlaylist(0, tx("Listem", "My playlist"), SavedPlaylist.TYPE_M3U) }) { Text(tx("+ M3U listesi", "+ M3U playlist")) }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.data.playlists, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = p.id == state.playlist?.id, onClick = { state.selectPlaylist(p) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (p.isXtream) "Xtream · ${Redact.text(p.server)} · ${p.username}" else "M3U · ${Redact.text(p.url)}",
                                color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = { editing = p }) { Text(tx("Düzenle", "Edit")) }
                        TextButton(onClick = { removing = p }) { Text(tx("Sil", "Delete")) }
                    }
                }
            }
        }
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(tx("Liste silinsin mi?", "Delete the playlist?")) },
            text = { Text(tx("\"${p.name}\" ve favorileri bu bilgisayardan silinir.", "\"${p.name}\" and its favorites are removed from this computer.")) },
            confirmButton = { TextButton(onClick = { state.removePlaylist(p); removing = null }) { Text(tx("Sil", "Delete")) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(tx("Vazgeç", "Cancel")) } },
        )
    }
}

@Composable
private fun PlaylistForm(initial: SavedPlaylist, onSave: (SavedPlaylist) -> Unit, onCancel: () -> Unit) {
    var p by remember { mutableStateOf(initial) }
    var advanced by remember { mutableStateOf(initial.userAgent != null || initial.guideUrl.isNotBlank()) }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (initial.id == 0L) tx("Liste ekle", "Add a playlist") else tx("Listeyi düzenle", "Edit the playlist"), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = p.isXtream, onClick = { p = p.copy(type = SavedPlaylist.TYPE_XTREAM) }, label = { Text("Xtream") })
            FilterChip(selected = !p.isXtream, onClick = { p = p.copy(type = SavedPlaylist.TYPE_M3U) }, label = { Text("M3U") })
        }
        OutlinedTextField(p.name, { p = p.copy(name = it) }, label = { Text(tx("Ad", "Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (p.isXtream) {
            OutlinedTextField(p.server, { p = p.copy(server = it) }, label = { Text(tx("Sunucu (ör. http://sunucu:8080)", "Server (e.g. http://server:8080)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.username, { p = p.copy(username = it) }, label = { Text(tx("Kullanıcı adı", "Username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                p.password, { p = p.copy(password = it) }, label = { Text(tx("Şifre", "Password")) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Canlı yayın biçimi:", "Live format:"))
                FilterChip(selected = p.liveFormat == "ts", onClick = { p = p.copy(liveFormat = "ts") }, label = { Text("MPEG-TS") })
                FilterChip(selected = p.liveFormat == "m3u8", onClick = { p = p.copy(liveFormat = "m3u8") }, label = { Text("HLS") })
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(p.url, { p = p.copy(url = it) }, label = { Text(tx("M3U adresi veya dosyası", "M3U address or file")) }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { chooseFile()?.let { p = p.copy(url = it) } }) { Text(tx("Dosya…", "File…")) }
            }
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) tx("Gelişmiş ▲", "Advanced ▲") else tx("Gelişmiş ▼", "Advanced ▼")) }
        if (advanced) {
            OutlinedTextField(p.guideUrl, { p = p.copy(guideUrl = it) }, label = { Text(tx("Rehber (XMLTV) adresi — boş: listenin kendi rehberi", "Guide (XMLTV) address — empty: the playlist's own")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.userAgent.orEmpty(), { p = p.copy(userAgent = it.ifBlank { null }) }, label = { Text("User-Agent") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        val valid = p.name.isNotBlank() && if (p.isXtream) p.server.isNotBlank() && p.username.isNotBlank() && p.password.isNotBlank() else p.url.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(p.copy(name = p.name.trim(), server = p.server.trim(), username = p.username.trim(), url = p.url.trim())) }, enabled = valid) { Text(tx("Kaydet ve yükle", "Save and load")) }
            OutlinedButton(onClick = onCancel) { Text(tx("Vazgeç", "Cancel")) }
        }
    }
}

private fun chooseFile(): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, tx("M3U dosyası seçin", "Choose an M3U file"), java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return java.io.File(dialog.directory, file).absolutePath
}

@Composable
fun SettingsScreen(state: AppState) {
    val s = state.settings
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tx("Ayarlar", "Settings"), style = MaterialTheme.typography.headlineSmall)
        Setting(tx("Dil", "Language")) {
            for ((value, label) in listOf("system" to tx("Sistem", "System"), "tr" to "Türkçe", "en" to "English")) {
                FilterChip(selected = s.language == value, onClick = { state.updateSettings { it.copy(language = value) } }, label = { Text(label) })
            }
        }
        Setting(tx("Ağ önbelleği", "Network cache"), tx("Fazlası zayıf bağlantıda takılmayı azaltır, azı daha hızlı açar.", "More rides out a weak connection, less starts faster.")) {
            for ((ms, label) in listOf(1_000 to tx("Küçük", "Small"), 3_000 to tx("Orta", "Medium"), 8_000 to tx("Büyük", "Large"))) {
                FilterChip(selected = s.networkCacheMs == ms, onClick = { state.updateSettings { it.copy(networkCacheMs = ms) } }, label = { Text(label) })
            }
        }
        Setting(tx("Donanım ile çözme", "Hardware decoding"), tx("Görüntüde bozulma olursa kapatın.", "Turn it off if the picture shows glitches.")) {
            Switch(checked = s.hardwareDecoding, onCheckedChange = { v -> state.updateSettings { it.copy(hardwareDecoding = v) } })
        }
        Setting(tx("Yetişkin kategorilerini gizle", "Hide adult categories")) {
            Switch(checked = s.hideAdult, onCheckedChange = { v -> state.updateSettings { it.copy(hideAdult = v) } })
        }
        Setting(tx("Önbellek", "Cache"), tx("Listeler, rehber ve resimler yeniden indirilir.", "Playlists, guide and pictures are downloaded again.")) {
            OutlinedButton(onClick = { state.clearCaches() }) { Text(tx("Temizle", "Clear")) }
        }
        Setting(tx("Klavye", "Keyboard")) {
            Text(
                tx(
                    "Oynatıcıda: Boşluk duraklat · F/F11 tam ekran · Esc çık · ↑↓ / PgUp PgDn kanal · ←→ 10 sn · Ctrl+↑↓, +/−, tekerlek ses · M sessiz · L kanal listesi · Geri tuşu önceki kanal · F5 listeyi yenile",
                    "In the player: Space pause · F/F11 full screen · Esc leave · ↑↓ / PgUp PgDn channel · ←→ 10 s · Ctrl+↑↓, +/−, wheel volume · M mute · L channel list · Backspace previous channel · F5 reload the playlist",
                ),
                color = Muted,
            )
        }
        Setting(
            tx("Destek ol", "Support ELM IPTV"),
            tx("ELM IPTV reklamsız ve ücretsizdir. Bağış isteğe bağlıdır; hiçbir özellik buna bağlı değildir.", "ELM IPTV is free and has no ads. Giving is optional; no feature depends on it."),
        ) {
            OutlinedButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(io.github.ta3hrj.elm.core.Support.URL)) } }) {
                Text(tx("GitHub Sponsors sayfasını aç", "Open GitHub Sponsors"))
            }
            Text(io.github.ta3hrj.elm.core.Support.label, color = Muted)
        }
        Setting(tx("Hakkında", "About")) {
            Text("ELM IPTV $appVersion  ·  VLC ${Vlc.version ?: "—"}  ·  errhan@hotmail.com", color = Muted)
        }
    }
}

@Composable
private fun Setting(title: String, note: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}
