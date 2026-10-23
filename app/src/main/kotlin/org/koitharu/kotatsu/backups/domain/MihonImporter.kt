package org.koitharu.kotatsu.backups.domain

import dagger.Reusable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Draft (v1.1) Mihon/Tachiyomi library migration.
 *
 * Tachiyomi extensions are Android apps and their stored links are extension-specific, so links are never copied.
 * Each favourite on a source that Monogatari also has (unique name match) is looked up on that source by its title,
 * and only an exact, unambiguous title match is added to the same-named category. Everything else is listed so the
 * user can migrate it by hand. Reading progress moves by chapter number (see [MihonProgress]).
 */
@Reusable
class MihonImporter @Inject constructor(
	private val repositories: MangaRepository.Factory,
	private val favourites: FavouritesRepository,
	private val jsStore: JsSourceStore,
	private val history: HistoryRepository,
) {

	class Preview internal constructor(
		internal val backup: MihonBackup.Backup,
		internal val summary: MihonImportSummary,
	) {
		val titles: Int get() = summary.titles
		val favourites: Int get() = summary.favourites
		val withProgress: Int get() = summary.withProgress
		val matchedTitles: Int get() = summary.matchedTitles
		val unmatchedSources: List<String> get() = summary.unmatchedSources
	}

	class Result(val imported: Int, val notFound: List<String>, val unsupported: Int, val withProgress: Int = 0)

	/** Monogatari source display name -> source key, for built-in parsers and installed JS extensions. */
	internal fun availableSources(): Map<String, String> {
		val names = LinkedHashMap<String, String>()
		MangaParserSource.entries.filterNot { it.isBroken }.forEach { names[it.title] = it.name }
		jsStore.list().forEach { names[it.name] = JsMangaSource(it.id).name }
		return names
	}

	internal fun preview(backup: MihonBackup.Backup): Preview =
		Preview(backup, MihonImportSummary.of(backup, availableSources()))

	suspend fun importFavourites(
		preview: Preview,
		defaultCategoryTitle: String,
		onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
	): Result = withContext(Dispatchers.IO) {
		val backup = preview.backup
		val matched = preview.summary.matchedSources
		val candidates = backup.manga.filter { it.favorite }
		val supported = candidates.filter { (backup.sources[it.sourceId] ?: "") in matched }
		val categoryTitles = backup.categories.associate { it.order to it.name }
		val categoryIds = HashMap<String, Long>()
		val existing = favourites.observeCategories().first().associateBy { it.title.lowercase(Locale.ROOT) }
		suspend fun categoryId(title: String): Long = categoryIds.getOrPut(title.lowercase(Locale.ROOT)) {
			existing[title.lowercase(Locale.ROOT)]?.id
				?: favourites.createCategory(title, ListSortOrder.NEWEST, isTrackerEnabled = true, isVisibleOnShelf = true).id
		}
		val done = AtomicInteger()
		val limiter = Semaphore(3)
		val found = coroutineScope {
			supported.map { item ->
				async {
					limiter.withPermit {
						val key = matched.getValue(backup.sources.getValue(item.sourceId))
						val hit = findOnSource(key, item)
						onProgress(done.incrementAndGet(), supported.size)
						item to hit
					}
				}
			}.awaitAll()
		}
		var imported = 0
		var progressCount = 0
		val notFound = ArrayList<String>()
		for ((item, manga) in found) {
			if (manga == null) {
				notFound += item.title
				continue
			}
			val titles = item.categoryOrders.mapNotNull { categoryTitles[it] }.ifEmpty { listOf(defaultCategoryTitle) }
			for (title in titles.distinct()) {
				favourites.addToCategory(categoryId(title), listOf(manga))
			}
			imported++
			if (transferProgress(item, manga)) progressCount++
		}
		Result(imported, notFound.sorted(), candidates.size - supported.size, progressCount)
	}

	/**
	 * Marks the same-numbered chapter as the reading position when Mihon recorded chapters as read. Never overwrites
	 * history Monogatari already has for the title. Chapter numbers are the only link: ids and links differ per source.
	 */
	private suspend fun transferProgress(item: MihonBackup.Manga, manga: Manga): Boolean = try {
		val target = MihonProgress.lastReadNumber(item.chapters)
		if (target == null || history.getOne(manga) != null) {
			false
		} else {
			val details = withTimeout(20_000) { repositories.create(manga.source).getDetails(manga) }
			val chapters = details.chapters.orEmpty()
			val index = MihonProgress.indexOfNumber(chapters.map { it.number }, target)
			if (index < 0) false else {
				val percent = if (chapters.size > 1) index.toFloat() / (chapters.size - 1) else 1f
				history.addOrUpdate(details, chapters[index].id, 0, 0, percent, force = true)
				true
			}
		}
	} catch (e: TimeoutCancellationException) {
		false // A timed-out remote source is a skipped title, not cancellation of the whole import.
	} catch (e: CancellationException) {
		throw e
	} catch (_: Exception) {
		false
	}

	private suspend fun findOnSource(sourceKey: String, item: MihonBackup.Manga): Manga? = try {
		val repository = repositories.create(MangaSource(sourceKey))
		val results = withTimeout(20_000) { repository.getList(0, null, MangaListFilter(query = item.title)) }
		MihonTitleMatch.pick(item.title, results.map { it.title to it }.toList())
	} catch (e: TimeoutCancellationException) {
		null
	} catch (e: CancellationException) {
		throw e
	} catch (_: Exception) {
		null
	}
}

/** Reading-progress mapping between a Mihon backup and a Monogatari source (pure, unit-tested). */
internal object MihonProgress {

	/** Highest chapter number marked read (or partly read) in Mihon, or null when nothing was read. */
	fun lastReadNumber(chapters: List<MihonBackup.Chapter>): Float? = chapters
		.filter { (it.read || it.lastPageRead > 0) && it.number >= 0f }
		.maxOfOrNull { it.number }

	/** Index of the chapter whose number equals [target]; the first match wins when branches repeat numbers. */
	fun indexOfNumber(numbers: List<Float>, target: Float): Int =
		numbers.indexOfFirst { kotlin.math.abs(it - target) < 0.001f }
}

/** Exact-title choice for migrated favourites: a unique normalised match, or nothing. */
internal object MihonTitleMatch {

	fun <T> pick(wanted: String, results: List<Pair<String, T>>): T? {
		val key = normalize(wanted)
		if (key.isEmpty()) return null
		val exact = results.filter { normalize(it.first) == key }
		return exact.singleOrNull()?.second
	}

	fun normalize(title: String): String = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
		.lowercase(Locale.ROOT)
		.replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
