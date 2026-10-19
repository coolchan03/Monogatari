package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
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
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.BookShelfCard
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.TapCounter

/** Full-screen, theme-aware More hub; no duplicate hard-coded menu destinations. */
class MoreActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.screen_title_more)
        val margin = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        val info = TextView(this).apply {
            text = "Library · Find something · Backup · App and tools"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, margin)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val tapCounter = TapCounter()
        val mascot = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            Mascot.bind(this, MascotForm.DEFAULT)
            contentDescription = "Kitsune, the Monogatari mascot"
            setOnClickListener {
                if (tapCounter.tap(android.os.SystemClock.uptimeMillis())) Mascot.showEasterEgg(this@MoreActivity)
            }
        }
        header.addView(mascot, LinearLayout.LayoutParams((72 * resources.displayMetrics.density).toInt(), (72 * resources.displayMetrics.density).toInt()))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        root.addView(RecyclerView(this).apply {
            // Settings-style rows stay one column on phones; two on wide tablets/foldables.
            val columns = if (resources.configuration.screenWidthDp >= 720) 2 else 1
            layoutManager = GridLayoutManager(this@MoreActivity, columns).apply {
                // Section titles span the whole row, like category headings in Settings.
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (MoreDestinations.sections[position] is String) columns else 1
                }
            }
            adapter = DestinationsAdapter()
            clipToPadding = false
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
    }

    private class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    private class HeaderHolder(val title: TextView) : RecyclerView.ViewHolder(title)

    private inner class DestinationsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = MoreDestinations.sections.size

        override fun getItemViewType(position: Int): Int = if (MoreDestinations.sections[position] is String) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 1) {
                val density = parent.resources.displayMetrics.density
                return HeaderHolder(TextView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
                    setPadding((8 * density).toInt(), (16 * density).toInt(), 0, (4 * density).toInt())
                    ViewCompat.setAccessibilityHeading(this, true)
                })
            }
            val card = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monogatari_settings_destination, parent, false) as MaterialCardView
            return Holder(card)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = MoreDestinations.sections[position]
            if (holder is HeaderHolder) {
                holder.title.text = item as String
                return
            }
            holder as Holder
            val page = item as MoreDestinations.Page
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
                imageTintList = null
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
