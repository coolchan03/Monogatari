package org.koitharu.kotatsu.main.ui

/** Pure window-size rules for the main navigation (unit-tested). */
internal object NavLayoutPolicy {

	/** Windows at least this wide (desktop, ChromeOS, large landscape tablets) get the labelled side drawer. */
	const val DESKTOP_WIDTH_DP = 1000

	/** The two-pane layouts need at least this width. */
	const val TWO_PANE_WIDTH_DP = 600

	fun expandRail(widthDp: Int, labelsVisible: Boolean): Boolean = labelsVisible && widthDp >= DESKTOP_WIDTH_DP

	fun useTwoPane(widthDp: Int): Boolean = widthDp >= TWO_PANE_WIDTH_DP
}
