package org.koitharu.kotatsu.core.media

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.image.CoilImageView
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.TapCounter
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.favourites.smart.SmartCollectionRule
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.main.ui.NavLayoutPolicy
import org.koitharu.kotatsu.parsers.model.Manga
import java.io.File
import javax.inject.Inject

/**
 * User-facing unified library across saved manga, novels, anime, audio and imported books.
 * Shown as a main navigation tab, or inside [UnifiedLibraryActivity] when opened from elsewhere
 * (its [UnifiedLibraryActivity.EXTRA_MEDIA_KIND] / [UnifiedLibraryActivity.EXTRA_FILTER] extras arrive as arguments).
 */
@AndroidEntryPoint
class UnifiedLibraryFragment : Fragment() {
	private companion object {
		const val MENU_NEW_SMART = 7501
	}
	@Inject lateinit var favorites: FavouritesRepository
	@Inject lateinit var history: HistoryRepository
	private data class Row(val title: String, val detail: String, val kind: MediaType,
		val manga: Manga? = null, val file: File? = null,
		/** The user's own video or audio file (Add files); [missing] when it can no longer be opened. */
		val media: LocalMediaShelf.Entry? = null, val missing: Boolean = false)
	private val fileImporter = LocalFileImportLauncher(this) { if (view != null && ::search.isInitialized) refresh() }
	private var all = emptyList<Row>()
	/** Most recently opened titles, newest first, for the Continue strip. */
	private var recent = emptyList<Row>()
	/** At 840dp and wider the library shows a details pane next to the list (#63). */
	private var twoPane = false
	private lateinit var paneCover: CoilImageView
	private lateinit var paneTitle: TextView
	private lateinit var paneMeta: TextView
	private lateinit var paneDescription: TextView
	private lateinit var paneOpen: MaterialButton
	private var paneRow: Row? = null
	private var restoredPaneKey: String? = null
	private lateinit var mascot: ImageView
	private lateinit var loading: com.google.android.material.progressindicator.LinearProgressIndicator
	/** Collections: the user's own folders across all media (long-press a title to file it). */
	private var collections: Map<String, Set<String>> = emptyMap()
	private var selectedCollection: String? = null
	/** v1.5 draft: smart collections, name -> rule text (see [SmartCollectionRule]). */
	private var smartCollections: Map<String, String> = emptyMap()
	private var selectedSmart: String? = null
	private lateinit var collectionRow: LinearLayout
	private val collectionPrefs by lazy { requireContext().getSharedPreferences("library_collections", Context.MODE_PRIVATE) }
	private val eggTaps = TapCounter()
	private lateinit var continueLabel: TextView
	private lateinit var continueRow: LinearLayout
	private lateinit var continueScroll: HorizontalScrollView
	/** Null represents All media, including items with an unknown/older source type. */
	private var selected: MediaType? = null
	private var requestedKind: MediaType? = null
	private var refreshJob: Job? = null
	private lateinit var search: EditText
	private lateinit var count: TextView
	private val adapter = Rows()
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
		requestedKind = MediaType.entries.firstOrNull {
			it.name.equals(arguments?.getString(UnifiedLibraryActivity.EXTRA_MEDIA_KIND), ignoreCase = true)
		}
		selected = if (savedInstanceState?.containsKey("selected_kind") == true) {
			MediaType.entries.firstOrNull { it.name == savedInstanceState?.getString("selected_kind") }
		} else requestedKind
		restoredPaneKey = savedInstanceState?.getString("pane_row_key")
		twoPane = NavLayoutPolicy.useTwoPane(resources.configuration.screenWidthDp)
		val px = (12 * resources.displayMetrics.density).toInt()
		val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(px, px, px, px) }
		val tabs = LinearLayout(context).apply {
			id = R.id.monogatari_library_tabs
			orientation = LinearLayout.HORIZONTAL
		}
		for (type in listOf<MediaType?>(null) + MediaType.entries) {
			// Filter chips, matching search and Discover, instead of filled toggle buttons.
			val button = Chip(context).apply {
				text = type?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: "All"
				isCheckable = true
				setOnClickListener { selected = type; render() }
			}
			buttons += type to button
			tabs.addView(button, LinearLayout.LayoutParams(-2, -2).apply {
				marginEnd = (6 * resources.displayMetrics.density).toInt()
			})
		}
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(tabs)
		})
		continueLabel = TextView(context).apply {
			setText(R.string.library_continue)
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
			setPadding(0, px / 2, 0, 0)
		}
		continueRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
		continueScroll = HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(continueRow)
		}
		root.addView(continueLabel, LinearLayout.LayoutParams(-1, -2))
		root.addView(continueScroll, LinearLayout.LayoutParams(-1, -2))
		collectionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(collectionRow)
		}, LinearLayout.LayoutParams(-1, -2))
		collections = LibraryCollections.decode(collectionPrefs.getString("data", null))
		smartCollections = decodeSmart(collectionPrefs.getString("smart", null))
		selectedSmart = savedInstanceState?.getString("selected_smart")?.takeIf { it in smartCollections }
		selectedCollection = savedInstanceState?.getString("selected_collection")?.takeIf { it in collections }
		search = EditText(context).apply { hint = getString(R.string.mono_library_filter_this_library); setSingleLine(true) }
		search.doAfterTextChanged { render() }
		search.setText(savedInstanceState?.getString("library_query") ?: arguments?.getString(UnifiedLibraryActivity.EXTRA_FILTER).orEmpty())
		root.addView(search, LinearLayout.LayoutParams(-1, -2))
		// Books, comics, videos and audio from the device (also in the overflow menu).
		root.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
			setText(R.string.add_files)
			setIconResource(R.drawable.ic_add)
			setOnClickListener { fileImporter.launchOrToast() }
		}, LinearLayout.LayoutParams(-2, -2))
		count = TextView(context)
		root.addView(count, LinearLayout.LayoutParams(-1, -2))
		loading = com.google.android.material.progressindicator.LinearProgressIndicator(context).apply {
			isIndeterminate = true
			contentDescription = getString(R.string.library_loading)
			visibility = View.GONE
		}
		root.addView(loading, LinearLayout.LayoutParams(-1, -2))
		// Kitsune keeps an empty library company, in the form of the selected tab. Tap her quickly for a surprise.
		mascot = ImageView(context).apply {
			adjustViewBounds = true
			setOnClickListener { if (eggTaps.tap(android.os.SystemClock.uptimeMillis())) Mascot.showEasterEgg(requireActivity()) }
		}
		root.addView(mascot, LinearLayout.LayoutParams((160 * resources.displayMetrics.density).toInt(), -2).apply {
			gravity = android.view.Gravity.CENTER_HORIZONTAL
		})
		val list = RecyclerView(context).apply {
			// Stable resource ID lets RecyclerView defer state restoration until async rows load.
			id = R.id.monogatari_library_results_list
			layoutManager = GridLayoutManager(context,
				if (twoPane) NavLayoutPolicy.libraryListColumns(resources.configuration.screenWidthDp)
				else MonogatariChrome.columns(context))
			adapter = this@UnifiedLibraryFragment.adapter
			if (isBottomTab) {
				// Room for the floating navigation bar, which is drawn over the main screen's content.
				clipToPadding = false
				setPadding(0, 0, 0, (96 * resources.displayMetrics.density).toInt())
			}
		}
		root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
		// onResume refreshes when the screen first opens and when a reader changes progress.
		return if (twoPane) {
			val split = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
			split.addView(root, LinearLayout.LayoutParams(0, -1, 1f))
			split.addView(buildPane(px), LinearLayout.LayoutParams(0, -1, 1f))
			split
		} else {
			root
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(LibraryMenuProvider())
		addMenuProvider(fileImporter.menuProvider())
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
			setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
			// TalkBack announces the newly selected title when the details pane changes.
			accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
		}
		paneMeta = TextView(requireContext())
		paneDescription = TextView(requireContext())
		paneOpen = MaterialButton(requireContext()).apply {
			setText(R.string.library_pane_open)
			setOnClickListener { paneRow?.let(::openRow) }
		}
		column.addView(paneCover, LinearLayout.LayoutParams(-1, (240 * resources.displayMetrics.density).toInt()))
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
		paneCover.setImageAsync(row.manga?.coverUrl)
		paneTitle.text = row.title
		paneMeta.text = "${row.kind.name.lowercase().replaceFirstChar { it.uppercaseChar() }} · ${row.detail}"
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
		val pad = (16 * resources.displayMetrics.density).toInt()
		val name = android.widget.EditText(requireContext()).apply { setHint(R.string.library_smart_name_hint); setSingleLine(true) }
		val rule = android.widget.EditText(requireContext()).apply { setHint(R.string.library_smart_rule_hint) }
		val form = LinearLayout(requireContext()).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad / 2, pad, 0)
			addView(name)
			addView(rule)
			addView(TextView(requireContext()).apply { setText(R.string.library_smart_help) })
		}
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
		favourite = false,
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

	private fun keyOf(row: Row): String = row.manga?.id?.toString() ?: row.media?.uri ?: row.file?.absolutePath.orEmpty()

	private fun saveCollections(value: Map<String, Set<String>>) {
		collections = value
		collectionPrefs.edit().putString("data", LibraryCollections.encode(value)).apply()
		if (selectedCollection != null && selectedCollection !in value) selectedCollection = null
		render()
	}

	/** One chip per collection; tap to show only its titles, tap again for everything. */
	private fun renderCollections() {
		collectionRow.removeAllViews()
		for ((name, rule) in smartCollections.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
			collectionRow.addView(com.google.android.material.chip.Chip(requireContext()).apply {
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
					com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
			collectionRow.addView(com.google.android.material.chip.Chip(requireContext()).apply {
				text = "$name (${collections[name].orEmpty().size})"
				isCheckable = true
				isChecked = name == selectedCollection
				setOnClickListener {
					selectedCollection = if (isChecked) name else null
					if (selectedCollection != null) selectedSmart = null
					render()
				}
				setOnLongClickListener {
					com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
		val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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

	/** Long-press on the user's own files: collections as for every title, plus Remove from library. */
	private fun showLocalActions(row: Row) {
		val actions = arrayOf(getString(R.string.add_files_collections), getString(R.string.add_files_remove))
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(row.title)
			.setItems(actions) { _, which -> if (which == 0) chooseCollections(row) else confirmRemove(row) }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun confirmRemove(row: Row) {
		val message = when {
			row.missing -> R.string.add_files_missing_message
			row.media != null -> R.string.add_files_remove_media_message
			else -> R.string.add_files_remove_book_message
		}
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.add_files_remove_title, row.title))
			.setMessage(message)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add_files_remove) { _, _ ->
				val context = context ?: return@setPositiveButton
				when {
					// Media is only forgotten; the file on the device is left alone.
					row.media != null -> LocalMediaShelf.remove(context, row.media.uri)
					// Books on the shelf are Monogatari's own copies in app storage.
					row.file != null -> row.file.delete()
				}
				refresh()
			}
			.show()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putString("selected_kind", selected?.name ?: "ALL")
		outState.putString("selected_collection", selectedCollection)
		outState.putString("selected_smart", selectedSmart)
		if (::search.isInitialized) outState.putString("library_query", search.text?.toString().orEmpty())
		paneRow?.let { outState.putString("pane_row_key", keyOf(it)) }
		super.onSaveInstanceState(outState)
	}

	override fun onResume() { super.onResume(); if (view != null && ::search.isInitialized) refresh() }

	private fun refresh() {
		count.text = getString(R.string.library_loading)
		loading.visibility = View.VISIBLE
		mascot.isVisible = false
		refreshJob?.cancel()
		val context = requireContext()
		refreshJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
			all = withContext(Dispatchers.IO) {
				val groups = MediaType.entries.associateWith { it.persistedSourceNames() }
				val remote = favorites.getAllManga().distinctBy { it.id }.map { manga ->
					val source = manga.source.name
					val kind = when {
						source in groups.getValue(MediaType.VIDEO) -> MediaType.VIDEO
						source in groups.getValue(MediaType.AUDIO) -> MediaType.AUDIO
						source in groups.getValue(MediaType.BOOK) -> MediaType.BOOK
						else -> MediaType.MANGA
					}
					Row(manga.title, source, kind, manga = manga)
				}
				val local = BookShelf.list(context).map { file ->
					Row(BookShelf.title(file), "Local ${file.extension.uppercase()} · " +
						BookShelf.progress(context, file), MediaType.BOOK, file = file)
				}
				val media = LocalMediaShelf.list(context).map { entry ->
					val readable = LocalMediaShelf.isReadable(context, entry)
					Row(LocalMediaShelf.title(entry), LocalMediaShelf.detail(context, entry, readable),
						entry.mediaType, media = entry, missing = !readable)
				}
				recent = runCatching { history.getList(0, 30) }.getOrDefault(emptyList()).map { manga ->
					val source = manga.source.name
					val kind = when {
						source in groups.getValue(MediaType.VIDEO) -> MediaType.VIDEO
						source in groups.getValue(MediaType.AUDIO) -> MediaType.AUDIO
						source in groups.getValue(MediaType.BOOK) -> MediaType.BOOK
						else -> MediaType.MANGA
					}
					Row(manga.title, source, kind, manga = manga)
				}
				(remote + local + media).sortedBy { it.title.lowercase() }
			}
			render()
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				// Keep the previous library visible and explain an I/O or database problem.
				count.text = getString(R.string.library_load_failed, error.message.orEmpty().take(160))
			} finally {
				loading.visibility = View.GONE
			}
		}
	}

	private fun render() {
		if (!::count.isInitialized) return
		val q = search.text?.toString().orEmpty().trim()
		val smartRule = selectedSmart?.let { smartCollections[it] }?.let(SmartCollectionRule::parse)
		val filtered = all.filter { row ->
			(selected == null || row.kind == selected) &&
				(selectedCollection == null || keyOf(row) in collections[selectedCollection].orEmpty()) &&
				(smartRule == null || smartRule.matches(smartItem(row))) && (q.isBlank() ||
				row.title.contains(q, ignoreCase = true) || row.detail.contains(q, ignoreCase = true))
		}
		if (twoPane) {
			val previousKey = paneRow?.let(::keyOf) ?: restoredPaneKey
			showPane(filtered.firstOrNull { keyOf(it) == previousKey } ?: filtered.firstOrNull())
			restoredPaneKey = null
		}
		adapter.setRows(filtered.take(1000))
		renderContinue()
		renderCollections()
		mascot.isVisible = filtered.isEmpty()
		Mascot.bind(mascot, if (filtered.isEmpty() && q.isNotBlank()) MascotForm.SAD else Mascot.formFor(selected))
		val category = selected?.name?.lowercase() ?: "saved"
		count.text = when {
			filtered.isEmpty() && q.isNotBlank() -> "No matching ${category} titles"
			filtered.isEmpty() -> "No ${category} items yet. Save a title or import a book to begin."
			else -> "${filtered.size} ${category} items" +
				if (filtered.size > 1000) " (first 1000 shown)" else ""
		}
		// Keep every media filter accessible, even on an empty or new installation.
		buttons.forEach { (type, button) ->
			val matching = if (type == null) all.size else all.count { it.kind == type }
			val label = type?.name?.lowercase()?.replaceFirstChar { it.uppercaseChar() } ?: "All"
			button.text = "$label ($matching)"
			button.isChecked = type == selected
		}
	}

	/** Quick "continue reading / watching / listening" buttons for the selected media tab. */
	private fun renderContinue() {
		val items = recent.filter { selected == null || it.kind == selected }.take(10)
		continueRow.removeAllViews()
		val visible = items.isNotEmpty()
		continueLabel.isVisible = visible
		continueScroll.isVisible = visible
		for (row in items) {
			val manga = row.manga ?: continue
			continueRow.addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
				text = row.title
				isAllCaps = false
				maxLines = 1
				maxWidth = (220 * resources.displayMetrics.density).toInt()
				contentDescription = getString(R.string.library_continue_item, row.title)
				setOnClickListener { startActivity(AppRouter.detailsIntent(requireContext(), manga)) }
			}, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * resources.displayMetrics.density).toInt() })
		}
	}

	private inner class Rows : RecyclerView.Adapter<Rows.Holder>() {
		private var rows = emptyList<Row>()
		init { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
		fun setRows(value: List<Row>) {
			rows = value
			notifyDataSetChanged()
		}
		override fun getItemCount(): Int = rows.size
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
			val card = LayoutInflater.from(parent.context)
				.inflate(R.layout.item_monogatari_book, parent, false) as MaterialCardView
			return Holder(card)
		}
		override fun onBindViewHolder(holder: Holder, position: Int) {
			val row = rows[position]
			holder.card.isCheckable = twoPane
			holder.card.isChecked = twoPane && paneRow?.let(::keyOf) == keyOf(row)
			BookShelfCard.bind(holder.card, row.title, row.detail) {
				if (twoPane) {
					showPane(row)
					notifyDataSetChanged()
				} else openRow(row)
			}
			holder.card.setOnLongClickListener {
				if (row.file != null || row.media != null) showLocalActions(row) else chooseCollections(row)
				true
			}
			holder.card.findViewById<ImageView>(R.id.monogatari_book_item_icon).setImageResource(
				when (row.kind) {
					MediaType.MANGA -> R.drawable.ic_manga_source
					MediaType.VIDEO -> R.drawable.ic_play
					MediaType.AUDIO -> R.drawable.ic_play
					MediaType.BOOK -> R.drawable.ic_monogatari_nav_books
				},
			)
		}
		inner class Holder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)
	}
}
