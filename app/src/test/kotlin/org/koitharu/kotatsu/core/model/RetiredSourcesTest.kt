package org.koitharu.kotatsu.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * Guards saved-library compatibility across parser upgrades. The resource lists every source id that shipped in
 * kotatsu-parsers-redo 68f3fed395 (the baseline of Monogatari 1.10.0); users' favourites, history, bookmarks and
 * backups may reference any of them.
 */
class RetiredSourcesTest {

	private val baselineIds: Set<String> = requireNotNull(
		javaClass.getResourceAsStream("/sources/parser_ids_68f3fed395.txt"),
	).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

	private val liveIds: Set<String> = MangaParserSource.entries.mapTo(HashSet()) { it.name }

	@Test
	fun baselineHasExpectedSize() {
		assertEquals(1385, baselineIds.size)
	}

	@Test
	fun everyBaselineIdStillResolvesWithItsOriginalName() {
		val unresolved = baselineIds.filter { id ->
			val source = MangaSource(id)
			source === UnknownMangaSource || source.name != id
		}
		assertTrue("ids that no longer resolve: $unresolved", unresolved.isEmpty())
	}

	@Test
	fun retiredIdsAreExactlyTheBaselineIdsTheParsersDropped() {
		assertEquals(baselineIds - liveIds, RetiredSources.ids)
	}

	@Test
	fun retiredAndLiveIdsNeverOverlap() {
		assertTrue(RetiredSources.ids.none { it in liveIds })
	}

	@Test
	fun liveParserWinsOverRetiredEntry() {
		val live = liveIds.first()
		assertTrue(MangaSource(live) is MangaParserSource)
	}

	@Test
	fun retiredIdKeepsItsNameAndIsNotUnknown() {
		val source = MangaSource("NETTRUYENUU")
		assertTrue(source is RetiredMangaSource)
		assertEquals("NETTRUYENUU", source.name)
		assertEquals("NetTruyenUU", (source as RetiredMangaSource).formerTitle)
	}

	@Test
	fun adultFlagSurvivesRetirement() {
		assertTrue(MangaSource("BEEHENTAI").isNsfw())
		assertFalse(MangaSource("NETTRUYENUU").isNsfw())
	}

	@Test
	fun unknownIdsStayUnknown() {
		assertEquals(UnknownMangaSource, MangaSource("NOT_A_REAL_SOURCE"))
		assertEquals(UnknownMangaSource, MangaSource(null))
	}

	@Test
	fun replacementCandidatesAreLiveSourcesAndOnlyHints() {
		val netTruyenMirrors = RetiredSources.ids.filter { it.startsWith("NETTRUYEN") }
		assertEquals(5, netTruyenMirrors.size)
		for (id in netTruyenMirrors) {
			val candidates = RetiredSources.replacementCandidates(id)
			assertTrue(candidates.isNotEmpty())
			assertTrue(candidates.all { it in liveIds })
		}
		// No hint where there is no evidence of a successor.
		assertTrue(RetiredSources.replacementCandidates("BEEHENTAI").isEmpty())
	}
}
