package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
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
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.parsers.model.Manga
import java.io.File
import javax.inject.Inject

/** User-facing unified library across saved manga, novels, anime, audio and imported books. */
@AndroidEntryPoint
class UnifiedLibraryActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEDIA_KIND = "monogatari_library_media_kind"
        const val EXTRA_FILTER = "monogatari_library_filter"
    }
    @Inject lateinit var favorites: FavouritesRepository
    @Inject lateinit var history: HistoryRepository
    private data class Row(val title: String, val detail: String, val kind: MediaType,
        val manga: Manga? = null, val file: File? = null)
    private var all = emptyList<Row>()
    /** Most recently opened titles, newest first, for the Continue strip. */
    private var recent = emptyList<Row>()
    private lateinit var continueLabel: TextView
    private lateinit var continueRow: LinearLayout
    private lateinit var continueScroll: HorizontalScrollView
    /** Null represents All media, including items with an unknown/older source type. */
    private var selected: MediaType? = null
    private var requestedKind: MediaType? = null
    private var refreshJob: Job? = null
    private lateinit var search: EditText
    private lateinit var count: TextView
    private val adapter = Rows()
    private val buttons = mutableListOf<Pair<MediaType?, MaterialButton>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedKind = MediaType.entries.firstOrNull {
            it.name.equals(intent.getStringExtra(EXTRA_MEDIA_KIND), ignoreCase = true)
        }
        selected = if (savedInstanceState?.containsKey("selected_kind") == true) {
            MediaType.entries.firstOrNull { it.name == savedInstanceState?.getString("selected_kind") }
        } else requestedKind
        title = "Unified Library"
        val px = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px, px, px, px) }
        val tabs = LinearLayout(this).apply {
            id = R.id.monogatari_library_tabs
            orientation = LinearLayout.HORIZONTAL
        }
        for (type in listOf<MediaType?>(null) + MediaType.entries) {
            val button = MaterialButton(this).apply {
                text = type?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: "All"
                isAllCaps = false
                isCheckable = true
                setOnClickListener { selected = type; render() }
            }
            buttons += type to button
            tabs.addView(button, LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            })
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        })
        continueLabel = TextView(this).apply {
            setText(R.string.library_continue)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, px / 2, 0, 0)
        }
        continueRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        continueScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(continueRow)
        }
        root.addView(continueLabel, LinearLayout.LayoutParams(-1, -2))
        root.addView(continueScroll, LinearLayout.LayoutParams(-1, -2))
        search = EditText(this).apply { hint = "Filter this library"; setSingleLine(true) }
        search.doAfterTextChanged { render() }
        search.setText(intent.getStringExtra(EXTRA_FILTER).orEmpty())
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        count = TextView(this)
        root.addView(count, LinearLayout.LayoutParams(-1, -2))
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@UnifiedLibraryActivity,
                MonogatariChrome.columns(context))
            adapter = this@UnifiedLibraryActivity.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_kind", selected?.name ?: "ALL")
        super.onSaveInstanceState(outState)
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
                recent = runCatching { history.getList(0, 30) }.getOrDefault(emptyList()).map { manga ->
                    val source = manga.source.name
                    val kind = when {
                        source in groups.getValue(MediaType.VIDEO) -> MediaType.VIDEO
                        source in groups.getValue(MediaType.AUDIO) -> MediaType.AUDIO
                        source in groups.getValue(MediaType.BOOK) -> MediaType.BOOK
                        else -> MediaType.MANGA
                    }
                    Row(manga.title, source, kind, manga = manga)
                }
                (remote + local).sortedBy { it.title.lowercase() }
            }
            render()
        }
    }

    private fun render() {
        if (!::count.isInitialized) return
        val q = search.text?.toString().orEmpty().trim()
        val filtered = all.filter { row ->
            (selected == null || row.kind == selected) && (q.isBlank() ||
                row.title.contains(q, ignoreCase = true) || row.detail.contains(q, ignoreCase = true))
        }
        adapter.setRows(filtered.take(1000))
        renderContinue()
        val category = selected?.name?.lowercase() ?: "saved"
        count.text = when {
            filtered.isEmpty() && q.isNotBlank() -> "No matching ${category} titles"
            filtered.isEmpty() -> "No ${category} items yet. Save a title or import a book to begin."
            else -> "${filtered.size} ${category} items" +
                if (filtered.size > 1000) " (first 1000 shown)" else ""
        }
        // Keep every media filter accessible, even on an empty or new installation.
        buttons.forEach { (type, button) ->
            val matching = if (type == null) all.size else all.count { it.kind == type }
            val label = type?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: "All"
            button.text = "$label ($matching)"
            button.isChecked = type == selected
        }
    }

    /** Quick "continue reading / watching / listening" buttons for the selected media tab. */
    private fun renderContinue() {
        val items = recent.filter { selected == null || it.kind == selected }.take(10)
        continueRow.removeAllViews()
        val visible = items.isNotEmpty()
        continueLabel.isVisible = visible
        continueScroll.isVisible = visible
        for (row in items) {
            val manga = row.manga ?: continue
            continueRow.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = row.title
                isAllCaps = false
                maxLines = 1
                maxWidth = (220 * resources.displayMetrics.density).toInt()
                contentDescription = getString(R.string.library_continue_item, row.title)
                setOnClickListener { startActivity(AppRouter.detailsIntent(this@UnifiedLibraryActivity, manga)) }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() })
        }
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
