package org.koitharu.kotatsu.discover

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationLoaderTest {

	private fun item(id: Int, key: String = id.toString()) =
		AniListDiscover.Item(id = id, title = "T$id", alternativeTitle = null, coverUrl = null, score = null, uniqueKey = key)

	private fun keys(items: List<AniListDiscover.Item>) = items.map { it.uniqueKey }

	@Test
	fun mixInterleavesListsRoundRobin() {
		val mixed = RecommendationLoader.mix(
			listOf(listOf(item(1), item(2), item(3)), listOf(item(11), item(12)), listOf(item(21))),
			perList = 3,
			cap = 16,
		)
		assertEquals(listOf("1", "11", "21", "2", "12", "3"), keys(mixed))
	}

	@Test
	fun mixTakesAtMostPerListFromEachList() {
		val mixed = RecommendationLoader.mix(
			listOf((1..10).map(::item), (11..20).map(::item)),
			perList = 3,
			cap = 16,
		)
		assertEquals(listOf("1", "11", "2", "12", "3", "13"), keys(mixed))
	}

	@Test
	fun mixCapsTheResult() {
		val mixed = RecommendationLoader.mix(listOf((1..30).map(::item), (31..60).map(::item)), perList = 30, cap = 16)
		assertEquals(16, mixed.size)
		assertEquals(listOf("1", "31", "2", "32"), keys(mixed).take(4))
	}

	@Test
	fun mixDropsDuplicatesAndFillsFromTheSameList() {
		val mixed = RecommendationLoader.mix(
			listOf(listOf(item(1), item(2), item(3)), listOf(item(1), item(2), item(4))),
			perList = 3,
			cap = 16,
		)
		assertEquals(listOf("1", "2", "3", "4"), keys(mixed))
	}

	@Test
	fun mixHandlesEmptyInput() {
		assertEquals(emptyList<AniListDiscover.Item>(), RecommendationLoader.mix(emptyList(), 4, 16))
		assertEquals(emptyList<AniListDiscover.Item>(), RecommendationLoader.mix(listOf(emptyList(), emptyList()), 4, 16))
	}

	@Test
	fun allChipMixesAboutFourPerType() {
		val lists = List(4) { type -> (1..10).map { item(type * 100 + it) } }
		val mixed = RecommendationLoader.mix(lists, RecommendationLoader.PER_TYPE_IN_ALL, RecommendationLoader.CAP)
		assertEquals(16, mixed.size)
		assertEquals(listOf("1", "101", "201", "301"), keys(mixed).take(4))
	}
}
