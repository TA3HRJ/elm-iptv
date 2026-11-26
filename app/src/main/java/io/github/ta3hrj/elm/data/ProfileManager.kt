package io.github.ta3hrj.elm.data

import android.content.SharedPreferences
import io.github.ta3hrj.elm.core.tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What a profile may see. A kids profile never sees locked categories (not even after the PIN was
 * entered) and, when categories were chosen, only those. `null` = every category of that kind.
 */
data class ContentFilter(
    val kids: Boolean = false,
    val live: Set<String>? = null,
    val movies: Set<String>? = null,
    val series: Set<String>? = null,
) {
    fun allows(scope: String, category: String): Boolean {
        if (!kids) return true
        val allowed = when (scope) {
            FlagScopes.LIVE -> live
            FlagScopes.MOVIE -> movies
            FlagScopes.SERIES -> series
            else -> null
        }
        return allowed == null || category in allowed
    }

    companion object {
        val NONE = ContentFilter()
    }
}

/** Category sets are stored one name per line; null = all, "" = none. */
fun decodeCategories(value: String?): Set<String>? =
    value?.let { if (it.isEmpty()) emptySet() else it.split('\n').toSet() }

fun encodeCategories(value: Set<String>?): String? = value?.joinToString("\n")

val ProfileEntity.filter: ContentFilter
    get() = if (!kids) ContentFilter.NONE else ContentFilter(true, decodeCategories(allowedLive), decodeCategories(allowedMovies), decodeCategories(allowedSeries))

val ProfileEntity.displayName: String
    get() = name.ifBlank { tx("Ana profil", "Main profile") }

/**
 * Profiles: each has its own favorites, lists, history, watchlist and progress. The active profile
 * survives restarts. Its kids flag and categories are also kept in preferences, so restrictions are in
 * force from the very first frame, before the database has answered.
 */
class ProfileManager(
    private val db: AppDatabase,
    private val prefs: SharedPreferences,
    private val lock: ParentalLock,
    scope: CoroutineScope,
) {
    private val dao = db.profiles()

    val profiles: StateFlow<List<ProfileEntity>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _active = MutableStateFlow(cachedActive())
    /** The profile in use. */
    val active: StateFlow<ProfileEntity> = _active.asStateFlow()

    val activeId: Long get() = _active.value.id

    val filter: StateFlow<ContentFilter> =
        _active.map { it.filter }.stateIn(scope, SharingStarted.Eagerly, _active.value.filter)

    /** Locked content may be shown: the PIN was entered (or none is set) and this is not a kids profile. */
    val contentUnlocked: StateFlow<Boolean> =
        combine(lock.unlocked, _active) { unlocked, profile -> unlocked && !profile.kids }
            .stateIn(scope, SharingStarted.Eagerly, lock.unlocked.value && !_active.value.kids)

    init {
        // Keep the active profile in step with edits (and fall back to the main profile if it was deleted).
        scope.launch {
            profiles.collect { list ->
                if (list.isEmpty()) return@collect
                val current = list.firstOrNull { it.id == _active.value.id } ?: list.firstOrNull { it.id == MAIN_ID } ?: list.first()
                if (current != _active.value) setActive(current)
            }
        }
    }

    /** Synchronous form of [contentUnlocked], for decisions taken right after a profile switch or lock. */
    val contentUnlockedNow: Boolean get() = lock.unlocked.value && !_active.value.kids

    /** Several profiles exist, so the app asks who is watching at start (if enabled). */
    val hasSeveral: Boolean get() = profiles.value.size > 1

    val hasKids: Boolean get() = profiles.value.any { it.kids }

    /**
     * Leaving a kids profile (or picking any adult profile while a kids profile exists) needs the PIN,
     * otherwise children could simply switch profile.
     */
    fun needsPin(target: ProfileEntity): Boolean =
        lock.hasPin() && !target.kids && target.id != _active.value.id && (hasKids || _active.value.kids)

    fun select(profile: ProfileEntity) {
        setActive(profile)
        // A PIN typed for an adult profile must not stay valid after handing the remote to a child.
        if (profile.kids) lock.lock()
    }

    suspend fun create(name: String, color: Int, kids: Boolean, filter: ContentFilter?): Long {
        val position = (profiles.value.maxOfOrNull { it.position } ?: 0) + 1
        return dao.insert(
            ProfileEntity(
                name = name.trim(), color = color, kids = kids, position = position,
                allowedLive = encodeCategories(filter?.live),
                allowedMovies = encodeCategories(filter?.movies),
                allowedSeries = encodeCategories(filter?.series),
            ),
        )
    }

    suspend fun update(profile: ProfileEntity) = dao.update(profile.copy(name = profile.name.trim()))

    /** The main profile cannot be deleted; the others take their favorites, lists and history with them. */
    suspend fun delete(profile: ProfileEntity) {
        if (profile.id == MAIN_ID) return
        db.lists().deleteProfileLists(profile.id)
        dao.delete(profile.id)
    }

    suspend fun get(id: Long): ProfileEntity? = dao.get(id)

    private fun setActive(profile: ProfileEntity) {
        prefs.edit()
            .putLong(KEY_ACTIVE, profile.id)
            .putBoolean(KEY_KIDS, profile.kids)
            .putString(KEY_LIVE, profile.allowedLive)
            .putString(KEY_MOVIES, profile.allowedMovies)
            .putString(KEY_SERIES, profile.allowedSeries)
            .apply()
        _active.value = profile
    }

    private fun cachedActive() = ProfileEntity(
        id = prefs.getLong(KEY_ACTIVE, MAIN_ID),
        name = "",
        color = DEFAULT_COLOR,
        kids = prefs.getBoolean(KEY_KIDS, false),
        allowedLive = prefs.getString(KEY_LIVE, null),
        allowedMovies = prefs.getString(KEY_MOVIES, null),
        allowedSeries = prefs.getString(KEY_SERIES, null),
    )

    companion object {
        const val MAIN_ID = 1L
        const val DEFAULT_COLOR = -11557889 // 0xFF4FA3FF, the app's accent blue
        private const val KEY_ACTIVE = "active_profile"
        private const val KEY_KIDS = "active_profile_kids"
        private const val KEY_LIVE = "active_profile_live"
        private const val KEY_MOVIES = "active_profile_movies"
        private const val KEY_SERIES = "active_profile_series"

        /** Avatar colours to choose from. */
        val COLORS = listOf(
            0xFF4FA3FF.toInt(), 0xFFFF7A59.toInt(), 0xFF5BD68A.toInt(), 0xFFFFC94D.toInt(),
            0xFFB07CFF.toInt(), 0xFFFF6FAE.toInt(), 0xFF3FD0D4.toInt(), 0xFF9AA4B2.toInt(),
        )

        /** Category names that look meant for children, offered as a starting point for kids profiles. */
        fun looksForKids(name: String): Boolean {
            val n = name.lowercase()
            return KIDS_WORDS.any { it in n }
        }

        private val KIDS_WORDS = listOf(
            "kid", "child", "çocuk", "cocuk", "cartoon", "çizgi", "cizgi", "anime", "animation", "animasyon",
            "family", "aile", "junior", "disney", "nick", "baby", "bebek", "toon", "minika", "trt çocuk", "kinder",
        )
    }
}
