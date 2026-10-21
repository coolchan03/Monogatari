package org.koitharu.kotatsu.core.media

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for [BookCatalogFragment] (the book catalog), used when it is opened from More, Settings, Discover,
 * the Books shelf or the Test Center while it is not one of the bottom navigation tabs. Its extras are passed on to the
 * fragment as arguments.
 */
@AndroidEntryPoint
class BookLibraryActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.monogatari_catalog_title)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container, MonogatariChrome.LIST_MAX_WIDTH_DP)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(R.id.container, BookCatalogFragment::class.java, intent.extras)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}
}
