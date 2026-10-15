package org.koitharu.kotatsu.core.media

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdditionalBookCatalogsTest {
    @Test fun fadedPagePreservesMetadataButRejectsUnsafeLinks() {
        val json = JSONObject("""{"rows":[
            {"pid":"20160532","title":"The Case-Book of Sherlock Holmes",
             "first_publication":1927,"lang":"en","cover":"books/20160532/cover.jpg",
             "authors":[{"realname":"Doyle, Arthur Conan"}]},
            {"pid":"x<script>","title":"Bad Item","cover":"https://attacker.test/a.jpg"},
            {"pid":"20160111","title":"Unrelated Novel"}]}""")
        val books = AdditionalBookCatalogs.fadedPage(json, "Sherlock")
        assertEquals(1, books.size)
        assertEquals("Doyle, Arthur Conan", books.single().author)
        assertEquals(1927, books.single().publicationYear)
        assertEquals("https://www.fadedpage.com/showbook.php?pid=20160532", books.single().url)
        assertTrue(books.single().coverUrl!!.startsWith("https://www.fadedpage.com/books/"))
    }

    @Test fun gutenbergAustraliaCombinesHtmlAndTextVersions() {
        val input = """
            ebooks01,0100011,txt,Orwell,George,Animal Farm
            ebooks01,0100011h,html,Orwell,George,Animal Farm
            ebooks01,0100021,txt,Orwell,George,Nineteen Eighty-four
            ../private,0100101,txt,Bad,Sender,Not allowed
        """.trimIndent()
        val rows = AdditionalBookCatalogs.gutenbergAustralia(input, "animal")
        assertEquals(1, rows.size)
        assertEquals("Animal Farm", rows.single().title)
        assertTrue(rows.single().url.endsWith("0100011h.html"))
        assertTrue(rows.single().format!!.contains("copyright"))
    }

    @Test fun baenOnlyMapsListedTitlesAndSafeLinks() {
        val html = """<ul>
            <li class="product-item"><a class="product-item-link"
            href="https://www.baen.com/1632.html">1632</a>
            <img class="product-image-photo" src="https://www.baen.com/media/1632.jpg"/></li>
            <li class="product-item"><a class="product-item-link"
            href="https://evil.example/trap">1633</a></li>
            </ul>"""
        val rows = AdditionalBookCatalogs.baenFreeLibrary(html, "")
        assertEquals(1, rows.size)
        assertEquals("1632", rows.single().title)
        assertEquals("https://www.baen.com/1632.html", rows.single().url)
    }

    @Test fun catalogLinksRejectFragmentsCustomPortsAndForeignCredentials() {
        val html = """<ul>
            <li class="product-item"><a class="product-item-link"
                href="https://www.baen.com:444/trap.html">Wrong port</a></li>
            <li class="product-item"><a class="product-item-link"
                href="https://www.baen.com/good.html#fragment">Fragment</a></li>
            <li class="product-item"><a class="product-item-link"
                href="https://someone@www.baen.com/secrets.html">Credentials</a></li>
            <li class="product-item"><a class="product-item-link"
                href="https://www.baen.com/clean.html">Allowed</a></li>
        </ul>"""
        val rows = AdditionalBookCatalogs.baenFreeLibrary(html, "")
        assertEquals(1, rows.size)
        assertEquals("https://www.baen.com/clean.html", rows.single().url)
    }

    @Test fun globalGreyUsesOnlyLegitimateBookPages() {
        val xml = """<?xml version="1.0"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            <url><loc>https://www.globalgreyebooks.com/book-page/ghosts-i-have-seen-ebook.html</loc></url>
            <url><loc>https://evil.example/book-page/ghosts-i-have-seen-ebook.html</loc></url>
            <url><loc>https://www.globalgreyebooks.com/about.html</loc></url>
            </urlset>"""
        val rows = AdditionalBookCatalogs.globalGreySitemap(xml, "ghosts")
        assertEquals(1, rows.size)
        assertEquals("Ghosts I Have Seen", rows.single().title)
    }

    @Test fun globalGreyAcceptsPublishedOnlineBooksPaths() {
        val xml = """<urlset>
            <url><loc>https://www.globalgreyebooks.com/online-books-2/ghosts_of_the_past-ebook.html</loc></url>
            <url><loc>https://www.globalgreyebooks.com/online-books-2/20230801_ghosts-at-dawn-ebook.html</loc></url>
            <url><loc>https://untrusted.example/online-books-2/ghosts-hidden-ebook.html</loc></url>
            </urlset>"""
        val results = AdditionalBookCatalogs.globalGreySitemap(xml, "ghosts")
        assertEquals(2, results.size)
        assertEquals("Ghosts Of The Past", results[0].title)
        assertEquals("Ghosts At Dawn", results[1].title)
    }

    @Test fun officialSiteSearchUrlsEncodeInputWithoutChangingHosts() {
        val url = OfficialMediaSites.searchUrl(OfficialMediaSites.Site.FANFICTION, "anise & snow")
        assertTrue(url.startsWith("https://www.fanfiction.net/search/"))
        assertTrue(url.contains("anise+%26+snow"))
        assertEquals("https://www.bilibili.com/",
            OfficialMediaSites.searchUrl(OfficialMediaSites.Site.BILIBILI, ""))
    }
}
