package org.koitharu.kotatsu.discover

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for [DiscoverFragment], used when Discover is opened from More, Explore's menu or the Test Center
 * while it is not one of the bottom navigation tabs. From the navigation bar Discover is a normal tab of the main screen.
 */
@AndroidEntryPoint
class DiscoverActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setTitle(R.string.discover)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(R.id.container, DiscoverFragment::class.java, null)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}
}
