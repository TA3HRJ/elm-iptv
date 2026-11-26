package io.github.ta3hrj.elm.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import io.github.ta3hrj.elm.core.tx
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import io.github.ta3hrj.elm.R

// ---- Icons and labels ----------------------------------------------------------------------

/** Button content: an icon followed by a label. */
@Composable
fun IconLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

// ---- Focus styling -------------------------------------------------------------------------

private val FocusShape = RoundedCornerShape(8.dp)

/** Focused list rows grow slightly and get an accent outline and glow. */
@Composable
fun listItemFocusScale() = ListItemDefaults.scale(focusedScale = 1.03f)

@Composable
fun listItemFocusBorder() = ListItemDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, Accent), shape = FocusShape),
)

/** Focused rows turn deep blue with white text, so coloured text (channel numbers) stays readable. */
@Composable
fun listItemFocusColors() = ListItemDefaults.colors(
    focusedContainerColor = FocusedRow,
    focusedContentColor = Color.White,
    focusedSelectedContainerColor = FocusedRow,
    focusedSelectedContentColor = Color.White,
    pressedContainerColor = FocusedRow,
    pressedContentColor = Color.White,
    pressedSelectedContainerColor = FocusedRow,
    pressedSelectedContentColor = Color.White,
)

private val FocusedRow = Color(0xFF1F4E86)

@Composable
fun listItemFocusGlow() = ListItemDefaults.glow(focusedGlow = Glow(Accent.copy(alpha = 0.45f), 10.dp))

@Composable
fun surfaceFocusBorder(shape: RoundedCornerShape = FocusShape) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
)

// ---- Channel logos -------------------------------------------------------------------------

private val BadgeColors = listOf(
    Color(0xFF2F6FB5), Color(0xFF8E44AD), Color(0xFF16A085), Color(0xFFC0392B),
    Color(0xFFD35400), Color(0xFF2C3E91), Color(0xFF7F8C2D), Color(0xFFB03A6E),
)

/**
 * A short label for a channel without a logo: "Show TV" -> "ST", "Kanal 14 HD" -> "K14", "TRT 1" -> "TRT1".
 * A channel number is kept whole so that "Kanal 14" and "Kanal 16" do not both become "K1".
 */
fun initials(name: String): String {
    // "UK: BBC One" -> "BBC One"; quality tags such as HD or FHD don't count ("3E HD" -> "3E").
    val withoutPrefix = name.substringAfter(':', name).substringAfter('|').trim().ifEmpty { name }
    val words = withoutPrefix.split(' ', '-', '_', '.', '|', '(', ')', '[', ']').filter { it.isNotBlank() }
    val meaningful = words.filter { it.uppercase(java.util.Locale.ROOT) !in QualityTags }.ifEmpty { words }
    val first = meaningful.getOrNull(0)
    val second = meaningful.getOrNull(1)
    val letters = when {
        first == null -> "?"
        second != null && second.all { it.isDigit() } -> {
            // A short all-capitals name stays whole before its number ("TRT 1"); other names give one letter.
            val head = if (first.length <= 3 && first.all { it.isUpperCase() || it.isDigit() }) first else first.take(1)
            head + second.take(3)
        }
        second != null -> "${first.first()}${second.first()}"
        else -> first.take(2)
    }
    return letters.take(4).uppercase(io.github.ta3hrj.elm.core.Lang.locale)
}

/** Logo addresses that could not be loaded since the app started. */
private val FailedLogos: MutableSet<String> = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

private val QualityTags = setOf("HD", "FHD", "UHD", "SD", "4K", "8K", "HEVC", "H265", "H.265", "RAW", "50FPS", "60FPS", "HDR", "LQ", "HQ", "+1")

/**
 * The channel's logo, or a coloured badge with its initials when it has none or the image can't be
 * loaded (many playlists carry dead logo links).
 */
@Composable
fun ChannelLogo(name: String, logo: String?, width: Dp, height: Dp, fontSize: TextUnit = 14.sp) {
    val shape = RoundedCornerShape(5.dp)
    // A logo address that failed once is not tried again while the app runs: dead logo servers often
    // only time out, which kept rows blank and held up the other logos.
    var failed by remember(logo) { mutableStateOf(logo.isNullOrBlank() || logo in FailedLogos) }
    var loaded by remember(logo) { mutableStateOf(false) }
    if (failed) {
        val color = BadgeColors[(name.hashCode() and Int.MAX_VALUE) % BadgeColors.size]
        Box(
            Modifier.size(width = width, height = height).clip(shape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize)
        }
    } else {
        Box(
            Modifier.size(width = width, height = height).clip(shape).background(Color(0xFF1F2630)),
            contentAlignment = Alignment.Center,
        ) {
            // The initials show faintly until the logo arrives, so a list never looks empty.
            if (!loaded) Text(initials(name), color = Color.White.copy(alpha = 0.35f), fontWeight = FontWeight.Bold, fontSize = fontSize)
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = {
                    logo?.let { FailedLogos += it }
                    failed = true
                },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
    }
}

// ---- Loading placeholders ------------------------------------------------------------------

/** A softly pulsing grey fill for content that is still loading. */
@Composable
fun Modifier.shimmer(shape: RoundedCornerShape = RoundedCornerShape(6.dp)): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    return clip(shape).background(Color(0xFF2A3341).copy(alpha = alpha))
}

