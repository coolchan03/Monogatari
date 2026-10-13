package org.koitharu.kotatsu.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.SortOrder

class SourceSettingsBackupPolicyTest {
    @Test fun permitsOnlyKnownNonSecretParserPreferences() {
        val raw = mapOf(
            SourceSettings.KEY_SLOWDOWN to true,
            SourceSettings.KEY_NO_AUTO_CAPTCHA to false,
            SourceSettings.KEY_SORT_ORDER to SortOrder.NEWEST.name,
            SourceSettings.KEY_CF_USER_AGENT to "should-not-leave-device",
            "access_token" to "private",
            "custom_cookie" to "private",
            "random_future_secret" to "private",
        )
        val exported = SourceSettingsBackupPolicy.sanitize(raw)
        assertEquals(3, exported.size)
        assertTrue(exported[SourceSettings.KEY_SLOWDOWN] == true)
        assertFalse(exported.containsKey(SourceSettings.KEY_CF_USER_AGENT))
        assertFalse(exported.containsKey("access_token"))
    }

    @Test fun rejectsWrongTypeAndInvalidSortOrder() {
        assertTrue(SourceSettingsBackupPolicy.sanitize(mapOf(
            SourceSettings.KEY_SLOWDOWN to "true",
            SourceSettings.KEY_SORT_ORDER to "NOT_AN_ENUM",
        )).isEmpty())
    }
}
