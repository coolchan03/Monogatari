package org.koitharu.kotatsu.search.ui.multi

import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.getLocale
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.image.ui.CoverImageView
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import kotlin.math.roundToInt

/** What the preview pane asks of the screen; the pane itself never navigates or touches repositories. */
internal interface SearchPreviewCallbacks {
	fun onPrimary(manga: Manga)
	fun onDetails(manga: Manga)
	fun onLibrary(manga: Manga)
	fun onDownload(manga: Manga)
	fun onChooseSource(manga: Manga)
	fun onBrowser(manga: Manga)
	fun onShare(manga: Manga)
	fun onRetry()
	fun onBookOpen(book: BookCatalogListModel)
	fun onCollapse()
	fun hasAlternatives(manga: Manga): Boolean
}

/**
 * The selection preview beside or below the results. It renders a [SearchPreviewState] (cover, titles, creator,
 * source, status, rating, progress, tags, description) and shows only the actions valid for the selected item.
 * Details arrive lazily, so everything it shows is either what the search returned or what the source reported.
 */
internal class SearchTabletPreview private constructor(
	root: View,
	private val callbacks: SearchPreviewCallbacks,
) {
	private val context = root.context
	private val hint = root.findViewById<TextView>(R.id.search_preview_hint)
	private val collapse = root.findViewById<MaterialButton>(R.id.search_preview_collapse)
	private val header = root.findViewById<View>(R.id.search_preview_header)
	private val cover = root.findViewById<CoverImageView>(R.id.search_preview_cover)
	private val title = root.findViewById<TextView>(R.id.search_preview_title)
	private val altTitle = root.findViewById<TextView>(R.id.search_preview_alt_title)
	private val author = root.findViewById<TextView>(R.id.search_preview_author)
	private val source = root.findViewById<TextView>(R.id.search_preview_source)
	private val facts = root.findViewById<TextView>(R.id.search_preview_facts)
	private val progress = root.findViewById<TextView>(R.id.search_preview_progress)
	private val loading = root.findViewById<LinearProgressIndicator>(R.id.search_preview_loading)
	private val tags = root.findViewById<ChipGroup>(R.id.search_preview_tags)
	private val description = root.findViewById<TextView>(R.id.search_preview_description)
	private val more = root.findViewById<MaterialButton>(R.id.search_preview_more)
	private val notice = root.findViewById<TextView>(R.id.search_preview_notice)
	private val primary = root.findViewById<MaterialButton>(R.id.search_preview_read)
	private val secondaryRow = root.findViewById<View>(R.id.search_preview_secondary_row)
	private val details = root.findViewById<MaterialButton>(R.id.search_preview_open)
	private val library = root.findViewById<MaterialButton>(R.id.search_preview_library)
	private val moreActions = root.findViewById<ChipGroup>(R.id.search_preview_more_actions)
	private val download = root.findViewById<Chip>(R.id.search_preview_download)
	private val chooseSource = root.findViewById<Chip>(R.id.search_preview_choose_source)
	private val browser = root.findViewById<Chip>(R.id.search_preview_browser)
	private val share = root.findViewById<Chip>(R.id.search_preview_share)

	private var state: SearchPreviewState? = null
	private var selectedBook: BookCatalogListModel? = null
	private var renderedId: Long? = null
	private var renderedTags: List<String> = emptyList()
	private var expanded = false

	/** Stable identity for restoring a manga preview after process death. */
	val selectedId: Long? get() = state?.manga?.id
	/** Stable identity for restoring a public-book preview after window resizing or rotation. */
	val selectedBookUrl: String? get() = selectedBook?.url
	val hasSelection: Boolean get() = state != null || selectedBook != null

	init {
		collapse.setOnClickListener { callbacks.onCollapse() }
		primary.setOnClickListener { state?.manga?.let(callbacks::onPrimary) }
		details.setOnClickListener {
			val manga = state?.manga
			if (manga != null) callbacks.onDetails(manga) else selectedBook?.let(callbacks::onBookOpen)
		}
		library.setOnClickListener { state?.manga?.let(callbacks::onLibrary) }
		download.setOnClickListener { state?.manga?.let(callbacks::onDownload) }
		chooseSource.setOnClickListener { state?.manga?.let(callbacks::onChooseSource) }
		browser.setOnClickListener { state?.manga?.let(callbacks::onBrowser) }
		share.setOnClickListener { state?.manga?.let(callbacks::onShare) }
		notice.setOnClickListener { if (state?.detailsFailed == true) callbacks.onRetry() }
		more.setOnClickListener {
			expanded = !expanded
			applyDescriptionExpansion()
		}
	}

	/** Renders the selected manga/video/audio title; null clears the selection. */
	fun render(newState: SearchPreviewState?) {
		state = newState
		if (newState == null) {
			if (selectedBook == null) showEmpty()
			return
		}
		selectedBook = null
		val manga = newState.manga
		val isNewSelection = renderedId != manga.id
		if (isNewSelection) {
			expanded = false
			renderedId = manga.id
		}
		hint.isVisible = false
		header.isVisible = true
		cover.setImageAsync(manga.largeCoverUrl.ifNullOrEmpty { manga.coverUrl }, manga)
		title.text = manga.title
		altTitle.bindText(manga.altTitles.firstOrNull())
		author.bindText(manga.authors.joinToString(", ").takeIf { it.isNotBlank() })
		source.text = manga.source.getTitle(context)
		facts.bindText(
			SearchPreviewActions.joinFacts(
				listOf(
					manga.state?.let { context.getString(it.titleResId) },
					manga.source.getLocale()?.getDisplayLanguage(context.resources.configuration.locales[0])
						?.takeIf { it.isNotBlank() },
					if (manga.hasRating) context.getString(R.string.search_preview_rating, manga.rating * 5f) else null,
					newState.totalChapters.takeIf { it > 0 }
						?.let { context.getString(R.string.search_preview_chapters, it) },
				),
			).takeIf { it.isNotEmpty() },
		)
		progress.bindText(progressText(newState))
		loading.isVisible = newState.loading
		bindTags(manga.tags.map { it.title })
		bindDescription(manga, newState)
		notice.isVisible = newState.loading || newState.detailsFailed
		notice.setText(
			if (newState.loading) R.string.search_preview_loading_details else R.string.search_preview_details_unavailable,
		)
		bindActions(newState)
		if (isNewSelection) title.announceForAccessibility(context.getString(R.string.search_preview_selected, manga.title))
	}

	/** Catalog results have a listing and attribution, not manga details, so their preview stays simple. */
	fun show(item: BookCatalogListModel) {
		state = null
		selectedBook = item
		renderedId = null
		hint.isVisible = false
		header.isVisible = true
		cover.setImageAsync(item.coverUrl?.takeIf { it.isNotBlank() })
		title.text = item.title
		altTitle.isVisible = false
		author.bindText(item.author.takeIf { it.isNotBlank() })
		source.text = item.catalog
		facts.bindText(
			SearchPreviewActions.joinFacts(
				listOf(
					if (item.isOfficialSiteSearch) context.getString(R.string.search_official_site_only) else null,
					item.format, item.language,
					item.genres.take(3).joinToString(" · "),
				),
			).takeIf { it.isNotEmpty() },
		)
		progress.isVisible = false
		loading.isVisible = false
		tags.isVisible = false
		description.bindText(item.attribution)
		more.isVisible = false
		notice.isVisible = false
		primary.isVisible = false
		secondaryRow.isVisible = true
		library.isVisible = false
		details.isVisible = true
		details.setText(R.string.search_book_options)
		moreActions.isVisible = false
		title.announceForAccessibility(context.getString(R.string.search_preview_selected, item.title))
	}

	fun clear() {
		state = null
		selectedBook = null
		renderedId = null
		showEmpty()
	}

	private fun showEmpty() {
		hint.isVisible = true
		for (v in listOf(header, tags, description, more, notice, primary, secondaryRow, moreActions)) v.isVisible = false
	}

	private fun progressText(state: SearchPreviewState): String? {
		val value = state.progress ?: return null
		val percent = (value * 100).roundToInt().coerceIn(0, 100)
		return if (state.currentChapter >= 0 && state.totalChapters > 0) {
			context.getString(R.string.search_preview_progress_chapter, state.currentChapter + 1, state.totalChapters, percent)
		} else context.getString(R.string.search_preview_progress_percent, percent)
	}

	private fun bindTags(newTags: List<String>) {
		val limited = newTags.filter { it.isNotBlank() }.distinct().take(MAX_TAGS)
		tags.isVisible = limited.isNotEmpty()
		if (limited == renderedTags) return
		renderedTags = limited
		tags.removeAllViews()
		for (tag in limited) {
			tags.addView(Chip(context).apply {
				text = tag
				isClickable = false
				isCheckable = false
				isFocusable = false
			})
		}
	}

	private fun bindDescription(manga: Manga, state: SearchPreviewState) {
		val raw = manga.description?.takeIf { it.isNotBlank() }
		if (raw == null) {
			// Do not claim "no description" while the real details may still arrive.
			description.bindText(if (state.loading) null else context.getString(R.string.tablet_search_no_description))
			more.isVisible = false
			return
		}
		description.text = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
		description.isVisible = true
		more.isVisible = description.text.length > LONG_DESCRIPTION
		applyDescriptionExpansion()
	}

	private fun applyDescriptionExpansion() {
		description.maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES
		more.setText(if (expanded) R.string.search_preview_show_less else R.string.search_preview_show_more)
	}

	private fun bindActions(state: SearchPreviewState) {
		val manga = state.manga
		val media = manga.source.mediaType()
		val actions = SearchPreviewActions.forTitle(
			media = media,
			hasAlternatives = callbacks.hasAlternatives(manga),
			hasWebUrl = manga.publicUrl.startsWith("http", ignoreCase = true),
		)
		primary.isVisible = PreviewAction.PRIMARY in actions
		primary.setText(
			when (SearchPreviewActions.primaryKind(media, state.progress != null)) {
				PrimaryActionKind.READ -> R.string.tablet_search_read
				PrimaryActionKind.CONTINUE -> R.string.search_preview_continue
				PrimaryActionKind.WATCH -> R.string.search_preview_watch
				PrimaryActionKind.CONTINUE_WATCHING -> R.string.search_preview_continue_watching
				PrimaryActionKind.LISTEN -> R.string.search_preview_listen
				PrimaryActionKind.CONTINUE_LISTENING -> R.string.search_preview_continue_listening
			},
		)
		secondaryRow.isVisible = true
		details.isVisible = PreviewAction.DETAILS in actions
		details.setText(R.string.tablet_search_open)
		library.isVisible = PreviewAction.LIBRARY in actions
		library.setText(if (state.inLibrary) R.string.search_preview_in_library else R.string.search_preview_add_library)
		download.isVisible = PreviewAction.DOWNLOAD in actions
		chooseSource.isVisible = PreviewAction.CHOOSE_SOURCE in actions
		browser.isVisible = PreviewAction.BROWSER in actions
		share.isVisible = PreviewAction.SHARE in actions
		moreActions.isVisible = true
	}

	private fun TextView.bindText(value: CharSequence?) {
		isVisible = !value.isNullOrBlank()
		if (isVisible) text = value
	}

	companion object {
		private const val MAX_TAGS = 12
		private const val COLLAPSED_LINES = 6
		private const val LONG_DESCRIPTION = 280

		fun attach(root: View, callbacks: SearchPreviewCallbacks): SearchTabletPreview? {
			if (root.findViewById<View>(R.id.search_preview_pane) == null) return null
			return SearchTabletPreview(root, callbacks)
		}
	}
}
