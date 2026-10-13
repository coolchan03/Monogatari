package org.koitharu.kotatsu.core.media

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.parsers.model.Manga
import java.io.File
import javax.inject.Inject

/** User-facing unified library across saved manga, novels, anime, audio and imported books. */
@AndroidEntryPoint
class UnifiedLibraryActivity : AppCompatActivity() {
    @Inject lateinit var favorites: FavouritesRepository
    private data class Row(val title: String, val detail: String, val kind: MediaType,
        val manga: Manga? = null, val file: File? = null)
    private var all = emptyList<Row>()
    private var selected = MediaType.MANGA
    private var refreshJob: Job? = null
    private lateinit var search: EditText
    private lateinit var count: TextView
    private val adapter = Rows()
    private val buttons = mutableListOf<Pair<MediaType, MaterialButton>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "My Library"
        val px = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px, px, px, px) }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (type in MediaType.entries) {
            val button = MaterialButton(this).apply {
                text = type.name.lowercase().replaceFirstChar { it.uppercaseChar() }
                isAllCaps = false
                setOnClickListener { selected = type; render() }
            }
            buttons += type to button
            tabs.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        search = EditText(this).apply { hint = "Filter this library"; setSingleLine(true) }
        search.doAfterTextChanged { render() }
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        count = TextView(this)
        root.addView(count, LinearLayout.LayoutParams(-1, -2))
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@UnifiedLibraryActivity)
            adapter = this@UnifiedLibraryActivity.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    override fun onResume() { super.onResume(); if (::search.isInitialized) refresh() }

    private fun refresh() {
        count.text = "Loading your saved items…"
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            all = withContext(Dispatchers.IO) {
                val groups = MediaType.entries.associateWith { it.persistedSourceNames() }
                val remote = favorites.getAllManga().distinctBy { it.id }.map { manga ->
                    val source = manga.source.name
                    val kind = when {
                        source in groups.getValue(MediaType.VIDEO) -> MediaType.VIDEO
                        source in groups.getValue(MediaType.AUDIO) -> MediaType.AUDIO
                        source in groups.getValue(MediaType.BOOK) -> MediaType.BOOK
                        else -> MediaType.MANGA
                    }
                    Row(manga.title, source, kind, manga = manga)
                }
                val local = BookShelf.list(this@UnifiedLibraryActivity).map { file ->
                    Row(BookShelf.title(file), "Local ${file.extension.uppercase()} · " +
                        BookShelf.progress(this@UnifiedLibraryActivity, file), MediaType.BOOK, file = file)
                }
                (remote + local).sortedBy { it.title.lowercase() }
            }
            render()
        }
    }

    private fun render() {
        if (!::count.isInitialized) return
        val q = search.text?.toString().orEmpty().trim()
        val filtered = all.filter { it.kind == selected && (q.isBlank() ||
            it.title.contains(q, ignoreCase = true) || it.detail.contains(q, ignoreCase = true)) }
        adapter.setRows(filtered.take(1000))
        count.text = "${filtered.size} ${selected.name.lowercase()} titles" +
            if (filtered.size > 1000) " (first 1000 shown)" else ""
        buttons.forEach { (type, button) -> button.isEnabled = type != selected }
    }

    private inner class Rows : RecyclerView.Adapter<Rows.Holder>() {
        private var rows = emptyList<Row>()
        fun setRows(value: List<Row>) {
            rows = value
            notifyDataSetChanged()
        }
        override fun getItemCount(): Int = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_monogatari_book, parent, false) as MaterialCardView
            return Holder(card)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            BookShelfCard.bind(holder.card, row.title, row.detail) {
                val intent = when {
                    row.file != null -> BookShelf.readerIntent(this@UnifiedLibraryActivity, row.file)
                    row.manga != null -> AppRouter.detailsIntent(this@UnifiedLibraryActivity, row.manga)
                    else -> null
                }
                if (intent != null) startActivity(intent)
            }
            holder.card.findViewById<ImageView>(R.id.monogatari_book_item_icon).setImageResource(
                when (row.kind) {
                    MediaType.MANGA -> R.drawable.ic_manga_source
                    MediaType.VIDEO -> R.drawable.ic_play
                    MediaType.AUDIO -> R.drawable.ic_play
                    MediaType.BOOK -> R.drawable.ic_monogatari_nav_books
                },
            )
        }
        inner class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
    }
}
