package org.koitharu.kotatsu.core.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonogatariTestPlanTest {
    @Test fun checklistCoversTheThreeMediaAndCoreWorkflows() {
        val ids = MonogatariTestPlan.cases.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in listOf("startup", "manga_search", "book_sources", "public_catalogs",
            "local_books", "anime_search", "video_playback", "extensions", "dart",
            "downloads", "backup_sync", "customization", "stability")) {
            assertTrue("Missing QA step: $id", id in ids)
        }
    }

    @Test fun reportIncludesFailuresAndDoesNotClaimUntestedFeaturesPass() {
        val findings = mapOf(
            "manga_search" to TestFinding(CheckResult.PASS, "Found chapters"),
            "video_playback" to TestFinding(CheckResult.FAIL, "Black screen after tapping Play"),
        )
        val report = MonogatariTestPlan.report(
            "2026.10-test", "Example phone / Android 16",
            "Network connected", "Saved checks=12 PASS=7 PARTIAL=3 FAIL=2",
            findings,
        )
        assertTrue(report.contains("1 looks good, 1 needs fixing"))
        assertTrue(report.contains("[FAIL] Actual video playback"))
        assertTrue(report.contains("Black screen after tapping Play"))
        assertTrue(report.contains("[UNTESTED]"))
        assertFalse(report.contains("guarantee of complete compatibility.\nAll passed"))
    }

    @Test fun longNotesAreBoundedAndNewlinesAreSingleLine() {
        val report = MonogatariTestPlan.report(
            "test", "test", "", "",
            mapOf("startup" to TestFinding(CheckResult.FAIL, "oops\n" + "x".repeat(3000))),
        )
        assertTrue(report.contains("Notes: oops |"))
        assertFalse(report.contains("x".repeat(1200)))
        assertTrue(report.contains("Not run"))
    }
}
