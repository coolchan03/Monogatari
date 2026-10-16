package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookAccessMetadataTest {
    @Test fun neverPromisesThatABorrowableBookIsAvailable() {
        assertEquals("Borrowing may be required", BookAccessMetadata.openLibraryAccess("borrowable"))
        assertEquals("Public online text", BookAccessMetadata.openLibraryAccess("public"))
        assertEquals("Restricted accessible edition", BookAccessMetadata.openLibraryAccess("printdisabled"))
        assertEquals("No digital copy listed", BookAccessMetadata.openLibraryAccess("no_ebook"))
        assertNull(BookAccessMetadata.openLibraryAccess("unexpected"))
        assertNull(BookAccessMetadata.openLibraryAccess(null))
    }
}
