package org.koitharu.kotatsu.core.media

/** Merge only a confident match of title + credited author; never lose provider links. */
internal object BookCatalogMerger {
    data class Group(val entries: List<PublicBookCatalog.Book>) {
        val primary: PublicBookCatalog.Book
            get() = entries.maxByOrNull {
                (if (it.downloadUrl != null) 4 else 0) +
                    (if (it.coverUrl != null) 2 else 0) +
                    (if (it.publicationYear != null) 1 else 0)
            } ?: entries.first()
        val providers: String get() = entries.map { it.catalog }.distinct().joinToString(", ")
    }

    fun merge(books: List<PublicBookCatalog.Book>): List<Group> =
        books.filter { it.title.isNotBlank() }.groupBy { book ->
            val author = normalize(book.author)
            val title = normalize(book.title)
            if (author.isBlank()) "$title|unknown|${book.catalog.lowercase()}"
            else "$title|$author"
        }.values.map(::Group)

    fun sort(groups: List<Group>, order: BookCatalogSorter.Order): List<Group> {
        fun year(group: Group) = group.entries.mapNotNull {
            it.publicationYear?.takeIf { value -> value in 1000..2100 }
        }.minOrNull()
        return when (order) {
            BookCatalogSorter.Order.RELEVANCE -> groups
            BookCatalogSorter.Order.NEWEST -> groups.sortedWith(
                compareBy<Group> { year(it) == null }
                    .thenByDescending { year(it) ?: Int.MIN_VALUE }
                    .thenBy { it.primary.title }
            )
            BookCatalogSorter.Order.OLDEST -> groups.sortedWith(
                compareBy<Group> { year(it) == null }
                    .thenBy { year(it) ?: Int.MAX_VALUE }
                    .thenBy { it.primary.title }
            )
        }
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")
}
