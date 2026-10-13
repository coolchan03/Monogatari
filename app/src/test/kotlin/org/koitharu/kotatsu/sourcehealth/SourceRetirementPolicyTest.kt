package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRetirementPolicyTest {
    private val now = 2_000_000_000_000L

    @Test fun missingSiteStaysVisibleDuringThirtyDayGrace() {
        assertFalse(SourceRetirementPolicy.isRetired(0L, now))
        assertFalse(SourceRetirementPolicy.isRetired(now - 1, now))
        assertFalse(SourceRetirementPolicy.isRetired(
            now - SourceRetirementPolicy.GRACE_PERIOD_MS + 1, now))
    }

    @Test fun onlyExpiredMissingSiteIsRetired() {
        assertTrue(SourceRetirementPolicy.isRetired(
            now - SourceRetirementPolicy.GRACE_PERIOD_MS, now))
        assertTrue(SourceRetirementPolicy.isRetired(
            now - SourceRetirementPolicy.GRACE_PERIOD_MS - 1, now))
    }

    @Test fun clockMovingBackNeverHidesSources() {
        assertFalse(SourceRetirementPolicy.isRetired(now + 5_000, now))
    }
}
