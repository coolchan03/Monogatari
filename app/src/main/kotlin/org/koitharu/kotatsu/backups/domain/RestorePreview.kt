package org.koitharu.kotatsu.backups.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import org.koitharu.kotatsu.backups.data.BackupCatalogPolicy
import org.koitharu.kotatsu.backups.data.BackupCategoryMergePlan
import org.koitharu.kotatsu.core.db.MangaDatabase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Read-only dry run of a restore: streams the sections of an already validated backup archive and compares them
 * with the local database to say how many entries would be new, newer, kept, etc. Nothing is written.
 *
 * The models below are slimmed-down views of the backup JSON models (same field names, unknown keys ignored), so
 * the large embedded manga objects are skipped instead of being materialised.
 */
internal object RestorePreview {

	/** More entries than this in one section: no preview, instead of a long database scan. */
	const val MAX_ENTRIES = 20_000
	private const val MAX_SETTINGS_BYTES = 4L * 1024 * 1024

	private val json = Json {
		allowSpecialFloatingPointValues = true
		coerceInputValues = true
		ignoreUnknownKeys = true
		useAlternativeNames = false
	}

	@Serializable
	class HistoryLite(
		@SerialName("manga_id") val mangaId: Long,
		@SerialName("updated_at") val updatedAt: Long,
		@SerialName("deleted_at") val deletedAt: Long = 0L,
	)

	@Serializable
	class FavouriteLite(
		@SerialName("manga_id") val mangaId: Long,
		@SerialName("category_id") val categoryId: Long,
		@SerialName("created_at") val createdAt: Long,
		@SerialName("deleted_at") val deletedAt: Long = 0L,
	)

	@Serializable
	class CategoryLite(
		@SerialName("category_id") val categoryId: Long,
		@SerialName("title") val title: String,
		@SerialName("deleted_at") val deletedAt: Long = 0L,
	)

	@Serializable
	class BookmarksLite(
		@SerialName("bookmarks") val bookmarks: List<BookmarkLite> = emptyList(),
	)

	@Serializable
	class BookmarkLite(
		@SerialName("page_id") val pageId: Long,
	)

	/**
	 * Calls [onSection] once per previewable section in [sections] that the archive contains, with null when that
	 * section could not be read. Cancellable; throws only [CancellationException] or archive-level I/O errors.
	 */
	suspend fun compute(
		archive: File,
		sections: Set<BackupSection>,
		database: MangaDatabase,
		onSection: suspend (BackupSection, RestorePreviewCounts?) -> Unit,
	) {
		ZipFile(archive).use { zip ->
			fun stream(section: BackupSection): InputStream? = zip.getEntry(section.entryName)?.let(zip::getInputStream)

			// Favourites are judged against the categories they would be remapped to.
			var plan: RestorePreviewRules.CategoryPlan? = null
			var planFailed = false
			if (BackupSection.CATEGORIES in sections || BackupSection.FAVOURITES in sections) {
				try {
					stream(BackupSection.CATEGORIES)?.use { plan = readCategoryPlan(it, database) }
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					planFailed = true
				}
			}
			for (section in PREVIEW_ORDER) {
				if (section !in sections) continue
				val counts = try {
					when (section) {
						BackupSection.CATEGORIES -> plan?.let { RestorePreviewCounts(new = it.new, same = it.matched) }
						BackupSection.FAVOURITES -> if (planFailed) null else stream(section)?.use {
							countFavourites(it, plan, database)
						}

						BackupSection.HISTORY -> stream(section)?.use { countHistory(it, database) }
						BackupSection.BOOKMARKS -> stream(section)?.use { countBookmarks(it, database) }
						BackupSection.SETTINGS -> countSettings(zip.getEntry(section.entryName)?.let { entry ->
							if (entry.size !in 0..MAX_SETTINGS_BYTES) null else zip.getInputStream(entry)
						})

						else -> null
					}
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					null
				}
				onSection(section, counts)
			}
		}
	}

