package io.github.ta3hrj.elm.player

/**
 * Notices a channel whose picture never comes although the stream itself is fine: the sound plays (or
 * plenty is buffered, so it is not the network) but not one new video frame is shown. Seen on Fire TV after
 * the preview handed its player to full screen: sound, a loading circle instead of the picture, and the same
 * player was fine again back in the preview. First the picture surface is attached again; if that does not
 * help, the channel is reconnected, once.
 *
 * Only the start of a channel is watched, until its first new frame: a channel showing a still picture
 * (radio with a logo) is never mistaken for a stuck one.
 */
internal class PictureWatchdog(private val stallMs: Long = STALL_MS) {
    enum class Action { NONE, REATTACH, RECONNECT }

    private var armed = false
    private var baseline = -1L
    private var since = -1L
    private var step = 0

    /** The user started a channel: watch for its first picture, from the first step. */
    fun start() {
        step = 0
        watch()
    }

    /** The same channel again (a reconnect): watch for the picture, without starting the steps over. */
    fun watch() {
        armed = true
        baseline = -1L
        since = -1L
    }

    fun stop() {
        armed = false
    }

    /**
     * Called about once a second. [wantsPicture]: a video stream that should be moving now (sound playing,
     * or waiting with plenty buffered). [renderedFrames]: video frames shown so far by the decoder.
     */
    fun check(nowMs: Long, wantsPicture: Boolean, renderedFrames: Long): Action {
        if (!armed) return Action.NONE
        // First look, or a new decoder whose count started again from zero.
        if (baseline < 0 || renderedFrames < baseline) {
            baseline = renderedFrames
            since = -1L
            return Action.NONE
        }
        if (renderedFrames > baseline) {
            // The picture moves: nothing to do for this channel.
            armed = false
            step = 0
            return Action.NONE
        }
        if (!wantsPicture) {
            since = -1L
            return Action.NONE
        }
        if (since < 0) {
            since = nowMs
            return Action.NONE
        }
        if (nowMs - since < stallMs) return Action.NONE
        step++
        return when (step) {
            1 -> Action.REATTACH.also { watch() }
            2 -> Action.RECONNECT.also { watch() }
            else -> Action.NONE.also { armed = false }
        }
    }

    companion object {
        const val STALL_MS = 4_000L
        /** Buffered ahead while still "loading": the player would have started long ago if the picture worked. */
        const val STUCK_AHEAD_MS = 8_000L
    }
}
