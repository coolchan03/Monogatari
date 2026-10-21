package org.koitharu.kotatsu.discover

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.Reusable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.MihonTitleMatch
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.filterByMediaType
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Finds a Discover suggestion (an AniList title, which has no source of its own) on the user's enabled sources of the
 * same media type, so it can be added to the library. Only an exact, unambiguous title match counts; the first source
 * in the user's order that has one wins.
 */
@Reusable
class DiscoverLibraryFinder @Inject constructor(
	private val sources: MangaSourcesRepository,
	private val repositories: MangaRepository.Factory,
) {

	suspend fun find(title: String, alternative: String?, type: MediaType): Manga? = coroutineScope {
		val candidates = sources.getEnabledSources().filterByMediaType(type).take(MAX_SOURCES)
		val queries = listOfNotNull(title, alternative).distinct()
		val limiter = Semaphore(PARALLELISM)
		candidates.map { source ->
			async {
				limiter.withPermit {
					// withTimeoutOrNull: a timeout must not cancel the lookups on the other sources.
					runCatchingCancellable {
						withTimeoutOrNull(TIMEOUT_MS) {
							val repository = repositories.create(source)
							queries.firstNotNullOfOrNull { query ->
								val results = repository.getList(0, null, MangaListFilter(query = query))
								MihonTitleMatch.pick(query, results.map { it.title to it })
							}
						}
					}.getOrNull()
				}
			}
		}.awaitAll().firstOrNull { it != null }
	}

	private companion object {
		const val MAX_SOURCES = 12
		const val PARALLELISM = 4
		const val TIMEOUT_MS = 15_000L
	}
}

/**
 * "Add to library" for a Discover card: titles from a built-in source go straight to the favourites sheet; AniList
 * suggestions are first looked up on the user's sources of [type]. When nothing matches, a snackbar offers a search.
 */
internal fun Fragment.addDiscoverItemToLibrary(
	item: AniListDiscover.Item,
	type: MediaType,
	finder: DiscoverLibraryFinder,
	onSearch: (String) -> Unit,
) {
	item.manga?.let { manga ->
		router.showFavoriteDialog(manga)
		return
	}
	val root = view ?: return
	val progress = Snackbar.make(root, getString(R.string.discover_library_finding, item.title), Snackbar.LENGTH_INDEFINITE)
	progress.show()
	viewLifecycleOwner.lifecycleScope.launch {
		val manga = runCatchingCancellable { finder.find(item.title, item.alternativeTitle, type) }.getOrNull()
		progress.dismiss()
		if (manga != null) {
			router.showFavoriteDialog(manga)
		} else {
			Snackbar.make(root, getString(R.string.discover_library_not_found, item.title), Snackbar.LENGTH_LONG)
				.setAction(R.string.mono_discover_search) { onSearch(item.title) }
				.show()
		}
	}
}
