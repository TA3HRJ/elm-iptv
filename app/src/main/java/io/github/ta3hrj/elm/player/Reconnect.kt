package io.github.ta3hrj.elm.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * When to try a dropped live stream again, for the full-screen player and the preview alike: a few quick
 * tries for any error, then, for connection trouble only, longer and longer pauses (up to a minute) for as
 * long as the player stays open. A refused, missing or undecodable stream is not tried forever.
 */
object Reconnect {
    const val QUICK_TRIES = 5
    const val QUICK_DELAY_MS = 3_000L
    const val LONG_DELAY_MS = 10_000L
    const val MAX_DELAY_MS = 60_000L

    /** How long to wait before try number [failures] + 1, or null to give up. */
    fun delayMs(failures: Int, networkProblem: Boolean): Long? = when {
        failures < QUICK_TRIES -> QUICK_DELAY_MS
        networkProblem -> (LONG_DELAY_MS shl (failures - QUICK_TRIES).coerceAtMost(3)).coerceAtMost(MAX_DELAY_MS)
        else -> null
    }
}

/** HTTP status code behind a playback error, if the server answered with one. */
internal fun httpStatus(error: PlaybackException): Int? {
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

/** Errors that a working connection would fix (as opposed to a refused, missing or undecodable stream). */
internal fun isNetworkProblem(error: PlaybackException): Boolean {
    httpStatus(error)?.let { code -> return code >= 500 || code == 408 || code == 429 }
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_UNSPECIFIED,
        -> true
        else -> false
    }
}

/** Why a live stream dropped, for the memory log: [error] null = it ended (the server closed it). */
internal fun dropReason(error: PlaybackException?): String =
    if (error == null) dropReason(null, null, null) else dropReason(error.errorCodeName, httpStatus(error), error.cause)

/** [errorCodeName] null = the stream ended. Otherwise the HTTP status, or the error and its deepest cause. */
internal fun dropReason(errorCodeName: String?, httpStatus: Int?, cause: Throwable?): String {
    if (errorCodeName == null) return "stream ended (the server closed it)"
    if (httpStatus != null) return "HTTP $httpStatus"
    val name = errorCodeName.removePrefix("ERROR_CODE_")
    var root = cause ?: return name
    while (true) {
        val next = root.cause
        if (next == null || next === root) break
        root = next
    }
    val message = root.message?.lineSequence()?.firstOrNull()?.trim()?.take(80)
    val detail = if (message.isNullOrEmpty()) root.javaClass.simpleName else "${root.javaClass.simpleName}: $message"
    return "$name ($detail)"
}

/** The memory-log line for a dropped stream; [waitMs] null = not tried again. */
internal fun dropLine(where: String, channel: String?, reason: String, bufferedMs: Long, attempt: Int, waitMs: Long?): String {
    val name = channel?.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
    val ahead = String.format(java.util.Locale.ROOT, "%.1f", bufferedMs.coerceAtLeast(0) / 1000.0)
    val next = if (waitMs == null) "not tried again" else "retry $attempt in ${waitMs / 1000} s"
    return "stream dropped ($where$name): $reason · $ahead s buffered · $next"
}

/**
 * Notices a server that keeps closing a stream soon after it starts playing. With a provider that allows one
 * connection per account, that is what happens while another device watches on the same account: each
 * reconnect throws the other device off, which takes the connection back half a minute later, and so on.
 * After [LIMIT] such short plays in a row the player stops trying, says why, and leaves the other device be.
 */
internal class ServerCloseGuard(private val shortPlayMs: Long = SHORT_PLAY_MS) {
    private var shortCloses = 0

    /** A new channel, or one chosen again by the user: count from zero. */
    fun reset() {
        shortCloses = 0
    }

    /** The server closed the stream [playedMs] after it was (re)connected; true = try again. */
    fun onServerClosed(playedMs: Long): Boolean {
        if (playedMs >= shortPlayMs) shortCloses = 0
        shortCloses++
        return shortCloses < LIMIT
    }

    companion object {
        const val LIMIT = 4
        const val SHORT_PLAY_MS = 120_000L

        /** The memory-log reason when the guard stops trying. */
        const val REASON = "the server keeps closing it (another device on the same account?)"
    }
}
