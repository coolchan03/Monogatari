package org.koitharu.kotatsu.core.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R

/**
 * The Books tab: the main Library locked to books, so imported books, saved novels and their covers, search, sort,
 * collections and empty state look and behave exactly as they do in the Library itself.
 */
@AndroidEntryPoint
class BookLibraryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentContainerView(requireContext()).apply { id = R.id.library_books_container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentById(R.id.library_books_container) == null) {
            val args = Bundle(arguments ?: Bundle.EMPTY).apply {
                putString(UnifiedLibraryFragment.ARG_LOCKED_KIND, MediaType.BOOK.name)
            }
            childFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.library_books_container, UnifiedLibraryFragment::class.java, args)
                .commit()
        }
    }
}
