package org.koitharu.kotatsu.core.media

import org.junit.Assert.*
import org.junit.Test

class OpdsAuthTest {
    private val saved = "https://alice:p%40ssword@books.example.org/opds/index.xml"

    @Test fun forwardsBasicAuthOnlyToExactlyTheSameHttpsOrigin() {
        val header = OpdsAuth.headerFor(saved, "https://books.example.org/opds/next?page=2")
        assertNotNull(header)
        assertTrue(header!!.startsWith("Basic "))
        assertEquals(header, OpdsAuth.headerFor(saved, "https://books.example.org:443/book.epub"))
    }

    @Test fun deniesOtherHostsSubdomainsSchemePortsAndUserInfo() {
        listOf(
            "https://evil.example.org/steal", "https://sub.books.example.org/book.epub",
            "http://books.example.org/book.epub", "https://books.example.org:8443/book.epub",
            "https://bob:other@books.example.org/book.epub",
            "not-a-url",
        ).forEach { assertNull(it, OpdsAuth.headerFor(saved, it)) }
    }

    @Test fun showsOnlyCredentialFreeCatalogAddress() {
        assertEquals("https://books.example.org/opds/index.xml", OpdsAuth.displayUrl(saved))
        assertNull(OpdsAuth.headerFor("https://books.example.org/opds", "https://books.example.org/file.epub"))
        assertNull(OpdsAuth.headerFor("http://alice:secret@books.example.org/opds", "http://books.example.org/next"))
    }
}
