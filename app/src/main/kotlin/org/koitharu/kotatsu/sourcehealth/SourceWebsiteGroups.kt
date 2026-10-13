package org.koitharu.kotatsu.sourcehealth

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.jsext.repo.JsSourceRegistry
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.net.URI
import java.util.Locale

/**
 * Host-aware display/search grouping. Unknown parser hostnames stay separate rather than
 * guessing and hiding a possibly different site. Category remains part of the key.
 */
object SourceWebsiteGroups {
    fun host(url: String): String? {
        val domain = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?.trimEnd('.') ?: return null
        return domain.removePrefix("www.").removePrefix("m.").takeIf { '.' in it }
    }

    fun key(source: MangaSource): String? {
        val resolved = source.unwrap()
        val address = when (resolved) {
            is JsMangaSource -> JsSourceRegistry.get(resolved.id)?.baseUrl
            is BuiltinSource -> when (resolved) {
                BuiltinSource.AO3 -> "https://archiveofourown.org"
                BuiltinSource.GUTENBERG -> "https://gutenberg.org"
                BuiltinSource.OPEN_LIBRARY -> "https://openlibrary.org"
                BuiltinSource.ROYAL_ROAD -> "https://royalroad.com"
                BuiltinSource.SCRIBBLE_HUB -> "https://scribblehub.com"
                BuiltinSource.YOUTUBE -> "https://youtube.com"
                BuiltinSource.SOUNDCLOUD -> "https://soundcloud.com"
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

    fun groups(sources: List<MangaSource>): List<List<MangaSource>> =
        orderedGroups(sources, ::key)

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
