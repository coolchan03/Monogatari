package org.koitharu.kotatsu.core.qa

import org.junit.Assert.*
import org.junit.Test

class DiscoverySafetyTest {
    @Test fun sanitizesFindCopyQueries() {
        assertEquals("A Story", DiscoveryQueryPolicy.findCopyQuery("  A   Story  "))
        assertNull(DiscoveryQueryPolicy.findCopyQuery(" "))
    }

    @Test fun followedTagsMatchExactlyInsteadOfSubstrings() {
        assertTrue(FollowedTagPolicy.matches(listOf("Boys' Love"), setOf("boys' love")))
        assertFalse(FollowedTagPolicy.matches(listOf("Love"), setOf("Boys' Love")))
    }

    @Test fun rssHostsMustBeExplicitlyTrusted() {
        assertTrue(NewsFeedPolicy.trustedFeed("https://www.animenewsnetwork.com/all/rss.xml"))
        assertFalse(NewsFeedPolicy.trustedFeed("http://www.youtube.com/rss"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://youtube.com.attacker.test/rss"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://user:secret@reddit.com/rss"))
    }

    @Test fun lockedPrivateCategoryDoesNotLeakToSecondarySurfaces() {
        for (surface in PrivateCategoryPolicy.Surface.entries) {
            assertFalse(PrivateCategoryPolicy.allowed(true, false, surface))
        }
        assertTrue(PrivateCategoryPolicy.allowed(true, true, PrivateCategoryPolicy.Surface.LIBRARY))
        assertFalse(PrivateCategoryPolicy.allowed(true, true, PrivateCategoryPolicy.Surface.WIDGET))
    }
}
