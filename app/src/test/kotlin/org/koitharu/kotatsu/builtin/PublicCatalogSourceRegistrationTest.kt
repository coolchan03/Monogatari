package org.koitharu.kotatsu.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType

class PublicCatalogSourceRegistrationTest {
    @Test fun officialBookCatalogsAreDiscoverableBySourceId() {
        val sources = listOf(
            BuiltinSource.FADED_PAGE,
            BuiltinSource.GUTENBERG_AU,
            BuiltinSource.GLOBAL_GREY,
            BuiltinSource.BAEN_FREE,
        )
        assertTrue(BuiltinSource.entries.containsAll(sources))
        sources.forEach { source ->
            assertSame(source, BuiltinSource.fromName(source.name))
            assertEquals(MediaType.BOOK, source.type)
        }
    }
}
