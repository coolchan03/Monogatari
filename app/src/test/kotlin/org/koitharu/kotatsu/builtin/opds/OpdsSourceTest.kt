package org.koitharu.kotatsu.builtin.opds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsSourceTest {
    @Test fun canonicalSourceIdsAreStable() {
        val a = OpdsSource.fromUrl("https://example.org/opds/")
        val b = OpdsSource.fromUrl("https://example.org/opds/")
        val c = OpdsSource.fromUrl("https://example.org/other/")
        assertEquals(a.name, b.name)
        assertNotEquals(a.name, c.name)
        assertTrue(a.name.matches(Regex("""opds:[0-9a-f]{12}""")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonHttpCatalogs() {
        OpdsSource.fromUrl("file:///private/catalog.xml")
    }
}
