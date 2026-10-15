package org.koitharu.kotatsu.core.media

import android.view.LayoutInflater
import android.view.View
import android.graphics.drawable.GradientDrawable
import com.google.android.material.color.MaterialColors
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
import com.google.android.material.button.MaterialButton
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.image.GeneratedCoverDrawable
import coil3.asImage

/** Same reusable Material result row for web book search and OPDS catalogs. */
internal data class CatalogRow(
    val title: String,
    val subtitle: String = "",
    val coverUrl: String? = null,
    val onOpen: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

internal class CatalogResultsAdapter : RecyclerView.Adapter<CatalogResultsAdapter.Holder>() {
    var imageLoader: ImageLoader? = null
    private var rows = emptyList<CatalogRow>()
    private var isLoading = false

    /** Placeholder cards keep layout stable without pretending results already exist. */
    fun showLoading() {
        isLoading = true
        rows = List(4) { CatalogRow("", "") }
        notifyDataSetChanged()
    }

    fun show(newRows: List<CatalogRow>) {
        isLoading = false
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
        if (isLoading) {
            holder.coverRequest?.dispose()
            holder.coverRequest = null
            val dp = holder.itemView.resources.displayMetrics.density
            val tint = MaterialColors.getColor(
                holder.itemView, com.google.android.material.R.attr.colorSurfaceVariant)
            fun skeleton() = GradientDrawable().apply {
                setColor(tint)
                cornerRadius = dp * 6
            }
            holder.cover.setImageDrawable(skeleton())
            holder.title.text = " "
            holder.title.minHeight = (22 * dp).toInt()
            holder.title.background = skeleton()
            holder.subtitle.text = " "
            holder.subtitle.isVisible = true
            holder.subtitle.minHeight = (16 * dp).toInt()
            holder.subtitle.background = skeleton()
            holder.action.isVisible = false
            holder.action.setOnClickListener(null)
            holder.card.contentDescription = "Loading book results"
            holder.card.setOnClickListener(null)
            holder.card.setOnLongClickListener(null)
            holder.card.isClickable = false
            return
        }
        holder.title.background = null
        holder.subtitle.background = null
        holder.title.minHeight = 0
        holder.subtitle.minHeight = 0
        holder.card.isClickable = true
        val row = rows[position]
        holder.title.text = row.title
        holder.subtitle.text = row.subtitle
        holder.subtitle.isVisible = row.subtitle.isNotBlank()
        holder.action.isVisible = !row.actionLabel.isNullOrBlank() && row.onAction != null
        holder.action.text = row.actionLabel.orEmpty()
        holder.action.contentDescription = if (holder.action.isVisible)
            "${row.actionLabel}: ${row.title}" else null
        holder.action.setOnClickListener(if (row.onAction == null) null else View.OnClickListener {
            row.onAction?.invoke()
        })
        holder.coverRequest?.dispose()
        holder.coverRequest = null
        val generated = GeneratedCoverDrawable(row.title, mediaKind = MediaType.BOOK)
        holder.cover.setImageDrawable(generated)
        val cover = row.coverUrl?.takeIf { it.startsWith("https://") }
        if (cover != null) {
            holder.coverRequest = imageLoader?.enqueue(
                ImageRequest.Builder(holder.itemView.context)
                    .data(cover)
                    .crossfade(true)
                    .error(generated.asImage())
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
        val action: MaterialButton = card.findViewById(R.id.monogatari_catalog_item_action)
    }
}