/** Placeholder rows shown while a list loads. */
@Composable
fun SkeletonList(rows: Int = 8, rowHeight: Dp = 56.dp, withLogo: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { i ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(rowHeight)) {
                if (withLogo) {
                    Box(Modifier.size(width = 64.dp, height = 40.dp).shimmer())
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.55f else 0.4f).height(14.dp).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(10.dp).shimmer())
                }
            }
        }
    }
}

/** Placeholder poster cards shown while films or series load. */
@Composable
fun SkeletonPosters(count: Int = 12) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat((count + 5) / 6) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { Box(Modifier.width(150.dp).aspectRatio(2f / 3f).shimmer(RoundedCornerShape(8.dp))) }
            }
        }
    }
}

// ---- Ambient backdrop ----------------------------------------------------------------------

/**
 * A heavily blurred, dimmed copy of [url] that fills the screen behind the content.
 * The image is decoded at a tiny size and stretched, which blurs it on every Android version
 * (Modifier.blur alone only works on Android 12+, while most TV sticks run 7–11).
 */
@Composable
fun AmbientBackdrop(url: String?, alpha: Float = 0.35f) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context).data(url).size(24, 14).precision(Precision.EXACT).crossfade(600).build()
    }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize().alpha(alpha)
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(30.dp) else Modifier),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(AppBackground.copy(alpha = 0.55f), AppBackground.copy(alpha = 0.92f))),
            ),
        )
    }
}

// ---- Splash --------------------------------------------------------------------------------

/** Shown while the app reads its database on start: the emblem grows in and the name fades in. */
@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.8f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        textAlpha.animateTo(1f, tween(700, delayMillis = 250))
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF16263D), AppBackground), radius = 1400f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.elm_emblem),
                contentDescription = null,
                modifier = Modifier.size(150.dp).scale(scale.value),
            )
            Spacer(Modifier.height(22.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(textAlpha.value)) {
                Text("ELM IPTV", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Every Live Moment",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9AA4B2),
                )
            }
        }
    }
}

// ---- Position bar ---------------------------------------------------------------------------

/**
 * Thin bar on the right of a long list showing where the visible part is (like a scrollbar, but only
 * an indicator: the remote moves through the list). Hidden when everything fits.
 */
@Composable
fun ListScrollbar(state: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier) {
    val position by androidx.compose.runtime.remember(state) {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty() || visible.size >= total) {
                null
            } else {
                val firstSize = visible.first().size.coerceAtLeast(1)
                val first = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset.toFloat() / firstSize
                val fraction = visible.size.toFloat() / total
                val start = (first / (total - visible.size).coerceAtLeast(1)).coerceIn(0f, 1f)
                start to fraction
            }
        }
    }
    val (start, fraction) = position ?: return
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .width(5.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x1FFFFFFF)),
    ) {
        val thumb = (maxHeight * fraction).coerceAtLeast(28.dp).coerceAtMost(maxHeight)
        Box(
            Modifier
                .offset(y = (maxHeight - thumb) * start)
                .height(thumb)
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Accent.copy(alpha = 0.85f)),
        )
    }
}

