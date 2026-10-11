package org.koitharu.kotatsu.core.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

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
            addView(MaterialButton(context).apply {
                text = "Search local books"
                setOnClickListener { refresh() }
            })
            addView(MaterialButton(context).apply {
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
        val files = BookShelf.list(requireContext(), filter.text.toString())
        if (files.isEmpty()) list.addView(TextView(requireContext()).apply {
            text = "No matching imported books. Use Import books to add EPUB, PDF, or text files."
        })
        files.forEach { file ->
            list.addView(MaterialButton(requireContext()).apply {
                val title = BookShelf.title(file)
                val progress = BookShelf.progress(requireContext(), file)
                text = "$title  -  $progress"
                isAllCaps = false
                setOnClickListener {
                    startActivity(BookShelf.readerIntent(requireContext(), file))
                }
            })
        }
    }
}
