package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.parsers.model.MangaChapter

/** Reader-independent contract for continuous text and video content. */
interface ContinuousMediaProvider {
    suspend fun getChapterHtml(title: String, chapter: MangaChapter): String
    suspend fun getStreams(chapter: MangaChapter): List<MediaStream>
}

data class MediaStream(
    val url: String,
    val quality: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<MediaSubtitle> = emptyList(),
)

data class MediaSubtitle(val url: String, val label: String? = null)
