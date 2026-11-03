package org.koitharu.kotatsu.backups.domain

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.builtin.newpipe.NewPipeChannelManga
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toEntities
import org.koitharu.kotatsu.core.db.entity.toEntity
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.favourites.data.FavouriteEntity
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.list.domain.ListSortOrder
import org.koitharu.kotatsu.parsers.model.Manga
import javax.inject.Inject

/**
 * Follows imported YouTube channels: each becomes a favourite in the "YouTube subscriptions" category
 * (tracked, so update checks run). No network is used; channel videos load when a channel is opened.
 * Re-importing only adds channels that are not in the category yet.
 */
@Reusable
class SubscriptionImporter @Inject constructor(
	private val favourites: FavouritesRepository,
	private val database: MangaDatabase,
) {

	class Result(val imported: Int, val alreadyPresent: Int, val category: FavouriteCategory)

	internal suspend fun import(
		channels: List<SubscriptionImportParser.Channel>,
		onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
	): Result = withContext(Dispatchers.IO) {
		val category = favourites.observeCategories().first()
			.firstOrNull { it.title.equals(CATEGORY_TITLE, ignoreCase = true) }
			?: favourites.createCategory(
				CATEGORY_TITLE, ListSortOrder.NEWEST, isTrackerEnabled = true, isVisibleOnShelf = true,
			)
		var imported = 0
		var present = 0
		var done = 0
		onProgress(0, channels.size)
		for (chunk in channels.chunked(CHUNK)) {
			database.withTransaction {
				for (channel in chunk) {
					val manga = NewPipeChannelManga.youtube(channel.channelId, channel.name)
					if (addIfAbsent(category.id, manga)) imported++ else present++
				}
			}
			done += chunk.size
			onProgress(done, channels.size)
		}
		Result(imported, present, category)
	}

	/**
	 * Like [MihonImporter]: never replaces an existing favourite row, and a channel the user removed
	 * (tombstone) is not brought back. Returns true when a row was added.
	 */
	private suspend fun addIfAbsent(categoryId: Long, manga: Manga): Boolean {
		if (database.getFavouritesDao().findIncludingDeleted(manga.id, categoryId) != null) return false
		if (manga.id !in database.getMangaDao()) {
			val tags = manga.tags.toEntities()
			database.getTagsDao().upsert(tags)
			database.getMangaDao().upsert(manga.toEntity(), tags)
		}
		database.getFavouritesDao().insert(
			FavouriteEntity(
				mangaId = manga.id,
				categoryId = categoryId,
				sortKey = 0,
				isPinned = false,
				createdAt = System.currentTimeMillis(),
				deletedAt = 0L,
			),
		)
		return true
	}

	companion object {
		const val CATEGORY_TITLE = "YouTube subscriptions"
		private const val CHUNK = 100
	}
}
