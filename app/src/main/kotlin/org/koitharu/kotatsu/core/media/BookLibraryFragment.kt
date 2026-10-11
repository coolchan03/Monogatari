package org.koitharu.kotatsu.core.media

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Navigation entry point that keeps the existing Kotatsu tabs intact. */
class BookLibraryFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(TextView(context).apply {
                text = "Books and novels"
                textSize = 24f
                gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "Open book library"
                setOnClickListener {
                    startActivity(Intent(requireContext(), BookLibraryActivity::class.java))
                }
            })
        }
    }
}
