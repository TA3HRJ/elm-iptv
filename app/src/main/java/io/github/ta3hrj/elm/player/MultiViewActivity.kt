package io.github.ta3hrj.elm.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.ta3hrj.elm.container
import io.github.ta3hrj.elm.core.tx
import io.github.ta3hrj.elm.data.ChannelEntity
import io.github.ta3hrj.elm.data.ChannelQuery
import io.github.ta3hrj.elm.data.PlaylistEntity
import io.github.ta3hrj.elm.data.toSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Multi-view: up to four channels at once. Each window is a separate stream (connection) from the
 * provider. Arrow keys select a window (its sound plays), OK picks its channel, Menu adds/removes
 * windows or opens the selected one full screen.
 */
@OptIn(UnstableApi::class)
class MultiViewActivity : ComponentActivity() {
    private val repository by lazy { container.repository }

    private class Pane(val frame: FrameLayout, val view: PlayerView, val label: TextView, var player: ExoPlayer?, var channel: ChannelEntity?) {
        var failed = false
    }

    private lateinit var grid: GridLayout
    private val panes = ArrayList<Pane>()
    private var selected = 0
    /** The window whose sound plays regardless of the selection; null = sound follows the selection. */
    private var audioPane: Pane? = null
    private var playlist: PlaylistEntity? = null
    private var list: List<ChannelEntity> = emptyList()
    /** Connections the Xtream account allows (null: unknown or not Xtream). */
    private var maxConnections: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        grid = GridLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(grid)

        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST, -1)
        val firstChannel = intent.getLongExtra(EXTRA_CHANNEL, -1)
        val group = intent.getStringExtra(EXTRA_GROUP)
        lifecycleScope.launch {
            playlist = repository.playlist(playlistId)
            playlist?.let { p ->
                val source = p.toSource() as? io.github.ta3hrj.elm.core.PlaylistSource.Xtream
                if (source != null) {
                    launch {
                        maxConnections = runCatching {
                            io.github.ta3hrj.elm.core.XtreamClient(container.http, source, repository.userAgentFor(p)).account().maxConnections
                        }.getOrNull()
                    }
                }
            }
            val unlocked = container.profiles.contentUnlockedNow
            list = if (group != null) repository.channels(playlistId, ChannelQuery.Group(group), unlocked) else emptyList()
            val start = list.firstOrNull { it.id == firstChannel } ?: list.firstOrNull()
            addPane(start)
            if (!container.prefs.getBoolean(PREF_NOTE_SHOWN, false)) {
                container.prefs.edit().putBoolean(PREF_NOTE_SHOWN, true).apply()
                AlertDialog.Builder(this@MultiViewActivity)
                    .setTitle(tx("Çoklu ekran", "Multi-view"))
                    .setMessage(
                        tx(
                            "Her pencere sağlayıcıya ayrı bir bağlantı açar. Hesabınız bağlantı sayısını sınırlıyorsa bazı pencereler açılmayabilir.\n\nYön tuşları: pencere seç (ses seçili pencereden gelir)\nOK: pencereye kanal seç\nMenü (☰): pencere ekle/kaldır, tam ekran",
                            "Each window opens its own connection to the provider. If your account limits connections, some windows may not open.\n\nArrows: select a window (its sound plays)\nOK: choose a channel for the window\nMenu (☰): add/remove windows, full screen",
                        ),
                    )
                    .setPositiveButton(tx("Tamam", "OK"), null)
                    .show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        panes.forEach { pane -> pane.channel?.let { play(pane, it) } }
    }

    override fun onStop() {
        super.onStop()
        panes.forEach { pane ->
            pane.player?.release()
            pane.player = null
            pane.view.player = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val columns = if (panes.size <= 1) 1 else 2
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> select(selected - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> select(selected + 1)
            KeyEvent.KEYCODE_DPAD_UP -> select(selected - columns)
            KeyEvent.KEYCODE_DPAD_DOWN -> select(selected + columns)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> pickChannel(selected)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> showMenu()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun addPane(channel: ChannelEntity?) {
        if (panes.size >= MAX_PANES) return
        val view = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
        }
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply { setColor(0xB3000000.toInt()); cornerRadius = dp(6).toFloat() }
        }
        val frame = FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(dp(10), 0, 0, dp(10)) })
        }
        val pane = Pane(frame, view, label, null, null)
        // Touch: a tap chooses the window (a second tap its channel), a long press opens the options.
        frame.setOnClickListener {
            val index = panes.indexOf(pane)
            if (index == selected) pickChannel(index) else select(index)
        }
        frame.setOnLongClickListener {
            showMenu()
            true
        }
        panes += pane
        layoutPanes()
        select(panes.lastIndex)
        if (channel != null) play(pane, channel) else pickChannel(panes.lastIndex)
    }

    /** Warns first when the account allows fewer connections than windows would need. */
    private fun addPaneChecked() {
        val limit = maxConnections
        if (limit == null || panes.size < limit) {
            addPane(null)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tx("Bağlantı sınırı", "Connection limit"))
            .setMessage(
                tx(
                    "Hesabınız aynı anda $limit bağlantıya izin veriyor. Yeni pencere büyük olasılıkla açılmaz ya da diğerini durdurur.",
                    "Your account allows $limit connection(s) at a time. A new window will most likely not open, or will stop another one.",
                ),
            )
            .setPositiveButton(tx("Yine de ekle", "Add anyway")) { _, _ -> addPane(null) }
            .setNegativeButton(tx("Vazgeç", "Cancel"), null)
            .show()
    }

    private fun removePane(index: Int) {
        if (panes.size <= 1) return
        val pane = panes.removeAt(index)
        pane.player?.release()
        if (audioPane === pane) audioPane = null
        layoutPanes()
        select(index.coerceAtMost(panes.lastIndex))
    }

    private fun layoutPanes() {
        grid.removeAllViews()
        val columns = if (panes.size <= 1) 1 else 2
        val rows = (panes.size + columns - 1) / columns
        grid.columnCount = columns
        grid.rowCount = rows
        panes.forEachIndexed { i, pane ->
            val params = GridLayout.LayoutParams(
                GridLayout.spec(i / columns, 1f),
                GridLayout.spec(i % columns, 1f),
            ).apply {
                width = 0
                height = 0
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            grid.addView(pane.frame, params)
        }
    }

    private fun select(index: Int) {
        if (panes.isEmpty()) return
        selected = index.coerceIn(0, panes.lastIndex)
        panes.forEachIndexed { i, pane ->
            val active = i == selected
            pane.frame.foreground = if (active) GradientDrawable().apply { setStroke(dp(3), 0xFF4FA3FF.toInt()) } else null
        }
        updateAudio()
    }

    private fun audible(pane: Pane): Boolean = (audioPane ?: panes.getOrNull(selected)) === pane

    /** Only one window plays sound; its label carries a speaker mark. */
    private fun updateAudio() {
        panes.forEach { pane ->
            val on = audible(pane)
            pane.player?.volume = if (on) 1f else 0f
            val channel = pane.channel ?: return@forEach
            if (pane.failed) return@forEach
            pane.label.text = (if (on && panes.size > 1) "🔊 " else "") + "${channel.number}  ${channel.name}"
        }
    }

    private fun play(pane: Pane, channel: ChannelEntity) {
        pane.channel = channel
        pane.failed = false
        io.github.ta3hrj.elm.data.MemoryLog.countPlay()
        pane.label.text = "${channel.number}  ${channel.name}"
        val player = pane.player ?: ExoPlayer.Builder(
            this,
            DefaultRenderersFactory(this).setEnableDecoderFallback(true).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(3_000, 15_000, 1_500, 2_500)
                    // A hard byte ceiling per pane (time does not override it): all panes together stay within half the heap.
                    .setTargetBufferBytes(multiViewBufferBytes(Runtime.getRuntime().maxMemory(), MAX_PANES))
                    .setPrioritizeTimeOverSizeThresholds(false)
                    .build(),
            )
            .build()
            .also { exo ->
                // Keep several decoders within what TV sticks can handle.
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setMaxVideoSize(1280, 720).build()
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        pane.failed = true
                        pane.label.text = "${channel.name} · " + tx("açılamadı (bağlantı sınırı?)", "failed (connection limit?)")
                    }
                })
                pane.player = exo
                pane.view.player = exo
            }
        val dataSource = OkHttpDataSource.Factory(container.http).setUserAgent(repository.userAgentFor(playlist, channel))
        val path = channel.streamUrl.substringBefore('?').lowercase()
        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .apply { if (path.endsWith(".m3u8") || path.endsWith(".m3u")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
        updateAudio()
    }

    private fun pickChannel(index: Int) {
        if (list.isEmpty()) return pickCategory(index)
        val names = list.map { "${it.number}  ${it.name}" } + tx("Başka kategori…", "Another category…")
        AlertDialog.Builder(this)
            .setTitle(tx("Kanal seç", "Choose a channel"))
            .setItems(names.toTypedArray()) { _, which ->
                if (which == list.size) pickCategory(index) else panes.getOrNull(index)?.let { play(it, list[which]) }
            }
            .show()
    }

    private fun pickCategory(index: Int) {
        val playlistId = playlist?.id ?: return
        lifecycleScope.launch {
            val groups = repository.observeGroups(playlistId).first().map { it.name }
            AlertDialog.Builder(this@MultiViewActivity)
                .setTitle(tx("Kategori", "Category"))
                .setItems(groups.toTypedArray()) { _, which ->
                    lifecycleScope.launch {
                        val unlocked = container.profiles.contentUnlockedNow
                        val group = groups[which]
                        if (!unlocked && repository.isGroupLocked(playlistId, io.github.ta3hrj.elm.data.FlagScopes.LIVE, group)) return@launch
                        list = repository.channels(playlistId, ChannelQuery.Group(group), unlocked)
                        pickChannel(index)
                    }
                }
                .show()
        }
    }

    private fun showMenu() {
        val items = buildList<Pair<String, () -> Unit>> {
            if (panes.size < MAX_PANES) add(tx("Pencere ekle", "Add window") to { addPaneChecked() })
            if (panes.size > 1) add(tx("Seçili pencereyi kaldır", "Remove selected window") to { removePane(selected) })
            panes.getOrNull(selected)?.channel?.let { channel ->
                add(tx("Tam ekran aç", "Open full screen") to {
                    PlayerActivity.start(this@MultiViewActivity, channel.playlistId, ChannelQuery.Group(channel.groupTitle), channel.id)
                    finish()
                })
            }
            add(tx("Kanal değiştir", "Change channel") to { pickChannel(selected) })
            if (panes.size > 1) {
                val current = panes.getOrNull(selected)
                if (current != null && audioPane === current) {
                    add(tx("Ses seçili pencereyi izlesin", "Sound follows the selected window") to { audioPane = null; updateAudio() })
                } else {
                    add(tx("Sesi bu pencereye sabitle", "Keep sound on this window") to { audioPane = current; updateAudio() })
                }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(tx("Çoklu ekran", "Multi-view"))
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val MAX_PANES = 4
        private const val EXTRA_PLAYLIST = "playlist"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_GROUP = "group"
        private const val PREF_NOTE_SHOWN = "multiview_note_shown"

        fun start(context: Context, channel: ChannelEntity) {
            context.startActivity(
                Intent(context, MultiViewActivity::class.java)
                    .putExtra(EXTRA_PLAYLIST, channel.playlistId)
                    .putExtra(EXTRA_CHANNEL, channel.id)
                    .putExtra(EXTRA_GROUP, channel.groupTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
