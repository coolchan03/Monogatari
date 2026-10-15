package org.koitharu.kotatsu.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GutenbergGenresTest {

	@Test
	fun extractsGenreAndKeepsRemainingText() {
		val parsed = GutenbergGenres.parse("dracula genre:horror")
		assertEquals("dracula", parsed.text)
		assertEquals("horror", parsed.topic)
	}

	@Test
	fun quotedAndAliasedGenres() {
		assertEquals("science fiction", GutenbergGenres.parse("""genre:"Science Fiction"""").topic)
		assertEquals("science fiction", GutenbergGenres.parse("genre:SF").topic)
		assertEquals("mystery", GutenbergGenres.parse("genre:detective holmes").topic)
	}

	@Test
	fun plainQueriesAreUntouched() {
		val parsed = GutenbergGenres.parse("  pride and prejudice ")
		assertEquals("pride and prejudice", parsed.text)
		assertNull(parsed.topic)
	}
}
