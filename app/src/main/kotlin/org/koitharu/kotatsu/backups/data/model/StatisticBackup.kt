package org.koitharu.kotatsu.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koitharu.kotatsu.stats.data.StatsEntity

/** Optional fields preserve compatibility with older backup archives. */
@Serializable
class StatisticBackup(
    @SerialName("manga_id") val mangaId: Long,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("duration") val duration: Long,
    @SerialName("pages") val pages: Int,
    @SerialName("chapter_id") val chapterId: Long? = null,
    @SerialName("words") val words: Int = 0,
    @SerialName("media_ms") val mediaMs: Long = 0,
) {
    constructor(entity: StatsEntity) : this(
        mangaId = entity.mangaId, startedAt = entity.startedAt,
        duration = entity.duration, pages = entity.pages,
        chapterId = entity.chapterId, words = entity.words, mediaMs = entity.mediaMs,
    )
    fun toEntity() = StatsEntity(
        mangaId = mangaId, startedAt = startedAt, duration = duration,
        pages = pages, chapterId = chapterId, words = words, mediaMs = mediaMs,
    )
}
