package org.koitharu.kotatsu.core.media

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import java.io.File
import java.util.Locale

/** Shared Monogatari cover-free book row for the shelf and catalog. */
internal object BookShelfCard {
    fun create(parent: ViewGroup, file: File, onOpen: (File) -> Unit): MaterialCardView {
        val context = parent.context
        val card = LayoutInflater.from(context)
            .inflate(R.layout.item_monogatari_book, parent, false) as MaterialCardView
        bind(card, file, onOpen)
        return card
    }

    fun bind(card: MaterialCardView, file: File, onOpen: (File) -> Unit) {
        val context = card.context
        val title = BookShelf.title(file)
        val progress = BookShelf.progress(context, file)
        val format = file.extension.uppercase(Locale.ROOT)
        card.findViewById<TextView>(R.id.monogatari_book_item_title).text = title
        card.findViewById<TextView>(R.id.monogatari_book_item_progress).text =
            "$format · $progress"
        card.contentDescription = "$title, $progress"
        card.setOnClickListener { onOpen(file) }
    }
}
