package org.koitharu.kotatsu.search.ui.multi

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative cross-source manga identity. We never merge on a title alone:
 * an author match or a distinctive identical alternate title is required.
 * Provider failures and differently credited editions remain separate.
 */
internal object MangaDuplicatePolicy {
    data class Candidate(
        val id: Long,
        val source: String,
        val title: String,
        val altTitle: String?,
        val authors: Set<String>,
        val mediaKind: String,
    )
    data class Group(val key: String, val copies: List<Candidate>)

    private fun norm(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    fun identity(item: Candidate): String? {
        val title = norm(item.title)
        if (title.length < 2) return null
        val authors = item.authors.map(::norm).filter { it.isNotBlank() }.distinct().sorted()
        val alt = item.altTitle?.let(::norm).orEmpty()
        val proof = when {
            authors.isNotEmpty() -> "author:" + authors.joinToString("|")
            alt.length >= 2 && alt != title -> "alternate:$alt"
            else -> return null
        }
        return item.mediaKind + "|" + title + "|" + proof
    }

    fun groups(candidates: List<Candidate>): List<Group> =
        candidates.mapNotNull { item -> identity(item)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, copies) ->
                val distinctSources = copies.distinctBy { it.source }
                if (distinctSources.size < 2) null else Group(key, distinctSources)
            }

    fun preferred(group: Group, source: String?): Candidate =
        group.copies.firstOrNull { it.source == source } ?: group.copies.first()
}
