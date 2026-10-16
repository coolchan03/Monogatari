package org.koitharu.kotatsu.core.media

import org.json.JSONObject
import java.net.URI

/** Parses the provider's documented public catalog response; no login, captcha or scraping. */
internal object StoryWeaverCatalog {
    private val slugPattern = Regex("[0-9]{1,12}-[a-z0-9-]{2,140}")

    fun officialStoryUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "storyweaver.org.in" &&
            uri.port == -1 && uri.userInfo == null &&
            uri.path.removePrefix("/en/stories/").let { slugPattern.matches(it) } &&
            uri.path.startsWith("/en/stories/")
    }.getOrDefault(false)

    fun officialHost(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "storyweaver.org.in" &&
            uri.userInfo == null && uri.port == -1
    }.getOrDefault(false)

    fun parse(response: JSONObject): List<PublicBookCatalog.Book> {
        if (!response.optBoolean("ok")) return emptyList()
        val items = response.optJSONArray("data") ?: return emptyList()
        return (0 until minOf(items.length(), 40)).mapNotNull { i ->
            val book = items.optJSONObject(i) ?: return@mapNotNull null
            val slug = book.optString("slug").takeIf { slugPattern.matches(it) }
                ?: return@mapNotNull null
            val title = book.optString("title").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val authors = book.optJSONArray("authors")
            val writers = (0 until (authors?.length() ?: 0)).mapNotNull {
                authors?.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
            }
            val illustrators = book.optJSONArray("illustrators")
            val artists = (0 until (illustrators?.length() ?: 0)).mapNotNull {
                illustrators?.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank)
            }
            val publisher = book.optJSONObject("publisher")?.optString("name").orEmpty()
            val covers = book.optJSONObject("coverImage")?.optJSONArray("sizes")
            val cover = (0 until (covers?.length() ?: 0)).mapNotNull { idx ->
                covers?.optJSONObject(idx)?.optString("url")?.takeIf {
                    it.startsWith("https://storage.googleapis.com/static.storyweaver.org.in/")
                }
            }.lastOrNull()
            val credit = buildList {
                if (writers.isNotEmpty()) add("Written by " + writers.joinToString(", "))
                if (artists.isNotEmpty()) add("Illustrated by " + artists.joinToString(", "))
                if (publisher.isNotBlank()) add("Published by $publisher")
                // License may differ per translated edition; never invent blanket rights for a catalog entry.
                add("Check the story’s license and attribution on StoryWeaver")
            }.joinToString(" · ")
            PublicBookCatalog.Book(
                title = title,
                author = writers.joinToString(", "),
                catalog = "StoryWeaver",
                url = "https://storyweaver.org.in/en/stories/$slug",
                coverUrl = cover,
                language = book.optString("language").takeIf(String::isNotBlank),
                format = "Illustrated online story",
                availability = "Free licensed reading; online connection required",
                attribution = credit,
            )
        }.distinctBy { it.url }
    }
}
