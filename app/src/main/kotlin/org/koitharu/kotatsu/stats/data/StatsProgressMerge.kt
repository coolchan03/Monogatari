package org.koitharu.kotatsu.stats.data

/**
 * StatsCollector writes snapshots asynchronously. Never allow a delayed snapshot
 * of the same reading session to replace a newer cumulative counter.
 */
internal fun StatsEntity.mergeSessionProgress(incoming: StatsEntity): StatsEntity {
    require(mangaId == incoming.mangaId && startedAt == incoming.startedAt) {
        "Cannot merge different reading sessions"
    }
    val incomingIsNewer = incoming.pages > pages ||
        (incoming.pages == pages && incoming.duration >= duration)
    return copy(
        duration = maxOf(duration, incoming.duration),
        pages = maxOf(pages, incoming.pages),
        chapterId = if (incomingIsNewer) incoming.chapterId ?: chapterId
            else chapterId ?: incoming.chapterId,
        words = maxOf(words, incoming.words),
        mediaMs = maxOf(mediaMs, incoming.mediaMs),
    )
}
