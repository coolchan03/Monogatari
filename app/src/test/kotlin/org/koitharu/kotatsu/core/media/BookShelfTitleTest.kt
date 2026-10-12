package org.koitharu.kotatsu.core.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class BookShelfTitleTest {
    @Test fun importedNamesDoNotExposeTemporaryPrefix() {
        assertEquals("A Tale of Two Cities", BookShelf.title(File("book-123456-A Tale of Two Cities.epub")))
        assertEquals("Volume 1", BookShelf.title(File("book-A987-Volume 1.pdf")))
    }

    @Test fun customNamesKeepTheirTitle() {
        assertEquals("Read me", BookShelf.title(File("Read me.txt")))
        assertEquals("My.long.story", BookShelf.title(File("My.long.story.epub")))
    }
}
