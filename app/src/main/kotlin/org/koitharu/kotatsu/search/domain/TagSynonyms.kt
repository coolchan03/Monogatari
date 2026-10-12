package org.koitharu.kotatsu.search.domain

/** Shared genre equivalences for tag lookup and search relevance. */
object TagSynonyms {
    private val groups: List<Set<String>> = listOf(
        setOf("yaoi", "boys' love", "boys love", "boy's love", "bl", "shounen ai",
            "shonen ai", "shounen-ai", "shonen-ai", "danmei", "m/m", "gay"),
        setOf("yuri", "girls' love", "girls love", "gl", "shoujo ai", "shojo ai",
            "shoujo-ai", "f/f"),
        setOf("smut", "explicit", "mature"),
        setOf("isekai", "another world", "reincarnation"),
        setOf("magical girl", "magical girls", "mahou shoujo", "mahou shojo"),
        setOf("slice of life", "slice-of-life", "sol"),
        setOf("omegaverse", "abo", "alpha/beta/omega"),
    )

    private fun normalize(value: String) = value.lowercase().filter { it.isLetterOrDigit() }

    fun expand(query: String): Set<String> {
        val normalized = normalize(query)
        return groups.firstOrNull { group -> group.any { normalize(it) == normalized } }
            ?: setOf(query.trim().lowercase())
    }

    fun isHidden(tags: Iterable<String>, hidden: Set<String>): Boolean =
        tags.any { title -> hidden.any { matches(it, title) } }

    fun matches(query: String, title: String): Boolean {
        val normalizedTitle = normalize(title)
        return expand(query).any { normalize(it) == normalizedTitle }
    }
}
