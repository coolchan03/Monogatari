package org.koitharu.kotatsu.search.ui.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchScopeRulesTest {

	@Test
	fun scopesAreAllPlusEveryMediaType() {
		assertEquals(listOf("ALL", "MANGA", "BOOK", "VIDEO", "AUDIO"), SearchScopeRules.scopes)
	}

	@Test
	fun normalizeAcceptsOnlyKnownScopes() {
		SearchScopeRules.scopes.forEach { assertEquals(it, SearchScopeRules.normalize(it)) }
		assertNull(SearchScopeRules.normalize(null))
		assertNull(SearchScopeRules.normalize(""))
		assertNull(SearchScopeRules.normalize("manga"))
		assertNull(SearchScopeRules.normalize("COMICS"))
	}

	@Test
	fun orAllNeverReturnsNull() {
		assertEquals("ALL", SearchScopeRules.orAll(null))
		assertEquals("ALL", SearchScopeRules.orAll("bogus"))
		assertEquals("VIDEO", SearchScopeRules.orAll("VIDEO"))
	}

	@Test
	fun hintFollowsTheScopeWhenSearching() {
		val expected = mapOf(
			"ALL" to SearchHint.SEARCH_ALL,
			"MANGA" to SearchHint.SEARCH_MANGA,
			"BOOK" to SearchHint.SEARCH_BOOKS,
			"VIDEO" to SearchHint.SEARCH_VIDEO,
			"AUDIO" to SearchHint.SEARCH_AUDIO,
		)
		SearchSuggestionScope.entries.forEach { screen ->
			expected.forEach { (scope, hint) ->
				assertEquals(hint, SearchScopeRules.hintFor(screen, isFiltering = false, scope = scope))
			}
		}
		assertEquals(SearchHint.SEARCH_ALL, SearchScopeRules.hintFor(SearchSuggestionScope.ALL, false, null))
		assertEquals(SearchHint.SEARCH_ALL, SearchScopeRules.hintFor(SearchSuggestionScope.ALL, false, "bogus"))
	}

	@Test
	fun hintSaysFilterOnlyOnHistoryAndFavouritesWhileFiltering() {
		assertEquals(
			SearchHint.FILTER_HISTORY,
			SearchScopeRules.hintFor(SearchSuggestionScope.HISTORY, true, "MANGA"),
		)
		assertEquals(
			SearchHint.FILTER_FAVOURITES,
			SearchScopeRules.hintFor(SearchSuggestionScope.FAVOURITES, true, "BOOK"),
		)
		// Other tabs can never be in filter mode, even if the flag were stale.
		assertEquals(SearchHint.SEARCH_BOOKS, SearchScopeRules.hintFor(SearchSuggestionScope.ALL, true, "BOOK"))
	}
}
