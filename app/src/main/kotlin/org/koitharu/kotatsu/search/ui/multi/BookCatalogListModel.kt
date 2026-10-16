package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.list.ui.model.ListModel

/** Clickable public-library result in the same RecyclerView as the manga/novel search results. */
data class BookCatalogListModel(
    val title: String,
    val author: String,
    val catalog: String,
    val url: String,
    /** Structured publication year supplied by a catalog, never inferred from its title. */
    val publicationYear: Int? = null,
    val coverUrl: String? = null,
    val format: String? = null,
    val language: String? = null,
    val attribution: String? = null,
    /** This row opens provider search; it is not a claim that a matching title was found. */
    val isOfficialSiteSearch: Boolean = false,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean =
        other is BookCatalogListModel && other.url == url
}
