package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.list.ui.model.ListModel

/** Clickable public-library result in the same RecyclerView as the manga/novel search results. */
data class BookCatalogListModel(
    val title: String,
    val author: String,
    val catalog: String,
    val url: String,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean =
        other is BookCatalogListModel && other.url == url
}
