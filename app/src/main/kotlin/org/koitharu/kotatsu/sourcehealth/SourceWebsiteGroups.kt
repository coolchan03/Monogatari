package org.koitharu.kotatsu.sourcehealth

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.net.URI
import java.util.Locale

/**
 * Host-aware display/search grouping. Unknown parser hostnames stay separate rather than
 * guessing and hiding a possibly different site. Category remains part of the key.
 */
object SourceWebsiteGroups {
    fun host(url: String): String? {
        val raw = url.trim().takeIf { it.isNotEmpty() } ?: return null
        // Kotatsu parser domains are often bare hosts, whereas JS entries use full URLs.
        val parsed = runCatching { URI(if ("://" in raw) raw else "https://$raw") }.getOrNull()
            ?: return null
        if (parsed.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        var domain = parsed.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        while (domain.startsWith("www.") || domain.startsWith("m.")) {
            domain = domain.substringAfter('.')
        }
        return domain.takeIf { '.' in it }
    }

    fun key(source: MangaSource, parserDomain: (MangaParserSource) -> String? = { null }): String? {
        val resolved = source.unwrap()
        val address = when (resolved) {
            is JsMangaSource -> JsSourceRegistry.get(resolved.id)?.baseUrl
            is MangaParserSource -> runCatching { parserDomain(resolved) }.getOrNull()
            is BuiltinSource -> when (resolved) {
                BuiltinSource.AO3 -> "https://archiveofourown.org"
                BuiltinSource.GUTENBERG -> "https://gutenberg.org"
                BuiltinSource.OPEN_LIBRARY -> "https://openlibrary.org"
                BuiltinSource.ROYAL_ROAD -> "https://royalroad.com"
                BuiltinSource.SCRIBBLE_HUB -> "https://scribblehub.com"
                BuiltinSource.YOUTUBE -> "https://youtube.com"
                BuiltinSource.SOUNDCLOUD -> "https://soundcloud.com"
                BuiltinSource.DAILYMOTION -> "https://dailymotion.com"
                BuiltinSource.WIKISOURCE -> "https://wikisource.org"
                BuiltinSource.ODYSEE -> "https://odysee.com"
                // Jellyfin is the user's own server: no canonical host.
                BuiltinSource.LIBRIVOX -> "https://librivox.org"
                BuiltinSource.IA_BOOKS, BuiltinSource.IA_VIDEO,
                BuiltinSource.IA_AUDIO, BuiltinSource.IA_COMICS -> "https://archive.org"
                // PeerTube is federated: no single canonical host.
                else -> null
            }
            else -> null
        } ?: return null
        val canonical = host(address) ?: return null
        return canonical + "|" + source.mediaType().name
    }

    fun groups(
        sources: List<MangaSource>,
        parserDomain: (MangaParserSource) -> String? = { null },
    ): List<List<MangaSource>> = orderedGroups(sources) { key(it, parserDomain) }

    fun <T> orderedGroups(items: List<T>, key: (T) -> String?): List<List<T>> {
        val groups = ArrayList<MutableList<T>>()
        val indexed = HashMap<String, Int>()
        for (item in items) {
            val hostKey = key(item)
            if (hostKey == null) {
                groups.add(mutableListOf(item))
            } else {
                val existing = indexed[hostKey]
                if (existing == null) {
                    indexed[hostKey] = groups.size
                    groups.add(mutableListOf(item))
                } else {
                    groups[existing].add(item)
                }
            }
        }
        return groups
    }
}
