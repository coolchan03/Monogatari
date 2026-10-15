package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardEbooksCatalogTest {
    @Test fun parsesPublicBookListAndIgnoresUntrustedLinks() {
        val html = """<html><body><ol class="ebooks-list list">
            <li typeof="schema:Book">
                <picture><img src="/images/covers/mary-shelley_frankenstein/cover.jpg"/></picture>
                <p><a property="schema:url" href="/ebooks/mary-shelley/frankenstein">
                    <span property="schema:name">Frankenstein</span></a></p>
                <p class="author"><a href="/ebooks/mary-shelley">Mary Shelley</a></p>
            </li>
            <li><p><a property="schema:url" href="https://evil.example/ebooks/a/b">
                <span property="schema:name">Malicious</span></a></p></li>
            </ol></body></html>"""
        val titles = StandardEbooksCatalog.parseListing(html)
        assertEquals(1, titles.size)
        assertEquals("Frankenstein", titles.single().title)
        assertEquals("Mary Shelley", titles.single().author)
        assertTrue(titles.single().coverUrl!!.contains("/images/covers/"))
        assertEquals("https://standardebooks.org/ebooks/mary-shelley/frankenstein", titles.single().url)
    }

    @Test fun onlyTheActualCompatibleEpubOnSameOfficialBookIsUsed() {
        val book = "https://standardebooks.org/ebooks/mary-shelley/frankenstein"
        val html = """<div>
            <a class="epub" property="schema:contentUrl"
               href="/ebooks/mary-shelley/frankenstein/downloads/edition_advanced.epub">Advanced EPUB</a>
            <a class="epub" property="schema:contentUrl"
               href="https://untrusted.test/ebooks/mary-shelley/frankenstein/downloads/edition.epub">Fake</a>
            <a class="epub" property="schema:contentUrl"
               href="/ebooks/mary-shelley/frankenstein/downloads/mary-shelley_frankenstein.epub">Compatible epub</a>
        </div>"""
        assertEquals(book + "/downloads/mary-shelley_frankenstein.epub",
            StandardEbooksCatalog.parseCompatibleEpub(html, book))
        assertNull(StandardEbooksCatalog.parseCompatibleEpub(html, "https://evil.example/ebooks/a/b"))
    }

    @Test fun queryIsEncodedAndNonstandardOriginsAreRejected() {
        val search = StandardEbooksCatalog.browseUrl("a & b", 2)
        assertTrue(search.contains("query=a+%26+b"))
        assertTrue(search.contains("page=2"))
        assertFalse(StandardEbooksCatalog.officialPage("https://standardebooks.org.attacker.test/ebooks/x/y"))
        assertFalse(StandardEbooksCatalog.officialPage("https://user:pwd@standardebooks.org/ebooks/x/y"))
        assertFalse(StandardEbooksCatalog.officialPage("https://standardebooks.org/ebooks/x/y#fragment"))
        assertTrue(StandardEbooksCatalog.officialPage("https://standardebooks.org/ebooks/x/y"))
    }
}
