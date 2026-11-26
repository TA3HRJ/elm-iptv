package io.github.ta3hrj.elm.reminders

import io.github.ta3hrj.elm.core.tx

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.R
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.ReminderEntity
import io.github.ta3hrj.elm.player.PlayerActivity
import io.github.ta3hrj.elm.ui.AppTheme
import io.github.ta3hrj.elm.ui.timeRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Your programme is starting" pop-up shown over whatever is on screen. */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        setContent {
            var reminder by remember { mutableStateOf<ReminderEntity?>(null) }
            LaunchedEffect(id) {
                reminder = container.reminders.get(id)
                if (reminder == null) finish()
            }
            LaunchedEffect(Unit) {
                delay(AUTO_CLOSE_MS)
                finish()
            }
            AppTheme {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    reminder?.let { r -> Popup(r, onWatch = { watch(r) }, onClose = { close(r) }) }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Popup(reminder: ReminderEntity, onWatch: () -> Unit, onClose: () -> Unit) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161B22))
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.elm_emblem), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(tx("Program başlıyor", "Programme starting"), style = MaterialTheme.typography.titleMedium, color = Color(0xFF7FB8FF))
            }
            Spacer(Modifier.height(14.dp))
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${reminder.channelName}  ·  ${timeRange(reminder.start, reminder.stop)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9AA4B2),
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onWatch, modifier = Modifier.focusRequester(focus)) { Text(tx("İzle", "Watch")) }
                OutlinedButton(onClick = onClose) { Text(tx("Kapat", "Turn off")) }
            }
        }
    }

    private fun watch(reminder: ReminderEntity) {
        lifecycleScope.launch {
            val channel = container.database.channels().byStreamUrl(reminder.playlistId, reminder.streamUrl)
            container.reminders.remove(reminder.id)
            if (channel != null) {
                PlayerActivity.start(this@ReminderActivity, channel.playlistId, ChannelQuery.Group(channel.groupTitle), channel.id)
            }
            finish()
        }
    }

    private fun close(reminder: ReminderEntity) {
        lifecycleScope.launch {
            container.reminders.remove(reminder.id)
            finish()
        }
    }

    companion object {
        private const val AUTO_CLOSE_MS = 90_000L

        fun intent(context: Context, id: Long): Intent =
            Intent(context, ReminderActivity::class.java).putExtra(Reminders.EXTRA_ID, id)
    }
}
