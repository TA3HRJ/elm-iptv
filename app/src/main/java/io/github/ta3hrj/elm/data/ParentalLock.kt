package io.github.ta3hrj.elm.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Parental PIN. Locked categories stay closed until the PIN is entered; after that they are open
 * until [lock] is called or the app process ends. Only a salted SHA-256 hash of the PIN is stored.
 */
class ParentalLock(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {
    private val _unlocked = MutableStateFlow(!hasPin())
    /** True when locked content may be shown (no PIN set, or the PIN was entered this session). */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _pinSet = MutableStateFlow(hasPin())
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    fun hasPin(): Boolean = prefs.getString(KEY_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        prefs.edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
        _pinSet.value = true
        _unlocked.value = true
    }

    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).remove(KEY_FAILS).remove(KEY_LOCKED_UNTIL).apply()
        _pinSet.value = false
        _unlocked.value = true
    }

    /** Checks the PIN and, if correct, opens locked content for this session. */
    fun unlock(pin: String): Boolean {
        val ok = verify(pin)
        if (ok) _unlocked.value = true
        return ok
    }

    /**
     * Checks the PIN. After [FREE_ATTEMPTS] wrong PINs in a row every further try waits, starting at
     * 30 seconds and doubling up to 15 minutes, so the four digits cannot simply be tried one by one
     * (the wait survives restarting the app). While waiting, every PIN counts as wrong.
     */
    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return true
        if (lockoutRemainingMs() > 0) return false
        val ok = prefs.getString(KEY_HASH, null) == hash(salt, pin)
        if (ok) {
            prefs.edit().remove(KEY_FAILS).remove(KEY_LOCKED_UNTIL).apply()
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILS, fails)
            if (fails >= FREE_ATTEMPTS) {
                val wait = (FIRST_WAIT_MS shl (fails - FREE_ATTEMPTS).coerceAtMost(5)).coerceAtMost(MAX_WAIT_MS)
                editor.putLong(KEY_LOCKED_UNTIL, clock() + wait)
            }
            editor.apply()
        }
        return ok
    }

    /** Time left before the PIN can be tried again; 0 when it can. */
    fun lockoutRemainingMs(): Long = (prefs.getLong(KEY_LOCKED_UNTIL, 0L) - clock()).coerceAtLeast(0L)

    /** Closes locked content again (if a PIN is set). */
    fun lock() {
        if (hasPin()) _unlocked.value = false
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val FREE_ATTEMPTS = 5
        const val FIRST_WAIT_MS = 30_000L
        const val MAX_WAIT_MS = 15 * 60_000L
        const val PIN_LENGTH = 4
    }
}
