package org.koitharu.kotatsu.sourcerepo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl

class RepoSeedingTest {

	private val a = "https://example.net/a.json"
	private val b = "https://example.net/b.json"
	private val c = "https://example.net/c.json"
	private val user = "https://example.net/mine.json"

	@Test fun freshInstallGetsEveryDefault() {
		val result = RepoSeeding.seed(null, null, setOf(a, b), setOf(a))
		assertEquals(setOf(a, b), result.urls)
		assertEquals(setOf(a, b), result.seeded)
	}

	@Test fun existingInstallGetsOnlyNewDefaultsAndKeepsRemovedLegacyOnes() {
		// The user removed legacy default 'a' long ago; it must not return, 'b' and 'c' are new.
		val result = RepoSeeding.seed(setOf(user), null, setOf(a, b, c), setOf(a))
		assertEquals(setOf(user, b, c), result.urls)
		assertFalse(a in result.urls)
		assertEquals(setOf(a, b, c), result.seeded)
	}

	@Test fun removedSeededRepoIsNeverReAdded() {
		val first = RepoSeeding.seed(null, null, setOf(a, b), emptySet())
		val afterRemove = first.urls - b
		val second = RepoSeeding.seed(afterRemove, first.seeded, setOf(a, b), emptySet())
		assertEquals(setOf(a), second.urls)
	}

	@Test fun laterReleaseSeedsOnlyTheAdditionalDefault() {
		val second = RepoSeeding.seed(setOf(a, user), setOf(a, b), setOf(a, b, c), emptySet())
		assertEquals(setOf(a, user, c), second.urls)
		assertEquals(setOf(a, b, c), second.seeded)
	}

	@Test fun emptyStoredSetStaysEmptyWhenAllSeeded() {
		val result = RepoSeeding.seed(emptySet(), setOf(a), setOf(a), emptySet())
		assertTrue(result.urls.isEmpty())
	}

	@Test fun recommendedReposAreValidHttpsAndUnique() {
		assertEquals(RecommendedRepos.all.size, RecommendedRepos.all.toSet().size)
		assertTrue(RecommendedRepos.all.all { normalizeRepoUrl(it) == it })
		assertEquals(12, RecommendedRepos.all.size)
		assertEquals(3, RecommendedRepos.byType.size)
		assertTrue(RecommendedRepos.byType.getValue(JsItemType.NOVEL).size == 4)
	}

	@Test fun mergedDefaultsCombineBuildListAndSkipInvalid() {
		val merged = RecommendedRepos.mergedDefaults(" $user , http://insecure.test/x.json,,")
		assertTrue(user in merged)
		assertTrue(RecommendedRepos.all.all { it in merged })
		assertEquals(RecommendedRepos.all.size + 1, merged.size)
		assertEquals(RecommendedRepos.all.toSet(), RecommendedRepos.mergedDefaults(""))
	}
}
