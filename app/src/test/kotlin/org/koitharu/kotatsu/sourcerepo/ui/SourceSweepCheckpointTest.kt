package org.koitharu.kotatsu.sourcerepo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceSweepCheckpointTest {
    @Test fun resumesByStableKeyAndKeepsLatestResult() {
        val file = File.createTempFile("monogatari-source-test", ".jsonl")
        try {
            val checkpoint = SourceSweepCheckpoint(file)
            checkpoint.append(SourceSweepCheckpoint.Record("KOTATSU:MANGADEX", "PASS", "PASS | KOTATSU | MANGADEX | popular=20"))
            checkpoint.append(SourceSweepCheckpoint.Record("JS:42", "FAIL", "FAIL | 42 | Foo | category=DNS"))
            checkpoint.append(SourceSweepCheckpoint.Record("JS:42", "PASS", "PASS | 42 | Foo | popular=5"))
            val saved = checkpoint.read()
            assertEquals(2, saved.size)
            assertEquals("PASS", saved["JS:42"]?.status)
            assertEquals("PASS | 42 | Foo | popular=5", saved["JS:42"]?.detail)
            assertTrue(checkpoint.exists())
            checkpoint.clear()
            assertFalse(checkpoint.exists())
        } finally {
            file.delete()
        }
    }

    @Test fun skipsMalformedRecordsAndPreservesValidEntries() {
        val file = File.createTempFile("monogatari-source-malformed", ".jsonl")
        try {
            file.writeText("not json\n{\"key\":\"JS:7\",\"status\":\"PASS\",\"detail\":\"PASS | 7\"}\n{broken")
            val entries = SourceSweepCheckpoint(file).read()
            assertEquals(1, entries.size)
            assertEquals("PASS | 7", entries["JS:7"]?.detail)
        } finally {
            file.delete()
        }
    }
}
