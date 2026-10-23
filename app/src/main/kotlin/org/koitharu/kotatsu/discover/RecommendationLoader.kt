package org.koitharu.kotatsu.discover

import dagger.Reusable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.discover.taste.TasteProfile
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * The Recommended strip of the Library: AniList lists plus built-in popular shelves per media type, with the user's
 * on-device "For you" taste ranking for manga and anime. Every source is optional: one that fails or times out is
 * skipped, and an empty result means the strip is hidden.
 */
@Reusable
class RecommendationLoader @Inject constructor(
	private val discover: AniListDiscover,
	private val history: HistoryRepository,
	private val repositories: MangaRepository.Factory,
) {

	/** A card of the strip and the media type that tapping or adding it should search. */
	data class Recommendation(val item: AniListDiscover.Item, val type: MediaType)

	/** Recommendations for [type] (`null` is All media: a short mix of every type). Adult titles dropped when [hideAdult]. */
	suspend fun load(type: MediaType?, hideAdult: Boolean): List<Recommendation> {
		if (type != null) {
			return AniListDiscover.visibleItems(loadType(type), hideAdult).take(CAP).map { Recommendation(it, type) }
		}
		val types = MediaType.entries
		val lists = coroutineScope {
			types.map { entry -> async { AniListDiscover.visibleItems(loadType(entry), hideAdult) } }.awaitAll()
		}
		// mix() returns the very same instances, so the owning type is found by identity.
		val owners = java.util.IdentityHashMap<AniListDiscover.Item, MediaType>()
		lists.forEachIndexed { index, list -> list.forEach { owners[it] = types[index] } }
		return mix(lists, PER_TYPE_IN_ALL, CAP).map { Recommendation(it, owners.getValue(it)) }
	}

	private suspend fun loadType(type: MediaType): List<AniListDiscover.Item> = coroutineScope {
		when (type) {
			MediaType.MANGA -> {
				val forYou = async { forYou(AniListDiscover.Feed.TRENDING_MANGA) }
				val trending = async { feed(AniListDiscover.Feed.TRENDING_MANGA) }
				mix(listOf(forYou.await(), trending.await()), CAP, CAP)
			}
			MediaType.BOOK -> {
				val novels = async { feed(AniListDiscover.Feed.LIGHT_NOVELS) }
				val shelf = async { shelf(BOOK_SHELF) }
				mix(listOf(novels.await(), shelf.await()), CAP, CAP)
			}
			MediaType.VIDEO -> {
				val anime = async { feed(AniListDiscover.Feed.TRENDING_ANIME) }
				val shelf = async { shelf(VIDEO_SHELF) }
				mix(listOf(anime.await(), shelf.await()), CAP, CAP)
			}
			MediaType.AUDIO -> shelf(AUDIO_SHELF)
		}
	}

	private suspend fun feed(feed: AniListDiscover.Feed): List<AniListDiscover.Item> = runCatchingCancellable {
		withTimeoutOrNull(TIMEOUT_MS) { discover.load(feed, 1).items }
	}.getOrNull().orEmpty()

	/**
	 * Two pages of [base], re-ranked by tags from the user's reading history (on device only). Empty when there is no
	 * history yet, so the plain popular list is used instead.
	 */
	suspend fun forYou(base: AniListDiscover.Feed, limit: Int = CAP): List<AniListDiscover.Item> {
		val candidates = runCatchingCancellable {
			withTimeoutOrNull(TIMEOUT_MS) {
				discover.load(base, 1).items +
					runCatchingCancellable { discover.load(base, 2).items }.getOrDefault(emptyList())
			}
		}.getOrNull().orEmpty().distinctBy { it.uniqueKey }
		if (candidates.isEmpty()) return emptyList()
		val read = runCatchingCancellable { history.getList(0, HISTORY_FOR_TASTE) }.getOrDefault(emptyList())
		return rankByTaste(candidates, read, limit)
	}

	private suspend fun shelf(sources: List<BuiltinSource>): List<AniListDiscover.Item> = coroutineScope {
		val lists = sources.map { source ->
			async {
				// withTimeoutOrNull: one slow site must not cancel the other sources.
				val list = runCatchingCancellable {
					withTimeoutOrNull(TIMEOUT_MS) { repositories.create(source).getList(0, null, null) }
				}.getOrNull().orEmpty()
				list.take(CAP).map { manga -> shelfItem(manga, source.title) }
			}
		}.awaitAll()
		mix(lists, CAP, CAP)
	}

	companion object {

		/** Most cards the strip ever shows. */
		const val CAP = 16
		/** In the All chip every type contributes about this many cards. */
		const val PER_TYPE_IN_ALL = 4

		private const val TIMEOUT_MS = 15_000L
		private const val HISTORY_FOR_TASTE = 150
		private val BOOK_SHELF = listOf(BuiltinSource.GUTENBERG, BuiltinSource.OPEN_LIBRARY)
		private val VIDEO_SHELF = listOf(BuiltinSource.DAILYMOTION, BuiltinSource.ODYSEE)
		private val AUDIO_SHELF = listOf(BuiltinSource.LIBRIVOX, BuiltinSource.IA_AUDIO)

		private fun shelfItem(manga: Manga, provider: String) = AniListDiscover.Item(
			id = manga.id.hashCode(),
			title = manga.title,
			alternativeTitle = null,
			coverUrl = manga.coverUrl?.takeIf { it.startsWith("https://") },
			score = null,
			isAdult = manga.contentRating == ContentRating.ADULT,
			providerLabel = provider,
			manga = manga,
		)

		/** [candidates] ranked by the taste built from [read]; empty when nothing overlaps. Pure apart from [TasteProfile]. */
		fun rankByTaste(candidates: List<AniListDiscover.Item>, read: List<Manga>, limit: Int): List<AniListDiscover.Item> {
			val profile = TasteProfile.build(read.mapIndexed { index, manga ->
				TasteProfile.Signal(manga.tags.map { it.title }, favourite = false, readChapters = 5, dropped = false, ageDays = index)
			})
			val readTitles = read.mapTo(HashSet()) { it.title.lowercase() }
			return profile.rank(candidates.map { item ->
				TasteProfile.Candidate(item, item.genres, alreadyInLibrary = item.title.lowercase() in readTitles)
			}, limit)
		}

		/**
		 * Round-robin interleave: takes at most [perList] cards from each list, skips a card whose
		 * [AniListDiscover.Item.uniqueKey] was already taken, and stops at [cap].
		 */
		fun mix(lists: List<List<AniListDiscover.Item>>, perList: Int, cap: Int): List<AniListDiscover.Item> {
			val result = ArrayList<AniListDiscover.Item>()
			val seen = HashSet<String>()
			val taken = IntArray(lists.size)
			val next = IntArray(lists.size)
			var progressed = true
			while (result.size < cap && progressed) {
				progressed = false
				for (i in lists.indices) {
					if (result.size >= cap) break
					val list = lists[i]
					while (taken[i] < perList && next[i] < list.size) {
						val item = list[next[i]++]
						if (seen.add(item.uniqueKey)) {
							result += item
							taken[i]++
							progressed = true
							break
						}
					}
				}
			}
			return result
		}
	}
}
