package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.list.ui.model.MangaListModel

/** Dates are best-effort estimates, not verified publication years. */
enum class PublicationYearOrder {
    RELEVANCE, OLDEST_FIRST, NEWEST_FIRST,
    /** Oldest/newest across every source at once, with the same title from several sources shown once. */
    OLDEST_FIRST_GLOBAL, NEWEST_FIRST_GLOBAL;

    val isGlobal: Boolean get() = this == OLDEST_FIRST_GLOBAL || this == NEWEST_FIRST_GLOBAL
    val isNewestFirst: Boolean get() = this == NEWEST_FIRST || this == NEWEST_FIRST_GLOBAL
}

/** Client-side check that complements the source's own year filter (pure, unit-tested). */
internal object YearFilter {

    /** Unknown years are kept only when [includeUnknown]; known (estimated) years must lie within the range. */
    fun matches(year: Int?, from: Int?, to: Int?, includeUnknown: Boolean): Boolean {
        if (year == null) return includeUnknown
        return (from == null || year >= from) && (to == null || year <= to)
    }

    /** True when the filter would change anything, so unfiltered searches skip the work. */
    fun isActive(from: Int?, to: Int?, includeUnknown: Boolean) = from != null || to != null || !includeUnknown
}

internal object PublicationYearSorter {
    private val yearPattern = Regex("(18|19|20)[0-9]{2}")
    private val metadataLabels = listOf(
        "originally published", "first published", "publication year",
        "publication date", "published", "release year", "release date",
        "released", "serialized", "year:",
    )

    fun estimateYear(title: String, alternate: String?, description: String?): Int? {
        val synopsis = description.orEmpty().take(1200)
        val lower = synopsis.lowercase()
        for (label in metadataLabels) {
            val index = lower.indexOf(label)
            if (index < 0) continue
            val start = index + label.length
            val window = synopsis.substring(start, (start + 35).coerceAtMost(synopsis.length))
            val year = yearPattern.find(window) ?: continue
            if (year.range.first <= 16) return year.value.toIntOrNull()
        }
        return listOf(title, alternate.orEmpty()).firstNotNullOfOrNull { candidate ->
            val match = yearPattern.findAll(candidate).lastOrNull() ?: return@firstNotNullOfOrNull null
            val before = candidate.getOrNull(match.range.first - 1)
            val after = candidate.getOrNull(match.range.last + 1)
            if (before in listOf('(', '[', ' ') && after in listOf(')', ']', null)) {
                match.value.toIntOrNull()
            } else null
        }
    }

    /**
     * One list for all sources, ordered by [order] (years unknown last). The same title found by several sources is
     * kept once, from the earliest list: callers pass lists in preference order (best source first).
     */
    fun <T> sortedGlobal(lists: List<List<T>>, order: PublicationYearOrder, titleOf: (T) -> String, yearOf: (T) -> Int?): List<T> {
        val seen = HashSet<String>()
        val unique = lists.flatten().filter { item ->
            val key = titleKey(titleOf(item))
            key.isEmpty() || seen.add(key)
        }
        val years = unique.map { yearOf(it) }
        return unique.indices.sortedWith(
            compareBy<Int> { years[it] == null }
                .thenBy { index ->
                    val year = years[index] ?: 0
                    if (order.isNewestFirst) -year else year
                },
        ).map { unique[it] }
    }

    /** Title with case, punctuation, bracketed tags such as "(2004)" or "[Official]" and spacing removed. */
    fun titleKey(title: String): String = title.lowercase()
        .replace(Regex("[(\\[{][^)\\]}]*[)\\]}]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    fun sorted(items: List<MangaListModel>, order: PublicationYearOrder): List<MangaListModel> {
        if (order == PublicationYearOrder.RELEVANCE) return items
        val years = items.associateWith {
            estimateYear(it.manga.title, it.manga.altTitle, it.manga.description)
        }
        return items.sortedWith(
            compareBy<MangaListModel> { years[it] == null }
                .thenBy {
                    val year = years[it] ?: 0
                    if (order.isNewestFirst) -year else year
                },
        )
    }
}
