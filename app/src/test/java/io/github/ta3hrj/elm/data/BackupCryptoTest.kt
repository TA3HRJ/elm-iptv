package io.github.ta3hrj.elm.data

import io.github.ta3hrj.elm.core.PlaylistException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCryptoTest {
    private val plain = """{"format":"elm-iptv-backup","playlists":[{"username":"erhan","password":"gizli"}]}"""

    @Test
    fun roundTrip() {
        val encrypted = BackupCrypto.encrypt(plain, "Şifre 123")
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse("logins must not be readable", encrypted.contains("gizli"))
        assertEquals(plain, BackupCrypto.decrypt(encrypted, "Şifre 123"))
        // Same text, new salt and IV each time.
        assertNotEquals(encrypted, BackupCrypto.encrypt(plain, "Şifre 123"))
    }

    @Test(expected = PlaylistException::class)
    fun wrongPasswordIsRefused() {
        BackupCrypto.decrypt(BackupCrypto.encrypt(plain, "doğru"), "yanlış")
    }
}
