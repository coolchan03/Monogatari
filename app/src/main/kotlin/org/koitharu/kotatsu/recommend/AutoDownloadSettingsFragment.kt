package org.koitharu.kotatsu.recommend

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.viewLifecycleScope
import org.koitharu.kotatsu.databinding.DialogAutoDownloadSetupBinding
import java.text.NumberFormat
import javax.inject.Inject

@AndroidEntryPoint
class AutoDownloadSettingsFragment : BasePreferenceFragment(R.string.auto_download_recommendations) {

	@Inject
	lateinit var store: AutoDownloadStore

	@Inject
	lateinit var scheduler: AutoDownloadWorker.Scheduler

	@Inject
	lateinit var cleaner: AutoDownloadCleaner

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		preferenceManager.sharedPreferencesName = AutoDownloadStore.PREFS_NAME
		preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
		addPreferencesFromResource(R.xml.pref_auto_download)
		findPreference<SwitchPreferenceCompat>(AutoDownloadStore.KEY_ENABLED)?.setOnPreferenceChangeListener { _, newValue ->
			if (newValue == true) {
				showSetupDialog(enableOnConfirm = true)
				false // switched on only once the setup is confirmed
			} else {
				updateSchedule(isEnabled = false)
				true
			}
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		bindSetupSummary()
		bindUsage(reconcile = true)
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			KEY_SETUP -> {
				showSetupDialog(enableOnConfirm = false)
				true
			}

			KEY_REMOVE -> {
				confirmRemoveAll()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	private fun showSetupDialog(enableOnConfirm: Boolean) {
		val context = context ?: return
		val binding = DialogAutoDownloadSetupBinding.inflate(LayoutInflater.from(context))
		val limitButtons = listOf(binding.limit500, binding.limit1024, binding.limit2048, binding.limit5120)
		val chapterButtons = listOf(binding.chapters1, binding.chapters3, binding.chapters5, binding.chapters10)
		val bookButtons = listOf(binding.books1, binding.books3, binding.books5)
		val numberFormat = NumberFormat.getIntegerInstance()
		limitButtons.forEachIndexed { i, button ->
			button.text = formatLimit(AutoDownloadStore.STORAGE_LIMIT_OPTIONS_MB[i])
		}
		chapterButtons.forEachIndexed { i, button ->
			button.text = numberFormat.format(AutoDownloadStore.CHAPTERS_OPTIONS[i])
		}
		bookButtons.forEachIndexed { i, button ->
			button.text = numberFormat.format(AutoDownloadStore.BOOKS_OPTIONS[i])
		}
		binding.groupLimit.select(limitButtons, AutoDownloadStore.STORAGE_LIMIT_OPTIONS_MB, store.storageLimitMb)
		binding.groupChapters.select(chapterButtons, AutoDownloadStore.CHAPTERS_OPTIONS, store.chaptersPerTitle)
		binding.groupBooks.select(bookButtons, AutoDownloadStore.BOOKS_OPTIONS, store.booksCount)
		binding.checkManga.isChecked = store.isMangaEnabled
		binding.checkBooks.isChecked = store.isBooksEnabled
		binding.checkVideo.isChecked = false
		binding.checkCharging.isChecked = store.isChargingRequired
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.auto_download_setup)
			.setView(binding.root)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				store.storageLimitMb = binding.groupLimit.selected(
					limitButtons, AutoDownloadStore.STORAGE_LIMIT_OPTIONS_MB, AutoDownloadStore.DEFAULT_STORAGE_LIMIT_MB,
				)
				store.chaptersPerTitle = binding.groupChapters.selected(
					chapterButtons, AutoDownloadStore.CHAPTERS_OPTIONS, AutoDownloadStore.DEFAULT_CHAPTERS_PER_TITLE,
				)
				store.booksCount = binding.groupBooks.selected(
					bookButtons, AutoDownloadStore.BOOKS_OPTIONS, AutoDownloadStore.DEFAULT_BOOKS_COUNT,
				)
				store.isMangaEnabled = binding.checkManga.isChecked
				store.isBooksEnabled = binding.checkBooks.isChecked
				store.isVideoEnabled = false
				store.isChargingRequired = binding.checkCharging.isChecked
				if (enableOnConfirm) {
					findPreference<SwitchPreferenceCompat>(AutoDownloadStore.KEY_ENABLED)?.isChecked = true
				}
				bindSetupSummary()
				bindUsage(reconcile = false)
				if (store.isEnabled) {
					updateSchedule(isEnabled = true) // also applies a changed charging constraint
				}
			}.show()
	}

	private fun confirmRemoveAll() {
		val context = context ?: return
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.auto_download_remove)
			.setMessage(R.string.auto_download_remove_confirm)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.auto_download_remove) { _, _ ->
				viewLifecycleScope.launch {
					try {
						withContext(Dispatchers.Default) { cleaner.removeAll() }
						view?.let { Snackbar.make(it, R.string.auto_download_removed, Snackbar.LENGTH_SHORT).show() }
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						e.printStackTraceDebug()
					}
					bindUsage(reconcile = false)
				}
			}.show()
	}

	private fun updateSchedule(isEnabled: Boolean) {
		viewLifecycleScope.launch {
			try {
				withContext(Dispatchers.Default) {
					if (isEnabled) scheduler.schedule() else scheduler.unschedule()
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				e.printStackTraceDebug()
			}
		}
	}

	private fun bindSetupSummary() {
		val pref = findPreference<Preference>(KEY_SETUP) ?: return
		val chapters = store.chaptersPerTitle
		pref.summary = getString(
			R.string.auto_download_setup_summary,
			formatLimit(store.storageLimitMb),
			resources.getQuantityString(R.plurals.auto_download_chapters_count, chapters, chapters),
		)
	}

	private fun bindUsage(reconcile: Boolean) {
		val pref = findPreference<Preference>(KEY_USAGE) ?: return
		viewLifecycleScope.launch {
			val entries = try {
				withContext(Dispatchers.Default) {
					if (reconcile) cleaner.reconcile() else store.getEntries()
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				e.printStackTraceDebug()
				store.getEntries()
			}
			val context = pref.context
			pref.summary = getString(
				R.string.auto_download_usage_summary,
				Formatter.formatShortFileSize(context, AutoDownloadPlanner.usedBytes(entries)),
				formatLimit(store.storageLimitMb),
				entries.size,
			)
		}
	}

	private fun formatLimit(mb: Int): String = if (mb >= 1024 && mb % 1024 == 0) {
		getString(R.string.auto_download_size_gb, mb / 1024)
	} else {
		getString(R.string.auto_download_size_mb, mb)
	}

	private fun RadioGroup.select(buttons: List<RadioButton>, values: IntArray, value: Int) {
		val index = values.indexOf(value).takeIf { it >= 0 } ?: 0
		check(buttons[index].id)
	}

	private fun RadioGroup.selected(buttons: List<RadioButton>, values: IntArray, fallback: Int): Int {
		val index = buttons.indexOfFirst { it.id == checkedRadioButtonId }
		return if (index >= 0) values[index] else fallback
	}

	private companion object {

		const val KEY_SETUP = "setup"
		const val KEY_USAGE = "usage"
		const val KEY_REMOVE = "remove"
	}
}
