package org.koitharu.kotatsu.discover.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.discover.taste.TasteProfile.Candidate
import org.koitharu.kotatsu.discover.taste.TasteProfile.Signal

class TasteProfileTest {

	private val profile = TasteProfile.build(listOf(
		Signal(listOf("Fantasy", "Adventure"), favourite = true, readChapters = 120, dropped = false, ageDays = 3),
		Signal(listOf("Isekai", "fantasy"), favourite = false, readChapters = 40, dropped = false, ageDays = 10),
		Signal(listOf("Horror"), favourite = false, readChapters = 2, dropped = true, ageDays = 5),
	))

	@Test
	fun favouredRecentTagsWeighMostAndDroppedOnesAreNegative() {
		assertEquals("fantasy", profile.topTags(1).single())
		assertTrue(profile.weightOf("Horror") < 0)
		assertEquals(1.0, profile.weightOf("FANTASY"), 1e-9)
	}

	@Test
	fun ranksUnseenCandidatesByTasteAndSkipsLibraryTitles() {
		val ranked = profile.rank(listOf(
			Candidate("horror one", listOf("horror"), false),
			Candidate("fantasy owned", listOf("fantasy"), true),
			Candidate("isekai fantasy", listOf("isekai", "fantasy"), false),
			Candidate("cooking", listOf("cooking"), false),
			Candidate("adventure", listOf("adventure"), false),
		))
		assertEquals(listOf("isekai fantasy", "adventure"), ranked)
	}

	@Test
	fun sourceTagsMatchCatalogGenresThroughAliases() {
		val bl = TasteProfile.build(listOf(
			Signal(listOf("Yaoi", "Sci-Fi"), favourite = false, readChapters = 10, dropped = false, ageDays = 1),
		))
		assertTrue(bl.weightOf("Boys' Love") > 0)
		assertTrue(bl.weightOf("Boys Love") > 0)
		assertTrue(bl.weightOf("sci fi") > 0)
		assertEquals(
			listOf("given"),
			bl.rank(listOf(Candidate("cooking", listOf("Cooking"), false), Candidate("given", listOf("Drama", "Boys' Love"), false))),
		)
	}
}
