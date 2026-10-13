package org.koitharu.kotatsu.core.media

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.koitharu.kotatsu.R

/** Monogatari's unified local book shelf, using the shared imported-book index. */
class BookLibraryFragment : Fragment() {
    private var shelfView: View? = null
    private val shelfAdapter = BookShelfRecyclerAdapter { file ->
        startActivity(BookShelf.readerIntent(requireContext(), file))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_monogatari_books, container, false).also {
        shelfView = it
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.monogatari_book_discover)
            .setOnClickListener {
                startActivity(Intent(requireContext(), BookLibraryActivity::class.java))
            }
        view.findViewById<RecyclerView>(R.id.monogatari_book_list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = shelfAdapter
        }
        view.findViewById<TextInputEditText>(R.id.monogatari_book_filter)
            .doAfterTextChanged { refresh() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        shelfView = null
        super.onDestroyView()
    }

    private fun refresh() {
        val root = shelfView ?: return
        val term = root.findViewById<TextInputEditText>(R.id.monogatari_book_filter)
            .text?.toString().orEmpty()
        val files = BookShelf.list(requireContext(), term)
        val empty = root.findViewById<View>(R.id.monogatari_book_empty)
        shelfAdapter.submitFiles(files)
        empty.isVisible = files.isEmpty()
        root.findViewById<TextView>(R.id.monogatari_book_count).text =
            getString(R.string.monogatari_books_count, files.size)
    }
}
