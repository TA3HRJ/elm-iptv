package io.github.ta3hrj.elm.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What kind of screen the app is on. The app was made for the TV (remote, 16:9, seen from the sofa); phones and
 * tablets get the same screens with touch, and narrow windows (a phone held upright) get single-column layouts.
 */
data class FormFactor(
    /** Touch is the main input (phone or tablet): hints speak of tapping instead of OK and Menu. */
    val touch: Boolean,
    /** The window is narrower than [COMPACT_WIDTH] at the chosen text size: one column at a time. */
    val compact: Boolean,
) {
    companion object {
        /** Below this width (dp at the chosen text size) side-by-side lists no longer fit. A TV never gets here. */
        val COMPACT_WIDTH = 600.dp

        /** A device without the TV feature but with a touch screen. */
        fun isTouchDevice(context: Context): Boolean {
            val pm = context.packageManager
            // Fire TV does not always declare the leanback feature; its UI mode says "television" everywhere.
            val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.currentModeType
            val tv = uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                pm.hasSystemFeature("amazon.hardware.fire_tv")
            return !tv && pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        }

        fun compact(width: Dp): Boolean = width < COMPACT_WIDTH
    }
}

val LocalFormFactor = compositionLocalOf { FormFactor(touch = false, compact = false) }

/** Measures the window and provides [LocalFormFactor] to [content]. */
@Composable
fun ProvideFormFactor(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val touch = remember(context) { FormFactor.isTouchDevice(context) }
    // No size of its own: it measures what the window offers and is as big as the content.
    BoxWithConstraints {
        val factor = FormFactor(touch = touch, compact = FormFactor.compact(maxWidth))
        CompositionLocalProvider(LocalFormFactor provides factor, content = content)
    }
}

/** A screen's outer padding: the TV's generous margins, or narrow ones on a compact (phone) screen. */
@Composable
fun Modifier.screenPadding(horizontal: Dp, vertical: Dp): Modifier =
    if (LocalFormFactor.current.compact) padding(horizontal = 12.dp, vertical = 10.dp) else padding(horizontal = horizontal, vertical = vertical)

/** [tv] on the TV, [touch] on a phone or tablet: for hints that name the remote's keys. */
@Composable
fun hint(tv: String, touch: String): String = if (LocalFormFactor.current.touch) touch else tv
