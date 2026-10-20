package org.koitharu.kotatsu.discover.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.ui.setMonogatariContent

/**
 * Stand-alone host for the books / videos Discover pages, used when they are opened from More while they are not
 * bottom navigation tabs. [EXTRA_MEDIA_TYPE] picks the page (BOOK or VIDEO; BOOK when missing).
 */
@AndroidEntryPoint
class MediaDiscoverActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		// The More hub can only pass a view URI (monogatari://discover/video), not extras.
		val isVideo = intent.getStringExtra(EXTRA_MEDIA_TYPE) == MediaType.VIDEO.name ||
			intent.data?.lastPathSegment.equals("video", ignoreCase = true)
		setTitle(if (isVideo) R.string.media_discover_video_nav else R.string.media_discover_books_nav)
		val container = FragmentContainerView(this).apply { id = R.id.container }
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(container)
		if (savedInstanceState == null) {
			supportFragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.replace(
					R.id.container,
					if (isVideo) DiscoverVideoFragment::class.java else DiscoverBooksFragment::class.java,
					null,
				)
				.commit()
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	companion object {

		const val EXTRA_MEDIA_TYPE = "monogatari.media_discover.type"

		fun intent(context: Context, type: MediaType): Intent =
			Intent(context, MediaDiscoverActivity::class.java).putExtra(EXTRA_MEDIA_TYPE, type.name)
	}
}
