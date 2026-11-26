package io.github.ta3hrj.elm.player

import io.github.ta3hrj.elm.core.tx

/**
 * The name of a subtitle track in the menu: what the stream calls it (or its language), a marker for
 * forced subtitles (only foreign speech is translated) and for hearing-impaired ones, and the language
 * code, so tracks with similar names can be told apart: "Türkçe · Zorunlu · tr".
 */
fun subtitleLabel(name: String, languageCode: String?, forced: Boolean, hearingImpaired: Boolean): String {
    val parts = mutableListOf(name)
    // Many streams already put "[Forced]" in the name; do not say it twice.
    if (forced && !name.contains("forced", ignoreCase = true) && !name.contains("zorunlu", ignoreCase = true)) {
        parts += tx("Zorunlu", "Forced")
    }
    if (hearingImpaired && !name.contains("SDH", ignoreCase = true)) parts += tx("İşitme engelliler", "Hearing impaired")
    val code = languageCode?.trim()?.takeIf { it.isNotEmpty() && it != "und" }
    if (code != null && !name.equals(code, ignoreCase = true)) parts += code
    return parts.joinToString(" · ")
}
