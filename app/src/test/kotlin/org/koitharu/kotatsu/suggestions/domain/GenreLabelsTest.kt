package org.koitharu.kotatsu.suggestions.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.core.model.LocalMangaSource

class GenreLabelsTest {

	@Test
	fun spellingAndPunctuationVariantsAreTheSameGenre() {
		assertTrue(GenreLabels.same("Sci-Fi", "SciFi"))
		assertTrue(GenreLabels.same("Sci-Fi", "Science Fiction"))
		assertTrue(GenreLabels.same("Slice of Life", "slice-of-life"))
		assertTrue(GenreLabels.same("Martial Arts", "martial art"))
		assertTrue(GenreLabels.same("Shonen", "Shounen"))
		assertTrue(GenreLabels.same("Humor", "Comedy"))
		assertTrue(GenreLabels.same("Sports", "Sport"))
	}

	@Test
	fun relatedButDifferentGenresStayApart() {
		assertFalse(GenreLabels.same("Shounen", "Shounen Ai"))
		assertFalse(GenreLabels.same("Drama", "Tragedy"))
		assertFalse(GenreLabels.same("Romance", "Romance Comedy"))
		assertFalse(GenreLabels.same("Action", "Adventure"))
	}

	@Test
	fun nonLatinLabelsAreKeptNotErased() {
		assertEquals("アクション", GenreLabels.canonical("アクション"))
		assertTrue(GenreLabels.same("Фэнтези", "фэнтези"))
		assertFalse(GenreLabels.same("アクション", "コメディ"))
	}

	@Test
	fun emptyOrSymbolOnlyLabelsNeverMatchEachOther() {
		assertFalse(GenreLabels.same("", ""))
		assertFalse(GenreLabels.same("---", "!!!"))
	}

	@Test
	fun fuzzyMatchStillWorksAsBefore() {
		assertTrue(GenreLabels.matches("Adventure", "Adventures", 0.4f))
		assertFalse(GenreLabels.matches("Horror", "Romance", 0.4f))
	}

	@Test
	fun blacklistingOneSpellingBlocksTheOther() {
		val blacklist = TagsBlacklist(setOf("Sci-Fi"), 0.4f)
		assertTrue(MangaTag(title = "Science Fiction", key = "sf", source = LocalMangaSource) in blacklist)
		assertFalse(MangaTag(title = "Romance", key = "r", source = LocalMangaSource) in blacklist)
	}
}
