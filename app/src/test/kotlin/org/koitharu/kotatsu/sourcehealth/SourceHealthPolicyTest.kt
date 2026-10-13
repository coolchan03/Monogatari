package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHealthPolicyTest {
    private val now = 1_800_000_000_000L

    @Test fun fiveFailuresWithoutRecentSuccessIsUnhealthy() {
        assertFalse(SourceHealthPolicy.isUnhealthy(4, 0, now))
        assertTrue(SourceHealthPolicy.isUnhealthy(5, 0, now))
    }
    @Test fun rollbackRequiresRecentHealthyPreviousInstall() {
        assertTrue(SourceHealthPolicy.shouldRollback(3, now - 86_400_000, now, true))
        assertFalse(SourceHealthPolicy.shouldRollback(2, now - 86_400_000, now, true))
        assertFalse(SourceHealthPolicy.shouldRollback(3, now - 86_400_000, now, false))
        assertFalse(SourceHealthPolicy.shouldRollback(3, 0, now, true))
    }

    @Test fun recentSuccessOverridesPreviousFailures() {
        assertFalse(SourceHealthPolicy.isUnhealthy(10, now - 86_400_000, now))
        assertTrue(SourceHealthPolicy.isUnhealthy(5, now - 8 * 86_400_000, now))
    }
}
