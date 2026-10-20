package org.koitharu.kotatsu.discover.media

import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.core.media.MediaType

/** "Discover videos" page; its own class so the navigation bar can map it to its tab. */
@AndroidEntryPoint
class DiscoverVideoFragment : MediaDiscoverFragment() {

	override val mediaType: MediaType
		get() = MediaType.VIDEO
}
