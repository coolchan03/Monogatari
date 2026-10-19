package org.koitharu.kotatsu.search.ui.multi.adapter

import org.koitharu.kotatsu.core.util.DisplayText
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.databinding.ItemMonogatariCatalogBinding
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.search.ui.multi.BookCatalogListModel

/** Shared search list item for public catalog entries. Opens catalog landing pages. */
fun bookCatalogAD(onOpen: (BookCatalogListModel) -> Unit) =
    adapterDelegateViewBinding<BookCatalogListModel, ListModel, ItemMonogatariCatalogBinding>(
        { inflater, parent -> ItemMonogatariCatalogBinding.inflate(inflater, parent, false) },
    ) {
        bind {
            // Catalog APIs sometimes return HTML entities or mis-decoded UTF-8 in titles and names.
            val title = DisplayText.clean(item.title)
            binding.monogatariCatalogItemTitle.text = title
            binding.monogatariCatalogItemCover.isVisible = !item.coverUrl.isNullOrBlank()
            binding.monogatariCatalogItemCover.setImageAsync(item.coverUrl)
            binding.monogatariCatalogItemSummary.text = listOf(
                item.author.takeIf { it.isNotBlank() }?.let(DisplayText::clean),
                item.catalog,
                if (item.isOfficialSiteSearch) "Opens provider website" else null,
                item.publicationYear?.let { "Published $it" },
                item.format,
                item.language,
                item.genres.firstOrNull(),
            ).filterNotNull().joinToString(" · ")
            binding.root.setOnClickListener { onOpen(item) }
            binding.root.contentDescription = "$title, ${binding.monogatariCatalogItemSummary.text}"
        }
    }
