package org.koitharu.kotatsu.builtin

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

/** Maps Open Library's public/borrowable/unavailable works without guessing access rights. */
internal object OpenLibraryMapping {
    fun map(json: JSONObject, readableNowOnly: Boolean = true): List<Manga> {
        val docs = json.optJSONArray("docs") ?: json.optJSONArray("works") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val item = docs.optJSONObject(i) ?: return@mapNotNull null
            val key = item.optString("key").takeIf { it.startsWith("/works/") } ?: return@mapNotNull null
            val access = item.optString("ebook_access")
            if (readableNowOnly && access != "public") return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val author = item.optJSONArray("author_name")
            val subjects = item.optJSONArray("subject")
            val cover = item.optInt("cover_i").takeIf { it > 0 }
            val source = BuiltinSource.OPEN_LIBRARY
            Manga(
                id = ArchiveMapping.uid(source, key),
                title = title,
                altTitles = emptySet(),
                url = key,
                publicUrl = "https://openlibrary.org$key",
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.SAFE,
                coverUrl = cover?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
                tags = subjects.strings().map { MangaTag(it, it, source) }.toSet(),
                state = null,
                authors = author.strings().toSet(),
                largeCoverUrl = null,
                description = when (access) {
                    "public" -> "Read online or download from an available library copy"
                    "borrowable", "printdisabled" -> "Borrow on Open Library"
                    else -> "Not available to read"
                },
                chapters = null,
                source = source,
            )
        }
    }

    private fun JSONArray?.strings(): List<String> = this?.let { a ->
        (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()
}
