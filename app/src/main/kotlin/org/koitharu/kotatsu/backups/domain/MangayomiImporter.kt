package org.koitharu.kotatsu.backups.domain

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.backups.data.BackupMergePolicy
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.MangaEntity
import org.koitharu.kotatsu.core.db.entity.TagEntity
import org.koitharu.kotatsu.favourites.data.FavouriteCategoryEntity
import org.koitharu.kotatsu.favourites.data.FavouriteEntity
import org.koitharu.kotatsu.history.data.HistoryEntity
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.jsext.source.jsUid
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.util.longHashCode
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.Locale
import javax.inject.Inject

/**
 * Imports a Mangayomi backup: library (favourites + categories) and reading progress. Missing extensions
 * are installed from the user's repositories first. Existing entries are never overwritten by older data.
 */
@Reusable
class MangayomiImporter @Inject constructor(
	private val database: MangaDatabase,
	private val repositories: SourceRepoRepository,
	private val installed: JsSourceStore,
) {

	class Preview internal constructor(
		internal val backup: MangayomiBackup,
		internal val plan: MangayomiImportPlanner.Plan,
		internal val entries: Map<Long, JsSourceEntry>,
	) {
		val titles: Int get() = plan.titles.size
		val favourites: Int get() = plan.titles.count { it.favorite }
		val withProgress: Int get() = plan.titles.count { MangayomiImportPlanner.progress(it, backup) != null }
		val matched: Int get() = plan.matched.size
		val sourcesToInstall: List<String> get() = plan.toInstall.map { it.name }
		val unmatchedSources: List<String> get() = plan.unmatched.map { it.source }.distinct().sorted()
	}

	class Result(val imported: Int, val skipped: Int, val installFailures: List<String>)

	internal suspend fun preview(backup: MangayomiBackup): Preview = withContext(Dispatchers.Default) {
		val entries = LinkedHashMap<Long, JsSourceEntry>()
		installed.list().filter { it.isRunnable }.forEach { entries[it.id] = it }
		// Repositories that can't be reached just mean fewer installable matches.
		val offered = runCatching { repositories.fetchAll().jsSources.map { it.entry } }
			.onFailure { if (it is CancellationException) throw it }
			.getOrDefault(emptyList())
		offered.forEach { entries.putIfAbsent(it.id, it) }
		val installedIds = installed.list().mapTo(HashSet()) { it.id }
		val sources = entries.values.map {
			MangayomiImportPlanner.Source(it.id, it.name, it.lang, it.itemType.code, it.id in installedIds)
		}
		Preview(backup, MangayomiImportPlanner.plan(backup, sources), entries)
	}

	suspend fun importAll(preview: Preview, defaultCategoryTitle: String): Result = withContext(Dispatchers.IO) {
		val failedInstalls = ArrayList<String>()
		val failedIds = HashSet<Long>()
		for (source in preview.plan.toInstall) {
			val entry = preview.entries[source.id] ?: continue
			try {
				repositories.installJsSource(entry)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				failedInstalls += source.name
				failedIds += source.id
			}
		}
		val backup = preview.backup
		val categories = resolveCategories(backup, preview.plan.titles, defaultCategoryTitle)
		var imported = 0
		var skipped = 0
		for (manga in preview.plan.titles) {
			val target = preview.plan.matched[manga.id]?.takeUnless { it.id in failedIds }
			val sourceName = target?.let { JsMangaSource(it.id).name }
				?: MangayomiImportPlanner.fallbackSourceName(manga)
			try {
				database.withTransaction { importOne(manga, sourceName, target != null, backup, categories) }
				imported++
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				skipped++
			}
		}
		Result(imported, skipped, failedInstalls)
	}

	/** Mangayomi category id (or `null` for its implicit default) -> local category id, created by title as needed. */
	private suspend fun resolveCategories(
		backup: MangayomiBackup,
		titles: List<MangayomiBackup.Manga>,
		defaultTitle: String,
	): Map<Long?, Long> {
		val dao = database.getFavouriteCategoriesDao()
		val existing = dao.findAll().associateBy { it.title.lowercase(Locale.ROOT) }.toMutableMap()
		val byId = backup.categories.associateBy { it.id }
		val result = HashMap<Long?, Long>()
		suspend fun localId(title: String): Long {
			existing[title.lowercase(Locale.ROOT)]?.let { return it.categoryId.toLong() }
			val entity = FavouriteCategoryEntity(
				categoryId = 0,
				createdAt = System.currentTimeMillis(),
				sortKey = dao.getNextSortKey(),
				title = title,
				order = ListSortOrder.NEWEST.name,
				track = true,
				isVisibleInLibrary = true,
				deletedAt = 0L,
			)
			val id = dao.insert(entity)
			existing[title.lowercase(Locale.ROOT)] = entity.copy(categoryId = id.toInt())
			return id
		}
		val used = titles.filter { it.favorite }
		val usedIds = used.flatMap { it.categoryIds }.distinct()
		for (category in usedIds.mapNotNull { byId[it] }.sortedBy { it.position }) {
			result[category.id] = localId(category.name)
		}
		if (used.any { m -> m.categoryIds.none { it in result } }) {
			result[null] = localId(defaultTitle)
		}
		return result
	}

	private suspend fun importOne(
		manga: MangayomiBackup.Manga,
		sourceName: String,
		isRunnable: Boolean,
		backup: MangayomiBackup,
		categories: Map<Long?, Long>,
	) {
		val mangaId = jsUid(sourceName, manga.link)
		val mangaDao = database.getMangaDao()
		if (mangaDao.find(mangaId) == null) {
			val tags = manga.genres.distinctBy { it.lowercase(Locale.ROOT) }.map { genre ->
				TagEntity(
					title = genre,
					key = genre,
					source = sourceName,
					id = "${genre}_$sourceName".longHashCode(),
					isPinned = false,
				)
			}
			database.getTagsDao().upsert(tags)
			mangaDao.upsert(manga.toEntity(mangaId, sourceName), tags)
		}
		if (manga.favorite) {
			val favourites = database.getFavouritesDao()
			val targets = manga.categoryIds.mapNotNull { categories[it] }.ifEmpty { listOfNotNull(categories[null]) }
			for (categoryId in targets.distinct()) {
				if (favourites.findIncludingDeleted(mangaId, categoryId)?.deletedAt == 0L) continue
				favourites.upsert(
					FavouriteEntity(
						mangaId = mangaId,
						categoryId = categoryId,
						sortKey = 0,
						isPinned = false,
						createdAt = manga.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
						deletedAt = 0L,
					),
				)
			}
		}
		// Chapter ids only mean something when the same extension produces them here.
		val progress = if (isRunnable) MangayomiImportPlanner.progress(manga, backup) else null
		if (progress != null) {
			val history = database.getHistoryDao()
			val existing = history.findIncludingDeleted(mangaId)
			if (BackupMergePolicy.shouldReplace(existing?.updatedAt, existing?.deletedAt ?: 0L, progress.date, 0L)) {
				history.upsertRestored(
					HistoryEntity(
						mangaId = mangaId,
						createdAt = existing?.createdAt ?: progress.date,
						updatedAt = progress.date,
						chapterId = jsUid(sourceName, progress.chapter.url),
						page = progress.page,
						scroll = 0f,
						percent = progress.percent,
						deletedAt = 0L,
						chaptersCount = progress.chapters,
					),
				)
			}
		}
	}

	private fun MangayomiBackup.Manga.toEntity(id: Long, sourceName: String) = MangaEntity(
		id = id,
		title = name,
		altTitles = null,
		url = link,
		publicUrl = link.takeIf { it.startsWith("http") }.orEmpty(),
		rating = RATING_UNKNOWN,
		isNsfw = false,
		contentRating = null,
		coverUrl = imageUrl.orEmpty(),
		largeCoverUrl = null,
		state = when (status) {
			0 -> MangaState.ONGOING
			1, 5 -> MangaState.FINISHED
			2 -> MangaState.ABANDONED
			4 -> MangaState.PAUSED
			else -> null
		}?.name,
		authors = listOfNotNull(author, artist).distinct().joinToString("\n").ifEmpty { null },
		source = sourceName,
	)
}
