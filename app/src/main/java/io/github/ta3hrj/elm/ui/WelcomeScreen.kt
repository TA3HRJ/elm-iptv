package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.tx

/** First start: what the app needs and how to add a playlist. */
@Composable
fun WelcomeScreen(viewModel: MainViewModel, onAddPlaylist: () -> Unit, onRestore: () -> Unit) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val activity = androidx.activity.compose.LocalActivity.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF16263D), AppBackground), radius = 1600f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 80.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(300.dp)) {
                Image(painterResource(R.drawable.elm_emblem), contentDescription = null, modifier = Modifier.size(160.dp))
                Spacer(Modifier.height(16.dp))
                Text("ELM IPTV", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Every Live Moment", color = Color(0xFF9AA4B2))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (choice in listOf(Lang.Choice.TURKISH, Lang.Choice.ENGLISH)) {
                        val label = if (choice == Lang.Choice.TURKISH) "Türkçe" else "English"
                        val onClick = {
                            viewModel.setLanguage(choice)
                            activity?.recreate()
                            Unit
                        }
                        if (choice == language) Button(onClick = onClick) { Text("✓ $label") } else OutlinedButton(onClick = onClick) { Text(label) }
                    }
                }
            }
            Spacer(Modifier.width(64.dp))
            Column(Modifier.widthIn(max = 620.dp)) {
                Text(tx("Hoş geldiniz", "Welcome"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    tx(
                        "ELM IPTV bir oynatıcıdır; kanal, film veya dizi içermez ve satmaz. İzlemek için kendi IPTV sağlayıcınızın verdiği listeyi ekleyin. Yayın haklarına sahip, yasal bir sağlayıcı kullanmak sizin sorumluluğunuzdadır.",
                        "ELM IPTV is a player; it contains and sells no channels, films or series. Add the playlist your own IPTV provider gave you to start watching. Using a lawful provider that holds the broadcasting rights is your responsibility.",
                    ),
                    color = Color(0xFFB8C2D0),
                )
                Spacer(Modifier.height(20.dp))
                Step(1, tx("Liste türünü seçin", "Choose the playlist type"),
                    tx("Xtream Codes (sunucu, kullanıcı adı, şifre) veya M3U bağlantısı.", "Xtream Codes (server, username, password) or an M3U link."))
                Step(2, tx("Bilgileri girin", "Enter the details"),
                    tx("Sağlayıcınızın e-posta veya mesajla gönderdiği bilgiler.", "The details your provider sent you by e-mail or message."))
                Step(3, tx("İzlemeye başlayın", "Start watching"),
                    tx("Kanallar hemen gelir; TV rehberi, film ve diziler arka planda iner.", "Channels appear right away; the TV guide, films and series download in the background."))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onAddPlaylist, modifier = Modifier.focusRequester(first)) {
                        IconLabel(Icons.Filled.Add, tx("Liste ekle", "Add playlist"))
                    }
                    OutlinedButton(onClick = onRestore) { IconLabel(Icons.Filled.Restore, tx("Yedekten geri yükle", "Restore a backup")) }
                }
            }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = AppBackground, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA4B2))
        }
    }
}
