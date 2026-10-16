package org.koitharu.kotatsu.search.ui.multi

import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.image.CoilImageView
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.parsers.model.Manga

/** A persistent right-hand selection preview on >=840dp windows. Never launches a second Activity until asked. */
internal class SearchTabletPreview private constructor(
    root: View,
    private val onDetails: (Manga) -> Unit,
    private val onRead: (Manga) -> Unit,
    private val onUrl: (String) -> Unit,
) {
    private val hint = root.findViewById<TextView>(R.id.search_preview_hint)
    private val cover = root.findViewById<CoilImageView>(R.id.search_preview_cover)
    private val title = root.findViewById<TextView>(R.id.search_preview_title)
    private val source = root.findViewById<TextView>(R.id.search_preview_source)
    private val description = root.findViewById<TextView>(R.id.search_preview_description)
    private val open = root.findViewById<MaterialButton>(R.id.search_preview_open)
    private val read = root.findViewById<MaterialButton>(R.id.search_preview_read)
    private var selectedManga: Manga? = null
    private var selectedUrl: String? = null
    val selectedId: Long? get() = selectedManga?.id

    init {
        open.setOnClickListener {
            val manga = selectedManga
            if (manga != null) onDetails(manga) else selectedUrl?.let(onUrl)
        }
        read.setOnClickListener { selectedManga?.let(onRead) }
    }

    fun show(item: MangaListModel) {
        val manga = item.toMangaWithOverride()
        selectedManga = manga
        selectedUrl = null
        val content = manga.description?.takeIf { it.isNotBlank() }
        showCommon(item.title, item.source.getTitle(title.context).toString(),
            content?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
                ?: title.context.getString(R.string.tablet_search_no_description))
        cover.isVisible = !item.coverUrl.isNullOrEmpty()
        cover.setImageAsync(item.coverUrl)
        open.setText(R.string.tablet_search_open)
        read.isVisible = true
    }

    fun show(item: BookCatalogListModel) {
        selectedManga = null
        selectedUrl = item.url
        cover.isVisible = !item.coverUrl.isNullOrBlank()
        cover.setImageAsync(item.coverUrl)
        showCommon(item.title, item.catalog,
            listOfNotNull(item.author.takeIf { it.isNotBlank() }, item.format, item.language, item.attribution)
                .joinToString(" · ").ifBlank { title.context.getString(R.string.tablet_search_no_description) })
        open.setText(R.string.tablet_search_website)
        read.isVisible = false
    }

    private fun showCommon(heading: String, provider: String, summary: CharSequence) {
        hint.isVisible = false
        title.isVisible = true
        source.isVisible = true
        description.isVisible = true
        open.isVisible = true
        title.text = heading
        source.text = provider
        description.text = summary
        title.announceForAccessibility(heading)
    }

    companion object {
        fun attach(
            root: View,
            onDetails: (Manga) -> Unit,
            onRead: (Manga) -> Unit,
            onUrl: (String) -> Unit,
        ): SearchTabletPreview? {
            if (root.findViewById<View>(R.id.search_preview_pane) == null) return null
            return SearchTabletPreview(root, onDetails, onRead, onUrl)
        }
    }
}
