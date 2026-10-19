package org.koitharu.kotatsu.search.ui.multi.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.databinding.ItemMonogatariCatalogBinding
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.search.ui.multi.CatalogNoticeListModel

fun catalogNoticeAD() =
    adapterDelegateViewBinding<CatalogNoticeListModel, ListModel, ItemMonogatariCatalogBinding>(
        { inflater, parent -> ItemMonogatariCatalogBinding.inflate(inflater, parent, false) },
    ) {
        bind {
            binding.monogatariCatalogItemTitle.text = item.title
            binding.monogatariCatalogItemSummary.text = item.description
            binding.monogatariCatalogItemCover.isVisible = false
            binding.monogatariCatalogItemAction.isVisible = false
            binding.root.isClickable = false
            binding.root.isFocusable = true
            binding.root.contentDescription = "${item.title}. ${item.description}"
        }
    }
