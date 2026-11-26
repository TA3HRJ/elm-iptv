package io.github.ta3hrj.elm.data

import android.content.SharedPreferences
import io.github.ta3hrj.elm.core.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Collator

enum class ChannelOrder { PLAYLIST, NUMBER, NAME }

enum class GroupOrder { PLAYLIST, NAME }

/** Order of films and series in a category. */
enum class VodOrder { PLAYLIST, ADDED_NEW, ADDED_OLD, NAME, NAME_DESC, RATING, YEAR, YEAR_OLD }

fun sortVod(list: List<VodItemEntity>, order: VodOrder): List<VodItemEntity> = when (order) {
    VodOrder.PLAYLIST -> list
    VodOrder.NAME -> {
        val collator = nameCollator()
        list.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
    VodOrder.NAME_DESC -> {
        val collator = nameCollator()
        list.sortedWith { a, b -> collator.compare(b.name, a.name) }
    }
    // Highest first; items without a rating / year / date go last.
    VodOrder.RATING -> list.sortedByDescending { it.rating?.replace(',', '.')?.toDoubleOrNull() ?: -1.0 }
    VodOrder.YEAR -> list.sortedByDescending { it.year?.take(4)?.toIntOrNull() ?: -1 }
    VodOrder.YEAR_OLD -> list.sortedWith(compareBy<VodItemEntity> { it.year?.take(4)?.toIntOrNull() == null }.thenBy { it.year?.take(4)?.toIntOrNull() ?: 0 })
    VodOrder.ADDED_NEW -> list.sortedWith(compareBy<VodItemEntity> { it.added == null }.thenByDescending { it.added ?: 0L })
    VodOrder.ADDED_OLD -> list.sortedWith(compareBy<VodItemEntity> { it.added == null }.thenBy { it.added ?: 0L })
}

/** Subtitle look. [size]: 0 small … 3 extra large; [color]: 0 white, 1 yellow; [background]: 0 none, 1 translucent, 2 black. */
data class SubtitleStyle(val size: Int = 1, val color: Int = 0, val background: Int = 1)

/** User preferences that several screens (and the player) read. */
class AppSettings(private val prefs: SharedPreferences) {
    private val _channelOrder = MutableStateFlow(enumOf(KEY_CHANNEL_ORDER, ChannelOrder.PLAYLIST))
    val channelOrder: StateFlow<ChannelOrder> = _channelOrder.asStateFlow()

    private val _groupOrder = MutableStateFlow(enumOf(KEY_GROUP_ORDER, GroupOrder.PLAYLIST))
    val groupOrder: StateFlow<GroupOrder> = _groupOrder.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(
        SubtitleStyle(prefs.getInt(KEY_SUB_SIZE, 1), prefs.getInt(KEY_SUB_COLOR, 0), prefs.getInt(KEY_SUB_BACKGROUND, 1)),
    )
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    private val _autoRefreshHours = MutableStateFlow(prefs.getInt(KEY_AUTO_REFRESH, 12))
    /** Playlist update interval while the app is open; 0 = never. */
    val autoRefreshHours: StateFlow<Int> = _autoRefreshHours.asStateFlow()

    private val _protectSettings = MutableStateFlow(prefs.getBoolean(KEY_PROTECT_SETTINGS, false))
    /** Ask for the parental PIN before opening Settings. */
    val protectSettings: StateFlow<Boolean> = _protectSettings.asStateFlow()

    private val _uiSize = MutableStateFlow(prefs.getInt(KEY_UI_SIZE, UI_SIZE_DEFAULT).coerceIn(0, UI_SCALES.lastIndex))
    /** Size of text and screen elements: 0 compact … 3 extra large (see [UI_SCALES]). */
    val uiSize: StateFlow<Int> = _uiSize.asStateFlow()

    fun setUiSize(size: Int) {
        prefs.edit().putInt(KEY_UI_SIZE, size).apply()
        _uiSize.value = size
    }

    private val _backgroundUpdate = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_UPDATE, true))
    /** Update channels and the guide once a day even when the app is not opened. */
    val backgroundUpdate: StateFlow<Boolean> = _backgroundUpdate.asStateFlow()

    fun setBackgroundUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_UPDATE, enabled).apply()
        _backgroundUpdate.value = enabled
    }

    private val _homePreview = MutableStateFlow(prefs.getBoolean(KEY_HOME_PREVIEW, true))
    /** Live preview and stream information beside the channel list (when the screen is wide enough). */
    val homePreview: StateFlow<Boolean> = _homePreview.asStateFlow()

    fun setHomePreview(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOME_PREVIEW, enabled).apply()
        _homePreview.value = enabled
    }

    private val _audioProfile = MutableStateFlow(prefs.getInt(KEY_AUDIO_PROFILE, AUDIO_PROFILE_GENERAL))
    /**
     * How the sound is tagged for the TV and the device: [AUDIO_PROFILE_GENERAL] (no content type) or
     * [AUDIO_PROFILE_MOVIE]. Some TVs and Fire TV models process "movie" sound differently, which can be
     * heard as a thinner or fuller sound; applies to the next player that starts.
     */
    val audioProfile: StateFlow<Int> = _audioProfile.asStateFlow()

    fun setAudioProfile(profile: Int) {
        prefs.edit().putInt(KEY_AUDIO_PROFILE, profile).apply()
        _audioProfile.value = profile
    }

    private val _audioDelayMs = MutableStateFlow(prefs.getInt(KEY_AUDIO_DELAY, 0))
    /** Lip sync: positive plays the sound later, negative earlier (milliseconds). */
    val audioDelayMs: StateFlow<Int> = _audioDelayMs.asStateFlow()

    fun setAudioDelayMs(ms: Int) {
        prefs.edit().putInt(KEY_AUDIO_DELAY, ms).apply()
        _audioDelayMs.value = ms
    }

    private val _vodOrder = MutableStateFlow(enumOf(KEY_VOD_ORDER, VodOrder.PLAYLIST))
    val vodOrder: StateFlow<VodOrder> = _vodOrder.asStateFlow()

    fun setVodOrder(order: VodOrder) {
        prefs.edit().putString(KEY_VOD_ORDER, order.name).apply()
        _vodOrder.value = order
    }

    private val _bufferSize = MutableStateFlow(prefs.getInt(KEY_BUFFER, 1))
    /** Live buffer: 0 small (fast start), 1 normal, 2 large (weak connections). */
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    fun setBufferSize(size: Int) {
        prefs.edit().putInt(KEY_BUFFER, size).apply()
        _bufferSize.value = size
    }

    private val _idleHours = MutableStateFlow(prefs.getInt(KEY_IDLE_HOURS, 4))
    /** Ask "still watching?" after this many hours without a key press; 0 = never. */
    val idleHours: StateFlow<Int> = _idleHours.asStateFlow()

    fun setIdleHours(hours: Int) {
        prefs.edit().putInt(KEY_IDLE_HOURS, hours).apply()
        _idleHours.value = hours
    }

    private val _frameRateMatching = MutableStateFlow(prefs.getBoolean(KEY_FRAME_RATE, false))
    /** Switch the TV's refresh rate to suit the video (50 Hz for European broadcasts, 24 Hz for films). */
    val frameRateMatching: StateFlow<Boolean> = _frameRateMatching.asStateFlow()

    fun setFrameRateMatching(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FRAME_RATE, enabled).apply()
        _frameRateMatching.value = enabled
    }

    private val _adultLock = MutableStateFlow(prefs.getBoolean(KEY_ADULT_LOCK, true))
    /** Lock (or, without a PIN, hide) categories that look adult. */
    val adultLock: StateFlow<Boolean> = _adultLock.asStateFlow()

    fun setAdultLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ADULT_LOCK, enabled).apply()
        _adultLock.value = enabled
    }

    /** "scope|name" of adult categories already handled automatically, per playlist. */
    fun adultHandled(playlistId: Long): Set<String> = prefs.getStringSet("$KEY_ADULT_HANDLED$playlistId", null).orEmpty().toSet()

    /** The subset of [adultHandled] that was hidden because no PIN was set. */
    fun adultHidden(playlistId: Long): Set<String> = prefs.getStringSet("$KEY_ADULT_HIDDEN$playlistId", null).orEmpty().toSet()

    fun setAdultState(playlistId: Long, handled: Set<String>, hidden: Set<String>) {
        prefs.edit()
            .putStringSet("$KEY_ADULT_HANDLED$playlistId", HashSet(handled))
            .putStringSet("$KEY_ADULT_HIDDEN$playlistId", HashSet(hidden))
            .apply()
    }

    fun setChannelOrder(order: ChannelOrder) {
        prefs.edit().putString(KEY_CHANNEL_ORDER, order.name).apply()
        _channelOrder.value = order
    }

    fun setGroupOrder(order: GroupOrder) {
        prefs.edit().putString(KEY_GROUP_ORDER, order.name).apply()
        _groupOrder.value = order
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        prefs.edit()
            .putInt(KEY_SUB_SIZE, style.size)
            .putInt(KEY_SUB_COLOR, style.color)
            .putInt(KEY_SUB_BACKGROUND, style.background)
            .apply()
        _subtitleStyle.value = style
    }

    fun setAutoRefreshHours(hours: Int) {
        prefs.edit().putInt(KEY_AUTO_REFRESH, hours).apply()
        _autoRefreshHours.value = hours
    }

    fun setProtectSettings(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROTECT_SETTINGS, enabled).apply()
        _protectSettings.value = enabled
    }

    /** Minutes added to every programme time of a playlist's guide, for guides in the wrong time zone. */
    fun epgOffsetMinutes(playlistId: Long): Int = prefs.getInt("$KEY_EPG_OFFSET$playlistId", 0)

    fun setEpgOffsetMinutes(playlistId: Long, minutes: Int) {
        prefs.edit().putInt("$KEY_EPG_OFFSET$playlistId", minutes).apply()
    }

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null) ?: default.name) }.getOrElse { default }

    companion object {
        private const val KEY_CHANNEL_ORDER = "channel_order"
        private const val KEY_GROUP_ORDER = "group_order"
        private const val KEY_SUB_SIZE = "subtitle_size"
        private const val KEY_SUB_COLOR = "subtitle_color"
        private const val KEY_SUB_BACKGROUND = "subtitle_background"
        private const val KEY_AUTO_REFRESH = "auto_refresh_hours"
        private const val KEY_PROTECT_SETTINGS = "protect_settings"
        private const val KEY_EPG_OFFSET = "epg_offset_"
        private const val KEY_ADULT_LOCK = "adult_lock"
        private const val KEY_FRAME_RATE = "frame_rate_matching"
        private const val KEY_BUFFER = "buffer_size"
        private const val KEY_IDLE_HOURS = "idle_hours"
        private const val KEY_UI_SIZE = "ui_size"
        private const val KEY_VOD_ORDER = "vod_order"
        private const val KEY_BACKGROUND_UPDATE = "background_update"
        private const val KEY_HOME_PREVIEW = "home_preview"
        private const val KEY_AUDIO_PROFILE = "audio_profile"
        const val AUDIO_PROFILE_GENERAL = 0
        const val AUDIO_PROFILE_MOVIE = 1
        private const val KEY_AUDIO_DELAY = "audio_delay_ms"
        val AUDIO_DELAY_CHOICES = listOf(-500, -300, -200, -100, -50, 0, 50, 100, 200, 300, 500)
        /**
         * Scale of everything drawn (text, rows, spacing). 1.0 is the look before 0.9.3; the default is
         * a little smaller so more rows fit and the screen is easier to take in.
         */
        val UI_SCALES = listOf(0.75f, 0.85f, 1f, 1.15f)
        const val UI_SIZE_DEFAULT = 1
        val IDLE_CHOICES = listOf(0, 2, 3, 4, 6)
        private const val KEY_ADULT_HANDLED = "adult_handled_"
        private const val KEY_ADULT_HIDDEN = "adult_hidden_"
        val AUTO_REFRESH_CHOICES = listOf(0, 6, 12, 24)
        const val EPG_OFFSET_LIMIT_MINUTES = 12 * 60
    }
}

