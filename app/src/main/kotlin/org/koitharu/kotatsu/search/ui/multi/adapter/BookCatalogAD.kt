package org.koitharu.kotatsu.search.ui.multi.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.databinding.ItemMonogatariCatalogBinding
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.search.ui.multi.BookCatalogListModel

/** Shared search list item for public catalog entries. Opens catalog landing pages. */
fun bookCatalogAD(onOpen: (String) -> Unit) =
    adapterDelegateViewBinding<BookCatalogListModel, ListModel, ItemMonogatariCatalogBinding>(
        { inflater, parent -> ItemMonogatariCatalogBinding.inflate(inflater, parent, false) },
    ) {
        bind {
            binding.monogatariCatalogItemTitle.text = item.title
            binding.monogatariCatalogItemSummary.text = listOf(
                item.author.takeIf { it.isNotBlank() },
                item.catalog,
            ).filterNotNull().joinToString(" · ")
            binding.root.setOnClickListener { onOpen(item.url) }
            binding.root.contentDescription = "${item.title}, ${binding.monogatariCatalogItemSummary.text}"
        }
    }
