package io.github.ta3hrj.elm.data

import android.util.Base64
import io.github.ta3hrj.elm.core.PlaylistException
import io.github.ta3hrj.elm.core.tx
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup needs its password before it can be restored. */
class BackupPasswordNeeded : Exception(tx("Bu yedek parolayla korunuyor.", "This backup is protected by a password."))

/**
 * Optional password for backup files (they contain the playlists' logins). AES-256-GCM with a key from
 * PBKDF2; the salt, IV and settings travel in the file, so only the password is needed to restore.
 * PBKDF2WithHmacSHA1 is used because it exists on every Android version the app supports.
 */
object BackupCrypto {
    const val FORMAT = "elm-iptv-backup-encrypted"
    // New backups use this many; the count travels in the file, so older backups (120 000) still open.
    private const val ITERATIONS = 400_000
    private const val KDF = "PBKDF2WithHmacSHA1"

    fun isEncrypted(text: String): Boolean = runCatching { JSONObject(text).optString("format") == FORMAT }.getOrElse { false }

    fun encrypt(plain: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(data))
            .toString(2)
    }

    /** Returns the backup text; a wrong password throws a [PlaylistException] saying so. */
    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = key(password, unb64(o.getString("salt")), o.optInt("iterations", ITERATIONS))
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(o.getString("iv"))))
        return try {
            cipher.doFinal(unb64(o.getString("data"))).toString(Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw PlaylistException(tx("Yedek parolası yanlış.", "Wrong backup password."))
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
