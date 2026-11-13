package org.koitharu.kotatsu.search.ui.multi

/** How much of the search screen the preview may use, chosen by the user. */
enum class SearchPaneMode {
	AUTO, LIST_ONLY, LIST_AND_PREVIEW;

	companion object {
		fun fromName(raw: String?): SearchPaneMode = entries.firstOrNull { it.name == raw } ?: AUTO
	}
}

/** Where the preview goes for the current window. */
enum class SearchPaneArrangement { LIST_ONLY, PREVIEW_BELOW, PREVIEW_SIDE }

/**
 * Pure window policy for the search screen. It looks at the live window size (not the device orientation or
 * resource qualifiers), so rotation, split screen, freeform and fold changes all resolve the same way.
 */
object SearchPaneLayoutPolicy {

	/** Below this width there is no room for a second pane. */
	const val MIN_PREVIEW_WIDTH_DP = 600
	/** Preview beside the list needs this much width to leave a usable list. */
	const val MIN_SIDE_WIDTH_DP = 720
	/** Stacked panes need this much height to keep both usable. */
	const val MIN_STACKED_HEIGHT_DP = 480
	const val MIN_FRACTION = 0.25f
	const val MAX_FRACTION = 0.75f
	const val DEFAULT_SIDE_FRACTION = 0.58f
	const val DEFAULT_STACKED_FRACTION = 0.5f

	fun arrangement(
		widthDp: Int,
		heightDp: Int,
		mode: SearchPaneMode,
		previewCollapsed: Boolean = false,
	): SearchPaneArrangement {
		if (mode == SearchPaneMode.LIST_ONLY || previewCollapsed) return SearchPaneArrangement.LIST_ONLY
		if (widthDp < MIN_PREVIEW_WIDTH_DP) return SearchPaneArrangement.LIST_ONLY
		val portrait = heightDp > widthDp
		return when {
			portrait -> if (heightDp >= MIN_STACKED_HEIGHT_DP) SearchPaneArrangement.PREVIEW_BELOW else SearchPaneArrangement.LIST_ONLY
			// A short window (a phone in landscape) cannot hold two usable panes, however wide it is.
			heightDp < MIN_STACKED_HEIGHT_DP -> SearchPaneArrangement.LIST_ONLY
			widthDp >= MIN_SIDE_WIDTH_DP -> SearchPaneArrangement.PREVIEW_SIDE
			else -> SearchPaneArrangement.PREVIEW_BELOW
		}
	}

	fun clampFraction(value: Float): Float =
		if (value.isNaN()) DEFAULT_SIDE_FRACTION else value.coerceIn(MIN_FRACTION, MAX_FRACTION)

	/** Share of the content area given to the list; the preview gets the rest. */
	fun defaultFraction(arrangement: SearchPaneArrangement): Float = when (arrangement) {
		SearchPaneArrangement.PREVIEW_SIDE -> DEFAULT_SIDE_FRACTION
		SearchPaneArrangement.PREVIEW_BELOW -> DEFAULT_STACKED_FRACTION
		SearchPaneArrangement.LIST_ONLY -> 1f
	}
}
