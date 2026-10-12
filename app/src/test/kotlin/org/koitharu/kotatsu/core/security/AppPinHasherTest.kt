package org.koitharu.kotatsu.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.parsers.util.md5

class AppPinHasherTest {
    @Test fun saltedHashAndVerification() {
        val first = AppPinHasher.hash("1234")
        val second = AppPinHasher.hash("1234")
        assertTrue(first.startsWith("pbkdf2$"))
        assertTrue(second.startsWith("pbkdf2$"))
        assertFalse(first == second)
        assertTrue(AppPinHasher.verify("1234", first).matches)
        assertFalse(AppPinHasher.verify("4321", first).matches)
        assertNull(AppPinHasher.verify("1234", first).upgradedHash)
    }

    @Test fun migrateLegacyMd5OnCorrectPinOnly() {
        val old = "2468".md5()
        assertFalse(AppPinHasher.verify("0000", old).matches)
        val migrated = AppPinHasher.verify("2468", old)
        assertTrue(migrated.matches)
        assertNotNull(migrated.upgradedHash)
        assertTrue(AppPinHasher.verify("2468", migrated.upgradedHash!!).matches)
        assertFalse(AppPinHasher.verify("0000", migrated.upgradedHash!!).matches)
    }

    @Test fun invalidHashesFailClosed() {
        assertFalse(AppPinHasher.verify("1234", "").matches)
        assertFalse(AppPinHasher.verify("1234", "pbkdf2$bad").matches)
        assertFalse(AppPinHasher.verify("1234", "pbkdf2$%%%$%%%").matches)
    }
}
