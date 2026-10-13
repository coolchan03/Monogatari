package org.koitharu.kotatsu.main.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.koitharu.kotatsu.core.media.MediaAccents
import android.content.res.ColorStateList
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.BookShelfCard

/** Full-screen, theme-aware More hub; no duplicate hard-coded menu destinations. */
class MoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "More"
        val margin = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        val info = TextView(this).apply {
            text = "Library · Reading · Sources · Tools"
            textSize = 15f
            setPadding(0, 0, 0, margin)
        }
        root.addView(info, LinearLayout.LayoutParams(-1, -2))
        root.addView(RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MoreActivity,
                if (resources.configuration.screenWidthDp >= 600) 2 else 1)
            adapter = DestinationsAdapter()
            clipToPadding = false
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    private inner class DestinationsAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = MoreDestinations.pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monogatari_book, parent, false) as MaterialCardView
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val page = MoreDestinations.pages[position]
            BookShelfCard.bind(holder.card, page.title, page.subtitle) {
                val destination = Intent(this@MoreActivity, page.target)
                page.viewUri?.let { uri ->
                    destination.action = Intent.ACTION_VIEW
                    destination.data = android.net.Uri.parse(uri)
                }
                startActivity(destination)
            }
            holder.card.findViewById<ImageView>(R.id.monogatari_book_item_icon).apply {
                setImageResource(page.icon)
                if (MediaAccents.enabled(this@MoreActivity)) {
                    val kind = when {
                        page.title.contains("Book", ignoreCase = true) -> "BOOK"
                        page.title.contains("Video", ignoreCase = true) || page.title == "Discover" -> "VIDEO"
                        page.title.contains("News", ignoreCase = true) -> "NEWS"
                        page.title.contains("Audio", ignoreCase = true) -> "AUDIO"
                        else -> "MANGA"
                    }
                    imageTintList = ColorStateList.valueOf(MediaAccents.color(kind))
                }
            }
        }
    }
}
