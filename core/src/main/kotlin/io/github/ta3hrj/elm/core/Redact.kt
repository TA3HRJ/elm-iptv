package io.github.ta3hrj.elm.core

/**
 * Hides logins in text that leaves the TV: error reports people send on. IPTV addresses carry the user
 * name and password in the address itself (`.../live/USER/PASS/123.ts`, `...get.php?username=U&password=P`),
 * and an exception message may quote such an address.
 */
object Redact {
    private val query = Regex("""(?i)\b(username|user|password|passwd|pwd|pass|token|auth|api[_-]?key|secret)=([^&\s"'<>]+)""")
    private val userInfo = Regex("""(?i)(https?://)[^/\s:@"'<>]+:[^/\s@"'<>]+@""")
    private val xtreamPath = Regex("""(?i)/(live|movie|series|timeshift)/[^/\s"'<>]+/[^/\s"'<>]+/""")
    private val bearer = Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{8,}""")

    fun text(input: String): String = input
        .replace(userInfo) { "${it.groupValues[1]}***:***@" }
        .replace(xtreamPath) { "/${it.groupValues[1]}/***/***/" }
        .replace(query) { "${it.groupValues[1]}=***" }
        .replace(bearer) { "${it.groupValues[1]} ***" }
}
