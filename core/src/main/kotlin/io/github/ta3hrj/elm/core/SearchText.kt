package io.github.ta3hrj.elm.core

import java.text.Normalizer
import java.util.Locale

/**
 * Search text without case and accents, so "cocuk" finds "ÇOCUK" and "istanbul" finds "İSTANBUL".
 * SQLite's LIKE only ignores case for English letters, so names are stored in this form as well.
 */
object SearchText {
    fun normalize(text: String): String {
        val folded = StringBuilder(text.length)
        for (ch in text) {
            folded.append(
                when (ch) {
                    // Turkish i's: whatever was typed, they all mean the same letter for searching.
                    'İ', 'I', 'ı' -> 'i'
                    else -> ch
                },
            )
        }
        val decomposed = Normalizer.normalize(folded, Normalizer.Form.NFD)
        val out = StringBuilder(decomposed.length)
        var space = false
        for (ch in decomposed) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            if (ch.isWhitespace()) {
                if (!space && out.isNotEmpty()) out.append(' ')
                space = true
                continue
            }
            space = false
            out.append(ch)
        }
        return out.toString().trimEnd().lowercase(Locale.ROOT)
    }

    /** LIKE pattern characters typed by the user are matched literally. */
    fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
