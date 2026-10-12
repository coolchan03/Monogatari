package org.koitharu.kotatsu.builtin.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertFalse
import org.koitharu.kotatsu.core.media.OpdsCatalog

class OpdsSourceTest {
    @Test fun canonicalSourceIdsAreStable() {
        val a = OpdsSource.fromUrl("https://example.org/opds/")
        val b = OpdsSource.fromUrl("https://example.org/opds/")
        val c = OpdsSource.fromUrl("https://example.org/other/")
        assertEquals(a.name, b.name)
        assertNotEquals(a.name, c.name)
        assertTrue(a.name.matches(Regex("""opds:[0-9a-f]{12}""")))
    }

    @Test fun onlyEpubAcquisitionsAreShownAsReadable() {
        val epub = OpdsCatalog.Entry("EPUB", "A", "https://example.org/a", true, "application/epub+zip")
        val pdf = OpdsCatalog.Entry("PDF", "A", "https://example.org/b.pdf", true, "application/pdf")
        val navigation = OpdsCatalog.Entry("Browse", "", "https://example.org/nav", false, "application/atom+xml")
        assertTrue(OpdsRepository.isEpubAcquisition(epub))
        assertFalse(OpdsRepository.isEpubAcquisition(pdf))
        assertFalse(navigation.acquisition)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpCatalogs() {
        OpdsSource.fromUrl("file:///private/catalog.xml")
    }
}
