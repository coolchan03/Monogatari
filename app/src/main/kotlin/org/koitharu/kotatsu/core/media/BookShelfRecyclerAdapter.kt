package org.koitharu.kotatsu.core.media

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import java.io.File

/** Recycles the same book-row layout used by the catalog instead of inflating the whole shelf. */
internal class BookShelfRecyclerAdapter(private val onOpen: (File) -> Unit) :
    RecyclerView.Adapter<BookShelfRecyclerAdapter.Holder>() {
    private var files = emptyList<File>()

    fun submitFiles(updated: List<File>) {
        files = updated.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_monogatari_book, parent, false)
            as MaterialCardView,
    )

    override fun getItemCount(): Int = files.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        BookShelfCard.bind(holder.card, files[position], onOpen)
    }

    class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
}
