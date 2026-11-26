package io.github.ta3hrj.elm.core

/** Where people can support the app's development (GitHub Sponsors); shown in About on every platform. */
object Support {
    const val URL = "https://github.com/sponsors/TA3HRJ"

    /** The address as shown on screen, without the scheme. */
    val label: String get() = URL.removePrefix("https://")
}
