package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.list.ui.model.ListModel

internal data class CatalogNoticeListModel(
    val title: String,
    val description: String,
) : ListModel {
    override fun areItemsTheSame(other: ListModel): Boolean =
        other is CatalogNoticeListModel && other.title == title
}
