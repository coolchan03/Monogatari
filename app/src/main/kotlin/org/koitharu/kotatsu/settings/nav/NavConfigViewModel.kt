package org.koitharu.kotatsu.settings.nav

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.NavItem
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.util.ActivityRecreationHandle
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.main.ui.MainActivity
import org.koitharu.kotatsu.main.ui.MainNavigationDelegate
import org.koitharu.kotatsu.parsers.util.move
import org.koitharu.kotatsu.settings.nav.model.NavItemAddModel
import org.koitharu.kotatsu.settings.nav.model.NavItemConfigModel
import org.koitharu.kotatsu.settings.nav.model.NavItemGroupHeader
import javax.inject.Inject

@HiltViewModel
class NavConfigViewModel @Inject constructor(
	private val settings: AppSettings,
	private val activityRecreationHandle: ActivityRecreationHandle,
) : BaseViewModel() {

	private val items = MutableStateFlow(settings.mainNavItems)

	val content: StateFlow<List<ListModel>> = items.map { snapshot ->
		buildList(snapshot.size + 1) {
			snapshot.mapTo(this) {
				NavItemConfigModel(it, getUnavailabilityHint(it))
			}
			if (OFFERED_GROUPS.any { (_, group) -> group.any { it !in snapshot } }) {
				add(NavItemAddModel(size < MainNavigationDelegate.MAX_ITEM_COUNT))
			}
		}
	}.stateIn(
		viewModelScope + Dispatchers.Default,
		SharingStarted.WhileSubscribed(5000),
		emptyList(),
	)

	private var commitJob: Job? = null

	/**
	 * What the Add dialog offers: a short list in two groups, each preceded by its [NavItemGroupHeader]. Older
	 * items (Local, Feed, the separate book/video Discover pages...) are no longer offered, but a bar that already
	 * has them keeps them, since bars are saved by item name.
	 */
	val availableItems: List<Any>
		get() = items.value.let { snapshot ->
			buildList {
				for ((title, group) in OFFERED_GROUPS) {
					val free = group.filterNot { x -> x in snapshot }
					if (free.isNotEmpty()) {
						add(NavItemGroupHeader(title))
						addAll(free)
					}
				}
			}
		}

	fun reorder(fromPos: Int, toPos: Int) {
		items.value = items.value.toMutableList().apply {
			move(fromPos, toPos)
			commit(this)
		}
	}

	fun addItem(item: NavItem) {
		items.value = items.value.plus(item).also {
			commit(it)
		}
	}

	/** Puts back the bar a new install starts with. */
	fun restoreDefault() {
		val defaults = DEFAULT_ITEMS
		items.value = defaults
		commit(defaults)
	}

	fun removeItem(item: NavItem) {
		val newList = items.value.toMutableList()
		newList.remove(item)
		if (newList.isEmpty()) {
			newList.add(NavItem.EXPLORE)
		}
		items.value = newList
		commit(newList)
	}

	private fun commit(value: List<NavItem>) {
		val prevJob = commitJob
		commitJob = launchJob {
			prevJob?.cancelAndJoin()
			delay(500)
			settings.mainNavItems = value
			activityRecreationHandle.recreate(MainActivity::class.java)
		}
	}

	private fun getUnavailabilityHint(item: NavItem) = if (item.isAvailable(settings)) {
		0
	} else when (item) {
		NavItem.FEED -> R.string.check_for_new_chapters_disabled
		NavItem.SUGGESTIONS -> R.string.suggestions_unavailable_text
		else -> 0
	}

	private companion object {

		/** The same items, in the same order, as [AppSettings.mainNavItems] gives when nothing was saved. */
		val DEFAULT_ITEMS = AppSettings.DEFAULT_NAV_ITEMS

		val OFFERED_GROUPS: List<Pair<Int, List<NavItem>>> = listOf(
			R.string.nav_config_group_tabs to listOf(
				NavItem.UNIFIED_LIBRARY,
				NavItem.FAVORITES,
				NavItem.HISTORY,
				NavItem.UPDATED,
				NavItem.DISCOVER,
				NavItem.EXPLORE,
				NavItem.BOOKMARKS,
				NavItem.BOOKS,
				NavItem.NEWS,
				NavItem.MEDIA_FEEDS,
			),
			R.string.nav_config_group_shortcuts to listOf(
				NavItem.SEARCH,
				NavItem.DOWNLOADS,
				NavItem.SETTINGS,
				NavItem.MORE,
			),
		)
	}
}
