package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.core.prefs.NavItem

/** Decides which tab the main screen opens on a fresh start, see the "Start page" setting. */
object StartPageResolver {

	const val FIRST = "FIRST"
	const val LAST = "LAST"

	/** Items that can be picked as a start page: real tabs, not More/Search/Downloads/Settings-like shortcuts. */
	fun tabsOf(barItems: List<NavItem>): List<NavItem> = barItems.filter { !it.isShortcut }

	/**
	 * @param startPage [FIRST], [LAST] or a [NavItem] name
	 * @param lastTab [NavItem] name of the tab the user was last on, if known
	 * @param barItems items currently on the navigation bar, in order
	 * @return the tab to open: the chosen one when it is on the bar and available, otherwise the first available tab;
	 * null when the bar has no available tab at all
	 */
	fun resolve(
		startPage: String?,
		lastTab: String?,
		barItems: List<NavItem>,
		isAvailable: (NavItem) -> Boolean,
	): NavItem? {
		val candidates = tabsOf(barItems).filter(isAvailable)
		val first = candidates.firstOrNull()
		val wanted = when (startPage) {
			null, FIRST -> return first
			LAST -> lastTab
			else -> startPage
		}
		return candidates.firstOrNull { it.name == wanted } ?: first
	}

	/** The tab Back returns to from any other tab: the chosen start page ("Last used" counts as the first tab). */
	fun backTarget(startPage: String?, barItems: List<NavItem>, isAvailable: (NavItem) -> Boolean): NavItem? =
		resolve(startPage.takeUnless { it == LAST }, null, barItems, isAvailable)
}
