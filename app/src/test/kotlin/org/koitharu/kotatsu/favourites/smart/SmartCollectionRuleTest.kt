package org.koitharu.kotatsu.favourites.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.favourites.smart.SmartCollectionRule.Item

class SmartCollectionRuleTest {

	private val romance = Item("Kaguya-sama", listOf("Romance", "Comedy"), "MANGA", "MangaDex", "ONGOING", 3, true)
	private val horror = Item("Uzumaki", listOf("Horror", "Romance"), "MANGA", "Comick", "FINISHED", 0, false)

	@Test
	fun combinesAndNegatesTerms() {
		val rule = SmartCollectionRule.parse("media:manga tag:romance -tag:horror unread")
		assertTrue(rule.matches(romance))
		assertFalse(rule.matches(horror))
		assertTrue(SmartCollectionRule.parse("status:finished").matches(horror))
		assertTrue(SmartCollectionRule.parse("kaguya favourite").matches(romance))
		assertTrue(SmartCollectionRule.parse("").matches(horror))
	}

	@Test
	fun roundTripsThroughText() {
		val text = "media:manga tag:\"slice of life\" -source:comick unread"
		val rule = SmartCollectionRule.parse(text)
		assertEquals(4, rule.terms.size)
		assertEquals("slice of life", rule.terms[1].value)
		assertEquals(rule, SmartCollectionRule.parse(rule.encode()))
	}
}
