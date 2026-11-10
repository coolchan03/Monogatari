package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonImportSummaryTest {

	private fun manga(sourceId: Long, title: String, fav: Boolean = true) = MihonBackup.Manga(
		sourceId = sourceId, url = "/$title", title = title, author = null, artist = null, description = null,
		genres = emptyList(), thumbnailUrl = null, favorite = fav, dateAdded = 0L, categoryOrders = emptyList(),
		chapters = emptyList(), history = emptyList(),
	)

	@Test
	fun localSourceTitlesAreCountedSeparatelyAndNeverMatched() {
		val backup = MihonBackup.Backup(
			manga = listOf(manga(0, "My scanned book"), manga(0, "Another CBZ"), manga(5, "Frieren")),
			categories = emptyList(),
			sources = mapOf(5L to "MangaDex (EN)"),
		)
		val summary = MihonImportSummary.of(backup, mapOf("MangaDex" to "MANGADEX"))
		assertEquals(3, summary.titles)
		assertEquals(2, summary.localTitles)
		assertEquals(1, summary.matchedTitles)
		// Local is not reported as an "unmatched extension"; it is its own, explained, category.
		assertTrue(summary.unmatchedSources.isEmpty())
		assertFalse(summary.matchedSources.containsKey("Source 0"))
	}

	@Test
	fun backupWithoutLocalTitlesHasNone() {
		val backup = MihonBackup.Backup(listOf(manga(5, "Frieren")), emptyList(), mapOf(5L to "MangaDex"))
		assertEquals(0, MihonImportSummary.of(backup, mapOf("MangaDex" to "MANGADEX")).localTitles)
	}

	@Test
	fun unknownSourceStillListedAsUnmatched() {
		val backup = MihonBackup.Backup(listOf(manga(9, "X")), emptyList(), mapOf(9L to "Some Extension"))
		val summary = MihonImportSummary.of(backup, emptyMap())
		assertEquals(listOf("Some Extension"), summary.unmatchedSources)
		assertEquals(0, summary.localTitles)
	}
}
