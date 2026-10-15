package org.koitharu.kotatsu.builtin.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3FiltersTest {

	@Test
	fun extractsAllFilterTokensAndKeepsPlainText() {
		val parsed = Ao3Filters.parse("""slow burn fandom:"Given (Manga)" ship:"Ritsuka/Mafuyu" rating:teen,mature lang:es updated:week""")
		assertEquals("slow burn", parsed.text)
		assertEquals(listOf("Given (Manga)"), parsed.fandoms)
		assertEquals(listOf("Ritsuka/Mafuyu"), parsed.ships)
		assertEquals(listOf(11, 12), parsed.ratingIds)
		assertEquals("es", parsed.language)
		assertEquals("< 1 week", parsed.updated)
	}

	@Test
	fun repeatedFandomsAccumulate() {
		val parsed = Ao3Filters.parse("fandom:Haikyuu fandom:\"Free!\"")
		assertEquals(listOf("Haikyuu", "Free!"), parsed.fandoms)
		assertEquals("", parsed.text)
	}

	@Test
	fun unknownOrInvalidTokensStayInTheText() {
		val parsed = Ao3Filters.parse("note:hello rating:bogus lang:123 updated:never title")
		assertEquals("note:hello rating:bogus lang:123 updated:never title", parsed.text)
		assertNull(parsed.ratingIds)
		assertNull(parsed.language)
		assertNull(parsed.updated)
	}

	@Test
	fun updatedExpressions() {
		assertEquals("< 1 day", Ao3Filters.updatedExpression("day"))
		assertEquals("< 1 year", Ao3Filters.updatedExpression("YEAR"))
		assertEquals("< 30 days", Ao3Filters.updatedExpression("30d"))
		assertNull(Ao3Filters.updatedExpression("soon"))
	}

	@Test
	fun contentLevelCapsRequestedRatings() {
		assertEquals(listOf(10, 11), Ao3Filters.effectiveRatings(null, listOf(10, 11)))
		assertEquals(listOf(11), Ao3Filters.effectiveRatings(listOf(11, 13), listOf(10, 11)))
		assertEquals(emptyList<Int>(), Ao3Filters.effectiveRatings(listOf(13), listOf(10)))
	}
}
