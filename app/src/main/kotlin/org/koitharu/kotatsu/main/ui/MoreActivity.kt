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
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import org.koitharu.kotatsu.core.ui.MonogatariSectionStyle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.BookShelfCard
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.TapCounter

/** Full-screen, theme-aware More hub; no duplicate hard-coded menu destinations. */
class MoreActivity : AppCompatActivity() {
    private lateinit var searchBox: TextInputEditText

    override fun onSaveInstanceState(outState: Bundle) {
        if (::searchBox.isInitialized) outState.putString("more_filter", searchBox.text?.toString())
        super.onSaveInstanceState(outState)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "More"
        val margin = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        val info = TextView(this).apply {
            text = "Library · Find something · Backup · App and tools"
            textSize = 15f
            setPadding(0, 0, 0, margin)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val tapCounter = TapCounter()
        val mascot = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            Mascot.bind(this, MascotForm.DEFAULT)
            setOnClickListener {
                if (tapCounter.tap(android.os.SystemClock.uptimeMillis())) Mascot.showEasterEgg(this@MoreActivity)
            }
        }
        header.addView(mascot, LinearLayout.LayoutParams((72 * resources.displayMetrics.density).toInt(), (72 * resources.displayMetrics.density).toInt()))
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        val destinationsAdapter = DestinationsAdapter()
        val emptyNotice = TextView(this).apply {
            setText(R.string.monogatari_more_no_results)
            isVisible = false
        }
        root.addView(TextInputLayout(this).apply {
            hint = getString(R.string.monogatari_more_filter)
            addView(TextInputEditText(this@MoreActivity).apply {
                isSingleLine = true
                setText(savedInstanceState?.getString("more_filter").orEmpty())
                searchBox = this
                doAfterTextChanged {
                    destinationsAdapter.filter(it?.toString().orEmpty())
                    emptyNotice.isVisible = destinationsAdapter.itemCount == 0
                }
            })
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(emptyNotice, LinearLayout.LayoutParams(-1, -2))
        destinationsAdapter.filter(savedInstanceState?.getString("more_filter").orEmpty())
        emptyNotice.isVisible = destinationsAdapter.itemCount == 0
        root.addView(RecyclerView(this).apply {
            val columns = MonogatariChrome.columns(context)
            layoutManager = GridLayoutManager(this@MoreActivity, columns).apply {
                // Section titles span the whole row, like category headings in Settings.
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (destinationsAdapter.isHeading(position)) columns else 1
                }
            }
            adapter = destinationsAdapter
            clipToPadding = false
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
    }

    private class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    private class HeaderHolder(val title: TextView) : RecyclerView.ViewHolder(title)

    private inner class DestinationsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var entries = MoreDestinations.sections

        fun filter(term: String) {
            entries = MoreDestinations.filteredSections(term)
            notifyDataSetChanged()
        }

        fun isHeading(position: Int): Boolean = entries.getOrNull(position) is MoreDestinations.Entry.Header

        override fun getItemCount(): Int = entries.size

        override fun getItemViewType(position: Int): Int = if (isHeading(position)) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 1) {
                return HeaderHolder(MonogatariSectionStyle.heading(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -2)
                })
            }
            val card = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monogatari_book, parent, false) as MaterialCardView
            MonogatariSectionStyle.card(card)
            return Holder(card)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = entries[position]
            if (holder is HeaderHolder) {
                holder.title.text = (item as MoreDestinations.Entry.Header).name
                return
            }
            holder as Holder
            val page = (item as MoreDestinations.Entry.Link).page
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
