package org.koitharu.kotatsu.backups.domain

import android.app.Dialog
import android.net.Uri
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router

/**
 * Dialog sequence of the subscription import: read the chosen file, preview what was found, import with
 * progress, then summarise with a shortcut to the Library. Hosted by a settings fragment.
 */
internal class SubscriptionImportFlow(
	private val fragment: Fragment,
	private val importer: SubscriptionImporter,
) {

	fun start(uri: Uri) {
		val context = fragment.requireContext()
		fragment.viewLifecycleOwner.lifecycleScope.launch {
			val reading = showProgress(context.getString(R.string.sub_import_reading), indeterminate = true)
			val parsed = try {
				withContext(Dispatchers.IO) {
					val bytes = context.contentResolver.openInputStream(uri)?.use {
						SubscriptionImportParser.readBounded(it)
					} ?: throw java.io.IOException("File is unreadable")
					SubscriptionImportParser.parse(bytes)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				showMessage(context.getString(R.string.sub_import_read_failed, e.message.orEmpty()))
				return@launch
			} finally {
				reading.close()
			}
			if (parsed.channels.isEmpty()) {
				showMessage(context.getString(R.string.sub_import_none_found))
			} else {
				showPreview(parsed)
			}
		}
	}

	private fun showPreview(parsed: SubscriptionImportParser.Result) {
		val context = fragment.requireContext()
		val lines = ArrayList<String>()
		lines += context.getString(R.string.sub_import_preview_found, parsed.channels.size)
		if (parsed.skipped > 0) lines += context.getString(R.string.sub_import_preview_skipped, parsed.skipped)
		if (parsed.duplicates > 0) lines += context.getString(R.string.sub_import_preview_duplicates, parsed.duplicates)
		if (parsed.truncated) {
			lines += context.getString(R.string.sub_import_preview_truncated, SubscriptionImportParser.MAX_ENTRIES)
		}
		lines += context.getString(R.string.sub_import_preview_category, SubscriptionImporter.CATEGORY_TITLE)
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.sub_import_title)
			.setMessage(lines.joinToString("\n"))
			.setPositiveButton(R.string.sub_import_action) { _, _ -> runImport(parsed) }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun runImport(parsed: SubscriptionImportParser.Result) {
		val context = fragment.requireContext()
		fragment.viewLifecycleOwner.lifecycleScope.launch {
			val progress = showProgress(context.getString(R.string.sub_import_importing), indeterminate = false)
			progress.bar.max = parsed.channels.size
			val result = try {
				importer.import(parsed.channels) { done, _ ->
					progress.bar.post { progress.bar.progress = done }
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				showMessage(context.getString(R.string.sub_import_read_failed, e.message.orEmpty()))
				return@launch
			} finally {
				progress.close()
			}
			val summary = listOf(
				context.getString(R.string.sub_import_done_imported, result.imported),
				context.getString(R.string.sub_import_done_present, result.alreadyPresent),
				context.getString(R.string.sub_import_done_skipped, parsed.skipped),
			).joinToString("\n")
			MaterialAlertDialogBuilder(context)
				.setTitle(R.string.sub_import_title)
				.setMessage(summary)
				.setPositiveButton(R.string.sub_import_open_library) { _, _ -> fragment.router.openFavorites(result.category) }
				.setNegativeButton(R.string.sub_import_close, null)
				.show()
		}
	}

	private fun showMessage(message: String) {
		MaterialAlertDialogBuilder(fragment.requireContext())
			.setTitle(R.string.sub_import_title)
			.setMessage(message)
			.setPositiveButton(R.string.sub_import_close, null)
			.show()
	}

	private class Progress(val dialog: Dialog, val bar: LinearProgressIndicator) {

		/** Also runs when the screen is left mid-way, where the window may already be gone. */
		fun close() {
			runCatching { dialog.dismiss() }
		}
	}

	private fun showProgress(message: String, indeterminate: Boolean): Progress {
		val context = fragment.requireContext()
		val pad = (24 * context.resources.displayMetrics.density).toInt()
		val bar = LinearProgressIndicator(context).apply { isIndeterminate = indeterminate }
		val column = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad / 2, pad, 0)
			addView(
				TextView(context).apply { text = message },
				LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
			)
			addView(
				bar,
				LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
					topMargin = pad / 2
				},
			)
		}
		val dialog = MaterialAlertDialogBuilder(context)
			.setTitle(R.string.sub_import_title)
			.setView(column)
			.setCancelable(false)
			.show()
		return Progress(dialog, bar)
	}
}
