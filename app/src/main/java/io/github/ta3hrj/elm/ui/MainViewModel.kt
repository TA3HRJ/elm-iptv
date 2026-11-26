package io.github.ta3hrj.elm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ContentFilter
import io.github.ta3hrj.elm.data.ProfileEntity
import io.github.ta3hrj.elm.data.ProfileManager
import io.github.ta3hrj.elm.data.displayName
import io.github.ta3hrj.elm.data.encodeCategories
import io.github.ta3hrj.elm.data.ChannelProgramme
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.GroupFlagEntity
import io.github.ta3hrj.elm.data.GuideRepository
import io.github.ta3hrj.elm.data.HiddenChannelEntity
import io.github.ta3hrj.elm.data.PlaylistType
import io.github.ta3hrj.elm.data.VodItemEntity
import io.github.ta3hrj.elm.data.VodKinds
import io.github.ta3hrj.elm.data.VodRepository
import io.github.ta3hrj.elm.data.WatchProgressEntity
import io.github.ta3hrj.elm.PREF_LANGUAGE
import io.github.ta3hrj.elm.core.Catchup
import io.github.ta3hrj.elm.core.Lang
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.RecordingEntity
import io.github.ta3hrj.elm.core.Episode
import io.github.ta3hrj.elm.player.PlayerActivity
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.GroupRow
import io.github.ta3hrj.elm.data.PlaylistEntity
import io.github.ta3hrj.elm.data.toSource
import io.github.ta3hrj.elm.data.FavoriteListRow
import io.github.ta3hrj.elm.data.ChannelOrder
import io.github.ta3hrj.elm.data.GroupOrder
import io.github.ta3hrj.elm.data.SubtitleStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A row in the left-hand category column. */
data class GroupItem(val title: String, val count: Int, val query: ChannelQuery, val locked: Boolean = false)