/** Case- and accent-insensitive comparison in the app language ("Çocuk" sorts with "C"). */
fun nameCollator(): Collator = Collator.getInstance(Lang.locale).apply { strength = Collator.PRIMARY }

fun sortChannels(list: List<ChannelEntity>, order: ChannelOrder): List<ChannelEntity> = when (order) {
    ChannelOrder.PLAYLIST -> list
    ChannelOrder.NUMBER -> list.sortedWith(compareBy<ChannelEntity> { it.number }.thenBy { it.position })
    ChannelOrder.NAME -> {
        val collator = nameCollator()
        list.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}

/**
 * Puts all channels in category order, then channel order within each category.
 * With both orders set to "playlist" the list is returned as it is.
 */
fun arrangeChannels(list: List<ChannelEntity>, channelOrder: ChannelOrder, groupOrder: GroupOrder): List<ChannelEntity> {
    if (channelOrder == ChannelOrder.PLAYLIST && groupOrder == GroupOrder.PLAYLIST) return list
    val groups = LinkedHashMap<String, MutableList<ChannelEntity>>()
    for (channel in list) groups.getOrPut(channel.groupTitle) { ArrayList() }.add(channel)
    val names = if (groupOrder == GroupOrder.NAME) {
        val collator = nameCollator()
        groups.keys.sortedWith { a, b -> collator.compare(a, b) }
    } else {
        groups.keys.toList()
    }
    return names.flatMap { sortChannels(groups.getValue(it), channelOrder) }
}

/** How a [SubtitleStyle] is drawn; shared by the Settings preview and the player. */
object SubtitleStyles {
    fun scale(size: Int): Float = when (size) {
        0 -> 0.8f
        2 -> 1.3f
        3 -> 1.6f
        else -> 1f
    }

    fun foreground(style: SubtitleStyle): Int = if (style.color == 1) 0xFFFFE14D.toInt() else 0xFFFFFFFF.toInt()

    fun background(style: SubtitleStyle): Int = when (style.background) {
        0 -> 0x00000000
        2 -> 0xFF000000.toInt()
        else -> 0x99000000.toInt()
    }
}

