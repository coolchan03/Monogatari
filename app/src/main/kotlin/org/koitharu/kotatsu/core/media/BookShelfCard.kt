package org.koitharu.kotatsu.core.media

import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import java.io.File
import java.util.Locale

/** Shared Monogatari cover-free book row for the shelf and catalog. */
internal object BookShelfCard {
    fun bind(card: MaterialCardView, file: File, onOpen: (File) -> Unit) {
        val context = card.context
        val title = BookShelf.title(file)
        val progress = BookShelf.progress(context, file)
        val format = file.extension.uppercase(Locale.ROOT)
        bind(card, title, "$format · $progress") { onOpen(file) }
    }

    /** One Material row used by the Books shelf, catalog, and unified media library. */
    fun bind(card: MaterialCardView, title: String, subtitle: String, onOpen: () -> Unit) {
        card.findViewById<TextView>(R.id.monogatari_book_item_title).text = title
        card.findViewById<TextView>(R.id.monogatari_book_item_progress).text = subtitle
        card.contentDescription = "$title, $subtitle"
        card.setOnClickListener { onOpen() }
    }
}
