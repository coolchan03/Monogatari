package org.koitharu.kotatsu.core.qa

import org.junit.Assert.*
import org.junit.Test

class DiscoverySafetyTest {
    @Test fun csvQuotesAndProtectsFormulaCells() {
        val csv = CsvExportFormatter.encode(listOf("title", "notes"), listOf(listOf("Book, One", "=1+1")))
        assertTrue(csv.contains("\"Book, One\""))
        assertTrue(csv.contains("\"'=1+1\""))
    }

    @Test fun crossMediaRelationLinksAreTypeSpecific() {
        assertEquals(CrossMediaRelationPolicy.Action.WATCH_ANIME,
            CrossMediaRelationPolicy.action("ADAPTATION", "TV"))
        assertEquals(CrossMediaRelationPolicy.Action.FIND_SEQUEL,
            CrossMediaRelationPolicy.action("SEQUEL", "MANGA"))
    }

    @Test fun sanitizesFindCopyQueries() {
        assertEquals("A Story", DiscoveryQueryPolicy.findCopyQuery("  A   Story  "))
        assertNull(DiscoveryQueryPolicy.findCopyQuery(" "))
    }

    @Test fun followedTagsMatchExactlyInsteadOfSubstrings() {
        assertTrue(FollowedTagPolicy.matches(listOf("Boys' Love"), setOf("boys' love")))
        assertFalse(FollowedTagPolicy.matches(listOf("Love"), setOf("Boys' Love")))
    }

    @Test fun rssFeedsMustBePublicHttps() {
        assertTrue(NewsFeedPolicy.trustedFeed("https://www.animenewsnetwork.com/all/rss.xml"))
        assertTrue(NewsFeedPolicy.trustedFeed("https://bookriot.com/feed/"))
        assertFalse(NewsFeedPolicy.trustedFeed("http://www.youtube.com/rss"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://user:secret@reddit.com/rss"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://localhost/feed"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://192.168.1.10/feed"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://127.0.0.1/feed"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://[::1]/feed"))
        assertFalse(NewsFeedPolicy.trustedFeed("https://router/feed"))
        assertFalse(NewsFeedPolicy.trustedFeed("file:///etc/passwd"))
    }

    @Test fun lockedPrivateCategoryDoesNotLeakToSecondarySurfaces() {
        for (surface in PrivateCategoryPolicy.Surface.entries) {
            assertFalse(PrivateCategoryPolicy.allowed(true, false, surface))
        }
        assertTrue(PrivateCategoryPolicy.allowed(true, true, PrivateCategoryPolicy.Surface.LIBRARY))
        assertFalse(PrivateCategoryPolicy.allowed(true, true, PrivateCategoryPolicy.Surface.WIDGET))
    }
}
