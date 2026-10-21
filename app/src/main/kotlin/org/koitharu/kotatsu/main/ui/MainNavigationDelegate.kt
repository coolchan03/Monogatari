package org.koitharu.kotatsu.main.ui

import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.view.iterator
import androidx.core.view.size
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.bookmarks.ui.AllBookmarksFragment
import org.koitharu.kotatsu.core.media.BookCatalogFragment
import org.koitharu.kotatsu.core.media.BookLibraryFragment
import org.koitharu.kotatsu.core.media.MediaFeedFragment
import org.koitharu.kotatsu.core.media.UnifiedLibraryFragment
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.NavItem
import org.koitharu.kotatsu.core.qa.NewsFeedFragment
import org.koitharu.kotatsu.core.ui.util.RecyclerViewOwner
import org.koitharu.kotatsu.core.ui.widgets.FloatingBottomNavigationView
import org.koitharu.kotatsu.core.ui.widgets.SlidingBottomNavigationView
import org.koitharu.kotatsu.core.util.ext.buildBundle
import org.koitharu.kotatsu.core.util.ext.setContentDescriptionAndTooltip
import org.koitharu.kotatsu.core.util.ext.smoothScrollToTop
import org.koitharu.kotatsu.databinding.NavigationRailFabBinding
import org.koitharu.kotatsu.discover.DiscoverFragment
import org.koitharu.kotatsu.discover.media.DiscoverBooksFragment
import org.koitharu.kotatsu.discover.media.DiscoverVideoFragment
import org.koitharu.kotatsu.download.ui.list.DownloadsActivity
import org.koitharu.kotatsu.explore.ui.ExploreFragment
import org.koitharu.kotatsu.favourites.ui.container.FavouritesContainerFragment
import org.koitharu.kotatsu.history.ui.HistoryListFragment
import org.koitharu.kotatsu.local.ui.LocalListFragment
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.settings.sources.catalog.SourcesCatalogActivity
import org.koitharu.kotatsu.stats.ui.StatsActivity
import org.koitharu.kotatsu.suggestions.ui.SuggestionsFragment
import org.koitharu.kotatsu.tracker.ui.feed.FeedFragment
import org.koitharu.kotatsu.tracker.ui.updates.UpdatesFragment
import java.util.LinkedList
import com.google.android.material.R as materialR

private const val TAG_PRIMARY = "primary"

