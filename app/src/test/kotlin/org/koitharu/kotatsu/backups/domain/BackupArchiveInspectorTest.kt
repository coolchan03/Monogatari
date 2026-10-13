package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveInspectorTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun archive(vararg sections: Pair<String, String>): File {
        val file = temporary.newFile("backup-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, body) in sections) {
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        return file
    }

    @Test fun validLegacyAndNewSectionsAreRecognized() {
        val file = archive("index" to """[{"created_at":0}]""",
            "history" to "[]", "chapter_meta" to "[]", "future_section" to "[]")
        val result = BackupArchiveInspector.inspect(file, requireIndex = true)
        assertEquals(4, result.entryCount)
        assertTrue(BackupSection.INDEX in result.sections)
        assertTrue(BackupSection.CHAPTER_META in result.sections)
        assertEquals(1, result.unknownSections)
        assertTrue(result.expandedBytes > 0L)
    }

    @Test fun oldSectionOnlyArchivesArePreviewableButCannotUploadAsDeviceSnapshot() {
        val file = archive("history" to "[]")
        assertTrue(BackupSection.HISTORY in BackupArchiveInspector.inspect(file).sections)
        assertThrows(IOException::class.java) {
            BackupArchiveInspector.inspect(file, requireIndex = true)
        }
    }

    @Test fun traversalAndCaseInsensitiveDuplicatesAreRejected() {
        assertThrows(IOException::class.java) {
            BackupArchiveInspector.inspect(archive("../history" to "[]"))
        }
        assertThrows(IOException::class.java) {
            BackupArchiveInspector.inspect(archive("history" to "[]", "HISTORY" to "[]"))
        }
        assertThrows(IOException::class.java) {
            BackupArchiveInspector.inspect(archive("anything" to "[]"))
        }
    }

    @Test fun indexCannotClaimUnboundedSize() {
        val file = archive("index" to "x".repeat(17_000), "history" to "[]")
        assertThrows(IOException::class.java) { BackupArchiveInspector.inspect(file) }
    }

    @Test fun corruptedStoredEntryFailsCrcCheck() {
        val file = temporary.newFile("stored.zip")
        val payload = "unique-history-payload".toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(payload) }.value
        ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("history").apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = size
                this.crc = crc
            })
            out.write(payload)
            out.closeEntry()
        }
        val bytes = file.readBytes()
        val start = bytes.indices.first {
            it + payload.size <= bytes.size &&
                bytes.copyOfRange(it, it + payload.size).contentEquals(payload)
        }
        bytes[start + 3] = (bytes[start + 3].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertThrows(IOException::class.java) { BackupArchiveInspector.inspect(file) }
    }

    @Test fun boundedCopyDoesNotLeavePartialTempFile() {
        val target = temporary.newFile("copied.zip")
        assertThrows(IOException::class.java) {
            BackupArchiveInspector.copyBounded(
                ByteArrayInputStream(ByteArray(128)), target, maxBytes = 64)
        }
        assertFalse(target.exists())
        val second = temporary.newFile("small.zip")
        assertEquals(3L, BackupArchiveInspector.copyBounded(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)), second, maxBytes = 64))
        assertEquals(3L, second.length())
    }
}
