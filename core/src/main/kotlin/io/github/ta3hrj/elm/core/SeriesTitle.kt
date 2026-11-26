package io.github.ta3hrj.elm.core

import java.util.Locale

/**
 * Series recording matches guide entries by title. Guides often append the episode to the title
 * ("Avrupa Yakası (125)", "News - Episode 4", "Show S02E05"); [key] removes such endings so all
 * episodes of a series share one key.
 */
object SeriesTitle {
    private val trailingNumberInBrackets = Regex("""\s*[\(\[][^\)\]]*\d[^\)\]]*[\)\]]\s*$""")
    private val trailingEpisode = Regex(
        """[\s\-:,.|/]*(s\d+\s*e\d+|\d+\s*\.\s*bölüm|bölüm\s*\d+|episode\s*\d+|ep\s*\.?\s*\d+|part\s*\d+|kısım\s*\d+|folge\s*\d+|\d+\s*/\s*\d+)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val spaces = Regex("""\s+""")

    fun key(title: String): String {
        var t = title.lowercase(Locale.ROOT).trim()
        while (true) {
            val next = t.replace(trailingNumberInBrackets, "").replace(trailingEpisode, "").trim().trimEnd('-', ':', ',', '|', '.').trim()
            if (next == t || next.isEmpty()) break
            t = next
        }
        return t.replace(spaces, " ")
    }

    fun sameSeries(a: String, b: String): Boolean = key(a).isNotEmpty() && key(a) == key(b)
}
