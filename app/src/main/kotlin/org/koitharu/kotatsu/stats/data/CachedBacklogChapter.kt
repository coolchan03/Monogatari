package org.koitharu.kotatsu.stats.data

data class CachedBacklogChapter(
    val mangaId: Long,
    val source: String,
    val chapterId: Long,
    val branch: String?,
    val chapterIndex: Int,
    val pages: Int,
    val words: Int,
    val durationMs: Long,
    val currentChapterId: Long?,
    val progress: Float?,
)
