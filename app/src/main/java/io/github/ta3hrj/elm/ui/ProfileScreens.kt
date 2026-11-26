package io.github.ta3hrj.elm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ta3hrj.elm.ui.touch.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import io.github.ta3hrj.elm.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import io.github.ta3hrj.elm.ui.touch.OutlinedButton
import io.github.ta3hrj.elm.ui.touch.Surface
import androidx.tv.material3.Text
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ContentFilter
import io.github.ta3hrj.elm.data.FlagScopes
import io.github.ta3hrj.elm.data.ProfileEntity
import io.github.ta3hrj.elm.data.ProfileManager
import io.github.ta3hrj.elm.data.displayName
import io.github.ta3hrj.elm.data.filter
import kotlinx.coroutines.launch

private val ProfileMuted = Color(0xFF9AA4B2)

/** Round avatar with the profile's first letter; kids profiles get a small badge. */
@Composable
fun ProfileAvatar(profile: ProfileEntity, size: Dp) {
    Box {
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(profile.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.trim().take(1).uppercase(io.github.ta3hrj.elm.core.Lang.locale),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
        if (profile.kids) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.38f).clip(CircleShape).background(Color(0xFFFFC94D)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChildCare, contentDescription = null, tint = Color(0xFF1B1B1B), modifier = Modifier.size(size * 0.26f))
            }
        }
    }
}

/** Small header button showing the active profile; opens the picker. */
@Composable
fun ProfileButton(viewModel: MainViewModel, compact: Boolean = false) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val active by viewModel.activeProfile.collectAsStateWithLifecycle()
    if (profiles.size < 2) return
    val shown = profiles.firstOrNull { it.id == active.id } ?: active
    Button(onClick = viewModel::showProfilePicker) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(shown, 24.dp)
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text(shown.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
            }
        }
    }
}

/** "Who's watching?" — shown at start (if enabled) and from the header button. */
@Composable
fun ProfilePicker(viewModel: MainViewModel) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val active by viewModel.activeProfile.collectAsStateWithLifecycle()
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = viewModel::dismissProfilePicker)
    LaunchedEffect(profiles.isNotEmpty()) { if (profiles.isNotEmpty()) runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tx("Kim izliyor?", "Who's watching?"), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(40.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == active.id
                    Surface(
                        onClick = {
                            if (viewModel.profileNeedsPin(profile)) pinFor = profile else viewModel.selectProfile(profile)
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0x223B82F6)),
                        border = surfaceFocusBorder(RoundedCornerShape(16.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        modifier = if (isActive || (profiles.none { it.id == active.id } && profile == profiles.first())) Modifier.focusRequester(focus) else Modifier,
                    ) {
                        Column(Modifier.padding(16.dp).width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ProfileAvatar(profile, 112.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 18.sp)
                            Text(
                                when {
                                    profile.kids -> tx("Çocuk", "Kids")
                                    isActive -> tx("Şu anki", "Current")
                                    else -> " "
                                },
                                color = ProfileMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                tx("Profiller Ayarlar > Profiller bölümünden yönetilir.", "Profiles are managed in Settings > Profiles."),
                color = ProfileMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pinFor?.let { target ->
        PinDialog(
            tx("PIN girin", "Enter PIN"),
            tx("\"${target.displayName}\" profiline geçmek için", "To switch to \"${target.displayName}\""),
            onSubmit = { pin -> viewModel.verifyPin(pin).also { if (it) viewModel.selectProfile(target) } },
            onDismiss = { pinFor = null },
        )
    }
}

/** Settings > Profiles: the list, "ask at start" and adding a profile. */
@Composable
fun ProfilesScreen(viewModel: MainViewModel, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val active by viewModel.activeProfile.collectAsStateWithLifecycle()
    val askOnStart by viewModel.askProfileOnStart.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize().screenPadding(64.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Profiller", "Profiles"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                // The message takes the room left over, so a long one (e.g. backup paths) never squeezes the title.
                Spacer(Modifier.width(16.dp))
                Text(message.orEmpty(), color = ProfileMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = onBack) { Text(tx("Geri", "Back")) }
            }
        }
        item {
            Text(
                tx(
                    "Her profilin kendi favorileri, listeleri, son izlenenleri, izleme listesi ve kaldığı yerleri vardır. " +
                        "Çocuk profili kilitli kategorileri hiç görmez (PIN girilse bile), yalnızca seçtiğiniz kategorileri görür; " +
                        "çocuk profilinden çıkmak ve Ayarlar'ı açmak PIN ister.",
                    "Each profile has its own favorites, lists, recently watched channels, watchlist and resume points. " +
                        "A kids profile never sees locked categories (not even with the PIN) and only sees the categories you choose; " +
                        "leaving it and opening Settings need the PIN.",
                ),
                color = ProfileMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onEdit(-1L) }, modifier = Modifier.focusRequester(first)) { IconLabel(Icons.Filled.Add, tx("Profil ekle", "Add profile")) }
                OutlinedButton(onClick = { viewModel.setAskProfileOnStart(!askOnStart) }) {
                    Text(
                        if (askOnStart) tx("✓ Açılışta \"Kim izliyor?\" sor", "✓ Ask \"Who's watching?\" at start")
                        else tx("Açılışta \"Kim izliyor?\" sor", "Ask \"Who's watching?\" at start"),
                    )
                }
            }
        }
        items(profiles, key = { it.id }) { profile ->
            ListItem(
                selected = profile.id == active.id,
                onClick = { onEdit(profile.id) },
                headlineContent = { Text(profile.displayName) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            if (profile.kids) tx("Çocuk profili", "Kids profile") else null,
                            if (profile.id == ProfileManager.MAIN_ID) tx("Ana profil", "Main profile") else null,
                            if (profile.id == active.id) tx("şu an kullanılıyor", "in use") else null,
                        ).joinToString(" · ").ifEmpty { " " },
                    )
                },
                leadingContent = { ProfileAvatar(profile, 40.dp) },
                scale = listItemFocusScale(),
                colors = listItemFocusColors(),
                border = listItemFocusBorder(),
            )
        }
    }
}

