package io.github.ta3hrj.elm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ta3hrj.elm.core.tx

/**
 * Text field for the remote. Moving onto it only selects it (highlighted, "OK: type"); OK opens the
 * on-screen keyboard. TV keyboards cover the whole screen, so opening one just because the field was
 * passed over left people not knowing what they were typing into.
 *
 * The keyboard's Done/Search/Next key or Back ends typing and the field stays selected; [onImeAction]
 * then runs (e.g. moving on to the next field or submitting). Up/down while typing leave the field.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    autoCorrect: Boolean = true,
    focusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    /** After typing ends: give the focus back to the field (false when the focus already moved on). */
    var refocus by remember { mutableStateOf(false) }
    var afterRefocus by remember { mutableStateOf<(() -> Unit)?>(null) }
    val field = remember { FocusRequester() }
    val ownSelector = remember { FocusRequester() }
    val selector = focusRequester ?: ownSelector
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val selected by interaction.collectIsFocusedAsState()

    fun stopEditing(thenRun: (() -> Unit)?) {
        refocus = true
        afterRefocus = thenRun
        editing = false
    }

    LaunchedEffect(editing) {
        if (editing) {
            runCatching { field.requestFocus() }
        } else if (refocus) {
            refocus = false
            runCatching { selector.requestFocus() }
            afterRefocus?.let { action ->
                afterRefocus = null
                action()
            }
        }
    }

    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = autoCorrect && !isPassword,
            ),
            keyboardActions = KeyboardActions { stopEditing(onImeAction) },
            trailingIcon = if (selected && !editing) {
                {
                    Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Keyboard, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                        Text(tx(" OK: yaz", " OK: type"), color = Accent, fontSize = 12.sp)
                    }
                }
            } else {
                null
            },
            colors = if (selected) {
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Accent,
                    unfocusedContainerColor = Accent.copy(alpha = 0.14f),
                    unfocusedLabelColor = Accent,
                )
            } else {
                OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(field)
                // Only reachable while typing; otherwise the selector below takes the focus.
                .focusProperties { canFocus = editing }
                .onFocusChanged { if (editing && !it.hasFocus) editing = false }
                .onPreviewKeyEvent { event ->
                    if (!editing) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            if (event.type == KeyEventType.KeyUp) stopEditing(null)
                            true
                        }
                        // OK in the field after the keyboard was closed brings it back.
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (event.type == KeyEventType.KeyUp) keyboard?.show()
                            false
                        }
                        Key.DirectionDown -> event.type == KeyEventType.KeyDown && focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> event.type == KeyEventType.KeyDown && focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                },
        )
        if (!editing) {
            // Transparent layer over the field: takes the focus while not typing; OK (or a tap) starts typing.
            Box(
                Modifier
                    .matchParentSize()
                    .focusRequester(selector)
                    .clickable(interactionSource = interaction, indication = null) { editing = true },
            )
        }
    }
}
