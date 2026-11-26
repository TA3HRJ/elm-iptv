package io.github.ta3hrj.elm.ui

import io.github.ta3hrj.elm.container

import io.github.ta3hrj.elm.core.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.data.ParentalLock

private val DialogBackground = Color(0xFF161B22)
private val DialogMuted = Color(0xFFB8C2D0)

/**
 * Ignores OK / Enter until it is pressed again inside the dialog. A dialog opened by holding OK
 * otherwise receives that same press: its release (or key repeat) chose the first option at once.
 */
@Composable
internal fun Modifier.ignoreHeldConfirm(): Modifier {
    var armed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!confirm) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) armed = true
        !armed
    }
}

/** A list of actions, e.g. for a channel or a category. [selected] marks the current choice in a picker. */
data class DialogOption(
    val label: String,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val action: () -> Unit,
)

@Composable
fun OptionsDialog(title: String, subtitle: String? = null, options: List<DialogOption>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val initial = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(460.dp).clip(RoundedCornerShape(14.dp)).background(DialogBackground).padding(24.dp).ignoreHeldConfirm(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DialogMuted) }
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEachIndexed { index, option ->
                    Button(
                        onClick = {
                            onDismiss()
                            option.action()
                        },
                        modifier = Modifier.fillMaxWidth().then(if (index == initial) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = if (option.selected) Icons.Filled.Check else option.icon
                            if (icon != null) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(option.label)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tx("Vazgeç", "Cancel")) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Four-digit PIN entry: on-screen digit pad for remotes without number keys, number keys work too.
 * [onSubmit] returns false to show "wrong PIN" and clear the entry.
 */
@Composable
fun PinDialog(title: String, subtitle: String? = null, onSubmit: (String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val lock = androidx.compose.ui.platform.LocalContext.current.container.parentalLock
    val first = remember { FocusRequester() }

    fun add(digit: Char) {
        if (pin.length >= ParentalLock.PIN_LENGTH) return
        pin += digit
        error = null
        if (pin.length == ParentalLock.PIN_LENGTH) {
            if (onSubmit(pin)) {
                onDismiss()
            } else {
                val wait = lock.lockoutRemainingMs()
                error = if (wait > 0) {
                    val seconds = (wait + 999) / 1000
                    val time = if (seconds >= 60) tx("${(seconds + 59) / 60} dk", "${(seconds + 59) / 60} min") else tx("$seconds sn", "$seconds s")
                    tx("Çok fazla yanlış deneme. $time sonra tekrar deneyin.", "Too many wrong tries. Try again in $time.")
                } else {
                    tx("PIN yanlış", "Wrong PIN")
                }
                pin = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DialogBackground)
                .padding(24.dp)
                .ignoreHeldConfirm()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = digitFor(event.key)
                    when {
                        digit != null -> { add(digit); true }
                        event.key == Key.Backspace || event.key == Key.Delete -> { pin = pin.dropLast(1); true }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DialogMuted) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(ParentalLock.PIN_LENGTH) { i ->
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0E1116))
                            .border(2.dp, if (i == pin.length) Color(0xFF4FA3FF) else Color(0xFF2B3440), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i < pin.length) Text("●", color = Color.White, fontSize = 20.sp)
                    }
                }
            }
            Text(error ?: " ", color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            val rows = listOf("123", "456", "789")
            rows.forEachIndexed { r, digits ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    digits.forEachIndexed { c, d ->
                        val modifier = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier
                        PadButton(d.toString(), modifier) { add(d) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadButton("⌫") { pin = pin.dropLast(1) }
                PadButton("0") { add('0') }
                PadButton("✕") { onDismiss() }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun PadButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.width(84.dp)) {
        Text(label, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

internal fun digitFor(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** Asks for a short text, e.g. the name of a new list. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    confirmLabel: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(460.dp).clip(RoundedCornerShape(14.dp)).background(DialogBackground).padding(24.dp).ignoreHeldConfirm(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DialogMuted) }
            TvTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                onImeAction = { if (text.isNotBlank()) { onDismiss(); onSubmit(text) } },
                focusRequester = field,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onDismiss(); onSubmit(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
                OutlinedButton(onClick = onDismiss) { Text(tx("Vazgeç", "Cancel")) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
}

/** Read-only details (label / value rows) with a Close button. [rows] null shows "loading". */
@Composable
fun InfoDialog(title: String, rows: List<Pair<String, String>>?, error: String? = null, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(14.dp)).background(DialogBackground).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            when {
                error != null -> Text(error, color = Color(0xFFFFB4A9), style = MaterialTheme.typography.bodyMedium)
                rows == null -> Text(tx("Yükleniyor…", "Loading…"), color = DialogMuted)
                else -> rows.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = DialogMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(200.dp))
                        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(close)) { Text(tx("Kapat", "Close")) }
        }
    }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
}
