package org.koitharu.kotatsu.core.media

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for [MediaFeedFragment], used when the podcast / video feeds are opened from More, Discover or the
 * Test Center while they are not one of the bottom navigation tabs. Its extras ([EXTRA_MEDIA_TYPE]) are passed on to
 * the fragment as arguments.
 */
@AndroidEntryPoint
class MediaFeedActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.screen_title_media_feeds)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container, MonogatariChrome.LIST_MAX_WIDTH_DP)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(R.id.container, MediaFeedFragment::class.java, intent.extras)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	companion object {

		const val EXTRA_MEDIA_TYPE = "media_feed_type"

		/** Pre-installed on first open. The first three returned HTTP 200 in the 2026-10-15 provider check. */
		internal val INCLUDED_FEEDS = listOf(
			"Escape Pod · science fiction stories" to "https://escapepod.org/feed/podcast/",
			"Welcome to Night Vale · fiction podcast" to "https://feeds.megaphone.fm/SBP4591212513",
			"Pop Culture Happy Hour · films and entertainment" to "https://feeds.npr.org/510282/podcast.xml",
			"PodCastle · fantasy stories" to "https://podcastle.org/feed/podcast/",
			"Pseudopod · horror stories" to "https://pseudopod.org/feed/podcast/",
		)
	}
}
