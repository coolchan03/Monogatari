package org.koitharu.kotatsu.jsext.repo

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsSourceIntegrityTest {
    private val hashA = JsSourceIntegrity.sha256("code A")
    private val hashB = JsSourceIntegrity.sha256("code B")

    @Test fun sameVersionCannotSilentlyChange() {
        val error = assertThrows(IOException::class.java) {
            JsSourceIntegrity.validate("1.0", hashB, "1.0", hashA, null)
        }
        assertEquals("Source file changed without a version bump", error.message)
    }

    @Test fun upgradeAllowsChangedCodeAndDeclaredHashMustMatch() {
        JsSourceIntegrity.validate("1.1", hashB, "1.0", hashA, hashB)
        assertThrows(IOException::class.java) {
            JsSourceIntegrity.validate("1.1", hashB, "1.0", hashA, hashA)
        }
    }

    @Test fun firstInstallAndIdenticalContentWork() {
        JsSourceIntegrity.validate("1.0", hashA, null, null, null)
        JsSourceIntegrity.validate("1.0", hashA, "1.0", hashA, null)
    }

    @Test fun downgradeAndInvalidHashAreRejected() {
        assertThrows(IOException::class.java) {
            JsSourceIntegrity.validate("0.9", hashA, "1.0", hashA, null)
        }
        assertThrows(IOException::class.java) {
            JsSourceIntegrity.validate("1.1", hashB, "1.0", hashA, "bad")
        }
    }
}
