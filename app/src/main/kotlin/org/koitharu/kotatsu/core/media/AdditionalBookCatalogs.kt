package org.koitharu.kotatsu.core.media

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI
import java.util.Locale

/** Pure metadata adapters for official public catalogs; no paid-content or DRM bypass. */
internal object AdditionalBookCatalogs {
    private fun officialLink(url: String, domain: String): String? =
        runCatching { URI(url) }.getOrNull()?.let {
            if (it.scheme.equals("https", true) &&
                it.host.equals(domain, true) &&
                it.userInfo == null && it.fragment == null &&
                (it.port == -1 || it.port == 443))
                url else null
        }

    fun fadedPage(data: JSONObject, query: String): List<PublicBookCatalog.Book> {
        val rows = data.optJSONArray("rows") ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until minOf(rows.length(), 80)).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("pid").takeIf { it.matches(Regex("[0-9]{4,12}")) }
                ?: return@mapNotNull null
            val title = row.optString("title").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (query.isNotBlank() && !title.contains(query, ignoreCase = true)) return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val authors = row.optJSONArray("authors")
            val author = authors?.optJSONObject(0)?.optString("realname").orEmpty()
            val cover = row.optString("cover").takeIf {
                it.matches(Regex("books/[0-9]+/[A-Za-z0-9._-]+"))
            }?.let { "https://www.fadedpage.com/" + it }
            PublicBookCatalog.Book(
                title, author, "Faded Page", "https://www.fadedpage.com/showbook.php?pid=" + id,
                coverUrl = cover,
                publicationYear = row.optInt("first_publication").takeIf { it in 1000..2100 },
                language = row.optString("lang").takeIf { it.isNotBlank() },
                format = "Canadian catalog — regional copyright laws vary",
            )
        }
    }

    private fun csvFields(line: String): List<String> {
        val parts = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { parts.add(cell.toString().trim()); cell.clear() }
                else -> cell.append(char)
            }
            i++
        }
        parts.add(cell.toString().trim())
        return parts
    }

    fun gutenbergAustralia(csv: String, query: String): List<PublicBookCatalog.Book> {
        val selected = LinkedHashMap<String, PublicBookCatalog.Book>()
        val needle = query.trim()
        for (line in csv.lineSequence().take(10_000)) {
            val cells = csvFields(line)
            if (cells.size < 6) continue
            val (directory, id, format, lastName, firstName) = cells.take(5)
            if (!directory.matches(Regex("ebooks[0-9]{2}")) ||
                !id.matches(Regex("[A-Za-z0-9-]{3,25}")) ||
                format !in setOf("txt", "html")) continue
            val title = cells.drop(5).joinToString(", ").trim().takeIf(String::isNotEmpty) ?: continue
            val author = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
            if (needle.isNotBlank() && !title.contains(needle, true) && !author.contains(needle, true)) continue
            val url = "https://www.gutenberg.net.au/" + directory + "/" + id +
                "." + (if (format == "html") "html" else "txt")
            val key = (title + "|" + author).lowercase(Locale.ROOT)
            if (key !in selected || format == "html") {
                selected[key] = PublicBookCatalog.Book(
                    title, author, "Project Gutenberg Australia", url,
                    format = "Read " + (if (format == "html") "online" else "plain text") +
                        " — check local copyright",
                )
            }
            if (selected.size >= 75 && needle.isBlank()) break
        }
        return selected.values.take(60)
    }

    fun baenFreeLibrary(html: String, query: String): List<PublicBookCatalog.Book> {
        val document = Jsoup.parse(html, "https://www.baen.com/")
        val seen = HashSet<String>()
        return document.select("li.product-item").mapNotNull { card ->
            val link = card.selectFirst("a.product-item-link") ?: return@mapNotNull null
            val title = link.text().trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
            if (query.isNotBlank() && !title.contains(query, ignoreCase = true)) return@mapNotNull null
            // Check the original publisher-supplied href before Jsoup resolves it.
            // URL resolution can normalize away fragments/credentials/ports that
            // must never be silently accepted as official book links.
            val raw = link.attr("href").trim()
            val original = runCatching { URI(raw) }.getOrNull() ?: return@mapNotNull null
            if ('#' in raw || '@' in raw ||
                original.rawFragment != null || original.rawUserInfo != null ||
                (original.port != -1 && original.port != 443))
                return@mapNotNull null
            val url = officialLink(link.absUrl("href"), "www.baen.com") ?: return@mapNotNull null
            if (!seen.add(url)) return@mapNotNull null
            val cover = card.selectFirst("img.product-image-photo")?.let {
                officialLink(it.absUrl("src"), "www.baen.com")
            }
            PublicBookCatalog.Book(title, "", "Baen Free Library", url,
                coverUrl = cover, format = "Free Library listing — open Baen")
        }.take(60)
    }

    fun globalGreySitemap(xml: String, query: String): List<PublicBookCatalog.Book> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val needle = query.trim()
        val seen = HashSet<String>()
        return document.select("loc").asSequence().mapNotNull { element ->
            val url = officialLink(element.text(), "www.globalgreyebooks.com")
                ?: return@mapNotNull null
            // The publisher also indexes titles under /online-books-2/.
            val path = runCatching { URI(url).path }.getOrNull().orEmpty()
            if (!path.contains("/book-page/") && !path.contains("/online-ebooks/") &&
                !Regex("^/online-books(?:-[0-9]+)?/").containsMatchIn(path))
                return@mapNotNull null
            if (!seen.add(url)) return@mapNotNull null
            val rawSlug = url.substringAfterLast('/').substringBefore(".html")
            val suffixless = rawSlug.removeSuffix("_complete-text")
                .removeSuffix("-complete-text").removeSuffix("-ebook")
            // Strip only a numeric ID prefix, never the first word of a title.
            val slug = if (suffixless.substringBefore('_').matches(Regex("[0-9]{4,10}")))
                suffixless.substringAfter('_') else suffixless
            val title = slug.replace('_', ' ').replace('-', ' ').trim()
                .split(' ').filter(String::isNotBlank)
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
                .takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (needle.isNotBlank() && !title.contains(needle, true)) return@mapNotNull null
            PublicBookCatalog.Book(title, "", "Global Grey Ebooks", url,
                format = "Read on official website")
        }.take(60).toList()
    }
}
