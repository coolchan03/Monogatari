package org.koitharu.kotatsu.core.qa

/** Step 62: safe query construction for future opt-in AniList discoveries. */
internal object DiscoveryQueryPolicy {
    fun findCopyQuery(title: String): String? =
        title.trim().replace(Regex("\\s+"), " ").take(180).takeIf { it.length >= 2 }

    fun relevantTags(tags: Collection<String>, followed: Set<String>): List<String> =
        tags.filter { tag -> followed.any { it.equals(tag, ignoreCase = true) } }.distinct()
}
