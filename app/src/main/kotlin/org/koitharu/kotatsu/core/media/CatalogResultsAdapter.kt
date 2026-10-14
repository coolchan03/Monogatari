package org.koitharu.kotatsu.core.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import coil3.ImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R

/** Same reusable Material result row for web book search and OPDS catalogs. */
internal data class CatalogRow(
    val title: String,
    val subtitle: String = "",
    val coverUrl: String? = null,
    val onOpen: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
)

internal class CatalogResultsAdapter : RecyclerView.Adapter<CatalogResultsAdapter.Holder>() {
    var imageLoader: ImageLoader? = null
    private var rows = emptyList<CatalogRow>()

    fun show(newRows: List<CatalogRow>) {
        rows = newRows.toList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val card = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_monogatari_catalog, parent, false) as MaterialCardView
        return Holder(card)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        holder.subtitle.text = row.subtitle
        holder.subtitle.isVisible = row.subtitle.isNotBlank()
        holder.coverRequest?.dispose()
        holder.coverRequest = null
        holder.cover.setImageResource(R.drawable.ic_monogatari_nav_explore)
        val cover = row.coverUrl?.takeIf { it.startsWith("https://") }
        if (cover != null) {
            holder.coverRequest = imageLoader?.enqueue(
                ImageRequest.Builder(holder.itemView.context)
                    .data(cover)
                    .crossfade(true)
                    .target(holder.cover)
                    .build()
            )
        }
        holder.card.contentDescription = if (row.subtitle.isBlank()) row.title
            else "${row.title}, ${row.subtitle}"
        val onOpen = row.onOpen
        val onLongPress = row.onLongPress
        holder.card.setOnClickListener(if (onOpen == null) null else View.OnClickListener { onOpen() })
        holder.card.setOnLongClickListener(if (onLongPress == null) null else View.OnLongClickListener {
            onLongPress()
            true
        })
    }

    override fun onViewRecycled(holder: Holder) {
        holder.coverRequest?.dispose()
        holder.coverRequest = null
        super.onViewRecycled(holder)
    }

    class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        var coverRequest: Disposable? = null
        val cover: ImageView = card.findViewById(R.id.monogatari_catalog_item_cover)
        val title: TextView = card.findViewById(R.id.monogatari_catalog_item_title)
        val subtitle: TextView = card.findViewById(R.id.monogatari_catalog_item_summary)
    }
}
