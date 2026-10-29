package org.koitharu.kotatsu.search.ui.suggestion.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.databinding.ItemSearchSuggestionSourceTipBinding
import org.koitharu.kotatsu.search.ui.suggestion.SearchSuggestionListener
import org.koitharu.kotatsu.search.ui.suggestion.model.SearchSuggestionItem

/** "Search all sources instead": the exit from screen-filter mode, shown while a query is typed. */
fun searchSuggestionSearchAllAD(
	listener: SearchSuggestionListener,
) = adapterDelegateViewBinding<SearchSuggestionItem.SearchAll, SearchSuggestionItem, ItemSearchSuggestionSourceTipBinding>(
	{ inflater, parent -> ItemSearchSuggestionSourceTipBinding.inflate(inflater, parent, false) },
) {

	binding.root.setOnClickListener {
		listener.onSearchAllSources(item.query)
	}

	bind {
		binding.textViewTitle.setText(R.string.search_all_sources_instead)
		binding.textViewSubtitle.text = item.query
		binding.imageViewCover.setImageAsync(R.drawable.ic_arrow_forward)
	}
}
