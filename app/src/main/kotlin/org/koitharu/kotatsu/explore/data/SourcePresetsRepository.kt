package org.koitharu.kotatsu.explore.data

import dagger.Reusable
import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.sourcescore.domain.SourceRanker
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.util.ext.mapItems
import javax.inject.Inject

@Reusable
class SourcePresetsRepository @Inject constructor(
	private val db: MangaDatabase,
    private val jsStore: JsSourceStore,
    private val ranker: SourceRanker,
    @ApplicationContext private val context: Context,
) {

	private val dao: SourcePresetsDao
		get() = db.getSourcePresetsDao()

    /**
     * Create presets at most once. Even if the user later deletes every preset, do not recreate
     * unwanted entries. Existing custom or soft-deleted presets suppress the initial seed.
     */
    suspend fun seedDefaultsIfAbsent(sources: List<MangaSource>) {
        val prefs = context.getSharedPreferences("monogatari_default_presets", Context.MODE_PRIVATE)
        if (prefs.getBoolean("initialized", false)) return
        val snapshot = runCatchingCancellable { ranker.snapshot() }.getOrNull()
        val scores = sources.associate { source ->
            source.name to (snapshot?.rank(source)?.composite ?: 0.5)
        }
        val suggestions = DefaultSourcePresets.plan(sources, jsStore.list(), scores)
        db.withTransaction {
            if (dao.countIncludingDeleted() == 0) {
                val now = System.currentTimeMillis()
                suggestions.forEachIndexed { index, suggestion ->
                    dao.insert(SourcePresetEntity(
                        presetId = 0,
                        title = suggestion.title,
                        languages = "",
                        sources = suggestion.sources.joinToString(","),
                        createdAt = now,
                        sortKey = index + 1,
                        deletedAt = 0L,
                    ))
                }
            }
        }
        prefs.edit().putBoolean("initialized", true).apply()
    }


	fun observeAll(): Flow<List<SourcePreset>> {
		return dao.observeAll().mapItems { it.toSourcePreset() }
	}

	fun observe(id: Long): Flow<SourcePreset?> {
		return dao.observe(id).map { it?.toSourcePreset() }
	}

	suspend fun getAll(): List<SourcePreset> {
		return dao.findAll().map { it.toSourcePreset() }
	}

	suspend fun getById(id: Long): SourcePreset? {
		return dao.find(id)?.toSourcePreset()
	}

	suspend fun createPreset(title: String, languages: Set<String>, sources: Set<String>): SourcePreset {
		val entity = SourcePresetEntity(
			presetId = 0,
			title = title,
			languages = languages.joinToString(","),
			sources = sources.joinToString(","),
			createdAt = System.currentTimeMillis(),
			sortKey = dao.getNextSortKey(),
			deletedAt = 0L,
		)
		val id = dao.insert(entity)
		return entity.copy(presetId = id).toSourcePreset()
	}

	suspend fun updatePreset(id: Long, title: String, languages: Set<String>) {
		dao.update(id, title, languages.joinToString(","))
	}

	suspend fun updatePresetSources(id: Long, sources: Set<String>) {
		dao.updateSources(id, sources.joinToString(","))
	}

	suspend fun deletePreset(id: Long) {
		dao.delete(id)
	}
}
