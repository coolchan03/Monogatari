package org.koitharu.kotatsu.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koitharu.kotatsu.stats.data.ChapterMetaEntity

@Serializable
data class ChapterMetaBackup(
    @SerialName("chapter_id") val chapterId: Long,
    @SerialName("manga_id") val mangaId: Long,
    @SerialName("pages") val pages: Int = 0,
    @SerialName("words") val words: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    constructor(entity: ChapterMetaEntity) : this(
        entity.chapterId, entity.mangaId, entity.pages, entity.words,
        entity.durationMs, entity.updatedAt,
    )
    fun toEntity() = ChapterMetaEntity(chapterId, mangaId, pages, words, durationMs, updatedAt)
}
