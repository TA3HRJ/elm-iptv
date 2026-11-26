package io.github.ta3hrj.elm.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.BuildConfig
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.ui.MainViewModel.UpdateState

/** The questions and progress of an in-app update; shown over any screen. */
@Composable
fun UpdateDialogs(viewModel: MainViewModel) {
    val state by viewModel.update.collectAsStateWithLifecycle()
    // Closing the progress dialog only hides it; the download goes on and the installer opens at the end.
    var progressHidden by remember { mutableStateOf(false) }
    LaunchedEffect(state is UpdateState.Downloading) { if (state !is UpdateState.Downloading) progressHidden = false }

    when (val s = state) {
        is UpdateState.Available -> if (s.ask) {
            OptionsDialog(
                title = tx("Yeni sürüm: ${s.release.version}", "New version: ${s.release.version}"),
                subtitle = tx("Kullandığınız: ${BuildConfig.VERSION_NAME}", "You have: ${BuildConfig.VERSION_NAME}") +
                    s.release.notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(4).joinToString("\n", prefix = "\n\n").take(300),
                options = listOf(
                    DialogOption(tx("Şimdi güncelle", "Update now"), Icons.Filled.SystemUpdate) { viewModel.installUpdate() },
                    DialogOption(tx("Sonra", "Later"), Icons.Filled.Schedule) { viewModel.postponeUpdate() },
                ),
                onDismiss = viewModel::dismissUpdate,
            )
        }
        is UpdateState.Downloading -> if (!progressHidden) {
            InfoDialog(
                title = tx("Güncelleme indiriliyor", "Downloading the update"),
                rows = listOf(
                    tx("Sürüm", "Version") to s.release.version,
                    tx("İlerleme", "Progress") to "%${(s.progress * 100).toInt()}",
                ),
                onDismiss = { progressHidden = true },
            )
        }
        is UpdateState.NeedsPermission -> OptionsDialog(
            title = tx("Kurulum izni gerekiyor", "Permission to install needed"),
            subtitle = tx(
                "Güncellemeyi kurabilmek için ELM IPTV'nin uygulama yüklemesine izin verin. Fire TV: Ayarlar → Cihazım (My Fire TV) → " +
                    "Geliştirici seçenekleri → Bilinmeyen kaynaklardan uygulamalar → ELM IPTV. Sonra \"Tekrar dene\"yi seçin.",
                "Allow ELM IPTV to install apps so it can install the update. Fire TV: Settings → My Fire TV → Developer options → " +
                    "Install unknown apps → ELM IPTV. Then choose \"Try again\".",
            ),
            options = listOf(
                DialogOption(tx("İzin ekranını aç", "Open the permission screen")) { viewModel.openUpdatePermission(s.file) },
                DialogOption(tx("Tekrar dene", "Try again")) { viewModel.retryUpdateInstall(s.file) },
            ),
            onDismiss = viewModel::dismissUpdate,
        )
        is UpdateState.Failed -> InfoDialog(
            title = tx("Güncelleme yapılamadı", "The update failed"),
            rows = null,
            error = s.reason,
            onDismiss = viewModel::dismissUpdate,
        )
        else -> Unit
    }
}

/** Settings → Accounts and system: this version, the last check and the update button. */
fun updateStatusText(state: UpdateState, checkedAt: Long): String {
    val last = if (checkedAt > 0) {
        tx(" · son denetim: ", " · last checked: ") +
            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", io.github.ta3hrj.elm.core.Lang.locale).format(java.util.Date(checkedAt))
    } else {
        ""
    }
    return tx("Kurulu sürüm: ", "Installed version: ") + BuildConfig.VERSION_NAME + when (state) {
        UpdateState.Checking -> tx(" · denetleniyor…", " · checking…")
        UpdateState.UpToDate -> tx(" · güncel", " · up to date") + last
        UpdateState.Unreachable -> tx(" · sürüm bilgisi alınamadı (internet yok ya da güncelleme sunucusuna ulaşılamıyor)", " · no version information (no internet, or the update server cannot be reached)")
        is UpdateState.Available -> tx(" · yeni sürüm: ", " · new version: ") + state.release.version
        is UpdateState.Downloading -> tx(" · indiriliyor %", " · downloading %") + (state.progress * 100).toInt()
        else -> last
    }
}
