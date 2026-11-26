package io.github.ta3hrj.elm.core

import java.util.Locale

/** Recognises adult categories by name ("+18", "XXX", "Adult", "Yetişkin"…) so they can be locked automatically. */
object AdultContent {
    // Matched against the upper-cased name; Turkish İ/I variants are listed explicitly.
    private val pattern = Regex(
        """(\+\s?18|18\s?\+|\+\s?21|21\s?\+|\bXXX\b|\bADULTS?\b|\bFOR ADULTS\b|YET[İI][SŞ]K[İI]N|EROT[İI]K|\bEROTIC|\bPORN|\bSEX\b|\bSEXY\b|\bHOT GIRLS\b|\bPLAYBOY\b|\bBRAZZERS\b)""",
    )

    /** Names that look adult but are not (an animation block, for example). */
    private val exceptions = listOf("ADULT SWIM", "YOUNG ADULT")

    fun isAdultCategory(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        if (exceptions.any { it in upper }) return false
        return pattern.containsMatchIn(upper)
    }
}
