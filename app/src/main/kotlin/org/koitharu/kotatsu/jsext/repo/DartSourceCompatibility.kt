package org.koitharu.kotatsu.jsext.repo

import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.toMediaType
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.net.URI

/**
 * Phase one of Dart compatibility: identify an already-supported parser/site.
 * This NEVER executes Dart, translates a parser, or claims a candidate passes health checks.
 */
object DartSourceCompatibility {
    data class Alternative(
        val parser: MangaParserSource? = null,
        val js: JsSourceEntry? = null,
    ) {
        val description: String get() = when {
            parser != null -> "Kotatsu built-in: ${parser.name}"
            js != null -> "JavaScript: ${js.name} (${js.lang})"
            else -> "Unavailable"
        }
    }

    fun candidate(
        dart: JsSourceEntry,
        runnableJs: List<JsSourceEntry>,
        builtins: List<MangaParserSource> = MangaParserSource.entries.toList(),
    ): Alternative? {
        if (dart.language != SourceCodeLanguage.DART) return null
        val media = when (dart.itemType) {
            JsItemType.MANGA -> MediaType.MANGA
            JsItemType.NOVEL -> MediaType.BOOK
            JsItemType.ANIME -> MediaType.VIDEO
        }
        val key = nameKey(dart.name)
        if (key.isEmpty()) return null
        val builtin = builtins.firstOrNull { source ->
            source.contentType.toMediaType() == media &&
                (nameKey(source.name) == key || nameKey(withoutLocaleSuffix(source.name)) == key)
        }
        if (builtin != null) return Alternative(parser = builtin)
        val host = hostOf(dart.baseUrl)
        val js = runnableJs.firstOrNull { source ->
            source.isRunnable && source.itemType == dart.itemType &&
                (nameKey(source.name) == key ||
                    (host != null && host == hostOf(source.baseUrl)))
        }
        return js?.let { Alternative(js = it) }
    }

    private fun nameKey(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun withoutLocaleSuffix(name: String): String =
        name.replace(Regex("_(EN|ES|ESLA|FR|DE|IT|PT|PTBR|RU|JA|ZH|KO|ID|VI|TH|AR|TR)$"), "")

    private fun hostOf(value: String): String? = runCatching {
        URI(value).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()
}
