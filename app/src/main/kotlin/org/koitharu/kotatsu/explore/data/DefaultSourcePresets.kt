package org.koitharu.kotatsu.explore.data

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.filterByMediaType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/** One-time suggestions, not permanent locks; any preset can be edited or deleted. */
object DefaultSourcePresets {
    data class Suggestion(val title: String, val sources: Set<String>)

    private val blTags = Regex("(?:^|[^A-Za-z])(?:yaoi|danmei|bl)(?:$|[^A-Za-z])|boys.?love|shounen.?ai", RegexOption.IGNORE_CASE)

    fun plan(
        sources: List<MangaSource>,
        installedJs: List<JsSourceEntry>,
        scores: Map<String, Double> = emptyMap(),
    ): List<Suggestion> {
        val all = sources.distinctBy { it.name }
        val js = installedJs.associateBy { JsMangaSource(it.id).name }
        fun allowed(source: MangaSource): Boolean {
            val entry = js[source.name] ?: return true
            return entry.lang.equals("en", true) || entry.lang.equals("all", true)
        }
        val available = all.filter(::allowed)
        fun forType(type: MediaType) = available.filterByMediaType(type).map { it.name }.toSet()
        val manga = available.filterByMediaType(MediaType.MANGA)
            .filter { it !is MangaParserSource || it.locale.startsWith("en", ignoreCase = true) || it.locale == "all" }
            .sortedWith(compareByDescending<MangaSource> { scores[it.name] ?: 0.5 }.thenBy { it.name })
            .take(15).map { it.name }.toSet()
        val bl = available.filter { source ->
            source == BuiltinSource.AO3 ||
                source == BuiltinSource.SCRIBBLE_HUB ||
                source.name.contains("MANGADEX", ignoreCase = true) ||
                js[source.name]?.let { blTags.containsMatchIn(it.name + " " + it.notes) } == true
        }.map { it.name }.toSet()
        return listOf(
            Suggestion("BL & Yaoi", bl),
            Suggestion("Popular manga", manga),
            Suggestion("Books & fanfic", forType(MediaType.BOOK)),
            Suggestion("Anime & video", forType(MediaType.VIDEO)),
            Suggestion("Audio", forType(MediaType.AUDIO)),
            Suggestion("Multi-media", manga + forType(MediaType.BOOK) + forType(MediaType.VIDEO) + forType(MediaType.AUDIO)),
        ).filter { it.sources.isNotEmpty() }
    }
}
