package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NearbySyncProtocolTest {
    private val digest = "a".repeat(64)

    @Test fun headerRoundTripPreservesIdentityAndType() {
        val original = NearbySyncProtocol.Header(
            922337203685477L, NearbySyncProtocol.Kind.BACKUP,
            "monogatari-library.bk.zip", 4_096, digest
        )
        assertEquals(original, NearbySyncProtocol.decode(NearbySyncProtocol.encode(original)))
    }

    @Test fun rejectsTraversalOversizedOrCorruptHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.Header(1, NearbySyncProtocol.Kind.FILE, "../secret", 10, digest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.Header(1, NearbySyncProtocol.Kind.BACKUP, "not-a-backup.zip", 10, digest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.Header(1, NearbySyncProtocol.Kind.FILE, "test.pdf",
                NearbySyncProtocol.MAX_BYTES + 1, digest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.Header(1, NearbySyncProtocol.Kind.FILE, "test.pdf", 40, "not-sha256")
        }
        assertThrows(Exception::class.java) {
            NearbySyncProtocol.decode("not JSON".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NearbySyncProtocol.decode(ByteArray(2049) { 65 })
        }
    }

    @Test fun sanitizesUntrustedSelectedNames() {
        val safe = NearbySyncProtocol.safeFileName("private/../../My book!.epub")
        assertFalse(safe.contains('/'))
        assertFalse(safe.contains('\\'))
        assertEquals("My book_.epub", safe)
        assertEquals("received-file", NearbySyncProtocol.safeFileName("../../.."))
        assertTrue(NearbySyncProtocol.safeFileName("a".repeat(400)).length <= 120)
    }

    @Test fun verifiesFileSha256AgainstKnownDigest() {
        val file = File.createTempFile("nearby-protocol-", ".tmp")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                NearbySyncProtocol.sha256(file))
        } finally {
            file.delete()
        }
    }
}
