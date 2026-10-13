package org.koitharu.kotatsu.core.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R

/** Same reusable Material result row for web book search and OPDS catalogs. */
internal data class CatalogRow(
    val title: String,
    val subtitle: String = "",
    val onOpen: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
)

internal class CatalogResultsAdapter : RecyclerView.Adapter<CatalogResultsAdapter.Holder>() {
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

    class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        val title: TextView = card.findViewById(R.id.monogatari_catalog_item_title)
        val subtitle: TextView = card.findViewById(R.id.monogatari_catalog_item_summary)
    }
}
