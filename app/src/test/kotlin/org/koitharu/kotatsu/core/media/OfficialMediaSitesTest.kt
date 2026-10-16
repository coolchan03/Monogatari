package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialMediaSitesTest {
    @Test fun identifiesOnlyActualProviderOwnedSecureHosts() {
        assertEquals(OfficialMediaSites.Site.FANFICTION,
            OfficialMediaSites.fictionSite("https://www.fanfiction.net/search/?keywords=cat"))
        assertEquals(OfficialMediaSites.Site.FICTIONPRESS,
            OfficialMediaSites.fictionSite("https://m.fictionpress.com/s/1234/1/Example"))
        assertNull(OfficialMediaSites.fictionSite("https://www.fanfiction.net.attacker.example/s/1"))
        assertNull(OfficialMediaSites.fictionSite("http://www.fictionpress.com/"))
        assertNull(OfficialMediaSites.fictionSite("https://user@www.fictionpress.com/"))
        assertNull(OfficialMediaSites.fictionSite("file:///android_asset/secret"))
    }

    @Test fun officialSearchLinksDoNotContainUnencodedUserQueries() {
        val url = OfficialMediaSites.searchUrl(OfficialMediaSites.Site.FICTIONPRESS, "a & b")
        assertTrue(url.startsWith("https://www.fictionpress.com/search/"))
        assertTrue(url.contains("a+%26+b"))
        assertEquals(OfficialMediaSites.Site.FICTIONPRESS, OfficialMediaSites.fictionSite(url))
    }
}
