package io.github.ta3hrj.elm.core

import java.util.Locale

/**
 * The app's two languages. Texts are written next to their use as `tx("Türkçe", "English")`,
 * so both languages stay together and nothing can be left untranslated by accident.
 */
object Lang {
    enum class Choice { SYSTEM, TURKISH, ENGLISH }

    @Volatile
    var english: Boolean = Locale.getDefault().language != "tr"
        private set

    /** Applies a language choice; SYSTEM follows the device (Turkish if the device is Turkish, else English). */
    fun apply(choice: Choice, deviceLanguage: String = Locale.getDefault().language) {
        english = when (choice) {
            Choice.TURKISH -> false
            Choice.ENGLISH -> true
            Choice.SYSTEM -> deviceLanguage != "tr"
        }
    }

    /** Locale for dates and number formatting in the selected language. */
    val locale: Locale get() = if (english) Locale.ENGLISH else Locale.forLanguageTag("tr")
}

/** Returns [tr] or [en] depending on the selected language. */
fun tx(tr: String, en: String): String = if (Lang.english) en else tr