	private val PREVIEW_ORDER = listOf(
		BackupSection.CATEGORIES,
		BackupSection.FAVOURITES,
		BackupSection.HISTORY,
		BackupSection.BOOKMARKS,
		BackupSection.SETTINGS,
	)

	private suspend fun readCategoryPlan(input: InputStream, database: MangaDatabase): RestorePreviewRules.CategoryPlan {
		val incoming = ArrayList<RestorePreviewRules.IncomingCategory>()
		for (item in input.readArray<CategoryLite>()) {
			currentCoroutineContext().ensureActive()
			if (incoming.size >= MAX_ENTRIES) throw IOException("Too many categories")
			incoming.add(RestorePreviewRules.IncomingCategory(item.categoryId, item.title, item.deletedAt))
		}
		val local = database.getFavouriteCategoriesDao().findAllForBackup().map { c ->
			BackupCategoryMergePlan.LocalCategory(c.categoryId.toLong(), c.title, c.deletedAt)
		}
		return RestorePreviewRules.planCategories(incoming, local)
	}

	private suspend fun countFavourites(
		input: InputStream,
		plan: RestorePreviewRules.CategoryPlan?,
		database: MangaDatabase,
	): RestorePreviewCounts {
		val dao = database.getFavouritesDao()
		var counts = RestorePreviewCounts()
		var seen = 0
		for (item in input.readArray<FavouriteLite>()) {
			currentCoroutineContext().ensureActive()
			if (++seen > MAX_ENTRIES) throw IOException("Too many favourites")
			// Same remapping as the restore: an entry whose category is skipped is not restored at all.
			val categoryId = BackupCategoryMergePlan.remap(plan?.remap, item.categoryId) ?: continue
			// A category that would be created has no favourites yet.
			val existing = if (categoryId < 0L) null else dao.findIncludingDeleted(item.mangaId, categoryId)
			counts = counts.plus(
				RestorePreviewRules.classify(existing?.createdAt, existing?.deletedAt ?: 0L, item.createdAt, item.deletedAt),
			)
		}
		return counts
	}

	private suspend fun countHistory(input: InputStream, database: MangaDatabase): RestorePreviewCounts {
		val dao = database.getHistoryDao()
		var counts = RestorePreviewCounts()
		var seen = 0
		for (item in input.readArray<HistoryLite>()) {
			currentCoroutineContext().ensureActive()
			if (++seen > MAX_ENTRIES) throw IOException("Too many history entries")
			val existing = dao.findIncludingDeleted(item.mangaId)
			counts = counts.plus(
				RestorePreviewRules.classify(existing?.updatedAt, existing?.deletedAt ?: 0L, item.updatedAt, item.deletedAt),
			)
		}
		return counts
	}

	private suspend fun countBookmarks(input: InputStream, database: MangaDatabase): RestorePreviewCounts {
		val dao = database.getBookmarksDao()
		var counts = RestorePreviewCounts()
		var seen = 0
		for (group in input.readArray<BookmarksLite>()) {
			for (bookmark in group.bookmarks) {
				currentCoroutineContext().ensureActive()
				if (++seen > MAX_ENTRIES) throw IOException("Too many bookmarks")
				// Bookmarks are only ever added; one already on this device is left as it is.
				counts = if (dao.find(bookmark.pageId) == null) counts.copy(new = counts.new + 1) else counts.copy(same = counts.same + 1)
			}
		}
		return counts
	}

	private fun countSettings(input: InputStream?): RestorePreviewCounts? {
		val text = (input ?: return null).use { it.readBytes().decodeToString() }
		val keys = json.parseToJsonElement(text).jsonArray[0].jsonObject.keys
		return RestorePreviewCounts(new = RestorePreviewRules.countSettings(keys, BackupCatalogPolicy::safePreferenceKey))
	}

	private inline fun <reified T> InputStream.readArray(): Sequence<T> =
		json.decodeToSequence(this, serializer<T>(), DecodeSequenceMode.ARRAY_WRAPPED)
}