class MainNavigationDelegate(
	private val navBar: NavigationBarView,
	private val fragmentManager: FragmentManager,
	private val settings: AppSettings,
	/** Opens the main screen's search view; the Search navigation item does nothing without it. */
	private val onSearchRequested: (() -> Unit)? = null,
) : OnBackPressedCallback(false),
	NavigationBarView.OnItemSelectedListener,
	NavigationBarView.OnItemReselectedListener, View.OnClickListener {

	private val listeners = LinkedList<OnFragmentChangedListener>()

	/** The configured items that are on this navigation face, in order (More added when some did not fit). */
	private var shownItems: List<NavItem> = emptyList()

	/** Configured items with no room on this navigation face; the More page lists them instead. */
	private var overflowItems: List<NavItem> = emptyList()
	val navRailHeader = (navBar as? NavigationRailView)?.headerView?.let {
		NavigationRailFabBinding.bind(it)
	}

	val primaryFragment: Fragment?
		get() = fragmentManager.findFragmentByTag(TAG_PRIMARY)

	init {
		navBar.setOnItemSelectedListener(this)
		navBar.setOnItemReselectedListener(this)
		navRailHeader?.run {
			root.updateLayoutParams<FrameLayout.LayoutParams> {
				gravity = Gravity.TOP or Gravity.CENTER
			}
			val horizontalPadding = (navBar as NavigationRailView).itemActiveIndicatorMarginHorizontal
			root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			buttonExpand.setOnClickListener(this@MainNavigationDelegate)
			buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
			railFab.isExtended = false
			railFab.isAnimationEnabled = false
		}
	}

	override fun onNavigationItemSelected(item: MenuItem): Boolean {
		return if (onNavigationItemSelected(item.itemId)) {
			item.isChecked = true
			true
		} else {
			false
		}
	}

	override fun onNavigationItemReselected(item: MenuItem) {
		val fragment = primaryFragment
		if (fragment != null && getItemId(fragment) != item.itemId) {
			// A destination from the More page is showing while this item stayed checked: go back to the item.
			onNavigationItemSelected(item.itemId)
		} else {
			onNavigationItemReselected()
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_expand -> {
				if (navBar is NavigationRailView) {
					setNavbarIsExpanded(!navBar.isExpanded)
				}
			}
		}
	}

	override fun handleOnBackPressed() {
		navBar.selectedItemId = firstItem()?.itemId ?: return
	}

	fun onCreate(lifecycleOwner: LifecycleOwner, savedInstanceState: Bundle?) {
		splitItems(settings.mainNavItems, navBar.maxItemCount).let { (shown, overflow) ->
			shownItems = shown
			overflowItems = overflow
		}
		if (navBar.menu.isEmpty()) {
			createMenu(shownItems, navBar.menu)
		}
		observeSettings(lifecycleOwner)
		val fragment = primaryFragment
		if (fragment != null) {
			onFragmentChanged(fragment, fromUser = false)
			val itemId = getItemId(fragment)
			if (navBar.selectedItemId != itemId) {
				navBar.selectedItemId = itemId
			}
		} else {
			val itemId = if (savedInstanceState == null) {
				firstItem()?.itemId ?: navBar.selectedItemId
			} else {
				navBar.selectedItemId
			}
			if (itemId == 0 || isShortcut(itemId)) {
				// Only shortcuts (More, Search, Settings...) are configured: never open one of them on launch.
				setPrimaryFragment(ExploreFragment::class.java)
			} else {
				onNavigationItemSelected(itemId)
			}
		}
	}

	/**
	 * Shows a navigation destination by its [NavItem] name, e.g. one picked from the More page because there was no
	 * room for it on the bar. Items on the bar are selected as if tapped; the others are opened directly, which
	 * leaves the bar's current selection as it is.
	 */
	fun openNavItem(name: String) {
		val item = NavItem.entries.firstOrNull { it.name == name } ?: return
		if (navBar.menu.findItem(item.id)?.isVisible == true) {
			navBar.selectedItemId = item.id
		} else {
			onNavigationItemSelected(item.id)
		}
	}

	fun observeTitle() = callbackFlow {
		val listener = OnFragmentChangedListener { f, _ ->
			trySendBlocking(getItemId(f))
		}
		addOnFragmentChangedListener(listener)
		awaitClose { removeOnFragmentChangedListener(listener) }
	}.map {
		navBar.menu.findItem(it)?.title
	}

	fun setCounter(item: NavItem, counter: Int) {
		setCounter(item.id, counter)
	}

	/** Switches to the Explore (source browsing) tab, e.g. from the Discover screen. */
	fun openExplore() {
		navBar.selectedItemId = R.id.nav_explore
	}

	/** Switches to the Discover tab when it is on the navigation bar; returns false when it is not. */
	fun openDiscover(): Boolean {
		val item = navBar.menu.findItem(R.id.nav_discover)?.takeIf { it.isVisible } ?: return false
		navBar.selectedItemId = item.itemId
		return true
	}

	fun syncSelectedItem() {
		val fragment = primaryFragment ?: return
		onFragmentChanged(fragment, fromUser = false)
		val itemId = getItemId(fragment)
		if (navBar.selectedItemId != itemId) {
			navBar.selectedItemId = itemId
		}
	}

	private fun setCounter(@IdRes id: Int, counter: Int) {
		// The Compose face draws its own badges; the MenuItem ones only show in legacy mode.
		(navBar as? FloatingBottomNavigationView)?.setComposeBadge(id, counter)
		// Material throws for a badge on an item that is not on the bar (for example one moved under More).
		navBar.menu.findItem(id) ?: return
		if (counter == 0) {
			navBar.getBadge(id)?.isVisible = false
		} else {
			val badge = navBar.getOrCreateBadge(id)
			if (counter < 0) {
				badge.clearNumber()
			} else {
				badge.number = counter
			}
			badge.isVisible = true
		}
	}

	fun setItemVisibility(@IdRes itemId: Int, isVisible: Boolean) {
		val item = navBar.menu.findItem(itemId) ?: return
		item.isVisible = isVisible
		if (item.isChecked && !isVisible) {
			navBar.selectedItemId = firstItem()?.itemId ?: return
		}
	}

	fun addOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.add(listener)
	}

	fun removeOnFragmentChangedListener(listener: OnFragmentChangedListener) {
		listeners.remove(listener)
	}

	private fun onNavigationItemSelected(@IdRes itemId: Int): Boolean {
		val context = navBar.context
		val page = when (itemId) {
			R.id.nav_more -> Intent(context, MoreActivity::class.java).putStringArrayListExtra(
				MoreActivity.EXTRA_OVERFLOW_ITEMS,
				overflowItems.filter { it.isAvailable(settings) }.mapTo(ArrayList()) { it.name },
			)

			R.id.nav_downloads -> Intent(context, DownloadsActivity::class.java)
			R.id.nav_stats -> Intent(context, StatsActivity::class.java)
			R.id.nav_sources -> Intent(context, SourcesCatalogActivity::class.java)
			R.id.nav_settings -> Intent(context, SettingsActivity::class.java)
			R.id.nav_search -> {
				onSearchRequested?.invoke()
				return false // Search opens over the current tab, which stays checked.
			}

			else -> null
		}
		if (page != null) {
			context.startActivity(page)
			return false // These open their own page; keep the current content tab checked.
		}
		val newFragment = when (itemId) {
			R.id.nav_books -> BookLibraryFragment::class.java
			R.id.nav_discover -> DiscoverFragment::class.java
			R.id.nav_discover_books -> DiscoverBooksFragment::class.java
			R.id.nav_discover_video -> DiscoverVideoFragment::class.java
			R.id.nav_history -> HistoryListFragment::class.java
			R.id.nav_favorites -> FavouritesContainerFragment::class.java
			R.id.nav_explore -> ExploreFragment::class.java
			R.id.nav_feed -> FeedFragment::class.java
			R.id.nav_local -> LocalListFragment::class.java
			R.id.nav_suggestions -> SuggestionsFragment::class.java
			R.id.nav_bookmarks -> AllBookmarksFragment::class.java
			R.id.nav_updated -> UpdatesFragment::class.java
			R.id.nav_news -> NewsFeedFragment::class.java
			R.id.nav_media_feeds -> MediaFeedFragment::class.java
			R.id.nav_unified_library -> UnifiedLibraryFragment::class.java
			R.id.nav_book_catalog -> BookCatalogFragment::class.java
			else -> return false
		}
		if (!setPrimaryFragment(newFragment)) {
			// probably already selected
			onNavigationItemReselected()
		}
		return true
	}

	private fun getItemId(fragment: Fragment) = when (fragment) {
		is BookLibraryFragment -> R.id.nav_books
		is DiscoverFragment -> R.id.nav_discover
		is DiscoverBooksFragment -> R.id.nav_discover_books
		is DiscoverVideoFragment -> R.id.nav_discover_video
		is HistoryListFragment -> R.id.nav_history
		is FavouritesContainerFragment -> R.id.nav_favorites
		is ExploreFragment -> R.id.nav_explore
		is FeedFragment -> R.id.nav_feed
		is LocalListFragment -> R.id.nav_local
		is SuggestionsFragment -> R.id.nav_suggestions
		is AllBookmarksFragment -> R.id.nav_bookmarks
		is UpdatesFragment -> R.id.nav_updated
		is NewsFeedFragment -> R.id.nav_news
		is MediaFeedFragment -> R.id.nav_media_feeds
		is UnifiedLibraryFragment -> R.id.nav_unified_library
		is BookCatalogFragment -> R.id.nav_book_catalog
		else -> 0
	}

	private fun isShortcut(@IdRes itemId: Int): Boolean =
		NavItem.entries.firstOrNull { it.id == itemId }?.isShortcut == true

	private fun setPrimaryFragment(fragmentClass: Class<out Fragment>): Boolean {
		if (fragmentManager.isStateSaved || fragmentClass.isInstance(primaryFragment)) {
			return false
		}
		val fragment = instantiateFragment(fragmentClass)
		val args = buildBundle(1) {
			putBoolean(AppRouter.KEY_IS_BOTTOMTAB, true)
		}
		fragment.enterTransition = MaterialFadeThrough()
		fragmentManager.beginTransaction()
			.setReorderingAllowed(true)
			.replace(R.id.container, fragmentClass, args, TAG_PRIMARY)
			.runOnCommit { onFragmentChanged(fragment, fromUser = true) }
			.commit()
		return true
	}

	private fun onNavigationItemReselected() {
		val recyclerView = (primaryFragment as? RecyclerViewOwner)?.recyclerView ?: return
		recyclerView.smoothScrollToTop()
	}

	private fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		isEnabled = getItemId(fragment) != firstItem()?.itemId
		listeners.forEach { it.onFragmentChanged(fragment, fromUser) }
	}

	/** [items] were already fitted to this face by [splitItems]; the size check is only a safety net. */
	private fun createMenu(items: List<NavItem>, menu: Menu) {
		for (item in items) {
			if (menu.size >= navBar.maxItemCount) {
				break
			}
			menu.add(Menu.NONE, item.id, Menu.NONE, item.title)
				.setIcon(item.icon)
		}
	}

	private fun instantiateFragment(fragmentClass: Class<out Fragment>): Fragment {
		val classLoader = navBar.context.classLoader
		return fragmentManager.fragmentFactory.instantiate(classLoader, fragmentClass.name)
	}

	private fun observeSettings(lifecycleOwner: LifecycleOwner) {
		settings.observe(
			AppSettings.KEY_TRACKER_ENABLED,
			AppSettings.KEY_SUGGESTIONS,
			AppSettings.KEY_NAV_LABELS,
			AppSettings.KEY_FLOATING_NAV,
		).onEach {
				setItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
				setItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				val isFloating = settings.isFloatingNavBar
				val isLabeled = settings.isNavLabelsVisible
				if (navBar is FloatingBottomNavigationView) {
					// The Compose face renders its own items, so it needs the menu contents pushed to
					// it separately - the MenuItems themselves only drive the legacy fallback.
					navBar.setUseLegacyNavigation(!isFloating)
					navBar.setComposeItems(shownItems)
					navBar.setComposeLabeled(isLabeled)
					navBar.setComposeItemVisibility(R.id.nav_suggestions, settings.isSuggestionsEnabled)
					navBar.setComposeItemVisibility(R.id.nav_feed, settings.isTrackerEnabled)
				}
				// The floating pill is a bottom-bar concept; the rail on large screens ignores it.
				setNavbarIsLabeled(
					value = if (isFloating && navBar !is FloatingBottomNavigationView) false else isLabeled,
				)
			}.launchIn(lifecycleOwner.lifecycleScope)
	}

	private fun firstItem(): MenuItem? {
		val menu = navBar.menu
		for (item in menu) {
			if (item.isVisible && !isShortcut(item.itemId)) return item
		}
		return null
	}

	/**
	 * The floating bar labels only the selected item. Labelling every item there would widen the pill
	 * past the room it has between its margins, which is why it used to be left unlabelled outright —
	 * but that also meant the section you were on went unnamed even with labels turned on.
	 */
	private fun setNavbarIsLabeled(value: Boolean) {
		if (navBar is SlidingBottomNavigationView) {
			navBar.minimumHeight = navBar.resources.getDimensionPixelSize(
				if (value) {
					materialR.dimen.m3_bottom_nav_min_height
				} else {
					R.dimen.nav_bar_height_compact
				},
			)
		}
		navRailHeader?.buttonExpand?.isVisible = value
		if (!value) {
			setNavbarIsExpanded(false)
		}
		navBar.labelVisibilityMode = if (value) {
			// Six or more labelled items get too narrow for their text on a phone's bottom bar, so only the
			// selected one keeps its label there. The rail stacks items vertically and has room for all of them.
			val visibleCount = navBar.menu.iterator().asSequence().count { it.isVisible }
			if (navBar !is NavigationRailView && visibleCount > MAX_FULLY_LABELED_ITEMS) {
				NavigationBarView.LABEL_VISIBILITY_SELECTED
			} else {
				NavigationBarView.LABEL_VISIBILITY_LABELED
			}
		} else {
			NavigationBarView.LABEL_VISIBILITY_UNLABELED
		}
		// Desktop-width windows start with the labelled side drawer; the expand button still collapses it.
		if (NavLayoutPolicy.expandRail(navBar.resources.configuration.screenWidthDp, value)) {
			setNavbarIsExpanded(true)
		}
	}

	private fun setNavbarIsExpanded(value: Boolean) {
		if (navBar !is NavigationRailView) {
			return
		}
		if (value) {
			navBar.expand()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.START
				}
				railFab.extend()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu_open)
				buttonExpand.setContentDescriptionAndTooltip(R.string.collapse)
				val horizontalPadding = navBar.itemActiveIndicatorExpandedMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		} else {
			navBar.collapse()
			navRailHeader?.run {
				root.updateLayoutParams<FrameLayout.LayoutParams> {
					gravity = Gravity.TOP or Gravity.CENTER
				}
				railFab.shrink()
				buttonExpand.setImageResource(R.drawable.ic_drawer_menu)
				buttonExpand.setContentDescriptionAndTooltip(R.string.expand)
				val horizontalPadding = navBar.itemActiveIndicatorMarginHorizontal
				root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
			}
		}
	}

	fun interface OnFragmentChangedListener {

		fun onFragmentChanged(fragment: Fragment, fromUser: Boolean)
	}

	companion object {

		/** How many items can be configured. A face with less room puts the rest under More, see [splitItems]. */
		const val MAX_ITEM_COUNT = 8

		private const val MAX_FULLY_LABELED_ITEMS = 5

		/**
		 * Fits the configured [items] to a navigation face that shows at most [maxCount] items. When they do not all
		 * fit, the first ones stay in their configured order, More takes the last slot (or keeps its own place when
		 * it was configured early) and the rest are returned as overflow, to be listed on the More page.
		 */
		fun splitItems(items: List<NavItem>, maxCount: Int): Pair<List<NavItem>, List<NavItem>> {
			if (maxCount <= 0 || items.size <= maxCount) {
				return items to emptyList()
			}
			val others = items.filter { it != NavItem.MORE }
			val kept = others.take((maxCount - 1).coerceAtLeast(0)).toSet()
			val shown = items.filter { it == NavItem.MORE || it in kept }.let {
				if (NavItem.MORE in it) it else it + NavItem.MORE
			}
			return shown to others.filterNot { it in kept }
		}
	}
}
