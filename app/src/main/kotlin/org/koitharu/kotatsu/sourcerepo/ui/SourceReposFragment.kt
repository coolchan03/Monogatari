package org.koitharu.kotatsu.sourcerepo.ui

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.jsext.repo.DartSourceCompatibility
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.source.JsExtensionProvider
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.sourcerepo.domain.JsSourceItem
import org.koitharu.kotatsu.sourcerepo.domain.PluginEntry
import org.koitharu.kotatsu.sourcerepo.domain.PluginState
import org.koitharu.kotatsu.sourcerepo.work.SourceUpdateWorker
import org.koitharu.kotatsu.sourcerepo.domain.RepoPreview
import javax.inject.Inject

@AndroidEntryPoint
class SourceReposFragment : BasePreferenceFragment(R.string.source_repos) {

	private val viewModel by viewModels<SourceReposViewModel>()

	@Inject
	lateinit var jsExtensions: JsExtensionProvider

	@Inject
	lateinit var diagnostics: JsSourceDiagnostics

	@Inject
	lateinit var scanManager: SourceScanManager

	@Inject
	lateinit var healthChecks: FirstRunHealthChecks

    @Inject
    lateinit var sourceUpdateScheduler: SourceUpdateWorker.Scheduler

	private var scanProgressDialog: AlertDialog? = null

