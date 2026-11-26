package org.koitharu.kotatsu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fallback ids the Mangayomi importer produces; they must survive a load/save round trip. */
class MissingExtensionSourceTest {

	private val importerNames = listOf(
		"MANGAYOMI_EXAMPLE_SCANS",
		"MANGAYOMI_EXAMPLE_COM",
		"MANGAYOMI_SOME_LONG_SOURCE_NAME_BY_A_PUBLISHER",
		"MANGAYOMI_X",
	)

	@Test
	fun importerNamesAreKeptVerbatim() {
		for (name in importerNames) {
			val source = MangaSource(name)
			assertTrue("$name resolved to $source", source is MissingExtensionSource)
			assertEquals("saving must write the same name back", name, source.name)
		}
	}

	@Test
	fun displayNameIsReadable() {
		assertEquals("Example Scans", MissingExtensionSource("MANGAYOMI_EXAMPLE_SCANS").displayName)
		assertEquals("Some Long Source Name", MissingExtensionSource("MANGAYOMI_SOME_LONG_SOURCE_NAME").displayName)
		assertEquals("X", MissingExtensionSource("MANGAYOMI_X").displayName)
	}

	@Test
	fun prefixAloneAndOtherNamesStayUnknown() {
		assertEquals(UnknownMangaSource, MangaSource("MANGAYOMI_"))
		assertEquals(UnknownMangaSource, MangaSource("MANGAYOMI"))
		assertEquals(UnknownMangaSource, MangaSource("SOMETHING_ELSE"))
	}

	@Test
	fun adultFlagIsNotGuessed() {
		assertTrue(!MangaSource("MANGAYOMI_EXAMPLE_SCANS").isNsfw())
	}

	@Test
	fun importerFallbackUsesTheSamePrefix() {
		assertEquals("MANGAYOMI_", MissingExtensionSource.PREFIX)
	}
}
