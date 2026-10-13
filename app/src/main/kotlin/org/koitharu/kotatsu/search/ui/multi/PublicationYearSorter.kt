package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.list.ui.model.MangaListModel

/** Dates are best-effort estimates, not verified publication years. */
enum class PublicationYearOrder { RELEVANCE, OLDEST_FIRST, NEWEST_FIRST }

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

    fun sorted(items: List<MangaListModel>, order: PublicationYearOrder): List<MangaListModel> {
        if (order == PublicationYearOrder.RELEVANCE) return items
        val years = items.associateWith {
            estimateYear(it.manga.title, it.manga.altTitle, it.manga.description)
        }
        return items.sortedWith(
            compareBy<MangaListModel> { years[it] == null }
                .thenBy {
                    val year = years[it] ?: 0
                    if (order == PublicationYearOrder.NEWEST_FIRST) -year else year
                },
        )
    }
}