	private val downloadReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
			if (id != -1L) {
				viewModel.onDownloadComplete(id)
			}
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_source_repos)

	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.state.observe(viewLifecycleOwner, ::render)
		viewLifecycleOwner.lifecycleScope.launch {
			scanManager.status.collectLatest { state ->
				scanProgressDialog?.takeIf { it.isShowing }?.setMessage(
					"${state.message}\n\nChecked ${state.done} of ${state.total} sources",
				)
			}
		}
		viewModel.onInstall.observeEvent(viewLifecycleOwner) { startActivity(it) }
		viewModel.onMessage.observeEvent(viewLifecycleOwner) {
			Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
		}
		viewModel.onRepoPreview.observeEvent(viewLifecycleOwner, ::showRepoPreview)
		viewModel.onRepoPreviewError.observeEvent(viewLifecycleOwner) {
			Toast.makeText(
				requireContext(),
				getString(R.string.repository_verify_failed, it.getDisplayMessage(resources)),
				Toast.LENGTH_LONG,
			).show()
		}
	}

	override fun onStart() {
		super.onStart()
		ContextCompat.registerReceiver(
			requireContext(),
			downloadReceiver,
			IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
			ContextCompat.RECEIVER_EXPORTED,
		)
	}

	override fun onStop() {
		requireContext().unregisterReceiver(downloadReceiver)
		super.onStop()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_ADD -> {
			showAddDialog()
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	private fun installDiagnosticBatch() {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Download test sources?")
			.setMessage("Install the next 10 sources, or select All to install every remaining runnable JavaScript source. Sources remain installed until removed. Large batches use data and may take a long time.")
			.setNegativeButton("Cancel", null)
			.setNeutralButton("All") { _, _ -> installDiagnosticSources(1000) }
			.setPositiveButton("Next 10") { _, _ -> installDiagnosticSources(10) }
			.show()
	}

	private fun installDiagnosticSources(maxSources: Int) {
		viewLifecycleOwner.lifecycleScope.launch {
					val progress = MaterialAlertDialogBuilder(requireContext())
						.setTitle("Installing JS sources").setMessage("Preparing source list...")
						.setNegativeButton("Hide progress", null).create()
					progress.show()
					val report = try { diagnostics.installBatch({ done, total ->
						progress.setMessage("Installed or attempted $done of $total sources")
					}, maxSources) }
					catch (error: Exception) { "Batch failed: ${error.message}" }
					if (progress.isShowing) progress.dismiss()
					MaterialAlertDialogBuilder(requireContext())
						.setTitle("Download report")
						.setMessage(report.take(2000))
						.setPositiveButton("Copy report") { _, _ ->
							val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
							clipboard.setPrimaryClip(ClipData.newPlainText("Monogatari batch", report))
						}
						.setNegativeButton("Close", null).show()
				}
	}

	private fun runDiagnostics(allSources: Boolean = false) {
		if (scanManager.status.value.running) {
			showScanProgress()
			return
		}
		if (!scanManager.hasSavedResults(allSources)) {
			launchScan(allSources, restart = false)
			return
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Saved source diagnostics")
			.setMessage("Previous results are saved. Resume skips sources already checked. Start new discards only the saved test results, not your extensions or library.")
			.setPositiveButton("Resume") { _, _ -> launchScan(allSources, restart = false) }
			.setNeutralButton("Start new") { _, _ -> launchScan(allSources, restart = true) }
			.setNegativeButton("View report") { _, _ -> showSavedScanReport(allSources) }
			.show()
	}

	private fun launchScan(allSources: Boolean, restart: Boolean) {
		if (scanManager.start(allSources, restart)) {
			showScanProgress()
		} else {
			Toast.makeText(requireContext(), scanManager.status.value.message, Toast.LENGTH_LONG).show()
		}
	}

	private fun showScanProgress() {
		scanProgressDialog?.dismiss()
		val current = scanManager.status.value
		val dialog = MaterialAlertDialogBuilder(requireContext())
			.setTitle(if (current.mode == "all") "All-source diagnostics" else "JS-source diagnostics")
			.setMessage(current.message)
			.setPositiveButton("Copy report") { _, _ ->
				copyScanReport(current.mode == "all")
			}
			.setNeutralButton("Stop scan") { _, _ -> scanManager.stop() }
			.setNegativeButton("Hide", null)
			.create()
		scanProgressDialog = dialog
		dialog.setOnDismissListener { if (scanProgressDialog === dialog) scanProgressDialog = null }
		dialog.show()
	}

	private fun showSavedScanReport(allSources: Boolean) {
		val report = scanManager.report(allSources)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Saved diagnostic report")
			.setMessage(report.take(1800) + if (report.length > 1800) "\n(Full report available via Copy)" else "")
			.setPositiveButton("Copy report") { _, _ -> copyScanReport(allSources) }
			.setNegativeButton("Close", null)
			.show()
	}

	private fun copyScanReport(allSources: Boolean) {
		val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText("Monogatari diagnostics", scanManager.report(allSources)))
		Toast.makeText(requireContext(), "Diagnostic report copied", Toast.LENGTH_SHORT).show()
	}

	override fun onDestroyView() {
		scanProgressDialog?.dismiss()
		scanProgressDialog = null
		super.onDestroyView()
	}

	private fun render(state: SourceReposViewModel.State) {
		val reposCategory = findPreference<PreferenceCategory>(KEY_REPOS) ?: return
		val pluginsCategory = findPreference<PreferenceCategory>(KEY_PLUGINS) ?: return
		val mangaCategory = findPreference<PreferenceCategory>("monogatari_source_manga") ?: return
		val novelCategory = findPreference<PreferenceCategory>("monogatari_source_novels") ?: return
		val animeCategory = findPreference<PreferenceCategory>("monogatari_source_anime") ?: return
		reposCategory.removeAll()
		pluginsCategory.removeAll()
		mangaCategory.removeAll()
		novelCategory.removeAll()
		animeCategory.removeAll()
		val context = requireContext()
		pluginsCategory.addPreference(Preference(context).apply {
			isPersistent = false
			isSelectable = false
			setIcon(R.drawable.ic_monogatari_nav_explore)
			summary = getString(R.string.monogatari_source_overview)
		})
        val sourceUpdatePrefs = context.getSharedPreferences(SourceUpdateWorker.PREFS, Context.MODE_PRIVATE)
        val currentDay = System.currentTimeMillis() / 86_400_000L
        val count = if (sourceUpdatePrefs.getLong("last_updated_day", -1) == currentDay)
            sourceUpdatePrefs.getInt("updates_today", 0) else 0
        pluginsCategory.addPreference(Preference(context).apply {
            key = "monogatari_source_update_status"
            title = "Check installed source updates"
            summary = "$count sources updated today. Runs automatically every 12 hours on a network; " +
                "the previous version can be restored if an update fails."
            isPersistent = false
            setOnPreferenceClickListener {
                sourceUpdateScheduler.checkNow()
                Toast.makeText(context, "Update check queued; refresh this screen after it finishes", Toast.LENGTH_LONG).show()
                true
            }
        })
		pluginsCategory.addPreference(Preference(context).apply {
			key = "temporary_js_batch_install"
			title = "Download JS sources for testing (10 or all)"
			summary = "Install next 10 or all remaining runnable extensions, then run diagnostics"
			isPersistent = false
			setOnPreferenceClickListener { installDiagnosticBatch(); true }
		})
		pluginsCategory.addPreference(Preference(context).apply {
			key = "monogatari_quick_health"
			title = "Run quick device checks"
			summary = "Check storage, network and installed source registry without contacting websites"
			isPersistent = false
			setOnPreferenceClickListener {
				viewLifecycleOwner.lifecycleScope.launch {
					val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { healthChecks.run() }
					MaterialAlertDialogBuilder(requireContext())
						.setTitle("Monogatari device checks")
						.setMessage(result.summary() + "\n\nFull source checks are available below. Neither test verifies actual video playback.")
						.setPositiveButton(android.R.string.ok, null)
						.show()
				}
				true
			}
		})
		pluginsCategory.addPreference(Preference(context).apply {
			key = "full_source_diagnostics"
			title = "Test all Kotatsu and installed JS sources"
			summary = "Resumable background sweep of all 1,370+ built-ins and installed JS sources. Network/data-intensive; may hit rate limits."
			isPersistent = false
			setOnPreferenceClickListener { runDiagnostics(allSources = true); true }
		})
		pluginsCategory.addPreference(Preference(context).apply {
			key = "temporary_js_diagnostics"
			title = "Test installed JS sources (temporary)"
			summary = "Run sample content checks and copy a diagnostic report"
			isPersistent = false
			setOnPreferenceClickListener { runDiagnostics(); true }
		})
		if (state.repos.isEmpty()) {
			reposCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					isSelectable = false
					setSummary(R.string.no_repositories)
				},
			)
		}
		for (url in state.repos) {
			reposCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					setIcon(R.drawable.ic_monogatari_nav_explore)
					title = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
					summary = listOfNotNull(url, state.errors[url]?.getDisplayMessage(resources)).joinToString("\n")
					setOnPreferenceClickListener {
						confirmRemove(url)
						true
					}
				},
			)
		}
		if (state.skippedDartCount > 0) {
			val runnableJs = state.jsSources.map { it.entry }
			val alternatives = state.dartSources.mapNotNull { dart ->
				DartSourceCompatibility.candidate(dart, runnableJs)?.let { dart to it }
			}
			pluginsCategory.addPreference(Preference(context).apply {
				isPersistent = false
				isSelectable = alternatives.isNotEmpty()
				title = "Dart alternatives: ${alternatives.size} candidates"
				summary = "${state.skippedDartCount} source entries cannot execute directly. " +
					"Open an existing Kotatsu parser or install matching JavaScript where available. " +
					"Candidates still need live testing."
				if (alternatives.isNotEmpty()) setOnPreferenceClickListener {
					showDartAlternatives(alternatives, state)
					true
				}
			})
		}
		val groups = mapOf(
			JsItemType.MANGA to mangaCategory,
			JsItemType.NOVEL to novelCategory,
			JsItemType.ANIME to animeCategory,
		)
		for ((type, category) in groups) {
			val count = state.jsSources.count { it.entry.itemType == type }
			category.isVisible = count > 0
			category.title = getString(
				when (type) {
					JsItemType.MANGA -> R.string.monogatari_source_manga_count
					JsItemType.NOVEL -> R.string.monogatari_source_novels_count
					JsItemType.ANIME -> R.string.monogatari_source_anime_count
				}, count,
			)
		}
		for (item in state.jsSources.sortedWith(
			compareBy<JsSourceItem> { it.state == PluginState.NOT_INSTALLED }
				.thenBy { it.entry.name.lowercase() },
		)) {
			groups.getValue(item.entry.itemType).addPreference(
				Preference(context).apply {
					isPersistent = false
					key = "monogatari_js_source_${item.entry.id}"
					setIcon(when (item.entry.itemType) {
						JsItemType.MANGA -> R.drawable.ic_monogatari_nav_explore
						JsItemType.NOVEL -> R.drawable.ic_monogatari_nav_books
						JsItemType.ANIME -> R.drawable.ic_monogatari_nav_feed
					})
					title = item.entry.name
					summary = describeJs(item)
					setOnPreferenceClickListener {
						onJsSourceClick(item)
						true
					}
				},
			)
		}
		for (entry in state.plugins) {
			pluginsCategory.addPreference(
				Preference(context).apply {
					isPersistent = false
					title = entry.plugin.name
					summary = describe(entry)
					setOnPreferenceClickListener {
						onPluginClick(entry)
						true
					}
				},
			)
		}
	}

	private fun showDartAlternatives(
		alternatives: List<Pair<JsSourceEntry, DartSourceCompatibility.Alternative>>,
		state: SourceReposViewModel.State,
	) {
		val display = alternatives.take(75)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Dart compatibility candidates")
			.setMessage("These are alternatives for the same named website, not converted Dart parsers. Availability and reading must still be tested.")
			.setItems(display.map { (dart, alternative) ->
				"${dart.name} → ${alternative.description}"
			}.toTypedArray()) { _, position ->
				val (dart, alternative) = display[position]
				val target = alternative.parser
				if (target != null) {
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(dart.name)
						.setMessage("Open the existing Kotatsu parser ${target.name}? This does not run the Dart extension.")
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton("Open") { _, _ -> router.openList(target, null, null) }
						.show()
				} else {
					val js = alternative.js ?: return@setItems
					val isInstalled = state.jsSources.any {
						it.entry.id == js.id && it.state != PluginState.NOT_INSTALLED
					}
					MaterialAlertDialogBuilder(requireContext())
						.setTitle(dart.name)
						.setMessage("A matching JavaScript source exists: ${js.name}. This does not execute Dart.")
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(if (isInstalled) "Open" else "Install") { _, _ ->
							if (isInstalled) router.openList(JsMangaSource(js.id), null, null)
							else viewModel.installJs(js)
						}
						.show()
				}
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun describe(entry: PluginEntry): String {
		val type = getString(
			when (entry.plugin.mediaType) {
				MediaType.MANGA -> R.string.media_type_manga
				MediaType.BOOK -> R.string.media_type_book
				MediaType.VIDEO -> R.string.media_type_video
				MediaType.AUDIO -> R.string.media_type_audio
			},
		)
		val status = when (entry.state) {
			PluginState.NOT_INSTALLED -> null
			PluginState.INSTALLED -> getString(R.string.plugin_installed)
			PluginState.UPDATE_AVAILABLE -> getString(R.string.plugin_update_available)
		}
		return listOfNotNull("v${entry.plugin.versionName}", type, status).joinToString(" · ")
	}

	private fun describeJs(item: JsSourceItem): String {
		val e = item.entry
		val type = getString(
			when (e.itemType) {
				JsItemType.MANGA -> R.string.media_type_manga
				JsItemType.NOVEL -> R.string.media_type_book
				JsItemType.ANIME -> R.string.media_type_video
			},
		)
		val status = when (item.state) {
			PluginState.NOT_INSTALLED -> null
			PluginState.INSTALLED -> getString(R.string.plugin_installed)
			PluginState.UPDATE_AVAILABLE -> getString(R.string.plugin_update_available)
		}
		return listOfNotNull("v${e.version}", e.lang.takeIf { it.isNotEmpty() }, type, status).joinToString(" · ")
	}

	private fun onJsSourceClick(item: JsSourceItem) {
		when (item.state) {
			PluginState.NOT_INSTALLED -> viewModel.installJs(item.entry)
			PluginState.INSTALLED -> showJsSourceActions(item, canUpdate = false)
			PluginState.UPDATE_AVAILABLE -> showJsSourceActions(item, canUpdate = true)
		}
	}

	private fun showJsSourceActions(item: JsSourceItem, canUpdate: Boolean) {
		val labels = ArrayList<CharSequence>(3)
		val actions = ArrayList<() -> Unit>(3)
		if (canUpdate) {
			labels += getString(R.string.update)
			actions += { viewModel.installJs(item.entry) }
		}
		labels += getString(R.string.settings)
		actions += { openJsSourceSettings(item.entry) }
		if (item.entry.baseUrl.startsWith("http")) {
			labels += getString(R.string.open_in_browser)
			actions += {
				startActivity(
					AppRouter.browserIntent(
						requireContext(),
						item.entry.baseUrl,
						JsMangaSource(item.entry.id),
						item.entry.name,
					),
				)
			}
		}
		labels += getString(R.string.remove)
		actions += { confirmJsSourceRemove(item.entry) }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(item.entry.name)
			.setItems(labels.toTypedArray()) { _, which -> actions[which]() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun confirmJsSourceRemove(entry: JsSourceEntry) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(entry.name)
			.setMessage(R.string.js_source_remove_confirm)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.remove) { _, _ -> viewModel.uninstallJs(entry) }
			.show()
	}

	private fun openJsSourceSettings(entry: JsSourceEntry) {
		viewLifecycleOwner.lifecycleScope.launch {
			val preferences = jsExtensions.getSourcePreferences(entry.id)
			if (preferences.isEmpty()) {
				Toast.makeText(requireContext(), R.string.js_source_no_preferences, Toast.LENGTH_SHORT).show()
				return@launch
			}
			showJsSourcePreferences(entry, preferences)
		}
	}

	private fun showJsSourcePreferences(entry: JsSourceEntry, preferences: List<JsonObject>) {
		val context = requireContext()
		val density = resources.displayMetrics.density
		val gap = (8 * density).toInt()
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val fields = ArrayList<Pair<String, () -> String?>>()
		val content = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, gap, padding, gap)
		}

		fun addHeading(title: String, summary: String?) {
			content.addView(TextView(context).apply {
				text = title
				setPadding(0, gap, 0, 0)
			})
			if (!summary.isNullOrBlank()) {
				content.addView(TextView(context).apply {
					text = summary
					alpha = 0.72f
				})
			}
		}

		for (definition in preferences) {
			val key = definition.string("key") ?: continue
			val stored = jsExtensions.getPreference(entry.id, key)

			val editText = definition["editTextPreference"] as? JsonObject
			if (editText != null) {
				addHeading(editText.string("title") ?: key, editText.string("summary"))
				val defaultValue = editText.string("value").orEmpty()
				val input = EditText(context).apply {
					setSingleLine(false)
					setText(stored ?: defaultValue)
					hint = editText.string("dialogMessage")
					inputType = if ((stored ?: defaultValue).startsWith("http")) {
						InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
					} else {
						InputType.TYPE_CLASS_TEXT
					}
				}
				content.addView(input)
				fields += key to { input.text?.toString() }
				continue
			}

			val toggle = (definition["switchPreferenceCompat"] as? JsonObject)
				?: (definition["checkBoxPreference"] as? JsonObject)
			if (toggle != null) {
				val box = CheckBox(context).apply {
					text = toggle.string("title") ?: key
					isChecked = stored?.toBooleanStrictOrNull()
						?: toggle.boolean("value")
						?: false
				}
				content.addView(box)
				toggle.string("summary")?.takeIf { it.isNotBlank() }?.let { summary ->
					content.addView(TextView(context).apply {
						text = summary
						alpha = 0.72f
					})
				}
				fields += key to { box.isChecked.toString() }
				continue
			}

			val list = definition["listPreference"] as? JsonObject
			if (list != null) {
				val values = list.stringArray("entryValues")
				if (values.isEmpty()) continue
				val entries = list.stringArray("entries").takeIf { it.size == values.size } ?: values
				addHeading(list.string("title") ?: key, list.string("summary"))
				val spinner = Spinner(context).apply {
					adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, entries)
					val defaultIndex = list.int("valueIndex")?.coerceIn(0, values.lastIndex) ?: 0
					setSelection(values.indexOf(stored).takeIf { it >= 0 } ?: defaultIndex)
				}
				content.addView(spinner)
				fields += key to { values.getOrNull(spinner.selectedItemPosition) }
				continue
			}

			val multiSelect = definition["multiSelectListPreference"] as? JsonObject
			if (multiSelect != null) {
				val values = multiSelect.stringArray("entryValues")
				if (values.isEmpty()) continue
				val entries = multiSelect.stringArray("entries").takeIf { it.size == values.size } ?: values
				val defaults = multiSelect.stringArray("values").toSet()
				val selected = stored?.let { raw ->
					runCatching {
						(Json.parseToJsonElement(raw) as? JsonArray)
							.orEmpty()
							.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
							.toSet()
					}.getOrNull()
				} ?: defaults
				addHeading(multiSelect.string("title") ?: key, multiSelect.string("summary"))
				val boxes = entries.mapIndexed { index, label ->
					CheckBox(context).apply {
						text = label
						isChecked = values[index] in selected
						content.addView(this)
					}
				}
				fields += key to {
					JsonArray(
						boxes.mapIndexedNotNull { index, box ->
							values.getOrNull(index)?.takeIf { box.isChecked }?.let(::JsonPrimitive)
						},
					).toString()
				}
			}
		}

		if (fields.isEmpty()) {
			Toast.makeText(context, R.string.js_source_no_preferences, Toast.LENGTH_SHORT).show()
			return
		}
		val scroll = ScrollView(context).apply { addView(content) }
		MaterialAlertDialogBuilder(context)
			.setTitle(entry.name)
			.setView(scroll)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.apply) { _, _ ->
				viewLifecycleOwner.lifecycleScope.launch {
					for ((key, value) in fields) {
						jsExtensions.setPreference(entry.id, key, value())
					}
					Toast.makeText(context, R.string.js_source_preferences_saved, Toast.LENGTH_SHORT).show()
				}
			}
			.show()
	}

	private fun JsonObject.string(key: String): String? =
		(this[key] as? JsonPrimitive)?.contentOrNull

	private fun JsonObject.boolean(key: String): Boolean? =
		(this[key] as? JsonPrimitive)?.booleanOrNull

	private fun JsonObject.int(key: String): Int? =
		(this[key] as? JsonPrimitive)?.intOrNull

	private fun JsonObject.stringArray(key: String): List<String> =
		(this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

	private fun onPluginClick(entry: PluginEntry) {
		if (entry.state == PluginState.INSTALLED) {
			Toast.makeText(requireContext(), R.string.plugin_up_to_date, Toast.LENGTH_SHORT).show()
		} else {
			viewModel.install(entry.plugin)
		}
	}

	private fun confirmRemove(url: String) {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.remove_repository_confirm)
			.setMessage(url)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.remove) { _, _ -> viewModel.removeRepo(url) }
			.show()
	}

	private fun showAddDialog() {
		val context = requireContext()
		val input = EditText(context).apply {
			setHint(R.string.repository_url_hint)
			inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
			setSingleLine()
		}
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val container = FrameLayout(context).apply {
			setPadding(padding, padding / 2, padding, 0)
			addView(input)
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.add_repository)
			.setView(container)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add) { _, _ ->
				if (!viewModel.previewRepo(input.text.toString())) {
					Toast.makeText(context, R.string.repository_invalid_url, Toast.LENGTH_SHORT).show()
				}
			}
			.show()
	}

	private fun showRepoPreview(preview: RepoPreview) {
		val context = requireContext()
		val names = (preview.jsSourceNames + preview.pluginNames).distinct()
		val shown = names.take(12)
		val remaining = names.size - shown.size
		val message = buildString {
			append(getString(R.string.repository_preview_url, preview.url))
			append("\n\n")
			append(getString(R.string.repository_preview_counts, preview.jsSourceNames.size, preview.pluginNames.size))
			if (preview.skippedDartCount > 0) {
				append("\n")
				append(getString(R.string.repository_preview_skipped, preview.skippedDartCount))
			}
			if (shown.isNotEmpty()) {
				append("\n\n")
				append(getString(R.string.repository_preview_contains))
				for (name in shown) append("\n- ").append(name)
				if (remaining > 0) append("\n").append(getString(R.string.repository_preview_more, remaining))
			}
			append("\n\n")
			append(getString(R.string.repository_trust_warning))
		}
		val padding = resources.getDimensionPixelSize(R.dimen.margin_normal)
		val body = TextView(context).apply {
			text = message
			setPadding(padding, padding / 2, padding, padding / 2)
		}
		val scroll = ScrollView(context).apply { addView(body) }
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.repository_preview_title)
			.setView(scroll)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.add) { _, _ -> viewModel.addRepo(preview.url) }
			.show()
	}

	private companion object {

		const val KEY_ADD = "source_repo_add"
		const val KEY_REPOS = "source_repos_list"
		const val KEY_PLUGINS = "source_plugins_list"
	}
}
