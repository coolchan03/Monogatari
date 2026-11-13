package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentType

class SearchContentFormatTest {

	@Test fun manhwaSourceIsNotAutomaticallyWebtoon() {
		val formats = SearchContentFormat.classify(ContentType.MANHWA, emptyList())
		assertEquals(setOf(IllustratedFormat.MANHWA), formats)
		assertFalse(SearchContentFormat.matches(FormatFacet.WEBTOON, false, formats))
		assertTrue(SearchContentFormat.matches(FormatFacet.MANHWA, false, formats))
	}

	@Test fun webtoonRequiresExplicitTagAndOverridesCoarseSourceType() {
		val formats = SearchContentFormat.classify(ContentType.MANGA, listOf("Action", " Webtoon "))
		assertEquals(setOf(IllustratedFormat.WEBTOON), formats)
		assertTrue(SearchContentFormat.matches(FormatFacet.WEBTOON, false, formats))
		assertFalse(SearchContentFormat.matches(FormatFacet.MANGA, false, formats))
	}

	@Test fun mangaSourceWithoutTagsIsManga() {
		val formats = SearchContentFormat.classify(ContentType.MANGA, listOf("Romance"))
		assertTrue(SearchContentFormat.matches(FormatFacet.MANGA, false, formats))
		assertFalse(SearchContentFormat.matches(FormatFacet.COMICS, false, formats))
	}

	@Test fun comicsFromSourceTypeOrTag() {
		assertTrue(SearchContentFormat.matches(FormatFacet.COMICS, false,
			SearchContentFormat.classify(ContentType.COMICS, emptyList())))
		assertTrue(SearchContentFormat.matches(FormatFacet.COMICS, false,
			SearchContentFormat.classify(null, listOf("Comics"))))
	}

	@Test fun noEvidenceIsUnknownNeverGuessed() {
		for (type in listOf(null, ContentType.OTHER, ContentType.NOVEL, ContentType.ONE_SHOT)) {
			assertEquals(setOf(IllustratedFormat.UNKNOWN), SearchContentFormat.classify(type, listOf("Drama")))
		}
	}

	@Test fun restrictiveFacetDropsUnknownUnlessIncluded() {
		val unknown = SearchContentFormat.classify(null, emptyList())
		assertFalse(SearchContentFormat.matches(FormatFacet.WEBTOON, false, unknown))
		assertTrue(SearchContentFormat.matches(FormatFacet.WEBTOON, true, unknown))
	}

	@Test fun allFacetKeepsEverything() {
		for (formats in listOf(setOf(IllustratedFormat.UNKNOWN), setOf(IllustratedFormat.COMICS))) {
			assertTrue(SearchContentFormat.matches(FormatFacet.ALL, false, formats))
		}
	}

	@Test fun includeUnclassifiedDoesNotLetOtherFormatsThrough() {
		val comics = setOf(IllustratedFormat.COMICS)
		assertFalse(SearchContentFormat.matches(FormatFacet.WEBTOON, true, comics))
	}

	@Test fun facetRestoreFromSavedNameIsSafe() {
		assertEquals(FormatFacet.WEBTOON, FormatFacet.fromName("WEBTOON"))
		assertEquals(FormatFacet.ALL, FormatFacet.fromName(null))
		assertEquals(FormatFacet.ALL, FormatFacet.fromName("bogus"))
	}
}
