package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialEpubDownloaderTest {
    @Test fun acceptsPublisherListedPublicDomainEpubDownloads() {
        assertTrue(OfficialEpubDownloader.approved("Standard Ebooks",
            "https://standardebooks.org/ebooks/jane-austen/pride-and-prejudice/downloads/jane-austen_pride-and-prejudice.epub"))
        assertTrue(OfficialEpubDownloader.approved("Project Gutenberg",
            "https://www.gutenberg.org/ebooks/74.epub3.images"))
        assertTrue(OfficialEpubDownloader.approved("Project Gutenberg",
            "https://www.gutenberg.org/cache/epub/74/pg74-images.epub"))
    }

    @Test fun rejectsArbitraryMirrorsLoginAndHostLookalikes() {
        assertFalse(OfficialEpubDownloader.approved("Google Books", "https://example.com/book.epub"))
        assertFalse(OfficialEpubDownloader.approved("Standard Ebooks",
            "https://standardebooks.org.evil.example/ebooks/a/downloads/b.epub"))
        assertFalse(OfficialEpubDownloader.approved("Standard Ebooks",
            "http://standardebooks.org/ebooks/a/downloads/b.epub"))
        assertFalse(OfficialEpubDownloader.approved("Project Gutenberg",
            "https://example.org/ebooks/74.epub"))
        assertFalse(OfficialEpubDownloader.approved("Project Gutenberg",
            "https://user@www.gutenberg.org/ebooks/74.epub"))
    }

    @Test
    fun retriesOnlyTemporaryPublisherFailures() {
        org.junit.Assert.assertTrue(OfficialEpubDownloader.isTransientStatus(503))
        org.junit.Assert.assertTrue(OfficialEpubDownloader.isTransientStatus(429))
        org.junit.Assert.assertTrue(OfficialEpubDownloader.isTransientStatus(408))
        org.junit.Assert.assertFalse(OfficialEpubDownloader.isTransientStatus(404))
        org.junit.Assert.assertFalse(OfficialEpubDownloader.isTransientStatus(403))
        org.junit.Assert.assertFalse(OfficialEpubDownloader.isTransientStatus(200))
    }
}
