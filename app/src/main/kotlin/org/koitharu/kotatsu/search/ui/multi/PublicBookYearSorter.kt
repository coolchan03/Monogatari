package org.koitharu.kotatsu.search.ui.multi

/**
 * Public-book dates come from source metadata, not title-pattern guesses.
 * Unknown years stay visible unless the user explicitly excludes them.
 */
internal object PublicBookYearSorter {
    fun display(
        books: List<BookCatalogListModel>,
        sort: PublicationYearOrder,
        from: Int?,
        to: Int?,
        includeUnknown: Boolean,
    ): List<BookCatalogListModel> {
        val filtered = if (YearFilter.isActive(from, to, includeUnknown)) {
            books.filter { YearFilter.matches(it.publicationYear, from, to, includeUnknown) }
        } else books

        if (sort == PublicationYearOrder.RELEVANCE) return filtered

        // Retain the original search relevance for equal and unknown dates.
        return filtered.withIndex().sortedWith(
            compareBy<IndexedValue<BookCatalogListModel>> {
                it.value.publicationYear == null
            }.thenBy {
                val year = it.value.publicationYear ?: 0
                if (sort.isNewestFirst) -year else year
            }.thenBy { it.index },
        ).map { it.value }
    }
}