private data class VodQuery(
    val playlistId: Long?,
    val kind: String,
    val category: String?,
    val search: String,
    val unlocked: Boolean,
    val categoryLocked: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val repository = container.repository
    private val guide = container.guide
    private val vod = container.vod
    private val reminders = container.reminders
    private val prefs = container.prefs

    val playlists: StateFlow<List<PlaylistEntity>?> = repository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedPlaylistId = MutableStateFlow(prefs.getLong(KEY_PLAYLIST, -1L).takeIf { it > 0 })

    /** The selected playlist, falling back to the first one if the saved id no longer exists. */
    val currentPlaylist: StateFlow<PlaylistEntity?> = combine(playlists, _selectedPlaylistId) { list, id ->
        list?.firstOrNull { it.id == id } ?: list?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val favoriteUrls: StateFlow<Set<String>> = currentPlaylist
        .flatMapLatest { p -> p?.let { repository.observeFavoriteUrls(it.id) } ?: flowOf(emptyList()) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favorites: StateFlow<Set<String>> = favoriteUrls

    private val parentalLock = container.parentalLock
    private val profileManager = container.profiles
    /** True when locked categories may be shown (never in a kids profile). */
    val unlocked: StateFlow<Boolean> = profileManager.contentUnlocked
    val pinSet: StateFlow<Boolean> = parentalLock.pinSet

    /** Lock/hide flags of the current playlist, by "scope|name". */
    val groupFlags: StateFlow<Map<String, GroupFlagEntity>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeGroupFlags(id) }
        .map { list -> list.associateBy { "${it.scope}|${it.name}" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun isLocked(scope: String, name: String): Boolean = groupFlags.value["$scope|$name"]?.locked == true

    val hiddenChannels: StateFlow<List<HiddenChannelEntity>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeHiddenChannels(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The user's own favorite lists of the current playlist. */
    val favoriteLists: StateFlow<List<FavoriteListRow>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeFavoriteLists(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val recentChannels: StateFlow<List<ChannelEntity>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), unlocked) { id, open -> id to open }
            .flatMapLatest { (id, open) -> if (id == null) flowOf(emptyList()) else repository.observeChannels(id, ChannelQuery.Recent, open) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Channels whose past programmes can be watched (the "Archive" list). */
    val archiveChannels: StateFlow<List<ChannelEntity>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), unlocked) { id, open -> id to open }
            .flatMapLatest { (id, open) -> if (id == null) flowOf(emptyList()) else repository.observeChannels(id, ChannelQuery.Archive, open) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<GroupItem>> = currentPlaylist
        .flatMapLatest { p ->
            if (p == null) {
                flowOf(emptyList())
            } else {
                combine(repository.observeGroups(p.id), favoriteUrls, groupFlags, favoriteLists, combine(recentChannels, archiveChannels) { r, a -> r to a }) { rows: List<GroupRow>, favs, flags, lists, (recent, archive) ->
                    buildList {
                        if (recent.isNotEmpty()) add(GroupItem(tx("🕒 Son izlenenler", "🕒 Recently watched"), recent.size, ChannelQuery.Recent))
                        if (archive.isNotEmpty()) add(GroupItem(tx("⟲ Arşiv", "⟲ Archive"), archive.size, ChannelQuery.Archive))
                        if (favs.isNotEmpty()) add(GroupItem(tx("★ Favoriler", "★ Favorites"), favs.size, ChannelQuery.Favorites))
                        lists.forEach { add(GroupItem("☆ " + it.name, it.count, ChannelQuery.FavoriteList(it.id, it.name))) }
                        rows.forEach {
                            val locked = flags["${FlagScopes.LIVE}|${it.name}"]?.locked == true
                            add(GroupItem(it.name, it.count, ChannelQuery.Group(it.name), locked))
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedQuery = MutableStateFlow<ChannelQuery?>(null)
    val selectedQuery: StateFlow<ChannelQuery?> = _selectedQuery.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    /** Channels for the selected category, or search results while a search is typed. */
    /** True when the selected category is locked and the PIN has not been entered. */
    val selectedLocked: StateFlow<Boolean> =
        combine(groups, _selectedQuery, _searchText, unlocked) { g, q, search, open ->
            val query = if (search.isNotBlank()) null else q ?: g.firstOrNull()?.query
            !open && g.firstOrNull { it.query == query }?.locked == true
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val channels: StateFlow<List<ChannelEntity>> =
        combine(currentPlaylist, groups, _selectedQuery, _searchText.debounce(250), unlocked) { p, g, q, search, open ->
            val query = if (search.isNotBlank()) ChannelQuery.Search(search.trim()) else q ?: g.firstOrNull()?.query
            val locked = query is ChannelQuery.Group && !open && g.firstOrNull { it.query == query }?.locked == true
            Triple(p, query.takeUnless { locked }, open)
        }.flatMapLatest { (p, query, open) ->
            if (p == null || query == null) flowOf(emptyList()) else repository.observeChannels(p.id, query, open)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Parental lock, hiding, settings -------------------------------------------------

    fun unlock(pin: String): Boolean = parentalLock.unlock(pin)

    private val _pendingPinAction = MutableStateFlow<(() -> Unit)?>(null)
    /** An action waiting for the PIN (see [requestPin]). */
    val pendingPinAction: StateFlow<(() -> Unit)?> = _pendingPinAction.asStateFlow()

    fun requestPin(action: () -> Unit) {
        _pendingPinAction.value = action
    }

    fun runPendingPinAction() {
        _pendingPinAction.value?.invoke()
        _pendingPinAction.value = null
    }

    fun cancelPendingPinAction() {
        _pendingPinAction.value = null
    }

    fun lockNow() {
        parentalLock.lock()
        _message.value = tx("Kilitli kategoriler yeniden kilitlendi.", "Locked categories are locked again.")
    }

    fun setPin(pin: String) {
        parentalLock.setPin(pin)
        applyAdultLockEverywhere()
        _message.value = tx("PIN kaydedildi.", "PIN saved.")
    }

    fun clearPin(currentPin: String): Boolean {
        if (!parentalLock.verify(currentPin)) return false
        if (profileManager.hasKids) {
            _message.value = tx("Çocuk profili varken PIN kaldırılamaz.", "The PIN cannot be removed while a kids profile exists.")
            return true
        }
        parentalLock.clearPin()
        applyAdultLockEverywhere()
        _message.value = tx("PIN kaldırıldı; kilitli kategoriler artık açık.", "PIN removed; locked categories are now open.")
        return true
    }

    fun verifyPin(pin: String) = parentalLock.verify(pin)

    /** Locks or unlocks a category. Requires a PIN; unlocking a category also requires entering it. */
    fun setGroupLocked(scope: String, name: String, locked: Boolean) {
        val playlist = currentPlaylist.value ?: return
        if (locked && !parentalLock.hasPin()) {
            _message.value = tx("Önce Ayarlar ekranından bir PIN belirleyin.", "First set a PIN in Settings.")
            return
        }
        viewModelScope.launch {
            repository.setGroupFlag(playlist.id, scope, name, locked = locked)
            if (locked) purgePrivateHistory(playlist.id)
            _message.value = if (locked) tx("\"$name\" kilitlendi.", "\"$name\" locked.") else tx("\"$name\" kilidi kaldırıldı.", "\"$name\" unlocked.")
        }
    }

    fun setGroupHidden(scope: String, name: String, hidden: Boolean) {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch {
            repository.setGroupFlag(playlist.id, scope, name, hidden = hidden)
            if (hidden) purgePrivateHistory(playlist.id)
            _message.value = if (hidden) tx("\"$name\" gizlendi (Ayarlar'dan geri açabilirsiniz).", "\"$name\" hidden (you can show it again in Settings).") else tx("\"$name\" yeniden gösteriliyor.", "\"$name\" is shown again.")
            if (hidden && _selectedQuery.value == ChannelQuery.Group(name)) _selectedQuery.value = null
        }
    }

    fun hideChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.hideChannel(channel)
            _message.value = tx("\"${channel.name}\" gizlendi (Ayarlar'dan geri açabilirsiniz).", "\"${channel.name}\" hidden (you can show it again in Settings).")
        }
    }

    fun unhideChannel(streamUrl: String) {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch { repository.unhideChannel(playlist.id, streamUrl) }
    }

    private val _resumeLastChannel = MutableStateFlow(prefs.getBoolean(KEY_RESUME, true))
    val resumeLastChannel: StateFlow<Boolean> = _resumeLastChannel.asStateFlow()

    fun setResumeLastChannel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESUME, enabled).apply()
        _resumeLastChannel.value = enabled
    }

    /**
     * The channel watched last and the list it was watched in (favorites, an own list, a category…),
     * if "resume on start" is on and the channel is still visible and not locked. The home screen
     * selects that list too, so Back from the player lands where the user was. Called on a fresh start.
     */
    suspend fun channelToResume(): Pair<ChannelEntity, ChannelQuery>? {
        if (!prefs.getBoolean(KEY_RESUME, true)) return null
        val profileId = profileManager.activeId
        val playlistId = prefs.getLong(PlayerActivity.lastPlaylistKey(profileId), -1L).takeIf { it > 0 } ?: return null
        val streamUrl = prefs.getString(PlayerActivity.lastStreamKey(profileId), null) ?: return null
        val channel = repository.channelByStreamUrl(playlistId, streamUrl) ?: return null
        val visible = repository.channels(playlistId, ChannelQuery.Group(channel.groupTitle), unlocked = true)
            .any { it.streamUrl == streamUrl }
        if (!visible) return null
        if (repository.isGroupLocked(playlistId, FlagScopes.LIVE, channel.groupTitle) && !profileManager.contentUnlockedNow) return null
        val saved = PlayerActivity.decodeQuery(prefs.getString(PlayerActivity.lastQueryKey(profileId), null))
        val query = saved?.takeIf { q ->
            repository.channels(playlistId, q, profileManager.contentUnlockedNow).any { it.streamUrl == streamUrl }
        } ?: ChannelQuery.Group(channel.groupTitle)
        // At start the playlist may still be loading; wait briefly so the home screen can select the list.
        val current = currentPlaylist.value ?: kotlinx.coroutines.withTimeoutOrNull(3_000) { currentPlaylist.first { it != null } }
        if (current?.id == playlistId) {
            _searchText.value = ""
            _selectedQuery.value = query
        }
        return channel to query
    }

    /** Opens the player on the channel to resume, in the list it was watched in. */
    suspend fun resumeLastChannel(): Boolean {
        val (channel, query) = channelToResume() ?: return false
        PlayerActivity.start(getApplication(), channel.playlistId, query, channel.id)
        return true
    }

    // ---- Profiles ----------------------------------------------------------------------------

    val profiles: StateFlow<List<ProfileEntity>> = profileManager.profiles
    val activeProfile: StateFlow<ProfileEntity> = profileManager.active

    private val _askProfile = MutableStateFlow(false)
    /** The "Who's watching?" screen is showing. */
    val askProfile: StateFlow<Boolean> = _askProfile.asStateFlow()
    private var resumeAfterPick = false

    private val _askProfileOnStart = MutableStateFlow(prefs.getBoolean(KEY_ASK_PROFILE, true))
    val askProfileOnStart: StateFlow<Boolean> = _askProfileOnStart.asStateFlow()

    fun setAskProfileOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ASK_PROFILE, enabled).apply()
        _askProfileOnStart.value = enabled
    }

    /**
     * On a fresh start with several profiles: shows "Who's watching?" and returns true; the last
     * channel ([resume]) is then continued for the profile that is picked.
     */
    suspend fun askProfileAtStart(resume: Boolean): Boolean {
        if (!_askProfileOnStart.value || container.database.profiles().all().size < 2) return false
        resumeAfterPick = resume
        _askProfile.value = true
        return true
    }

    fun showProfilePicker() {
        resumeAfterPick = false
        _askProfile.value = true
    }

    /** Back on the picker keeps the current profile. */
    fun dismissProfilePicker() {
        _askProfile.value = false
        if (resumeAfterPick) selectProfile(profileManager.active.value)
    }

    fun profileNeedsPin(profile: ProfileEntity) = profileManager.needsPin(profile)

    fun selectProfile(profile: ProfileEntity) {
        val changed = profile.id != profileManager.activeId
        profileManager.select(profile)
        _askProfile.value = false
        if (changed) {
            _selectedQuery.value = null
            _searchText.value = ""
            _message.value = tx("Profil: ${profile.displayName}", "Profile: ${profile.displayName}")
        }
        if (resumeAfterPick) {
            resumeAfterPick = false
            viewModelScope.launch {
                resumeLastChannel()
            }
        }
    }

    suspend fun profile(id: Long) = profileManager.get(id)

    /** Creates ([id] = null) or changes a profile. A kids profile needs a PIN, or it could simply be left. */
    suspend fun saveProfile(id: Long?, name: String, color: Int, kids: Boolean, filter: ContentFilter?): Boolean {
        if (kids && !parentalLock.hasPin()) {
            _message.value = tx("Çocuk profili için önce Ayarlar'dan bir PIN belirleyin.", "Set a PIN in Settings before creating a kids profile.")
            return false
        }
        val stored = if (kids) filter else null
        if (id == null) {
            profileManager.create(name, color, kids, stored)
        } else {
            val current = profileManager.get(id) ?: return false
            profileManager.update(
                current.copy(
                    name = if (id == ProfileManager.MAIN_ID && name.isBlank()) "" else name,
                    color = color,
                    kids = kids && id != ProfileManager.MAIN_ID,
                    allowedLive = encodeCategories(stored?.live),
                    allowedMovies = encodeCategories(stored?.movies),
                    allowedSeries = encodeCategories(stored?.series),
                ),
            )
        }
        _message.value = tx("Profil kaydedildi.", "Profile saved.")
        return true
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            profileManager.delete(profile)
            _message.value = tx("\"${profile.displayName}\" silindi.", "\"${profile.displayName}\" deleted.")
        }
    }

    /**
     * Category names of the current playlist that can be given to a kids profile (locked and hidden
     * categories are never shown to children, so they are left out).
     */
    suspend fun profileCategories(scope: String): List<String> {
        val playlistId = currentPlaylist.value?.id ?: return emptyList()
        val names = when (scope) {
            FlagScopes.LIVE -> container.database.channels().allGroupNames(playlistId)
            FlagScopes.MOVIE -> container.database.vod().allCategoryNames(playlistId, VodKinds.MOVIE)
            else -> container.database.vod().allCategoryNames(playlistId, VodKinds.SERIES)
        }
        val excluded = repository.observeGroupFlags(playlistId).first()
            .filter { it.scope == scope && (it.locked || it.hidden) }.mapTo(HashSet()) { it.name }
        val collator = io.github.ta3hrj.elm.data.nameCollator()
        return names.filter { it !in excluded }.sortedWith { a, b -> collator.compare(a, b) }
    }

    /** The channel this profile watched last, if it belongs to the current playlist (the guide opens there). */
    fun lastWatchedStreamUrl(): String? {
        val playlistId = currentPlaylist.value?.id ?: return null
        val profileId = profileManager.activeId
        if (prefs.getLong(PlayerActivity.lastPlaylistKey(profileId), -1L) != playlistId) return null
        return prefs.getString(PlayerActivity.lastStreamKey(profileId), null)
    }

    /** The query the visible channel list was built from; the player uses it for channel up/down. */
    fun activeQuery(): ChannelQuery? =
        _searchText.value.takeIf { it.isNotBlank() }?.let { ChannelQuery.Search(it.trim()) }
            ?: _selectedQuery.value
            ?: groups.value.firstOrNull()?.query

    /** Emits the current time at every minute boundary, so "now" EPG data stays current. */
    private val minuteTicker = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000 - now % 60_000 + 50)
        }
    }

    /** The programme airing now on each channel of the current playlist, by stream URL. */
    val nowPlaying: StateFlow<Map<String, ChannelProgramme>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), minuteTicker) { id, now -> id to now }
            .flatMapLatest { (id, now) -> if (id == null) flowOf(emptyList()) else guide.observeNow(id, now) }
            .map { rows -> rows.associateBy { it.streamUrl } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val guideUpdating: StateFlow<Set<Long>> = guide.updating

    private val _guideWindowStart = MutableStateFlow(alignToHalfHour(System.currentTimeMillis()))
    /** Left edge of the TV guide grid. */
    val guideWindowStart: StateFlow<Long> = _guideWindowStart.asStateFlow()

    /** Programmes in the visible guide window, grouped by stream URL and sorted by start time. */
    val guideProgrammes: StateFlow<Map<String, List<ChannelProgramme>>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), _guideWindowStart) { id, start -> id to start }
            .flatMapLatest { (id, start) ->
                if (id == null) flowOf(emptyList()) else guide.observeWindow(id, start, start + GUIDE_WINDOW_MS)
            }
            .map { rows -> rows.groupBy { it.streamUrl } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** One row of the guide: a category heading or a channel. */
    sealed interface GuideRow {
        val key: String
        /** [ordinal] keeps keys unique when a category name appears more than once (duplicate or split categories). */
        data class Heading(val title: String, val query: ChannelQuery, val ordinal: Int) : GuideRow { override val key = "h|$ordinal|$title" }
        data class Row(val channel: ChannelEntity, val query: ChannelQuery, val favorite: Boolean) : GuideRow {
            override val key = (if (favorite) "f|" else "c|") + channel.id
        }
    }

    /** All visible channels of every category (favorites first), so the guide continues across categories. */
    val guideRows: StateFlow<List<GuideRow>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), unlocked) { id, open -> id to open }
            .flatMapLatest { (id, open) ->
                if (id == null) flowOf(emptyList()) else combine(repository.observeAllVisible(id, open), favoriteUrls) { all, favs ->
                    buildList {
                        val order = favs.withIndex().associate { (i, url) -> url to i }
                        val favorites = all.filter { it.streamUrl in favs }.sortedBy { order[it.streamUrl] ?: Int.MAX_VALUE }
                        if (favorites.isNotEmpty()) {
                            add(GuideRow.Heading("★ " + tx("Favoriler", "Favorites"), ChannelQuery.Favorites, 0))
                            favorites.forEach { add(GuideRow.Row(it, ChannelQuery.Favorites, favorite = true)) }
                        }
                        var group: String? = null
                        var headings = 0
                        val seen = HashSet<Long>()
                        for (channel in all) {
                            if (!seen.add(channel.id)) continue
                            if (channel.groupTitle != group) {
                                group = channel.groupTitle
                                add(GuideRow.Heading(channel.groupTitle, ChannelQuery.Group(channel.groupTitle), ++headings))
                            }
                            add(GuideRow.Row(channel, ChannelQuery.Group(channel.groupTitle), favorite = false))
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftGuideWindow(hours: Int): Boolean {
        val now = System.currentTimeMillis()
        val target = _guideWindowStart.value + hours * HOUR_MS
        val min = alignToHalfHour(now - GuideRepository.KEEP_PAST_CATCHUP_MS)
        val max = alignToHalfHour(now + GuideRepository.KEEP_FUTURE_MS - GUIDE_WINDOW_MS)
        if (target < min || target > max) return false
        _guideWindowStart.value = target
        return true
    }

    fun resetGuideWindow() {
        _guideWindowStart.value = alignToHalfHour(System.currentTimeMillis())
    }

    fun refreshGuide(playlistId: Long) = guide.refreshInBackground(playlistId)

    fun saveGuideUrl(playlistId: Long, url: String, updateNow: Boolean) {
        viewModelScope.launch {
            guide.setGuideUrl(playlistId, url)
            _message.value = tx("Rehber adresi kaydedildi.", "Guide address saved.")
            if (updateNow) guide.refreshInBackground(playlistId)
        }
    }

    // ---- Films and series -------------------------------------------------------------------

    private val _vodKind = MutableStateFlow(VodKinds.MOVIE)
    val vodKind: StateFlow<String> = _vodKind.asStateFlow()

    private val _vodCategory = MutableStateFlow<String?>(null)
    val vodCategory: StateFlow<String?> = _vodCategory.asStateFlow()

    private val _vodSearch = MutableStateFlow("")
    val vodSearch: StateFlow<String> = _vodSearch.asStateFlow()

    val vodUpdating: StateFlow<Set<Long>> = vod.updating

    /** "kind|providerId" of every watchlist entry of the current playlist. */
    val watchlistKeys: StateFlow<Set<String>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vod.observeWatchlistKeys(id) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Categories of the selected kind, with "recently added" first. */
    val vodCategories: StateFlow<List<GroupItem>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), _vodKind) { id, kind -> id to kind }
            .flatMapLatest { (id, kind) -> if (id == null) flowOf(emptyList()) else vod.observeCategories(id, kind) }
            .combine(groupFlags) { rows, flags -> rows to flags }
            .combine(watchlistKeys) { pair, keys -> Triple(pair.first, pair.second, keys) }
            .map { (rows, flags, keys) ->
                if (rows.isEmpty()) {
                    emptyList()
                } else {
                    watchlistGroup(_vodKind.value, keys) +
                    listOf(GroupItem(RECENT_TITLE, rows.sumOf { it.count }, ChannelQuery.Group(RECENT))) +
                        rows.map {
                            GroupItem(it.name, it.count, ChannelQuery.Group(it.name), flags["${_vodKind.value}|${it.name}"]?.locked == true)
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val vodItems: StateFlow<List<VodItemEntity>> =
        combine(
            combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), _vodKind, _vodCategory) { id, kind, cat -> Triple(id, kind, cat) },
            _vodSearch.debounce(250),
            unlocked,
            groupFlags,
        ) { (id, kind, cat), q, open, flags -> VodQuery(id, kind, cat, q.trim(), open, flags["$kind|$cat"]?.locked == true) }
            .flatMapLatest { qy ->
                val playlistId = qy.playlistId ?: return@flatMapLatest flowOf(emptyList())
                when {
                    qy.search.isNotEmpty() -> vod.observeSearch(playlistId, qy.kind, qy.search, qy.unlocked)
                    qy.category == WATCHLIST -> vod.observeWatchlist(playlistId, qy.kind, qy.unlocked)
                    qy.category == null || qy.category == RECENT -> vod.observeRecent(playlistId, qy.kind, qy.unlocked)
                    qy.categoryLocked && !qy.unlocked -> flowOf(emptyList())
                    else -> vod.observeCategory(playlistId, qy.kind, qy.category)
                }
            }
            .combine(container.settings.vodOrder) { list, order -> io.github.ta3hrj.elm.data.sortVod(list, order) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val vodOrder: StateFlow<io.github.ta3hrj.elm.data.VodOrder> get() = container.settings.vodOrder
    fun setVodOrder(order: io.github.ta3hrj.elm.data.VodOrder) = container.settings.setVodOrder(order)

    /** Saved positions by media key ("movie:1", "episode:2"). */
    val watchProgress: StateFlow<Map<String, WatchProgressEntity>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vod.observeProgress(id) }
        .map { list -> list.associateBy { it.mediaKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private fun watchlistGroup(kind: String, keys: Set<String>): List<GroupItem> {
        val count = keys.count { it.startsWith("$kind|") }
        return if (count == 0) emptyList() else listOf(GroupItem(tx("♥ İzleme listem", "♥ My watchlist"), count, ChannelQuery.Group(WATCHLIST)))
    }

    fun inWatchlist(item: VodItemEntity) = "${item.kind}|${item.providerId}" in watchlistKeys.value

    fun toggleWatchlist(item: VodItemEntity) {
        viewModelScope.launch {
            val added = vod.toggleWatchlist(item)
            _message.value = if (added) tx("İzleme listesine eklendi: ${item.name}", "Added to your watchlist: ${item.name}")
                else tx("İzleme listesinden çıkarıldı: ${item.name}", "Removed from your watchlist: ${item.name}")
        }
    }

    /** Films, episodes and recordings left part-way, newest first. */
    val continueWatching: StateFlow<List<io.github.ta3hrj.elm.data.ContinueWatchingEntity>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else vod.observeContinue(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun resume(entry: io.github.ta3hrj.elm.data.ContinueWatchingEntity) {
        PlayerActivity.startMedia(
            getApplication(), entry.playlistId, entry.url, entry.title, entry.subtitle, entry.mediaKey,
            seriesItemId = entry.seriesItemId, episodeId = entry.episodeId, poster = entry.poster,
        )
    }

    fun removeFromContinue(entry: io.github.ta3hrj.elm.data.ContinueWatchingEntity) {
        viewModelScope.launch { vod.removeContinue(entry.playlistId, entry.mediaKey) }
    }

    fun showVod(kind: String) {
        if (_vodKind.value != kind) {
            _vodKind.value = kind
            _vodCategory.value = null
            _vodSearch.value = ""
        }
    }

    fun selectVodCategory(name: String?) {
        if (_vodCategory.value != name) _vodCategory.value = name
    }

    fun setVodSearch(text: String) {
        _vodSearch.value = text
    }

    fun refreshVod(playlistId: Long) = vod.refreshInBackground(playlistId)

    suspend fun vodItem(id: Long) = vod.item(id)

    suspend fun movieInfo(item: VodItemEntity) = currentPlaylist.value?.let { vod.movieInfo(it, item) }

    suspend fun seriesInfo(item: VodItemEntity) = currentPlaylist.value?.let { vod.seriesInfo(it, item) }

    fun playMovie(item: VodItemEntity, fromStart: Boolean) {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch {
            val key = VodRepository.movieKey(item.providerId)
            if (fromStart) container.database.vod().clearProgress(playlist.id, profileManager.activeId, key)
            PlayerActivity.startMedia(
                getApplication(), playlist.id, vod.movieUrl(playlist, item), item.name,
                listOfNotNull(tx("Film", "Film"), item.year).joinToString(" · "), key,
                poster = item.poster,
                privateViewing = isLocked(item.kind, item.category),
            )
        }
    }

    fun playEpisode(series: VodItemEntity, episode: Episode, fromStart: Boolean) {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch {
            val key = VodRepository.episodeKey(episode.id)
            if (fromStart) container.database.vod().clearProgress(playlist.id, profileManager.activeId, key)
            PlayerActivity.startMedia(
                getApplication(), playlist.id, vod.episodeUrl(playlist, episode.id, episode.containerExtension),
                episode.title, tx("${series.name} · S${episode.season} B${episode.number}", "${series.name} · S${episode.season} E${episode.number}"), key,
                seriesItemId = series.id, episodeId = episode.id,
                poster = episode.image ?: series.poster,
                privateViewing = isLocked(series.kind, series.category),
            )
        }
    }

    // ---- Catch-up and reminders ------------------------------------------------------------

    /** Reminder keys ("streamUrl|start") of the current playlist. */
    val reminderKeys: StateFlow<Set<String>> = currentPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else reminders.observeKeys(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun catchupType(channel: ChannelEntity): String? =
        channel.catchupType ?: if (channel.catchupDays > 0 && currentPlaylist.value?.type == PlaylistType.XTREAM) Catchup.TYPE_XTREAM else null

    fun isArchived(channel: ChannelEntity, programme: ChannelProgramme, now: Long = System.currentTimeMillis()): Boolean =
        Catchup.isAvailable(channel.catchupDays, catchupType(channel), programme.start, now) && programme.stop <= now

    fun playArchive(channel: ChannelEntity, programme: ChannelProgramme) {
        val playlist = currentPlaylist.value ?: return
        val url = Catchup.archiveUrl(
            streamUrl = channel.streamUrl,
            catchupType = catchupType(channel),
            catchupSource = channel.catchupSource,
            start = programme.start,
            stop = programme.stop,
            timezone = playlist.serverTimezone,
        )
        if (url == null) {
            _message.value = tx("Bu kanal için geriye dönük izleme ayarı bulunamadı.", "No catch-up configuration found for this channel.")
            return
        }
        PlayerActivity.startMedia(
            getApplication(), playlist.id, url, programme.title,
            tx("${channel.name} · ${timeRange(programme.start, programme.stop)} · Arşiv", "${channel.name} · ${timeRange(programme.start, programme.stop)} · Archive"), progressKey = null,
        )
    }

    fun toggleReminder(channel: ChannelEntity, programme: ChannelProgramme) {
        viewModelScope.launch {
            val set = reminders.toggle(channel.playlistId, channel.streamUrl, channel.name, programme.title, programme.start, programme.stop)
            _message.value = if (set) {
                tx("Hatırlatıcı kuruldu: ${programme.title} (${timeRange(programme.start, programme.stop)})", "Reminder set: ${programme.title} (${timeRange(programme.start, programme.stop)})")
            } else {
                tx("Hatırlatıcı kaldırıldı: ${programme.title}", "Reminder removed: ${programme.title}")
            }
        }
    }

    // ---- Recordings ---------------------------------------------------------------------------

    private val recordingsManager = container.recordings
    val recordings: StateFlow<List<RecordingEntity>> = recordingsManager.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun recordProgramme(channel: ChannelEntity, programme: ChannelProgramme) {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch {
            val (saved, error) = recordingsManager.schedule(
                playlistId = channel.playlistId,
                channelName = channel.name,
                title = programme.title,
                streamUrl = channel.streamUrl,
                userAgent = repository.userAgentFor(playlist, channel),
                start = programme.start - io.github.ta3hrj.elm.recordings.Recordings.START_MARGIN_MS,
                stop = programme.stop + io.github.ta3hrj.elm.recordings.Recordings.END_MARGIN_MS,
            )
            _message.value = when {
                saved == null -> error
                saved.start <= System.currentTimeMillis() + 5_000 -> tx("● Kayıt başladı: ", "● Recording started: ") + programme.title
                else -> tx("Kayıt planlandı: ", "Recording scheduled: ") + "${programme.title} (${timeRange(programme.start, programme.stop)})"
            }
        }
    }

    private val seriesRecording = container.seriesRecording
    val recordingRules: StateFlow<List<io.github.ta3hrj.elm.data.RecordingRuleEntity>> = seriesRecording.observeRules()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Records this programme and every later episode of it on the same channel. */
    fun recordSeries(channel: ChannelEntity, programme: ChannelProgramme) {
        viewModelScope.launch {
            val added = seriesRecording.addRule(channel, programme.title)
            // The chosen episode itself, even if it is already on air.
            val playlist = currentPlaylist.value
            if (playlist != null && programme.stop > System.currentTimeMillis() && recordingsManager.isFree(channel.streamUrl, programme.start, programme.stop)) {
                recordingsManager.schedule(
                    playlistId = channel.playlistId,
                    channelName = channel.name,
                    title = programme.title,
                    streamUrl = channel.streamUrl,
                    userAgent = repository.userAgentFor(playlist, channel),
                    start = programme.start - io.github.ta3hrj.elm.recordings.Recordings.START_MARGIN_MS,
                    stop = programme.stop + io.github.ta3hrj.elm.recordings.Recordings.END_MARGIN_MS,
                )
            }
            val planned = seriesRecording.apply()
            _message.value = if (!added) {
                tx("Bu dizi zaten kaydediliyor.", "This series is already being recorded.")
            } else {
                tx("Dizi kaydı açıldı: ${programme.title} · $planned bölüm daha planlandı. Rehber güncellendikçe yenileri eklenir.",
                    "Series recording on: ${programme.title} · $planned more episodes planned. New ones are added as the guide updates.")
            }
        }
    }

    fun deleteRecordingRule(id: Long) = viewModelScope.launch {
        seriesRecording.deleteRule(id)
        _message.value = tx("Dizi kaydı kapatıldı (planlanmış bölümler Kayıtlar'da kalır).", "Series recording off (planned episodes stay in Recordings).")
    }

    fun cancelRecording(id: Long) = viewModelScope.launch { recordingsManager.cancel(id) }

    // ---- Matching a channel to the guide by hand ----------------------------------------------

    private val _epgMatchChannel = MutableStateFlow<ChannelEntity?>(null)
    /** The channel the match screen is open for. */
    val epgMatchChannel: StateFlow<ChannelEntity?> = _epgMatchChannel.asStateFlow()

    fun openEpgMatch(channel: ChannelEntity) {
        _epgMatchChannel.value = channel
    }

    suspend fun epgMatchInfo(channel: ChannelEntity) = guide.matchInfo(channel.playlistId, channel)

    /** [epgKey] null: back to automatic; "": no guide; else the guide channel [name]d. */
    fun setEpgMatch(channel: ChannelEntity, epgKey: String?, name: String?) {
        viewModelScope.launch {
            val downloading = guide.setManualMatch(channel.playlistId, channel, epgKey)
            _message.value = when {
                epgKey == null -> tx("\"${channel.name}\" otomatik eşleştirmeye döndü; rehber güncelleniyor.", "\"${channel.name}\" is matched automatically again; updating the guide.")
                epgKey.isEmpty() -> tx("\"${channel.name}\" için rehber gösterilmeyecek.", "No guide will be shown for \"${channel.name}\".")
                downloading -> tx("\"${channel.name}\" → $name. Rehber indiriliyor, programlar birazdan gelir.", "\"${channel.name}\" → $name. Downloading the guide; programmes follow shortly.")
                else -> tx("\"${channel.name}\" → $name eşleştirildi.", "\"${channel.name}\" matched to $name.")
            }
        }
    }

    /** Plays the channel in VLC, MX Player or another installed player. */
    fun openExternal(channel: ChannelEntity) {
        val playlist = currentPlaylist.value
        val error = io.github.ta3hrj.elm.player.ExternalPlayer.open(
            getApplication(), channel.streamUrl, channel.name, repository.userAgentFor(playlist, channel),
        )
        if (error != null) _message.value = error else viewModelScope.launch { repository.markWatched(channel) }
    }

    fun deleteRecording(id: Long) = viewModelScope.launch { recordingsManager.delete(id) }

    fun playRecording(recording: RecordingEntity) {
        val path = recording.filePath ?: return
        PlayerActivity.startMedia(
            getApplication(), recording.playlistId, android.net.Uri.fromFile(java.io.File(path)).toString(),
            recording.title, tx("Kayıt · ", "Recording · ") + recording.channelName, progressKey = "recording:${recording.id}",
        )
    }

    fun recordingStorage(): String {
        val dir = recordingsManager.storageDirs().first()
        val free = recordingsManager.freeBytes(dir) / (1024 * 1024 * 1024.0)
        return tx("Kayıt yeri: ", "Saved to: ") + dir.path + "  ·  " + tx("boş alan %.1f GB", "%.1f GB free").format(free)
    }

    // ---- Global search ----------------------------------------------------------------------

    private val _globalQuery = MutableStateFlow("")
    val globalQuery: StateFlow<String> = _globalQuery.asStateFlow()

    fun setGlobalQuery(text: String) {
        _globalQuery.value = text
    }

    private fun <T> searchFlow(block: (Long, String, Boolean) -> kotlinx.coroutines.flow.Flow<List<T>>): StateFlow<List<T>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), _globalQuery.debounce(300), unlocked) { id, q, open -> Triple(id, q.trim(), open) }
            .flatMapLatest { (id, q, open) -> if (id == null || q.length < 2) flowOf(emptyList()) else block(id, q, open) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchChannels: StateFlow<List<ChannelEntity>> = searchFlow { id, q, open -> repository.observeChannels(id, ChannelQuery.Search(q), open) }
    val searchMovies: StateFlow<List<VodItemEntity>> = searchFlow { id, q, open -> vod.observeSearch(id, VodKinds.MOVIE, q, open) }
    val searchSeries: StateFlow<List<VodItemEntity>> = searchFlow { id, q, open -> vod.observeSearch(id, VodKinds.SERIES, q, open) }

    /** A guide programme found by the search, with its channel. */
    data class ProgrammeHit(val channel: ChannelEntity, val programme: ChannelProgramme)

    /** Programmes whose title matches, from the archive window up to the end of the stored guide. */
    val searchProgrammes: StateFlow<List<ProgrammeHit>> =
        combine(currentPlaylist.map { it?.id }.distinctUntilChanged(), _globalQuery.debounce(300), unlocked) { id, q, open -> Triple(id, q.trim(), open) }
            .flatMapLatest { (id, q, open) ->
                if (id == null || q.length < 3) {
                    flowOf(emptyList())
                } else {
                    guide.observeProgrammeSearch(id, q, System.currentTimeMillis() - GuideRepository.KEEP_PAST_CATCHUP_MS, open).map { rows ->
                        val channels = HashMap<String, ChannelEntity?>()
                        rows.mapNotNull { row ->
                            val channel = channels.getOrPut(row.streamUrl) { repository.channelByStreamUrl(id, row.streamUrl)?.takeIf { repository.allowed(it) } } ?: return@mapNotNull null
                            ProgrammeHit(channel, row)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Channel archive ---------------------------------------------------------------------

    private val _archiveChannel = MutableStateFlow<ChannelEntity?>(null)
    val archiveChannel: StateFlow<ChannelEntity?> = _archiveChannel.asStateFlow()

    fun openArchive(channel: ChannelEntity) {
        _archiveChannel.value = channel
    }

    fun hasArchive(channel: ChannelEntity): Boolean = catchupType(channel) != null && channel.catchupDays > 0

    /** Days of archive offered for a channel (at most a week). */
    fun archiveDays(channel: ChannelEntity): Int = channel.catchupDays.coerceIn(1, 7)

    suspend fun archive(channel: ChannelEntity): List<GuideRepository.ArchiveEntry> {
        val playlist = currentPlaylist.value ?: return emptyList()
        return guide.archive(playlist, channel, archiveDays(channel))
    }

    // ---- Language, start screen, backup -------------------------------------------------------

    private val _language = MutableStateFlow(
        runCatching { Lang.Choice.valueOf(prefs.getString(PREF_LANGUAGE, null) ?: "SYSTEM") }.getOrDefault(Lang.Choice.SYSTEM),
    )
    val language: StateFlow<Lang.Choice> = _language.asStateFlow()

    /** Saves the language; the caller recreates the screen so every text is redrawn. */
    fun setLanguage(choice: Lang.Choice) {
        prefs.edit().putString(PREF_LANGUAGE, choice.name).apply()
        _language.value = choice
        Lang.apply(choice)
    }

    private val _homeView = MutableStateFlow(prefs.getString(KEY_HOME_VIEW, HOME_LIST) ?: HOME_LIST)
    /** Start screen: [HOME_LIST] (channel list), [HOME_GUIDE] (guide with preview) or [HOME_SECTIONS] (section cards). */
    val homeView: StateFlow<String> = _homeView.asStateFlow()

    fun setHomeView(view: String) {
        prefs.edit().putString(KEY_HOME_VIEW, view).apply()
        _homeView.value = view
    }

    fun backupFiles(): List<java.io.File> = container.backup.listBackups()

    /** Starts the home-network page for moving backups to/from a phone; returns its address. */
    fun startBackupTransfer(): String? {
        val transfer = container.backupTransfer
        transfer.onRestored = { text ->
            _message.value = text
            viewModelScope.launch {
                playlists.value.orEmpty().filter { it.updatedAt == 0L }.forEach { refresh(it.id, quiet = true) }
            }
        }
        return transfer.start()
    }

    fun stopBackupTransfer() {
        container.backupTransfer.onRestored = null
        container.backupTransfer.stop()
    }

    fun deleteBackup(file: java.io.File) = viewModelScope.launch {
        _message.value = if (container.backup.delete(file.name)) {
            tx("Yedek silindi: ${file.name}", "Backup deleted: ${file.name}")
        } else {
            tx("Yedek silinemedi.", "The backup could not be deleted.")
        }
    }

    fun exportBackup(password: String? = null) = viewModelScope.launch {
        _message.value = runCatching { container.backup.export(password?.takeIf { it.isNotEmpty() }) }.fold(
            { paths ->
                (if (password.isNullOrEmpty()) tx("Yedek kaydedildi: ", "Backup saved: ") else tx("Parolalı yedek kaydedildi: ", "Password-protected backup saved: ")) +
                    paths.joinToString("  ·  ")
            },
            { tx("Yedek kaydedilemedi: ", "Backup failed: ") + (it.message ?: it.javaClass.simpleName) },
        )
    }

    /** Where a restore came from, kept while the backup's password is asked. */
    data class RestoreSource(val file: java.io.File?, val url: String?, val uri: android.net.Uri?)

    private val _restoreNeedsPassword = MutableStateFlow<RestoreSource?>(null)
    val restoreNeedsPassword: StateFlow<RestoreSource?> = _restoreNeedsPassword.asStateFlow()

    fun cancelRestorePassword() {
        _restoreNeedsPassword.value = null
    }

    fun restoreBackup(file: java.io.File?, url: String?, uri: android.net.Uri? = null, password: String? = null) = viewModelScope.launch {
        _restoreNeedsPassword.value = null
        _message.value = runCatching {
            when {
                uri != null -> container.backup.restoreUri(uri, password)
                file != null -> container.backup.restoreFile(file, password)
                else -> container.backup.restoreUrl(url.orEmpty(), password)
            }
        }.onFailure {
            if (it is io.github.ta3hrj.elm.data.BackupPasswordNeeded) _restoreNeedsPassword.value = RestoreSource(file, url, uri)
        }.fold(
            { text ->
                // Download the playlists that were added.
                playlists.value.orEmpty().filter { it.updatedAt == 0L }.forEach { refresh(it.id, quiet = true) }
                text
            },
            { tx("Geri yüklenemedi: ", "Restore failed: ") + (it.message ?: it.javaClass.simpleName) },
        )
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { guide.events.collect { _message.value = it } }
        viewModelScope.launch { vod.events.collect { _message.value = it } }
        if (io.github.ta3hrj.elm.data.CrashLog.hasUnseen(app)) {
            io.github.ta3hrj.elm.data.CrashLog.markSeen(app)
            _message.value = tx(
                "Uygulama son kullanımda beklenmedik şekilde kapandı. Ayrıntılar: Ayarlar → Hata günlüğü.",
                "The app closed unexpectedly last time. Details: Settings → Error log.",
            )
        }
        // Film and series categories arrive in the background; check them for adult content when a download ends.
        viewModelScope.launch {
            var previous = emptySet<Long>()
            vod.updating.collect { now ->
                (previous - now).forEach { applyAdultLock(it) }
                previous = now
            }
        }
        // Also on start, for playlists added before this feature existed.
        viewModelScope.launch {
            playlists.first { it != null }.orEmpty().forEach {
                applyAdultLock(it.id)
                purgePrivateHistory(it.id)
            }
        }
    }

    /**
     * Updates the selected playlist, its guide and films/series in the background when they are older than
     * the interval chosen in Settings. MainActivity calls this on start and then every half hour while it
     * is visible, because a TV app can stay open for days.
     */
    suspend fun refreshIfStale() {
        val hours = settings.autoRefreshHours.value
        if (hours <= 0) return
        val maxAge = hours * HOUR_MS
        val list = playlists.first { it != null }.orEmpty()
        val playlist = list.firstOrNull { it.id == _selectedPlaylistId.value } ?: list.firstOrNull() ?: return
        if (System.currentTimeMillis() - playlist.updatedAt > maxAge) {
            refresh(playlist.id, quiet = true)
        } else {
            guide.refreshIfStale(playlist, minOf(maxAge, GuideRepository.STALE_AFTER_MS))
        }
        vod.refreshIfStale(playlist, maxOf(maxAge, VodRepository.STALE_AFTER_MS))
    }

    /** Removes locked or hidden content from "recently watched" and "continue watching". */
    private suspend fun purgePrivateHistory(playlistId: Long) {
        runCatching {
            container.database.lists().purgePrivateRecent(playlistId)
            vod.purgePrivateContinue(playlistId).forEach { container.watchNext.remove(it) }
        }
    }

    private suspend fun applyAdultLock(playlistId: Long) {
        runCatching { repository.applyAdultLock(playlistId, parentalLock.hasPin()) }
        purgePrivateHistory(playlistId)
    }

    private fun applyAdultLockEverywhere() = viewModelScope.launch {
        playlists.value.orEmpty().forEach { applyAdultLock(it.id) }
    }

    val adultLock: StateFlow<Boolean> get() = container.settings.adultLock
    val frameRateMatching: StateFlow<Boolean> get() = container.settings.frameRateMatching

    fun setFrameRateMatching(enabled: Boolean) = container.settings.setFrameRateMatching(enabled)

    val bufferSize: StateFlow<Int> get() = container.settings.bufferSize
    val idleHours: StateFlow<Int> get() = container.settings.idleHours
    fun setBufferSize(size: Int) = container.settings.setBufferSize(size)
    fun setIdleHours(hours: Int) = container.settings.setIdleHours(hours)

    // ---- In-app update -------------------------------------------------------------------------

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState
        data object Unreachable : UpdateState
        /** [ask]: show the question at once (start-up check, unless postponed; or a check from Settings). */
        data class Available(val release: io.github.ta3hrj.elm.core.AppRelease, val ask: Boolean) : UpdateState
        data class Downloading(val release: io.github.ta3hrj.elm.core.AppRelease, val progress: Float) : UpdateState
        data class NeedsPermission(val file: java.io.File) : UpdateState
        data class Failed(val reason: String) : UpdateState
    }

    private val updater = container.updater
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    val updatesEnabled: Boolean get() = updater.enabled
    val updateCheckedAt: Long get() = updater.lastCheckedAt

    /** On its own at start, at most once a day; [manual] from the Settings button. */
    fun checkForUpdate(manual: Boolean) {
        if (!manual && !updater.dueForCheck()) return
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        if (manual) _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = when (val result = updater.check()) {
                is io.github.ta3hrj.elm.data.AppUpdater.Check.Newer ->
                    UpdateState.Available(result.release, ask = manual || !updater.postponed(result.release.version))
                io.github.ta3hrj.elm.data.AppUpdater.Check.UpToDate -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                io.github.ta3hrj.elm.data.AppUpdater.Check.Unreachable -> if (manual) UpdateState.Unreachable else UpdateState.Idle
            }
        }
    }

    /** "Later": the question comes back for this version after a few days; Settings still offers it. */
    fun postponeUpdate() {
        val state = _update.value as? UpdateState.Available ?: return
        updater.postpone(state.release.version)
        _update.value = state.copy(ask = false)
    }

    fun installUpdate() {
        val release = when (val state = _update.value) {
            is UpdateState.Available -> state.release
            else -> return
        }
        _update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            when (val result = updater.download(release) { p -> _update.value = UpdateState.Downloading(release, p) }) {
                is io.github.ta3hrj.elm.data.AppUpdater.Download.Failed -> _update.value = UpdateState.Failed(result.reason)
                is io.github.ta3hrj.elm.data.AppUpdater.Download.Ready -> handOver(result.file)
            }
        }
    }

    /** After "Install unknown apps" was allowed in the system settings. */
    fun retryUpdateInstall(file: java.io.File) = handOver(file)

    /** Opens the system's permission screen; the question stays for "Try again" on return. */
    fun openUpdatePermission(file: java.io.File) {
        updater.openInstallPermission()
        _update.value = UpdateState.NeedsPermission(file)
    }

    fun dismissUpdate() {
        _update.value = when (val state = _update.value) {
            is UpdateState.Available -> state.copy(ask = false)
            else -> UpdateState.Idle
        }
    }

    private fun handOver(file: java.io.File) {
        _update.value = when {
            !updater.canInstall() -> UpdateState.NeedsPermission(file)
            updater.install(file) -> UpdateState.Idle
            else -> UpdateState.Failed(tx("Yükleyici açılamadı", "The installer could not be opened"))
        }
    }

    // ---- Connection test and crash log ------------------------------------------------------

    private val _connectionResult = MutableStateFlow<String?>(null)
    /** Text result of the last connection test; "…" while it runs. */
    val connectionResult: StateFlow<String?> = _connectionResult.asStateFlow()

    fun runConnectionTest() {
        val playlist = currentPlaylist.value ?: return
        if (_connectionResult.value == RUNNING) return
        _connectionResult.value = RUNNING
        viewModelScope.launch {
            val lastUrl = prefs.getString(PlayerActivity.lastStreamKey(profileManager.activeId), null)
                ?.takeIf { prefs.getLong(PlayerActivity.lastPlaylistKey(profileManager.activeId), -1) == playlist.id }
            val channel = lastUrl?.let { repository.channelByStreamUrl(playlist.id, it) }
                ?: repository.observeAllVisible(playlist.id, unlocked.value).first().firstOrNull()
            if (channel == null) {
                _connectionResult.value = tx("Ölçülecek kanal yok.", "No channel to measure.")
                return@launch
            }
            val result = io.github.ta3hrj.elm.data.ConnectionTest(container.http)
                .run(playlist.url, channel.streamUrl, repository.userAgentFor(playlist, channel))
            _connectionResult.value = listOfNotNull(
                result.latencyMs?.let { tx("Sunucu yanıtı: $it ms", "Server response: $it ms") },
                result.mbps?.let { tx("İndirme: %.1f Mbit/sn", "Download: %.1f Mbit/s").format(it) + "  (" + channel.name + ")" },
                result.mbps?.let { io.github.ta3hrj.elm.data.ConnectionTest.advice(it) },
                result.error,
            ).joinToString("\n").ifEmpty { tx("Sunucuya ulaşılamadı.", "The server could not be reached.") }
        }
    }

    fun crashEntries(): List<io.github.ta3hrj.elm.data.CrashLog.Entry> = io.github.ta3hrj.elm.data.CrashLog.entries(getApplication())

    fun clearCrashes() = io.github.ta3hrj.elm.data.CrashLog.clear(getApplication())

    fun memorySummary(): io.github.ta3hrj.elm.data.MemoryLog.Summary = io.github.ta3hrj.elm.data.MemoryLog.summary(getApplication())

    fun setAdultLock(enabled: Boolean) {
        container.settings.setAdultLock(enabled)
        applyAdultLockEverywhere()
    }

    // ---- Settings shared with the player ------------------------------------------------------

    private val settings = container.settings
    val channelOrder: StateFlow<ChannelOrder> = settings.channelOrder
    val groupOrder: StateFlow<GroupOrder> = settings.groupOrder
    val subtitleStyle: StateFlow<SubtitleStyle> = settings.subtitleStyle
    val autoRefreshHours: StateFlow<Int> = settings.autoRefreshHours
    val protectSettings: StateFlow<Boolean> = settings.protectSettings

    fun setChannelOrder(order: ChannelOrder) = settings.setChannelOrder(order)
    fun setGroupOrder(order: GroupOrder) = settings.setGroupOrder(order)
    fun setSubtitleStyle(style: SubtitleStyle) = settings.setSubtitleStyle(style)

    val backgroundUpdate: StateFlow<Boolean> get() = settings.backgroundUpdate
    val audioProfile: StateFlow<Int> get() = settings.audioProfile
    fun setAudioProfile(profile: Int) = settings.setAudioProfile(profile)
    val homePreview: StateFlow<Boolean> get() = settings.homePreview
    fun setHomePreview(enabled: Boolean) = settings.setHomePreview(enabled)
    fun setBackgroundUpdate(enabled: Boolean) {
        settings.setBackgroundUpdate(enabled)
        io.github.ta3hrj.elm.data.DailyUpdateWorker.schedule(getApplication(), enabled)
    }

    val uiSize: StateFlow<Int> get() = settings.uiSize
    fun setUiSize(size: Int) = settings.setUiSize(size)
    fun setAutoRefreshHours(hours: Int) = settings.setAutoRefreshHours(hours)

    fun setProtectSettings(enabled: Boolean) {
        if (enabled && !parentalLock.hasPin()) {
            _message.value = tx("Önce bir PIN belirleyin.", "Set a PIN first.")
            return
        }
        settings.setProtectSettings(enabled)
    }

    /** True when opening Settings must ask for the PIN first. */
    fun settingsNeedPin(): Boolean = parentalLock.hasPin() &&
        // In a kids profile Settings always ask, even after the PIN was typed once.
        (profileManager.active.value.kids || (settings.protectSettings.value && !parentalLock.unlocked.value))

    fun epgOffsetMinutes(playlistId: Long): Int = settings.epgOffsetMinutes(playlistId)

    /** Saves the guide time offset and re-imports the guide so the new times apply. */
    fun setEpgOffsetMinutes(playlistId: Long, minutes: Int) {
        settings.setEpgOffsetMinutes(playlistId, minutes)
        guide.refreshInBackground(playlistId)
    }

    fun selectPlaylist(id: Long) {
        prefs.edit().putLong(KEY_PLAYLIST, id).apply()
        _selectedPlaylistId.value = id
        _selectedQuery.value = null
        _searchText.value = ""
    }

    fun selectGroup(query: ChannelQuery) {
        if (_selectedQuery.value != query) _selectedQuery.value = query
    }

    fun setSearch(text: String) {
        _searchText.value = text
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---- Own favorite lists and recently watched ----------------------------------------------

    suspend fun listsContaining(channel: ChannelEntity): List<Long> = repository.listsContaining(channel)

    fun toggleInList(listId: Long, listName: String, channel: ChannelEntity) {
        viewModelScope.launch {
            val added = repository.toggleInList(listId, channel)
            _message.value = if (added) tx("\"$listName\" listesine eklendi: ${channel.name}", "Added to \"$listName\": ${channel.name}")
                else tx("\"$listName\" listesinden çıkarıldı: ${channel.name}", "Removed from \"$listName\": ${channel.name}")
        }
    }

    /** Creates a list and, if given, puts [channel] in it. */
    fun createList(name: String, channel: ChannelEntity?) {
        val playlist = currentPlaylist.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createList(playlist.id, name)
            if (channel != null) repository.toggleInList(id, channel)
            _message.value = tx("\"${name.trim()}\" listesi oluşturuldu.", "List \"${name.trim()}\" created.")
        }
    }

    fun renameList(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameList(id, name)
            if ((_selectedQuery.value as? ChannelQuery.FavoriteList)?.id == id) _selectedQuery.value = ChannelQuery.FavoriteList(id, name.trim())
        }
    }

    fun deleteList(id: Long, name: String) {
        viewModelScope.launch {
            repository.deleteList(id)
            if ((_selectedQuery.value as? ChannelQuery.FavoriteList)?.id == id) _selectedQuery.value = null
            _message.value = tx("\"$name\" listesi silindi.", "List \"$name\" deleted.")
        }
    }

    fun clearRecent() {
        val playlist = currentPlaylist.value ?: return
        viewModelScope.launch {
            repository.clearRecent(playlist.id)
            if (_selectedQuery.value == ChannelQuery.Recent) _selectedQuery.value = null
        }
    }

    /** Moves a channel within the favorites or the open own list. */
    fun moveChannel(channel: ChannelEntity, step: Int) {
        val query = activeQuery()?.takeIf { it.hasOwnOrder } ?: return
        viewModelScope.launch { repository.move(query, channel, step) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val added = repository.toggleFavorite(channel)
            _message.value = if (added) tx("Favorilere eklendi: ${channel.name}", "Added to favorites: ${channel.name}") else tx("Favorilerden çıkarıldı: ${channel.name}", "Removed from favorites: ${channel.name}")
        }
    }

    fun addPlaylist(playlist: PlaylistEntity, onSuccess: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = tx("Liste indiriliyor…", "Downloading playlist…")
            try {
                val id = repository.add(playlist)
                applyAdultLock(id)
                selectPlaylist(id)
                _message.value = tx("Liste eklendi. TV rehberi arka planda indiriliyor…", "Playlist added. The TV guide is downloading in the background…")
                onSuccess()
                guide.refreshInBackground(id)
                if (playlist.type == PlaylistType.XTREAM) vod.refreshInBackground(id, quiet = true)
            } catch (e: PlaylistException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = tx("Beklenmeyen hata: ${e.message ?: e.javaClass.simpleName}", "Unexpected error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Live account details from the Xtream server (status, dates, connections, provider message). */
    suspend fun accountInfo(playlist: PlaylistEntity): Result<io.github.ta3hrj.elm.core.XtreamAccount> = runCatching {
        val source = playlist.toSource() as? io.github.ta3hrj.elm.core.PlaylistSource.Xtream
            ?: throw PlaylistException(tx("Hesap bilgisi yalnızca Xtream Codes listelerinde var.", "Account details exist only for Xtream Codes playlists."))
        io.github.ta3hrj.elm.core.XtreamClient(container.http, source, repository.userAgentFor(playlist)).account()
    }

    /** Saves changed account details (see [io.github.ta3hrj.elm.data.PlaylistRepository.edit]). */
    fun editPlaylist(playlist: PlaylistEntity, onSuccess: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = tx("Bilgiler kontrol ediliyor…", "Checking the details…")
            try {
                val old = repository.playlist(playlist.id)
                val mapping = repository.edit(playlist)
                // The last-watched channel (per profile) follows the new address too.
                val editor = prefs.edit()
                for ((key, value) in prefs.all) {
                    if (key.startsWith("last_stream_url") && value is String) mapping[value]?.let { editor.putString(key, it) }
                }
                editor.apply()
                val sourceChanged = old != null && old.toSource() != repository.playlist(playlist.id)?.toSource()
                if (sourceChanged) {
                    applyAdultLock(playlist.id)
                    guide.refreshInBackground(playlist.id, quiet = true)
                    if (playlist.type == PlaylistType.XTREAM) vod.refreshInBackground(playlist.id, quiet = true)
                }
                _message.value = if (sourceChanged) {
                    tx("Liste güncellendi; favoriler ve listeler yeni bilgilere taşındı.", "Playlist updated; favorites and lists moved to the new details.")
                } else {
                    tx("Kaydedildi.", "Saved.")
                }
                onSuccess()
            } catch (e: PlaylistException) {
                _message.value = e.message
            } catch (e: Exception) {
                _message.value = tx("Beklenmeyen hata: ${e.message ?: e.javaClass.simpleName}", "Unexpected error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun refresh(id: Long, quiet: Boolean = false) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            if (!quiet) _message.value = tx("Liste güncelleniyor…", "Updating playlist…")
            try {
                repository.refresh(id)
                applyAdultLock(id)
                if (!quiet) _message.value = tx("Liste güncellendi. Rehber güncelleniyor…", "Playlist updated. Updating the guide…")
                guide.refreshInBackground(id, quiet = quiet)
                if (repository.playlist(id)?.type == PlaylistType.XTREAM) vod.refreshInBackground(id, quiet = true)
            } catch (e: Exception) {
                _message.value = tx("Güncellenemedi: ${e.message ?: e.javaClass.simpleName}", "Update failed: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.delete(playlist)
            if (_selectedPlaylistId.value == playlist.id) {
                _selectedPlaylistId.value = null
                _selectedQuery.value = null
            }
            _message.value = tx("Liste silindi.", "Playlist deleted.")
        }
    }

    companion object {
        private const val KEY_PLAYLIST = "selected_playlist"
        private const val KEY_RESUME = "resume_last_channel"
        private const val KEY_ASK_PROFILE = "ask_profile_on_start"
        private const val KEY_HOME_VIEW = "home_view"
        const val HOME_LIST = "list"
        const val HOME_GUIDE = "guide"
        const val HOME_SECTIONS = "sections"
        const val HOUR_MS = 3_600_000L
        /** The guide grid shows three hours at a time. */
        const val GUIDE_WINDOW_MS = 3 * HOUR_MS

        const val RECENT = "\u0000recent"
        const val WATCHLIST = "\u0000watchlist"
        const val RUNNING = "…"
        val RECENT_TITLE: String get() = tx("Son eklenenler", "Recently added")

        fun alignToHalfHour(time: Long): Long = time - time % (HOUR_MS / 2)
    }
}
