package org.koitharu.kotatsu.core.media

/** A missing year is not an estimated date: keep unknowns after dated books. */
internal object BookCatalogSorter {
    enum class Order(val label: String) {
        RELEVANCE("Best match"), NEWEST("Newest first"), OLDEST("Oldest first")
    }

    fun sort(books: List<PublicBookCatalog.Book>, order: Order): List<PublicBookCatalog.Book> =
        when (order) {
            Order.RELEVANCE -> books
            Order.NEWEST -> books.sortedWith(
                compareBy<PublicBookCatalog.Book> { year(it) == null }
                    .thenByDescending { year(it) ?: Int.MIN_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            Order.OLDEST -> books.sortedWith(
                compareBy<PublicBookCatalog.Book> { year(it) == null }
                    .thenBy { year(it) ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        }

    private fun year(book: PublicBookCatalog.Book) =
        book.publicationYear?.takeIf { it in 1000..2100 }
}
