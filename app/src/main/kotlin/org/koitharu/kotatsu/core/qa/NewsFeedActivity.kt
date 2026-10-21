package org.koitharu.kotatsu.core.qa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for [NewsFeedFragment], used when News is opened from More, Discover or the Test Center
 * while it is not one of the bottom navigation tabs. From the navigation bar News is a normal tab of the main screen.
 */
@AndroidEntryPoint
class NewsFeedActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.screen_title_news)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(R.id.container, NewsFeedFragment::class.java, intent.extras)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}
}
