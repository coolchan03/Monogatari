package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.db.entity.toManga
import org.koitharu.kotatsu.core.db.entity.toMangaTags
import org.koitharu.kotatsu.core.image.CoilImageView
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.TapCounter
import org.koitharu.kotatsu.core.ui.bodyText
import org.koitharu.kotatsu.core.ui.primaryButton
import org.koitharu.kotatsu.core.ui.image.GeneratedCoverDrawable
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import org.koitharu.kotatsu.core.util.ext.setThemeTextAppearance
import org.koitharu.kotatsu.discover.DiscoverLibraryFinder
import org.koitharu.kotatsu.discover.RecommendationLoader
import org.koitharu.kotatsu.discover.addDiscoverItemToLibrary
import org.koitharu.kotatsu.favourites.data.toManga
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.smart.SmartCollectionRule
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.image.ui.CoverImageView
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.local.data.LocalStorageChanges
import org.koitharu.kotatsu.local.domain.DeleteLocalMangaUseCase
import org.koitharu.kotatsu.local.domain.model.LocalManga
import org.koitharu.kotatsu.main.ui.NavLayoutPolicy
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import org.koitharu.kotatsu.search.ui.suggestion.MainSearchScope
import javax.inject.Inject

private typealias Row = LibraryShelf.Item

/**
 * The main Library: everything the user saved or added, across manga, books, video and audio — favourites,
 * downloaded / "On device" titles, imported books and the user's own video and audio files — with a Continue strip
 * from history. Shown as a main navigation tab, as the Books tab ([BookLibraryFragment], locked to books), or inside
 * [UnifiedLibraryActivity] (its [UnifiedLibraryActivity.EXTRA_MEDIA_KIND] / [UnifiedLibraryActivity.EXTRA_FILTER]
 * extras arrive as arguments).
 */
@AndroidEntryPoint
class UnifiedLibraryFragment : Fragment() {

	companion object {
		private const val MENU_NEW_SMART = 7501
		private const val HISTORY_LIMIT = 300
		private const val KEY_SORT = "sort"

		/** Shows only this [MediaType] (by name) and hides the type chips; used by the Books tab. */
		const val ARG_LOCKED_KIND = "monogatari_library_locked_kind"
	}

	@Inject lateinit var db: MangaDatabase
	@Inject lateinit var favorites: FavouritesRepository
	@Inject lateinit var history: HistoryRepository
	@Inject lateinit var localManga: LocalMangaRepository
	@Inject lateinit var deleteLocalManga: DeleteLocalMangaUseCase
	@Inject @LocalStorageChanges lateinit var localStorageChanges: SharedFlow<LocalManga?>
	@Inject lateinit var settings: AppSettings
	@Inject lateinit var recommendations: RecommendationLoader
	@Inject lateinit var libraryFinder: DiscoverLibraryFinder

