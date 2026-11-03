package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backups.domain.NearbyBatch.Entry

class NearbyBatchTest {

    private val mb = 1024L * 1024

    @Test fun acceptsEverythingWithinLimits() {
        val admission = NearbyBatch.admit(listOf(Entry("a", 10 * mb), Entry("b", null), Entry("c", 1L)))
        assertEquals(listOf(0, 1, 2), admission.accepted)
        assertTrue(admission.tooLarge.isEmpty())
        assertFalse(admission.overTotal)
        assertFalse(admission.tooMany)
    }

    @Test fun leavesOutFilesOverThePerFileLimitButSendsTheRest() {
        val admission = NearbyBatch.admit(listOf(Entry("a", 1 * mb), Entry("big", 257 * mb), Entry("c", 2 * mb)))
        assertEquals(listOf(0, 2), admission.accepted)
        assertEquals(listOf(1), admission.tooLarge)
        assertFalse(admission.overTotal)
    }

    @Test fun exactPerFileLimitIsAllowed() {
        val admission = NearbyBatch.admit(listOf(Entry("a", NearbySyncProtocol.MAX_BYTES)))
        assertEquals(listOf(0), admission.accepted)
    }

    @Test fun rejectsASelectionAboveTheTotalCap() {
        // Nine files of 256 MB = 2.25 GB, over the 2 GB cap.
        val entries = List(9) { Entry("f$it", 256 * mb) }
        val admission = NearbyBatch.admit(entries)
        assertTrue(admission.overTotal)
        assertTrue(admission.accepted.isEmpty())
        // Eight of them are exactly 2 GB and fine.
        assertEquals(8, NearbyBatch.admit(entries.take(8)).accepted.size)
    }

    @Test fun rejectsTooManyFiles() {
        val admission = NearbyBatch.admit(List(NearbySyncProtocol.MAX_BATCH_FILES + 1) { Entry("f$it", 1L) })
        assertTrue(admission.tooMany)
        assertTrue(admission.accepted.isEmpty())
    }

    @Test fun summaryListsOnlyNonZeroProblems() {
        fun text(sent: Int, failed: Int, skipped: Int) = NearbyBatch.summary(
            NearbyBatch.Tally(sent, failed, skipped),
            { "$it sent" }, { "$it failed" }, { "$it skipped" },
        )
        assertEquals("4 sent, 1 failed", text(4, 1, 0))
        assertEquals("5 sent", text(5, 0, 0))
        assertEquals("0 sent, 2 failed", text(0, 2, 0))
        assertEquals("2 sent, 1 failed, 3 skipped", text(2, 1, 3))
    }

    @Test fun percentIsClamped() {
        assertEquals(0, NearbyBatch.percent(5, 0))
        assertEquals(37, NearbyBatch.percent(37, 100))
        assertEquals(100, NearbyBatch.percent(500, 100))
    }
}
