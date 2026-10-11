package org.koitharu.kotatsu.core.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.File

/** Embedded local book shelf, using the same imported files as BookLibraryActivity. */
class BookLibraryFragment : Fragment() {
    private lateinit var list: LinearLayout
    private lateinit var filter: EditText

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            addView(TextView(context).apply { text = "Books and novels"; textSize = 22f })
            filter = EditText(context).apply {
                hint = "Filter imported books"
                setSingleLine(true)
            }
            addView(filter)
            addView(Button(context).apply {
                text = "Search local books"
                setOnClickListener { refresh() }
            })
            addView(Button(context).apply {
                text = "Import books / Browse catalogs"
                setOnClickListener { startActivity(Intent(requireContext(), BookLibraryActivity::class.java)) }
            })
            list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val files = File(requireContext().filesDir, "imported-books").listFiles().orEmpty()
            .filter { it.isFile && it.name.contains(filter.text.toString().trim(), ignoreCase = true) }
            .sortedBy { it.name }
        if (files.isEmpty()) list.addView(TextView(requireContext()).apply {
            text = "No matching imported books. Use Import books to add EPUB, PDF, or text files."
        })
        files.forEach { file ->
            list.addView(Button(requireContext()).apply {
                val title = file.name.substringAfter('-', file.name)
                val progress = when (file.extension.lowercase()) {
                    "epub" -> {
                        val chapter = requireContext().getSharedPreferences("epub_progress", android.content.Context.MODE_PRIVATE)
                            .getInt(file.name, -1)
                        if (chapter >= 0) "Chapter ${chapter + 1}" else "Not started"
                    }
                    "pdf" -> {
                        val page = requireContext().getSharedPreferences("pdf_progress", android.content.Context.MODE_PRIVATE)
                            .getInt(file.name, -1)
                        if (page >= 0) "Page ${page + 1}" else "Not started"
                    }
                    else -> {
                        val prefs = requireContext().getSharedPreferences("book_scroll", android.content.Context.MODE_PRIVATE)
                        if (prefs.contains(file.name)) "Continue reading" else "Not started"
                    }
                }
                text = "$title  -  $progress"
                isAllCaps = false
                setOnClickListener {
                    val reader = when (file.extension.lowercase()) {
                        "epub" -> LocalEpubReaderActivity::class.java
                        "pdf" -> LocalPdfReaderActivity::class.java
                        else -> LocalTextReaderActivity::class.java
                    }
                    startActivity(Intent(requireContext(), reader).putExtra("local_book_file", file.absolutePath))
                }
            })
        }
    }
}