	private val fileImporter = LocalFileImportLauncher(this) { if (view != null && ::search.isInitialized) refresh() }
	private var all = emptyList<Row>()
	/** Most recently opened titles, newest first, for the Continue strip. */
	private var recent = emptyList<Row>()
	/** Imported books, audio and video with a saved position; they join the Continue strip. */
	private var resumable = emptyList<Row>()
	/** Sources that failed to load in the last refresh; everything else is still shown. */
	private var loadProblems = emptyList<String>()
	/** At 840dp and wider the library shows a details pane next to the list (#63). */
	private var twoPane = false
	private lateinit var paneCover: CoilImageView
	private lateinit var paneTitle: TextView
	private lateinit var paneMeta: TextView
	private lateinit var paneDescription: TextView
	private lateinit var paneOpen: MaterialButton
	private var paneRow: Row? = null
	private var restoredPaneKey: String? = null
	/** Collections: the user's own folders across all media (long-press a title to file it). */
	private var collections: Map<String, Set<String>> = emptyMap()
	private var selectedCollection: String? = null
	/** v1.5 draft: smart collections, name -> rule text (see [SmartCollectionRule]). */
	private var smartCollections: Map<String, String> = emptyMap()
	private var selectedSmart: String? = null
	private val collectionPrefs by lazy { requireContext().getSharedPreferences("library_collections", Context.MODE_PRIVATE) }
	private val uiPrefs by lazy { requireContext().getSharedPreferences("library_ui", Context.MODE_PRIVATE) }
	private val eggTaps = TapCounter()
	/** Null represents All media, including items with an unknown/older source type. */
	private var selected: MediaType? = null
	private var lockedKind: MediaType? = null
	private var sort = LibraryShelf.Sort.RECENTLY_ADDED
	private var refreshJob: Job? = null
	/** "On this device" toggle: only imported files and downloads. */
	private var onDeviceOnly = false
	/** Recommended cards per type chip (null key is All), kept for the fragment's lifetime; an empty list hides the strip. */
	private val recommended = HashMap<MediaType?, List<RecommendationLoader.Recommendation>>()
	private var recJob: Job? = null
	private var recType: MediaType? = null
	private var recLoading = false
	private var listStrip: RecommendedStrip? = null
	private lateinit var emptyStrip: RecommendedStrip
	private lateinit var search: EditText
	private lateinit var status: TextView
	private lateinit var loading: LinearProgressIndicator
	private lateinit var list: RecyclerView
	private lateinit var layoutManager: GridLayoutManager
	private lateinit var emptyView: LinearLayout
	private lateinit var emptyMascot: ImageView
	private lateinit var emptyText: TextView
	private lateinit var collectionRow: ChipGroup
	private val adapter = Entries()
	private val buttons = mutableListOf<Pair<MediaType?, Chip>>()
	private val isBottomTab: Boolean
		get() = arguments?.getBoolean(AppRouter.KEY_IS_BOTTOMTAB, false) == true

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val context = requireContext()
		buttons.clear()
		lockedKind = parseKind(arguments?.getString(ARG_LOCKED_KIND))
		val requestedKind = lockedKind ?: parseKind(arguments?.getString(UnifiedLibraryActivity.EXTRA_MEDIA_KIND))
		selected = when {
			lockedKind != null -> lockedKind
			savedInstanceState?.containsKey("selected_kind") == true -> parseKind(savedInstanceState?.getString("selected_kind"))
			else -> requestedKind
		}
		sort = runCatching { LibraryShelf.Sort.valueOf(uiPrefs.getString(KEY_SORT, null).orEmpty()) }
			.getOrDefault(LibraryShelf.Sort.RECENTLY_ADDED)
		restoredPaneKey = savedInstanceState?.getString("pane_row_key")
		onDeviceOnly = savedInstanceState?.getBoolean("on_device_only", false) ?: false
		twoPane = NavLayoutPolicy.useTwoPane(resources.configuration.screenWidthDp)
		val root = inflater.inflate(R.layout.fragment_unified_library, container, false)
		bindHeader(root, savedInstanceState)
		bindList(root)
		collections = LibraryCollections.decode(collectionPrefs.getString("data", null))
		smartCollections = decodeSmart(collectionPrefs.getString("smart", null))
		selectedSmart = savedInstanceState?.getString("selected_smart")?.takeIf { it in smartCollections }
		selectedCollection = savedInstanceState?.getString("selected_collection")?.takeIf { it in collections }
		// onResume refreshes when the screen first opens and when a reader changes progress.
		return if (twoPane) {
			val px = resources.getDimensionPixelSize(R.dimen.library_gutter)
			val split = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
			split.addView(root, LinearLayout.LayoutParams(0, -1, 1f))
			split.addView(buildPane(px), LinearLayout.LayoutParams(0, -1, 1f))
			split
		} else {
			root
		}
	}

	private fun parseKind(name: String?): MediaType? =
		MediaType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

	private fun bindHeader(root: View, savedInstanceState: Bundle?) {
		val context = root.context
		search = root.findViewById(R.id.library_search)
		search.setText(savedInstanceState?.getString("library_query") ?: arguments?.getString(UnifiedLibraryActivity.EXTRA_FILTER).orEmpty())
		search.doAfterTextChanged { render() }
		// Books, comics, videos and audio from the device (also in the overflow menu).
		root.findViewById<MaterialButton>(R.id.library_add_files).setOnClickListener { fileImporter.launchOrToast() }
		root.findViewById<MaterialButton>(R.id.library_empty_add).setOnClickListener { fileImporter.launchOrToast() }
		root.findViewById<MaterialButton>(R.id.library_sort).setOnClickListener { chooseSort() }
		root.findViewById<MaterialButton>(R.id.library_empty_find_books).apply {
			isVisible = lockedKind == MediaType.BOOK
			setOnClickListener { openBookCatalog() }
		}
		val tabs = root.findViewById<ChipGroup>(R.id.monogatari_library_tabs)
		root.findViewById<View>(R.id.library_tabs_scroll).isVisible = lockedKind == null
		if (lockedKind == null) {
			for (type in listOf<MediaType?>(null) + MediaType.entries) {
				// Filter chips, matching search and Discover; each shows how many items it holds.
				val chip = Chip(context).apply {
					text = kindLabel(type)
					isCheckable = true
					if (type != null) MediaAccents.tintChip(context, type.name, this)
					setOnClickListener { selected = type; followSearchScope(); render() }
				}
				buttons += type to chip
				tabs.addView(chip)
			}
		}
		collectionRow = root.findViewById(R.id.library_collections)
		status = root.findViewById(R.id.library_status)
		loading = root.findViewById(R.id.library_loading)
		emptyView = root.findViewById(R.id.library_empty)
		emptyMascot = root.findViewById(R.id.library_empty_mascot)
		emptyText = root.findViewById(R.id.library_empty_text)
		// A fresh install is not blank: Recommended sits below the mascot, with room for the floating bar.
		emptyStrip = RecommendedStrip(LayoutInflater.from(context).inflate(R.layout.item_library_recommended, emptyView, false))
		emptyView.addView(emptyStrip.root)
		emptyView.updatePadding(bottom = emptyView.paddingBottom + resources.getDimensionPixelSize(
			if (isBottomTab) R.dimen.library_bottom_padding_tab else R.dimen.library_bottom_padding,
		))
		// Kitsune keeps an empty library company, in the form of the selected tab. Tap her quickly for a surprise.
		emptyMascot.setOnClickListener {
			if (eggTaps.tap(android.os.SystemClock.uptimeMillis())) Mascot.showEasterEgg(requireActivity())
		}
	}

	private fun bindList(root: View) {
		list = root.findViewById(R.id.monogatari_library_results_list)
		layoutManager = GridLayoutManager(root.context, initialColumns())
		layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
			override fun getSpanSize(position: Int): Int =
				if (adapter.isFullWidth(position)) layoutManager.spanCount else 1
		}
		list.layoutManager = layoutManager
		list.adapter = adapter
		// Room for the floating navigation bar, which is drawn over the main screen's content.
		list.updatePadding(bottom = resources.getDimensionPixelSize(
			if (isBottomTab) R.dimen.library_bottom_padding_tab else R.dimen.library_bottom_padding,
		))
		list.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
			if (right - left != oldRight - oldLeft) {
				val columns = columnsFor(view.width - view.paddingLeft - view.paddingRight)
				if (columns != layoutManager.spanCount) view.post { layoutManager.spanCount = columns }
			}
		}
	}

	private fun initialColumns(): Int {
		val width = resources.displayMetrics.widthPixels
		return columnsFor(if (twoPane) width / 2 else width)
	}

	private fun columnsFor(widthPx: Int): Int {
		val min = resources.getDimensionPixelSize(R.dimen.library_cover_min_width) +
			2 * resources.getDimensionPixelSize(R.dimen.library_card_spacing)
		return (widthPx / min.coerceAtLeast(1)).coerceIn(2, 8)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(LibraryMenuProvider())
		addMenuProvider(fileImporter.menuProvider())
		// Comics from Add files are copied in the background; show them as soon as each one lands.
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				localStorageChanges.collect { refresh() }
			}
		}
	}

	private fun kindLabel(type: MediaType?): String = getString(
		when (type) {
			null -> R.string.library_ov_chip_all
			MediaType.MANGA -> R.string.library_ov_chip_manga
			MediaType.BOOK -> R.string.library_ov_chip_books
			MediaType.VIDEO -> R.string.library_ov_chip_video
			MediaType.AUDIO -> R.string.library_ov_chip_audio
		},
	)

	private fun kindIcon(type: MediaType): Int = when (type) {
		MediaType.MANGA -> R.drawable.ic_manga_source
		MediaType.VIDEO -> R.drawable.ic_play
		MediaType.AUDIO -> R.drawable.ic_play
		MediaType.BOOK -> R.drawable.ic_monogatari_nav_books
	}

	private fun chooseSort() {
		val options = LibraryShelf.Sort.entries
		val labels = options.map {
			getString(
				when (it) {
					LibraryShelf.Sort.RECENTLY_ADDED -> R.string.library_ov_sort_added
					LibraryShelf.Sort.RECENTLY_READ -> R.string.library_ov_sort_read
					LibraryShelf.Sort.TITLE -> R.string.library_ov_sort_title
				},
			)
		}.toTypedArray()
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.library_ov_sort)
			.setSingleChoiceItems(labels, options.indexOf(sort)) { dialog, which ->
				sort = options[which]
				uiPrefs.edit().putString(KEY_SORT, sort.name).apply()
				render()
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun openBookCatalog() {
		startActivity(Intent(requireContext(), BookLibraryActivity::class.java))
	}

	private fun buildPane(px: Int): View {
		val column = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(px, px, px, px)
		}
		paneCover = CoilImageView(requireContext()).apply {
			scaleType = ImageView.ScaleType.FIT_CENTER
			contentDescription = getString(R.string.library_pane_cover)
		}
		paneTitle = TextView(requireContext()).apply {
			setThemeTextAppearance(
				com.google.android.material.R.attr.textAppearanceHeadlineSmall,
				com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall,
			)
			// TalkBack announces the newly selected title when the details pane changes.
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
		}
		paneMeta = requireContext().bodyText(null)
		paneDescription = requireContext().bodyText(null)
		paneOpen = requireContext().primaryButton(getString(R.string.library_pane_open)) { paneRow?.let(::openRow) }
		column.addView(paneCover, LinearLayout.LayoutParams(-1, resources.getDimensionPixelSize(R.dimen.library_pane_cover_height)))
		column.addView(paneTitle, LinearLayout.LayoutParams(-1, -2))
		column.addView(paneMeta, LinearLayout.LayoutParams(-1, -2))
		column.addView(paneOpen, LinearLayout.LayoutParams(-2, -2))
		column.addView(ScrollView(requireContext()).apply { addView(paneDescription) }, LinearLayout.LayoutParams(-1, 0, 1f))
		showPane(null)
		return column
	}

	private fun showPane(row: Row?) {
		paneRow = row
		if (row == null) {
			paneCover.setImageDrawable(null)
			paneTitle.setText(R.string.library_pane_empty)
			paneMeta.text = ""
			paneDescription.text = ""
			paneOpen.isVisible = false
			return
		}
		val coverUrl = row.manga?.coverUrl ?: row.coverFile?.toUri()?.toString()
		if (coverUrl != null) {
			paneCover.setImageAsync(coverUrl)
		} else {
			paneCover.disposeImage()
			paneCover.setImageDrawable(GeneratedCoverDrawable(row.title, row.manga?.authors?.firstOrNull(), row.kind))
		}
		paneTitle.text = row.title
		paneMeta.text = getString(R.string.library_ov_detail_join, kindLabel(row.kind), row.detail)
		val description = row.manga?.description?.takeIf { it.isNotBlank() }
		paneDescription.text = if (description != null) {
			HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_COMPACT)
		} else {
			getString(R.string.library_pane_no_description)
		}
		paneOpen.isVisible = true
	}

	private inner class LibraryMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: android.view.Menu, menuInflater: MenuInflater) {
			menu.add(0, MENU_NEW_SMART, 0, R.string.library_smart_new)
		}

		override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
			if (menuItem.itemId != MENU_NEW_SMART) return false
			newSmartCollection()
			return true
		}
	}

	private fun newSmartCollection() {
		val pad = resources.getDimensionPixelSize(R.dimen.library_gutter)
		val name = android.widget.EditText(requireContext()).apply { setHint(R.string.library_smart_name_hint); setSingleLine(true) }
		val rule = android.widget.EditText(requireContext()).apply { setHint(R.string.library_smart_rule_hint) }
		val form = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad / 2, pad, 0)
			addView(name)
			addView(rule)
			addView(TextView(requireContext()).apply { setText(R.string.library_smart_help) })
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.library_smart_new)
			.setView(form)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val title = name.text.toString().trim().take(40)
				val text = SmartCollectionRule.parse(rule.text.toString()).encode()
				if (title.isNotEmpty() && text.isNotEmpty()) {
					saveSmart(smartCollections + (title to text))
					selectedSmart = title
					selectedCollection = null
					render()
				}
			}
			.show()
	}

	/** What a smart rule can see about a library row; unread counts are not tracked here yet. */
	private fun smartItem(row: Row) = SmartCollectionRule.Item(
		title = row.title,
		tags = row.manga?.tags?.map { it.title }.orEmpty(),
		mediaType = row.kind.name,
		source = row.manga?.source?.name.orEmpty(),
		status = row.manga?.state?.name,
		unreadChapters = 0,
		favourite = row.favourite,
	)

	private fun saveSmart(value: Map<String, String>) {
		smartCollections = value
		collectionPrefs.edit().putString("smart", encodeSmart(value)).apply()
		if (selectedSmart != null && selectedSmart !in value) selectedSmart = null
		render()
	}

	private fun encodeSmart(value: Map<String, String>): String =
		value.entries.joinToString("\n") { (k, v) -> k.replace('\t', ' ').replace('\n', ' ') + "\t" + v.replace('\n', ' ') }

	private fun decodeSmart(raw: String?): Map<String, String> = raw.orEmpty().lineSequence()
		.mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() } }
		.associate { it[0] to it[1] }

	private fun keyOf(row: Row): String = row.key

	private fun saveCollections(value: Map<String, Set<String>>) {
		collections = value
		collectionPrefs.edit().putString("data", LibraryCollections.encode(value)).apply()
		if (selectedCollection != null && selectedCollection !in value) selectedCollection = null
		render()
	}

	/**
	 * Favourites categories (the existing categories screen), Find books on the Books tab, then one chip per smart
	 * collection and collection; tap to show only its titles, tap again for everything.
	 */
	private fun renderCollections() {
		collectionRow.removeAllViews()
		collectionRow.addView(Chip(requireContext()).apply {
			setText(R.string.library_rec_chip_on_device)
			setChipIconResource(R.drawable.ic_storage)
			isChipIconVisible = true
			isCheckable = true
			isChecked = onDeviceOnly
			setOnClickListener { onDeviceOnly = isChecked; render() }
		})
		collectionRow.addView(Chip(requireContext()).apply {
			setText(R.string.library_ov_categories)
			setChipIconResource(R.drawable.ic_monogatari_nav_favorites)
			isChipIconVisible = true
			setOnClickListener { router.openFavoriteCategories() }
		})
		if (lockedKind == MediaType.BOOK) {
			collectionRow.addView(Chip(requireContext()).apply {
				setText(R.string.library_ov_find_books)
				setChipIconResource(R.drawable.ic_monogatari_nav_books)
				isChipIconVisible = true
				setOnClickListener { openBookCatalog() }
			})
		}
		for ((name, rule) in smartCollections.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
			collectionRow.addView(Chip(requireContext()).apply {
				text = getString(R.string.library_smart_chip, name)
				isCheckable = true
				isChecked = name == selectedSmart
				contentDescription = getString(R.string.library_smart_description, name, rule)
				setOnClickListener {
					selectedSmart = if (isChecked) name else null
					if (selectedSmart != null) selectedCollection = null
					render()
				}
				setOnLongClickListener {
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(name)
						.setMessage(rule)
						.setPositiveButton(R.string.library_collection_delete) { _, _ ->
							saveSmart(smartCollections - name)
						}
						.setNegativeButton(android.R.string.cancel, null)
						.show()
					true
				}
			})
		}
		for (name in LibraryCollections.names(collections)) {
			collectionRow.addView(Chip(requireContext()).apply {
				text = getString(R.string.library_ov_chip_count, name, collections[name].orEmpty().size)
				isCheckable = true
				isChecked = name == selectedCollection
				setOnClickListener {
					selectedCollection = if (isChecked) name else null
					if (selectedCollection != null) selectedSmart = null
					render()
				}
				setOnLongClickListener {
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(name)
						.setPositiveButton(R.string.library_collection_delete) { _, _ -> saveCollections(LibraryCollections.delete(collections, name)) }
						.setNegativeButton(android.R.string.cancel, null)
						.show()
					true
				}
			})
		}
	}

	/** Long-press a title: tick the collections it belongs to, or start a new one. */
	private fun chooseCollections(row: Row) {
		val key = keyOf(row)
		if (key.isBlank()) return
		val names = LibraryCollections.names(collections)
		val checked = BooleanArray(names.size) { key in collections[names[it]].orEmpty() }
		val builder = MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.library_collection_title, row.title))
			.setNegativeButton(android.R.string.cancel, null)
			.setNeutralButton(R.string.library_collection_new) { _, _ -> newCollection(row) }
		if (names.isNotEmpty()) {
			builder.setMultiChoiceItems(names.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
				.setPositiveButton(android.R.string.ok) { _, _ ->
					var data = collections
					names.forEachIndexed { i, name ->
						data = if (checked[i]) LibraryCollections.add(data, name, key) else LibraryCollections.remove(data, name, key)
					}
					saveCollections(data)
				}
		} else {
			builder.setMessage(R.string.library_collection_none)
		}
		builder.show()
	}

	private fun newCollection(row: Row) {
		val input = EditText(requireContext()).apply { setSingleLine(true); setHint(R.string.library_collection_name_hint) }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.library_collection_new)
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				saveCollections(LibraryCollections.add(collections, input.text.toString(), keyOf(row)))
			}
			.show()
	}

	private fun openRow(row: Row) {
		if (row.media != null) {
			if (row.missing) confirmRemove(row) else startActivity(LocalMediaShelf.playerIntent(requireContext(), row.media))
			return
		}
		val intent = when {
			row.file != null -> BookShelf.readerIntent(requireContext(), row.file)
			row.manga != null -> AppRouter.detailsIntent(requireContext(), row.manga)
			else -> null
		}
		if (intent != null) startActivity(intent)
	}

	/** The same long-press menu for every kind of item: Open, Add to collection, Remove. */
	private fun showActions(row: Row, fromContinue: Boolean) {
		val actions = mutableListOf<Pair<String, () -> Unit>>(
			getString(R.string.library_ov_action_open) to { openRow(row) },
			getString(R.string.library_ov_action_collections) to { chooseCollections(row) },
		)
		if (fromContinue) {
			val manga = row.manga
			if (manga != null) actions += getString(R.string.library_ov_action_remove_continue) to { removeFromContinue(manga) }
		} else {
			actions += getString(R.string.add_files_remove) to { confirmRemove(row) }
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(row.title)
			.setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun confirmRemove(row: Row) {
		val message = when {
			row.missing -> R.string.add_files_missing_message
			row.media != null -> R.string.add_files_remove_media_message
			row.file != null -> R.string.add_files_remove_book_message
			row.favourite && row.onDevice -> R.string.library_ov_remove_favourite_on_device_message
			row.favourite -> R.string.library_ov_remove_favourite_message
			else -> R.string.library_ov_remove_on_device_message
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.add_files_remove_title, row.title))
			.setMessage(message)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add_files_remove) { _, _ -> remove(row) }
			.show()
	}

	private fun remove(row: Row) {
		val context = context?.applicationContext ?: return
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				withContext(Dispatchers.IO) {
					when {
						// Media is only forgotten; the file on the device is left alone.
						row.media != null -> LocalMediaShelf.remove(context, row.media.uri)
						// Books on the shelf are Monogatari's own copies in app storage.
						row.file != null -> row.file.delete()
						row.manga != null && row.favourite -> favorites.removeFromFavourites(setOf(row.manga.id))
						row.manga != null && row.onDevice -> deleteLocalManga(row.manga)
						else -> Unit
					}
					Unit
				}
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				showStatus(getString(R.string.library_ov_remove_failed, row.title, error.message.orEmpty().take(160)))
			}
			refresh()
		}
	}

	private fun removeFromContinue(manga: Manga) {
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				history.delete(setOf(manga.id))
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				showStatus(getString(R.string.library_ov_remove_failed, manga.title, error.message.orEmpty().take(160)))
			}
			refresh()
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString("selected_kind", selected?.name ?: "ALL")
		outState.putString("selected_collection", selectedCollection)
		outState.putString("selected_smart", selectedSmart)
		outState.putBoolean("on_device_only", onDeviceOnly)
		if (::search.isInitialized) outState.putString("library_query", search.text?.toString().orEmpty())
		paneRow?.let { outState.putString("pane_row_key", keyOf(it)) }
		super.onSaveInstanceState(outState)
	}

	override fun onDestroyView() {
		recJob?.cancel()
		recJob = null
		recLoading = false
		listStrip = null
		super.onDestroyView()
	}

	override fun onResume() {
		super.onResume()
		followSearchScope()
		if (view != null && ::search.isInitialized) refresh()
	}

	/** The main search bar searches the type chip shown here (All -> every type; the Books tab -> books). */
	private fun followSearchScope() {
		MainSearchScope.followSection(requireContext(), selected?.name)
	}

	private class Loaded(val items: List<Row>, val recent: List<Row>, val resumable: List<Row>, val problems: List<String>, val recovered: Int)

	/** Runs one part of the library load; a failure is reported but never hides the other parts. */
	private inline fun <T> part(problems: MutableList<String>, default: T, block: () -> T): T = try {
		block()
	} catch (cancel: CancellationException) {
		throw cancel
	} catch (error: Exception) {
		problems += error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
		default
	}

	/**
	 * Downloads are opened through the local placeholder source, so the original source (if the app has ever
	 * saved this title) tells whether a download is a comic, a novel, a video or audio.
	 */
	private suspend fun originalSourceName(mangaId: Long): String =
		runCatchingCancellable { db.getMangaDao().find(mangaId)?.manga?.source }.getOrNull().orEmpty()

	/** Everything the user added, from every place it can live; call off the main thread. */
	private suspend fun load(context: Context): Loaded {
		val problems = mutableListOf<String>()
		val groups = part(problems, emptyMap()) { MediaType.entries.associateWith { it.persistedSourceNames() } }
		val favouriteRows = part(problems, emptyList()) {
			db.getFavouritesDao().findAll().map { favourite ->
				val manga = favourite.toManga()
				Row(
					key = LibraryShelf.mangaKey(manga.id),
					title = manga.title,
					detail = manga.source.getTitle(context),
					kind = LibraryShelf.kindOf(manga.source.name, groups),
					origin = LibraryShelf.Origin.FAVOURITE,
					addedAt = favourite.favourite.createdAt,
					manga = manga,
				)
			}
		}
		val onDeviceLabel = context.getString(R.string.library_ov_detail_on_device)
		// Downloads and comics imported with Add files ("On device"), which are not favourites by themselves.
		val onDeviceRows = part(problems, emptyList()) {
			localManga.getList(0, SortOrder.NEWEST, null).map { manga ->
				Row(
					key = LibraryShelf.mangaKey(manga.id),
					title = manga.title,
					detail = onDeviceLabel,
					kind = LibraryShelf.kindOf(originalSourceName(manga.id), groups),
					origin = LibraryShelf.Origin.ON_DEVICE,
					addedAt = runCatching { LocalManga(manga).createdAt }.getOrDefault(0L),
					manga = manga,
				)
			}
		}
		val recovered = part(problems, 0) { BookShelf.recoverUnlisted(context) }
		val bookRows = part(problems, emptyList()) {
			BookShelf.list(context).map { file ->
				Row(
					key = file.absolutePath,
					title = BookShelf.title(file),
					detail = context.getString(
						R.string.library_ov_detail_local_book,
						file.extension.uppercase(),
						BookShelf.progress(context, file),
					),
					kind = MediaType.BOOK,
					origin = LibraryShelf.Origin.IMPORTED,
					addedAt = file.lastModified(),
					file = file,
					coverFile = BookShelf.cachedCover(context, file),
				)
			}
		}
		val mediaRows = part(problems, emptyList()) {
			LocalMediaShelf.list(context).map { entry ->
				val readable = LocalMediaShelf.isReadable(context, entry)
				Row(
					readAt = if (readable) AudioResumeStore.updatedAt(context, entry.uri) else 0L,
					key = entry.uri,
					title = LocalMediaShelf.title(entry),
					detail = LocalMediaShelf.detail(context, entry, readable),
					kind = entry.mediaType,
					origin = LibraryShelf.Origin.IMPORTED,
					addedAt = entry.addedAt,
					media = entry,
					missing = !readable,
				)
			}
		}
		val favouriteKeys = favouriteRows.mapTo(HashSet()) { it.key }
		val historyRows = part(problems, emptyList()) {
			db.getHistoryDao().findAll(0, HISTORY_LIMIT).map { entry ->
				val manga = entry.manga.toManga(entry.tags.toMangaTags(), null)
				val key = LibraryShelf.mangaKey(manga.id)
				Row(
					key = key,
					title = manga.title,
					detail = manga.source.getTitle(context),
					kind = LibraryShelf.kindOf(manga.source.name, groups),
					origin = LibraryShelf.Origin.FAVOURITE,
					readAt = entry.history.updatedAt,
					favourite = key in favouriteKeys,
					onDevice = false,
					manga = manga,
				)
			}
		}
		val readTimes = HashMap<String, Long>()
		for (row in historyRows) readTimes[row.key] = maxOf(readTimes[row.key] ?: 0L, row.readAt)
		val merged = LibraryShelf.merge(favouriteRows, onDeviceRows, bookRows + mediaRows).map { row ->
			if (row.favourite && row.onDevice) row.copy(detail = context.getString(R.string.library_ov_detail_join, row.detail, onDeviceLabel)) else row
		}
		val items = LibraryShelf.withReadTimes(merged, readTimes)
		// Imported files keep their position outside history; those with one can be continued.
		val resumable = items.filter { row ->
			(row.file != null && BookShelf.hasProgress(context, row.file)) || (row.media != null && !row.missing && row.readAt > 0L)
		}
		return Loaded(items, historyRows, resumable, problems, recovered)
	}

	private fun refresh() {
		if (view == null || !::status.isInitialized) return
		loading.visibility = View.VISIBLE
		refreshJob?.cancel()
		val context = requireContext().applicationContext
		refreshJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
				val loaded = withContext(Dispatchers.IO) { load(context) }
				all = loaded.items
				recent = loaded.recent
				resumable = loaded.resumable
				loadProblems = loaded.problems
				render()
				if (loaded.recovered > 0) {
					showStatus(getString(R.string.library_ov_recovered, loaded.recovered))
				}
				loadCovers(context)
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				// Keep the previous library visible and explain an I/O or database problem.
				showStatus(getString(R.string.library_load_failed, error.message.orEmpty().take(160)))
			} finally {
				loading.visibility = View.GONE
			}
		}
	}

	/** Extracts EPUB covers not seen before (once per book), then shows them. */
	private suspend fun loadCovers(context: Context) {
		val pending = all.mapNotNull { row -> row.file?.takeIf { row.coverFile == null && BookShelf.needsCoverLookup(context, it) } }
		if (pending.isEmpty()) return
		val covers = withContext(Dispatchers.IO) {
			pending.mapNotNull { file -> BookShelf.extractCover(context, file)?.let { file.absolutePath to it } }.toMap()
		}
		if (covers.isEmpty()) return
		all = all.map { row -> covers[row.file?.absolutePath]?.let { row.copy(coverFile = it) } ?: row }
		render()
	}

	private fun showStatus(text: String) {
		if (!::status.isInitialized) return
		status.text = text
		status.isVisible = true
	}

	private fun render() {
		if (!::status.isInitialized) return
		val q = search.text?.toString().orEmpty().trim()
		val smartRule = selectedSmart?.let { smartCollections[it] }?.let(SmartCollectionRule::parse)
		val inScope = all.filter { row ->
			(selectedCollection == null || keyOf(row) in collections[selectedCollection].orEmpty()) &&
				(smartRule == null || smartRule.matches(smartItem(row))) &&
				(!onDeviceOnly || LibraryShelf.isOnThisDevice(row)) &&
				LibraryShelf.matches(row, null, q)
		}
		val filtered = inScope.filter { LibraryShelf.matches(it, selected, "") }
		val sections = LibraryShelf.sections(filtered, sort)
		val continueRows = if (selectedCollection == null && selectedSmart == null && !onDeviceOnly && q.isEmpty()) {
			LibraryShelf.continueItems(recent, selected, resumable = resumable)
		} else {
			emptyList()
		}
		if (twoPane) {
			val previousKey = paneRow?.let(::keyOf) ?: restoredPaneKey
			val ordered = sections.flatMap { it.items }
			showPane(ordered.firstOrNull { keyOf(it) == previousKey } ?: ordered.firstOrNull())
			restoredPaneKey = null
		}
		adapter.submit(continueRows, sections, listVisible = filtered.isNotEmpty(), searchQuery = q)
		renderCollections()
		renderEmpty(filtered.isEmpty(), q, continueRows.isNotEmpty())
		val summary = resources.getQuantityString(R.plurals.library_ov_items, filtered.size, filtered.size)
		status.text = if (loadProblems.isEmpty()) {
			summary
		} else {
			summary + "\n" + getString(R.string.library_ov_load_partial, loadProblems.distinct().joinToString("; ").take(200))
		}
		status.isVisible = true
		// Keep every media filter accessible, even on an empty or new installation.
		val counts = LibraryShelf.counts(inScope)
		buttons.forEach { (type, chip) ->
			chip.text = getString(R.string.library_ov_chip_count, kindLabel(type), counts[type] ?: 0)
			chip.isChecked = type == selected
		}
		// Local content is on screen first; Recommended loads afterwards and ignores the search field.
		loadRecommended()
	}

	/** The Library box only filters local items; this hands the same text to the one main search. */
	private fun searchAllSources(text: String) {
		router.openMainSearch(selected?.name ?: "ALL", text)
	}

	private fun openTypedSearch(title: String, type: MediaType) {
		startActivity(
			Intent(requireContext(), SearchActivity::class.java)
				.putExtra(AppRouter.KEY_QUERY, title)
				.putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
				.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, type.name),
		)
	}

	/** Loads the Recommended strip of the selected chip once (also when it comes back empty); a failure hides it. */
	private fun loadRecommended() {
		val type = selected
		if (recommended.containsKey(type)) {
			if (recLoading && recType != type) {
				recJob?.cancel()
				recLoading = false
			}
			return
		}
		if (recJob?.isActive == true && recType == type) return
		recJob?.cancel()
		recType = type
		recLoading = true
		refreshRecommended()
		recJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
				val result = runCatchingCancellable { recommendations.load(type, settings.isNsfwContentDisabled) }
					.getOrDefault(emptyList())
				recommended[type] = result
			} finally {
				if (recType == type) recLoading = false
			}
			refreshRecommended()
		}
	}

	private fun isRecommendedLoading(): Boolean = recLoading && recType == selected

	private fun refreshRecommended() {
		if (!::emptyStrip.isInitialized) return
		emptyStrip.bind()
		adapter.refreshRecommended()
	}

	private fun renderEmpty(isEmpty: Boolean, query: String, hasContinue: Boolean) {
		emptyView.isVisible = isEmpty
		emptyStrip.bind()
		if (!isEmpty) return
		// Below the Continue strip when it is shown, centred otherwise.
		(emptyView.layoutParams as? FrameLayout.LayoutParams)?.let {
			it.gravity = if (hasContinue) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
			emptyView.layoutParams = it
		}
		Mascot.bind(emptyMascot, if (query.isNotEmpty()) MascotForm.SAD else Mascot.formFor(selected))
		emptyView.findViewById<MaterialButton>(R.id.library_empty_search_all).apply {
			isVisible = query.isNotEmpty()
			text = getString(R.string.library_search_all_sources, query)
			setOnClickListener { searchAllSources(query) }
		}
		emptyText.text = when {
			query.isNotEmpty() -> getString(R.string.library_ov_no_match, query)
			selectedCollection != null || selectedSmart != null -> getString(R.string.library_ov_empty_collection)
			else -> getString(
				when (selected) {
					null -> R.string.library_ov_empty_all
					MediaType.MANGA -> R.string.library_ov_empty_manga
					MediaType.BOOK -> R.string.library_ov_empty_book
					MediaType.VIDEO -> R.string.library_ov_empty_video
					MediaType.AUDIO -> R.string.library_ov_empty_audio
				},
			)
		}
	}

	private fun sectionTitle(group: LibraryShelf.Group): String = getString(
		when (group) {
			LibraryShelf.Group.FAVOURITES -> R.string.library_ov_section_favourites
			LibraryShelf.Group.ON_THIS_DEVICE -> R.string.library_rec_section_on_device
		},
	)

	/** One cover card, the same for favourites, downloads, books, videos, audio and Continue. */
	private fun bindCard(holder: CardHolder, row: Row, fromContinue: Boolean, onClick: () -> Unit) {
		val card = holder.card
		holder.title.text = row.title
		holder.detail.text = row.detail
		holder.kind.setImageResource(kindIcon(row.kind))
		val badge = when {
			fromContinue -> 0
			row.favourite -> R.drawable.ic_heart
			row.onDevice -> R.drawable.ic_storage
			else -> 0
		}
		holder.badge.isVisible = badge != 0
		if (badge != 0) holder.badge.setImageResource(badge)
		val manga = row.manga
		when {
			manga != null -> holder.cover.setImageAsync(manga.coverUrl, manga)
			row.coverFile != null -> holder.cover.setImageAsync(row.coverFile.toUri().toString())
			else -> {
				holder.cover.disposeImage()
				holder.cover.setImageDrawable(GeneratedCoverDrawable(row.title, null, row.kind))
			}
		}
		card.contentDescription = getString(
			R.string.library_ov_kind_badge, row.title,
			getString(R.string.library_ov_detail_join, kindLabel(row.kind), row.detail),
		)
		card.isCheckable = twoPane && !fromContinue
		card.isChecked = card.isCheckable && paneRow?.let(::keyOf) == keyOf(row)
		card.setOnClickListener { onClick() }
		card.setOnLongClickListener {
			showActions(row, fromContinue)
			true
		}
	}

	private class CardHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
		val cover: CoverImageView = card.findViewById(R.id.library_item_cover)
		val kind: ImageView = card.findViewById(R.id.library_item_kind)
		val badge: ImageView = card.findViewById(R.id.library_item_badge)
		val title: TextView = card.findViewById(R.id.library_item_title)
		val detail: TextView = card.findViewById(R.id.library_item_detail)
	}

	private fun createCard(parent: ViewGroup): CardHolder = CardHolder(
		LayoutInflater.from(parent.context).inflate(R.layout.item_library_cover, parent, false) as MaterialCardView,
	)

	private sealed class Entry {
		class Continue(val rows: List<Row>) : Entry()
		class Header(val title: String) : Entry()
		class Card(val row: Row) : Entry()
		class SearchAll(val query: String) : Entry()
		data object Recommended : Entry()
	}

	/** Continue strip, a header and a cover grid per section (Favourites, On this device), then Recommended. */
	private inner class Entries : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		private var entries = emptyList<Entry>()
		private val continueAdapter = ContinueCards()
		private var listVisible = false
		init { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }

		private fun wantRecommended(): Boolean =
			listVisible && (recommended[selected].orEmpty().isNotEmpty() || isRecommendedLoading())

		/** Shows or hides the Recommended entry at the end of the list, or rebinds it, without touching the rest. */
		fun refreshRecommended() {
			val had = entries.lastOrNull() is Entry.Recommended
			val want = wantRecommended()
			when {
				want && !had -> { entries = entries + Entry.Recommended; notifyItemInserted(entries.lastIndex) }
				!want && had -> { entries = entries.dropLast(1); notifyItemRemoved(entries.size) }
				want -> notifyItemChanged(entries.lastIndex)
			}
		}

		fun submit(continueRows: List<Row>, sections: List<LibraryShelf.Section>, listVisible: Boolean, searchQuery: String) {
			this.listVisible = listVisible
			entries = buildList {
				if (continueRows.isNotEmpty()) add(Entry.Continue(continueRows))
				for (section in sections) {
					add(Entry.Header(getString(R.string.library_ov_section_count, sectionTitle(section.group), section.items.size)))
					section.items.mapTo(this) { Entry.Card(it) }
				}
				if (listVisible && searchQuery.isNotEmpty()) add(Entry.SearchAll(searchQuery))
				if (wantRecommended()) add(Entry.Recommended)
			}
			continueAdapter.submit(continueRows)
			notifyDataSetChanged()
		}

		fun isFullWidth(position: Int): Boolean = entries.getOrNull(position) !is Entry.Card

		override fun getItemCount(): Int = entries.size

		override fun getItemViewType(position: Int): Int = when (entries[position]) {
			is Entry.Continue -> R.layout.item_library_continue
			is Entry.Header -> R.layout.item_library_section
			is Entry.Card -> R.layout.item_library_cover
			is Entry.SearchAll -> R.layout.item_library_search_all
			is Entry.Recommended -> R.layout.item_library_recommended
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
			R.layout.item_library_continue -> {
				val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
				view.findViewById<RecyclerView>(R.id.library_continue_list).apply {
					layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
					adapter = continueAdapter
				}
				object : RecyclerView.ViewHolder(view) {}
			}
			R.layout.item_library_recommended -> RecommendedHolder(
				RecommendedStrip(LayoutInflater.from(parent.context).inflate(viewType, parent, false)),
			).also { listStrip = it.strip }
			R.layout.item_library_search_all -> object : RecyclerView.ViewHolder(
				LayoutInflater.from(parent.context).inflate(viewType, parent, false),
			) {}
			R.layout.item_library_section -> object : RecyclerView.ViewHolder(
				LayoutInflater.from(parent.context).inflate(viewType, parent, false),
			) {}
			else -> createCard(parent)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (val entry = entries[position]) {
				is Entry.Continue -> Unit
				is Entry.Recommended -> (holder as RecommendedHolder).strip.bind()
				is Entry.Header -> (holder.itemView as TextView).text = entry.title
				is Entry.SearchAll -> holder.itemView.findViewById<MaterialButton>(R.id.library_search_all).apply {
					text = getString(R.string.library_search_all_sources, entry.query)
					setOnClickListener { searchAllSources(entry.query) }
				}
				is Entry.Card -> bindCard(holder as CardHolder, entry.row, fromContinue = false) {
					if (twoPane) {
						showPane(entry.row)
						notifyDataSetChanged()
					} else {
						openRow(entry.row)
					}
				}
			}
		}
	}

	/** Recently opened titles, newest first; tapping one opens it straight away. */
	private inner class ContinueCards : RecyclerView.Adapter<CardHolder>() {
		private var rows = emptyList<Row>()

		fun submit(value: List<Row>) {
			rows = value
			notifyDataSetChanged()
		}

		override fun getItemCount(): Int = rows.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder = createCard(parent).also {
			it.card.layoutParams = it.card.layoutParams.apply {
				width = resources.getDimensionPixelSize(R.dimen.library_continue_card_width)
			}
		}

		override fun onBindViewHolder(holder: CardHolder, position: Int) {
			val row = rows[position]
			bindCard(holder, row, fromContinue = true) { openRow(row) }
			holder.card.contentDescription = getString(R.string.library_continue_item, row.title)
		}
	}

	private inner class RecommendedHolder(val strip: RecommendedStrip) : RecyclerView.ViewHolder(strip.root)

	/** The Recommended header, progress bar and horizontal row of covers; used in the list and in the empty state. */
	private inner class RecommendedStrip(val root: View) {
		private val progress: LinearProgressIndicator = root.findViewById(R.id.library_rec_progress)
		private val cards = RecommendedCards()

		init {
			root.findViewById<RecyclerView>(R.id.library_rec_list).apply {
				layoutManager = LinearLayoutManager(root.context, LinearLayoutManager.HORIZONTAL, false)
				adapter = cards
			}
		}

		fun bind() {
			val items = recommended[selected].orEmpty()
			val busy = isRecommendedLoading()
			root.isVisible = items.isNotEmpty() || busy
			progress.isVisible = busy && items.isEmpty()
			cards.submit(items)
		}
	}

	/** Suggested titles (not in the library yet): tap opens or searches them, long-press offers Add to library. */
	private inner class RecommendedCards : RecyclerView.Adapter<CardHolder>() {
		private var rows = emptyList<RecommendationLoader.Recommendation>()

		fun submit(value: List<RecommendationLoader.Recommendation>) {
			if (value == rows) return
			rows = value
			notifyDataSetChanged()
		}

		override fun getItemCount(): Int = rows.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder = createCard(parent).also {
			it.card.layoutParams = it.card.layoutParams.apply {
				width = resources.getDimensionPixelSize(R.dimen.library_continue_card_width)
			}
		}

		override fun onBindViewHolder(holder: CardHolder, position: Int) {
			val rec = rows[position]
			val item = rec.item
			holder.title.text = item.title
			holder.detail.text = listOfNotNull(item.providerLabel, item.score?.let { "$it%" }).joinToString(" \u00b7 ")
			holder.kind.setImageResource(kindIcon(rec.type))
			holder.badge.isVisible = false
			val manga = item.manga
			val coverUrl = item.coverUrl
			when {
				manga != null -> holder.cover.setImageAsync(manga.coverUrl, manga)
				coverUrl != null -> holder.cover.setImageAsync(coverUrl)
				else -> {
					holder.cover.disposeImage()
					holder.cover.setImageDrawable(GeneratedCoverDrawable(item.title, null, rec.type))
				}
			}
			holder.card.isCheckable = false
			holder.card.contentDescription = getString(R.string.library_rec_item, item.title)
			holder.card.setOnClickListener {
				if (manga != null) router.openDetails(manga) else openTypedSearch(item.title, rec.type)
			}
			holder.card.setOnLongClickListener {
				addDiscoverItemToLibrary(item, rec.type, libraryFinder) { openTypedSearch(it, rec.type) }
				true
			}
		}
	}
}
