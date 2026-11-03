package org.koitharu.kotatsu.core.ui.util

/** A tab whose content is not a RecyclerView but can still scroll back to its top when its tab is tapped again. */
interface ScrollToTopOwner {

	fun scrollToTop()
}
