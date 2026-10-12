package org.koitharu.kotatsu.builtin

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import java.net.URLEncoder

/** Maps the Internet Archive search response without source-specific data loss. */
internal object ArchiveMapping {
    fun mapResults(source: BuiltinSource, json: JSONObject): List<Manga> {
        val docs = json.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { index ->
            val item = docs.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = "/details/$id"
            Manga(
                id = uid(source, path),
                title = title,
                altTitles = emptySet(),
                url = path,
                publicUrl = "https://archive.org/details/${encode(id)}",
                rating = RATING_UNKNOWN,
                contentRating = null,
                coverUrl = "https://archive.org/services/img/${encode(id)}",
                tags = strings(item, "subject").map { MangaTag(it, it, source) }.toSet(),
                state = null,
                authors = strings(item, "creator").toSet(),
                largeCoverUrl = null,
                description = null,
                chapters = null,
                source = source,
            )
        }
    }

    private fun strings(json: JSONObject, key: String): List<String> = when (val value = json.opt(key)) {
        is String -> listOf(value)
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).takeIf(String::isNotBlank) }
        else -> emptyList()
    }

    fun uid(source: BuiltinSource, url: String): Long {
        var hash = 1125899906842597L
        source.name.forEach { hash = 31 * hash + it.code }
        url.forEach { hash = 31 * hash + it.code }
        return hash
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
