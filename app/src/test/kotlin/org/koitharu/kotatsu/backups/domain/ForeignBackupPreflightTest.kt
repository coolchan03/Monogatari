package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForeignBackupPreflightTest {
    @Test fun onlyRecognizesCompatibleHeaderAndExtensionPairs() {
        val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val gzip = byteArrayOf(0x1f, 0x8b.toByte(), 0x08)
        assertEquals(ForeignBackupFormat.KOTATSU_ZIP,
            ForeignBackupPreflight.detect("export.bk.zip", zip))
        assertEquals(ForeignBackupFormat.MIHON_PROTOBUF_GZIP,
            ForeignBackupPreflight.detect("export.tachibk", gzip))
        assertEquals(ForeignBackupFormat.MANGAYOMI_JSON,
            ForeignBackupPreflight.detect("backup.JSON", "  {\"manga\":[],\"sources\":[]}".toByteArray()))
        assertEquals(ForeignBackupFormat.UNKNOWN,
            ForeignBackupPreflight.detect("unsafe.zip", gzip))
        assertEquals(ForeignBackupFormat.UNKNOWN,
            ForeignBackupPreflight.detect("unsafe.tachibk", zip))
        assertEquals(ForeignBackupFormat.UNKNOWN,
            ForeignBackupPreflight.detect("unsafe.json", "<html>".toByteArray()))
        assertEquals(ForeignBackupFormat.UNKNOWN,
            ForeignBackupPreflight.detect("generic.json", "{\"data\":1}".toByteArray()))
    }

    private val available = listOf(
        ForeignSourceMatcher.Source("a", "Manga House", "https://www.mangahouse.example"),
        ForeignSourceMatcher.Source("b", "Library", "https://books.example"),
        ForeignSourceMatcher.Source("c", "Library", "https://other.example"),
    )
    @Test fun uniquelyMatchesKnownHostEvenWithMobilePrefix() {
        val result = ForeignSourceMatcher.resolve(ForeignSourceMatcher.Requested(
            null, "Not the same name", "https://m.mangahouse.example/series/123"), available)
        assertEquals("a", result.candidate?.id)
        assertEquals(ForeignSourceMatcher.Confidence.UNIQUE_HOST, result.confidence)
    }

    @Test fun ambiguityOrConflictingHostNeverAutoMaps() {
        val ambiguous = ForeignSourceMatcher.resolve(ForeignSourceMatcher.Requested(
            null, "Library", null), available)
        assertNull(ambiguous.candidate)
        val wrongHost = ForeignSourceMatcher.resolve(ForeignSourceMatcher.Requested(
            "a", "Manga House", "https://evil.example"), available)
        assertNull(wrongHost.candidate)
        val insecure = ForeignSourceMatcher.resolve(ForeignSourceMatcher.Requested(
            "a", "Manga House", "http://www.mangahouse.example"), available)
        assertNull(insecure.candidate)
    }

    @Test fun unambiguousIdAndNameCanMatchWithoutClaimingVerifiedContent() {
        val idOnly = ForeignSourceMatcher.Requested("a", null, null)
        assertNull(ForeignSourceMatcher.resolve(idOnly, available).candidate)
        val byId = ForeignSourceMatcher.resolve(idOnly, available, trustSourceIds = true)
        assertEquals(ForeignSourceMatcher.Confidence.EXACT_ID, byId.confidence)
        val byName = ForeignSourceMatcher.resolve(ForeignSourceMatcher.Requested(
            null, "  manga  HOUSE  ", null), available)
        assertEquals(ForeignSourceMatcher.Confidence.UNIQUE_NAME, byName.confidence)
        assertEquals("a", byName.candidate?.id)
    }
}
