package org.koitharu.kotatsu.builtin

import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

/** Pure mapping of Gutendex JSON; independently unit-testable. */
internal object GutenbergMapping {
    fun mapBooks(json: JSONObject): List<Manga> {
        val source = BuiltinSource.GUTENBERG
        val items = json.optJSONArray("results") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = "/ebooks/$id"
            val authors = item.optJSONArray("authors")
            val subjects = item.optJSONArray("subjects")
            Manga(
                id = uid(link),
                title = title,
                altTitles = emptySet(),
                url = link,
                publicUrl = "https://www.gutenberg.org$link",
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.SAFE,
                coverUrl = item.optJSONObject("formats")?.optString("image/jpeg")?.takeIf { it.startsWith("https://") },
                tags = (0 until (subjects?.length() ?: 0)).mapNotNull { i ->
                    subjects?.optString(i)?.takeIf { it.isNotBlank() }?.let {
                        MangaTag(title = it, key = it, source = source)
                    }
                }.toSet(),
                state = null,
                authors = (0 until (authors?.length() ?: 0)).mapNotNull { i ->
                    authors?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                }.toSet(),
                largeCoverUrl = null,
                description = null,
                chapters = null,
                source = source,
            )
        }
    }


    private fun uid(url: String): Long {
        var hash = 1125899906842597L
        BuiltinSource.GUTENBERG.name.forEach { hash = 31 * hash + it.code }
        url.forEach { hash = 31 * hash + it.code }
        return hash
    }
}