/** The app emblem and name; opens the About screen. */
@Composable
fun LogoButton(subtitle: String?, onClick: () -> Unit, showName: Boolean = true) {
    io.github.ta3hrj.elm.ui.touch.Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0x223B82F6)),
        border = surfaceFocusBorder(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(io.github.ta3hrj.elm.R.drawable.elm_emblem),
                contentDescription = tx("ELM IPTV hakkında", "About ELM IPTV"),
                modifier = Modifier.size(44.dp),
            )
            if (showName) androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            if (showName) androidx.compose.foundation.layout.Column(Modifier.widthIn(max = 200.dp)) {
                androidx.tv.material3.Text("ELM IPTV", style = androidx.tv.material3.MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                subtitle?.let {
                    androidx.tv.material3.Text(
                        it,
                        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9AA4B2),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Time and date for screen headers; follows the device's 12/24-hour setting and ticks on the minute. */
@Composable
fun HeaderClock(modifier: Modifier = Modifier, showDate: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000 - now % 60_000 + 50)
            now = System.currentTimeMillis()
        }
    }
    val locale = io.github.ta3hrj.elm.core.Lang.locale
    val time = java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
    // Short date: the headers are already full of buttons.
    val date = java.text.SimpleDateFormat("d MMM EEE", locale)
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.End) {
        androidx.tv.material3.Text(time.format(java.util.Date(now)), style = androidx.tv.material3.MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        if (showDate) {
            androidx.tv.material3.Text(date.format(java.util.Date(now)), style = androidx.tv.material3.MaterialTheme.typography.labelSmall, color = Color(0xFF9AA4B2), maxLines = 1)
        }
    }
}

private val headerFitCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

/**
 * How much a header must shrink to fit: 0 = full labels, 1 = icon-only buttons, 2 = also no logo
 * text. Measured from the header's scroll row: whenever its content is wider than the room it has,
 * the level goes up one step. The level is remembered per screen and width, so going back to a screen
 * does not flash the wider layout again.
 */
@Composable
fun rememberHeaderFit(screen: String, width: androidx.compose.ui.unit.Dp, scroll: androidx.compose.foundation.ScrollState): Int {
    val key = "$screen:${width.value.toInt()}"
    var level by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableIntStateOf(headerFitCache[key] ?: 0) }
    androidx.compose.runtime.LaunchedEffect(key) {
        androidx.compose.runtime.snapshotFlow { scroll.maxValue }.collect { overflow ->
            // Int.MAX_VALUE means "not measured yet".
            if (overflow in 1 until Int.MAX_VALUE && level < 2) {
                level++
                headerFitCache[key] = level
            }
        }
    }
    return level
}

/**
 * A screen header that never loses its right-hand button (Settings, where the text size is changed back):
 * [leading] on the left, [nav] in a row that scrolls when it does not fit, [trailing] always on screen.
 * The `fit` level (see [rememberHeaderFit]) tells the parts how much to shrink; `nav` gets
 * `compact = fit >= 1`.
 */
@Composable
fun AdaptiveHeader(
    screen: String,
    leading: @Composable (fit: Int) -> Unit,
    nav: @Composable (compact: Boolean) -> Unit,
    trailing: @Composable (fit: Int) -> Unit,
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scroll = androidx.compose.foundation.rememberScrollState()
        val fit = rememberHeaderFit(screen, maxWidth, scroll)
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            leading(fit)
            androidx.compose.foundation.layout.Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                androidx.compose.foundation.layout.Row(
                    Modifier.horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
                ) {
                    nav(fit >= 1)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(spacing))
            trailing(fit)
        }
    }
}

/**
 * A choice button: filled with a check mark when [selected], outlined otherwise. It is always the same
 * button with a different look. Swapping between `Button` and `OutlinedButton` instead replaces the button
 * that has the focus, and the focus then falls to the first item of the screen (in Settings, the section
 * menu, which opened "Accounts and system").
 */
@Composable
fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, check: Boolean = true, onClick: () -> Unit) {
    io.github.ta3hrj.elm.ui.touch.Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) androidx.tv.material3.ButtonDefaults.colors() else androidx.tv.material3.OutlinedButtonDefaults.colors(),
        border = if (selected) androidx.tv.material3.ButtonDefaults.border() else androidx.tv.material3.OutlinedButtonDefaults.border(),
    ) { androidx.tv.material3.Text(if (selected && check) "✓ $text" else text) }
}

/**
 * Down on the last item of a list goes round to its first item. Only for a single press: holding the key
 * stops at the end instead of looping. [first] must be attached to the list's first item; [enabled] lets a
 * screen mode (such as moving a channel) keep its own Down key.
 */
@Composable
fun Modifier.wrapDown(listState: androidx.compose.foundation.lazy.LazyListState, first: androidx.compose.ui.focus.FocusRequester, enabled: Boolean = true): Modifier {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        if (!enabled || event.key != androidx.compose.ui.input.key.Key.DirectionDown) return@onPreviewKeyEvent false
        if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
            !focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) &&
            event.nativeKeyEvent.repeatCount == 0
        ) {
            scope.launch {
                listState.scrollToItem(0)
                // The first item is composed by now; one more frame is enough for it to be attached.
                repeat(3) {
                    if (runCatching { first.requestFocus() }.isSuccess) return@launch
                    androidx.compose.runtime.withFrameNanos { }
                }
            }
        }
        true
    }
}