/** Name, colour, kids mode and (for kids) the allowed categories of one profile. [profileId] -1 = new. */
@Composable
fun ProfileEditScreen(viewModel: MainViewModel, profileId: Long, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(profileId < 0) }
    var original by remember { mutableStateOf<ProfileEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(ProfileManager.COLORS[1]) }
    var kids by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ContentFilter(kids = true)) }
    var editName by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(FlagScopes.LIVE) }
    var categories by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val first = remember { FocusRequester() }

    LaunchedEffect(profileId) {
        if (profileId >= 0) {
            viewModel.profile(profileId)?.let { p ->
                original = p
                name = p.name
                color = p.color
                kids = p.kids
                filter = if (p.kids) p.filter else ContentFilter(kids = true)
            }
            loaded = true
        } else {
            editName = true
        }
        categories = listOf(FlagScopes.LIVE, FlagScopes.MOVIE, FlagScopes.SERIES).associateWith { viewModel.profileCategories(it) }
        runCatching { first.requestFocus() }
    }
    BackHandler(onBack = onDone)
    if (!loaded) return

    val isMain = profileId == ProfileManager.MAIN_ID

    /** Turning kids mode on for the first time pre-selects categories that look meant for children. */
    fun suggested(): ContentFilter = ContentFilter(
        kids = true,
        live = categories[FlagScopes.LIVE].orEmpty().filter(ProfileManager::looksForKids).toSet(),
        movies = categories[FlagScopes.MOVIE].orEmpty().filter(ProfileManager::looksForKids).toSet(),
        series = categories[FlagScopes.SERIES].orEmpty().filter(ProfileManager::looksForKids).toSet(),
    )

    fun allowedFor(scopeName: String): Set<String>? = when (scopeName) {
        FlagScopes.LIVE -> filter.live
        FlagScopes.MOVIE -> filter.movies
        else -> filter.series
    }

    fun setAllowed(scopeName: String, value: Set<String>?) {
        filter = when (scopeName) {
            FlagScopes.LIVE -> filter.copy(live = value)
            FlagScopes.MOVIE -> filter.copy(movies = value)
            else -> filter.copy(series = value)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().screenPadding(64.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (profileId < 0) tx("Yeni profil", "New profile") else tx("Profili düzenle", "Edit profile"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                // The message takes the room left over, so a long one (e.g. backup paths) never squeezes the title.
                Spacer(Modifier.width(16.dp))
                Text(message.orEmpty(), color = ProfileMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            if (viewModel.saveProfile(profileId.takeIf { it >= 0 }, name, color, kids, filter)) onDone()
                        }
                    },
                    enabled = name.isNotBlank() || isMain,
                    modifier = Modifier.focusRequester(first),
                ) { IconLabel(Icons.Filled.Check, tx("Kaydet", "Save")) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDone) { Text(tx("Vazgeç", "Cancel")) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileAvatar(ProfileEntity(name = name, color = color, kids = kids), 64.dp)
                OutlinedButton(onClick = { editName = true }) {
                    Text(tx("Ad: ", "Name: ") + name.ifBlank { if (isMain) tx("Ana profil", "Main profile") else "—" })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tx("Renk", "Colour"), color = ProfileMuted, modifier = Modifier.width(80.dp))
                ProfileManager.COLORS.forEach { c ->
                    Surface(
                        onClick = { color = c },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(c), focusedContainerColor = Color(c)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                        border = surfaceFocusBorder(RoundedCornerShape(50)),
                        modifier = Modifier.size(36.dp),
                    ) {
                        if (c == color) {
                            Box(Modifier.fillMaxSize().border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        if (!isMain) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        kids = !kids
                        val fresh = original?.kids != true && filter.live == null && filter.movies == null && filter.series == null
                        if (kids && fresh) filter = suggested()
                    }) {
                        IconLabel(Icons.Filled.ChildCare, if (kids) tx("✓ Çocuk profili", "✓ Kids profile") else tx("Çocuk profili", "Kids profile"))
                    }
                    if (original != null) {
                        OutlinedButton(onClick = { confirmDelete = true }) { Text(tx("Profili sil", "Delete profile")) }
                    }
                }
            }
        }
        if (kids) {
            item {
                Text(
                    tx(
                        "Çocuk profili yalnızca işaretli kategorileri görür. Kilitli ve gizli kategoriler burada listelenmez; bunlar çocuk profiline hiçbir zaman gösterilmez.",
                        "The kids profile only sees the ticked categories. Locked and hidden categories are not listed; they are never shown to a kids profile.",
                    ),
                    color = ProfileMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((key, label) in listOf(FlagScopes.LIVE to tx("Canlı TV", "Live TV"), FlagScopes.MOVIE to tx("Filmler", "Films"), FlagScopes.SERIES to tx("Diziler", "Series"))) {
                        val allowed = allowedFor(key)
                        val count = if (allowed == null) tx("tümü", "all") else allowed.size.toString()
                        ChoiceButton("$label ($count)", selected = tab == key, check = false) { tab = key }
                    }
                }
            }
            item {
                val names = categories[tab].orEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { setAllowed(tab, null) }) { Text(tx("Tümüne izin ver", "Allow all")) }
                    OutlinedButton(onClick = { setAllowed(tab, emptySet()) }) { Text(tx("Hiçbiri", "None")) }
                    OutlinedButton(onClick = { setAllowed(tab, names.filter(ProfileManager::looksForKids).toSet()) }) {
                        Text(tx("Çocuklara uygun görünenler", "Ones that look child-friendly"))
                    }
                }
            }
            val names = categories[tab].orEmpty()
            if (names.isEmpty()) {
                item { Text(tx("Bu listede kategori yok.", "No categories in this playlist."), color = ProfileMuted) }
            }
            items(names, key = { "$tab|$it" }) { category ->
                val allowed = allowedFor(tab)
                val ticked = allowed == null || category in allowed
                ListItem(
                    selected = false,
                    onClick = {
                        val current = allowed ?: names.toSet()
                        setAllowed(tab, if (ticked) current - category else current + category)
                    },
                    headlineContent = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Icon(
                            if (ticked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (ticked) Accent else ProfileMuted,
                        )
                    },
                    scale = listItemFocusScale(),
                    colors = listItemFocusColors(),
                    border = listItemFocusBorder(),
                )
            }
        }
    }

    if (editName) {
        TextInputDialog(
            tx("Profil adı", "Profile name"),
            initial = name,
            confirmLabel = tx("Tamam", "OK"),
            onSubmit = { name = it.trim() },
            onDismiss = { editName = false },
        )
    }
    if (confirmDelete) {
        original?.let { p ->
            OptionsDialog(
                tx("\"${p.displayName}\" silinsin mi?", "Delete \"${p.displayName}\"?"),
                tx("Favorileri, listeleri ve izleme geçmişi de silinir.", "Its favorites, lists and history are deleted too."),
                options = listOf(DialogOption(tx("Sil", "Delete")) { viewModel.deleteProfile(p); onDone() }),
                onDismiss = { confirmDelete = false },
            )
        }
    }
}
