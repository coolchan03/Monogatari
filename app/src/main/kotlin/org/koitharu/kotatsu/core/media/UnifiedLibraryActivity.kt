package org.koitharu.kotatsu.core.media

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for [UnifiedLibraryFragment], used when the unified library is opened from More, Favourites,
 * Discover, the book catalog or the Test Center while it is not one of the bottom navigation tabs. Its extras
 * ([EXTRA_MEDIA_KIND], [EXTRA_FILTER]) are passed on to the fragment as arguments.
 */
@AndroidEntryPoint
class UnifiedLibraryActivity : AppCompatActivity() {

	companion object {
		const val EXTRA_MEDIA_KIND = "monogatari_library_media_kind"
		const val EXTRA_FILTER = "monogatari_library_filter"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.screen_title_library)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container, MonogatariChrome.LIST_MAX_WIDTH_DP)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(R.id.container, UnifiedLibraryFragment::class.java, intent.extras)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}
}
