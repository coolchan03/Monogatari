package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ForeignBackupReviewTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun mangayomiCollectionsArePreviewedWithoutImport() {
        val file = temporary.newFile("mangayomi.json")
        file.writeText("""{"manga":[{"title":"First"}],"sources":[1,2],"history":[]}""")
        val result = ForeignBackupReview.inspect(file, file.name)
        assertEquals(ForeignBackupFormat.MANGAYOMI_JSON, result.format)
        assertEquals(1, result.counts["Manga"])
        assertEquals(2, result.counts["Sources"])
        assertEquals(0, result.counts["History"])
        assertFalse(result.importSupported)
    }

    @Test fun forgedOrWrongSchemaCannotClaimMangayomiSupport() {
        val generic = temporary.newFile("generic.json")
        generic.writeText("""{"manga":"not a collection","sources":"also not a collection"}""")
        assertThrows(IOException::class.java) {
            ForeignBackupReview.inspect(generic, generic.name)
        }
    }

    @Test fun nativeZipIsIntegrityCheckedAndNeverAutoRestored() {
        val file = temporary.newFile("backup.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            listOf("index", "history").forEach { entry ->
                out.putNextEntry(ZipEntry(entry))
                out.write("[]".toByteArray())
                out.closeEntry()
            }
        }
        val result = ForeignBackupReview.inspect(file, file.name)
        assertEquals(ForeignBackupFormat.KOTATSU_ZIP, result.format)
        assertEquals(2, result.counts["Known sections"])
        assertFalse(result.importSupported)
    }

    @Test fun mihonContainerIsValidatedButNotMisrepresentedAsDecoded() {
        val file = temporary.newFile("export.tachibk")
        GZIPOutputStream(file.outputStream()).use { it.write(byteArrayOf(1, 2, 3, 4)) }
        val valid = ForeignBackupReview.inspect(file, file.name)
        assertEquals(ForeignBackupFormat.MIHON_PROTOBUF_GZIP, valid.format)
        assertEquals(4, valid.counts["Unpacked bytes"])
        assertFalse(valid.importSupported)
        val bad = file.readBytes()
        bad[bad.lastIndex] = (bad.last().toInt() xor 4).toByte()
        file.writeBytes(bad)
        assertThrows(IOException::class.java) {
            ForeignBackupReview.inspect(file, file.name)
        }
    }

    @Test fun unknownFileIsNotImported() {
        val file = temporary.newFile("random.txt")
        file.writeText("hello")
        val result = ForeignBackupReview.inspect(file, file.name)
        assertEquals(ForeignBackupFormat.UNKNOWN, result.format)
        assertTrue(result.counts.isEmpty())
        assertFalse(result.importSupported)
    }
}
