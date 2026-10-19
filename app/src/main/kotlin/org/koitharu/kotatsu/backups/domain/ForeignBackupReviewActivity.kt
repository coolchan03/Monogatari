package org.koitharu.kotatsu.backups.domain

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

/**
 * Imports a backup from another app. Kotatsu backups share Monogatari's format and go to the normal restore
 * dialog; Mangayomi backups are previewed first and only imported once the user taps Import.
 */
@AndroidEntryPoint
class ForeignBackupReviewActivity : AppCompatActivity() {

	@Inject
	lateinit var mangayomiImporter: MangayomiImporter

	@Inject
	lateinit var mihonImporter: MihonImporter

	private lateinit var status: TextView
	private lateinit var chooseFile: MaterialButton
	private lateinit var action: MaterialButton
	private var pendingAction: (() -> Unit)? = null

	private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri != null) inspectSelected(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.foreign_backup_review_title)
		val pad = (16f * resources.displayMetrics.density).toInt()
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad, pad, pad)
		}
		chooseFile = MaterialButton(this).apply {
			text = getString(R.string.foreign_backup_choose)
			setOnClickListener { picker.launch(arrayOf("*/*")) }
		}
		action = MaterialButton(this).apply {
			isVisible = false
			setOnClickListener { pendingAction?.invoke() }
		}
		status = TextView(this).apply {
			text = getString(R.string.foreign_backup_read_only)
			textSize = 15f
			setTextIsSelectable(true)
		}
		root.addView(chooseFile)
		root.addView(
			ScrollView(this).apply { addView(status) },
			LinearLayout.LayoutParams(-1, 0, 1f),
		)
		root.addView(action)
		setMonogatariContent(root, MonogatariChrome.TEXT_MAX_WIDTH_DP)
	}

	private fun displayName(uri: Uri): String =
		runCatching {
			contentResolver.query(
				uri, arrayOf(OpenableColumns.DISPLAY_NAME),
				null, null, null,
			)?.use { cursor ->
				if (cursor.moveToFirst()) cursor.getString(0) else null
			}
		}.getOrNull().orEmpty()

	private sealed interface Detected {
		data object Kotatsu : Detected
		class Mangayomi(val preview: MangayomiImporter.Preview) : Detected
		data object Mihon : Detected
		class MihonDecoded(val preview: MihonImporter.Preview) : Detected
		data object Unknown : Detected
	}

	private fun inspectSelected(uri: Uri) {
		chooseFile.isEnabled = false
		showAction(null, null)
		status.text = getString(R.string.foreign_backup_reviewing)
		lifecycleScope.launch {
			val detected = runCatching {
				withContext(Dispatchers.IO) { detect(uri) }
			}.onFailure { if (it is CancellationException) throw it }
			chooseFile.isEnabled = true
			detected.fold(
				onSuccess = { show(it, uri) },
				onFailure = { status.text = getString(R.string.foreign_backup_failed, it.message.orEmpty()) },
			)
		}
	}

	private suspend fun detect(uri: Uri): Detected {
		val name = displayName(uri).lowercase(Locale.ROOT)
		val staged = File.createTempFile("foreign-import-", ".tmp", cacheDir)
		try {
			contentResolver.openInputStream(uri)?.use {
				BackupArchiveInspector.copyBounded(it, staged)
			} ?: throw IOException("Selected backup is unreadable")
			val head = ByteArray(4)
			val n = staged.inputStream().use { it.read(head) }
			val isZip = n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
			val isGzip = n >= 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
			if (name.endsWith(".tachibk") || name.endsWith(".proto.gz") || isGzip) {
				// Undecodable or empty backups fall back to the explanation instead of a partial import.
				val backup = runCatching { staged.inputStream().use { MihonBackup.read(it) } }.getOrNull()
					?.takeIf { it.manga.isNotEmpty() } ?: return Detected.Mihon
				return Detected.MihonDecoded(mihonImporter.preview(backup))
			}
			if (isZip && !name.endsWith(".backup")) {
				val isKotatsu = runCatching { BackupArchiveInspector.inspect(staged) }.isSuccess
				if (isKotatsu) return Detected.Kotatsu
			}
			val backup = runCatching { staged.inputStream().use { MangayomiBackup.read(it) } }.getOrNull()
				?: return Detected.Unknown
			return Detected.Mangayomi(mangayomiImporter.preview(backup))
		} finally {
			staged.delete()
		}
	}

	private fun show(detected: Detected, uri: Uri) {
		when (detected) {
			Detected.Kotatsu -> {
				status.text = getString(R.string.foreign_backup_kotatsu)
				showAction(getString(R.string.foreign_backup_restore)) { router.showBackupRestoreDialog(uri) }
			}

			is Detected.Mangayomi -> {
				val preview = detected.preview
				status.text = buildString {
					append(
						getString(
							R.string.foreign_backup_mangayomi_summary,
							preview.titles, preview.favourites, preview.withProgress, preview.matched,
						),
					)
					if (preview.sourcesToInstall.isNotEmpty()) {
						append("\n\n")
						append(getString(R.string.foreign_backup_mangayomi_install, preview.sourcesToInstall.joinToString(", ")))
					}
					if (preview.unmatchedSources.isNotEmpty()) {
						append("\n\n")
						append(getString(R.string.foreign_backup_mangayomi_unmatched, preview.unmatchedSources.joinToString(", ")))
					}
				}
				showAction(getString(R.string.foreign_backup_import)) { runImport(preview) }
			}

			Detected.Mihon -> status.text = getString(R.string.foreign_backup_mihon)
			is Detected.MihonDecoded -> {
				val preview = detected.preview
				status.text = buildString {
					append(getString(R.string.foreign_backup_mihon_summary,
						preview.titles, preview.favourites, preview.withProgress, preview.matchedTitles))
					if (preview.unmatchedSources.isNotEmpty()) {
						append("\n\n")
						append(getString(R.string.foreign_backup_mangayomi_unmatched, preview.unmatchedSources.joinToString(", ")))
					}
					append("\n\n")
					append(getString(R.string.foreign_backup_mihon_how))
				}
				if (preview.matchedTitles > 0) {
					showAction(getString(R.string.foreign_backup_import)) { runMihonImport(preview) }
				}
			}
			Detected.Unknown -> status.text = getString(R.string.foreign_backup_unknown)
		}
	}

	private fun runImport(preview: MangayomiImporter.Preview) {
		showAction(null, null)
		chooseFile.isEnabled = false
		status.text = getString(R.string.foreign_backup_importing)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		lifecycleScope.launch {
			val result = runCatching {
				mangayomiImporter.importAll(preview, getString(R.string.foreign_backup_default_category))
			}.onFailure { if (it is CancellationException) throw it }
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			chooseFile.isEnabled = true
			status.text = result.fold(
				onSuccess = { r ->
					buildString {
						append(getString(R.string.foreign_backup_done, r.imported))
						if (r.skipped > 0) append("\n").append(getString(R.string.foreign_backup_done_skipped, r.skipped))
						if (r.installFailures.isNotEmpty()) {
							append("\n\n")
							append(getString(R.string.foreign_backup_done_install_failed, r.installFailures.joinToString(", ")))
						}
					}
				},
				onFailure = { getString(R.string.foreign_backup_failed, it.message.orEmpty()) },
			)
		}
	}

	private fun runMihonImport(preview: MihonImporter.Preview) {
		showAction(null, null)
		chooseFile.isEnabled = false
		status.text = getString(R.string.foreign_backup_importing)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		lifecycleScope.launch {
			val result = runCatching {
				mihonImporter.importFavourites(preview, getString(R.string.foreign_backup_default_category)) { done, total ->
					runOnUiThread { status.text = getString(R.string.foreign_backup_mihon_progress, done, total) }
				}
			}.onFailure { if (it is CancellationException) throw it }
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			chooseFile.isEnabled = true
			status.text = result.fold(
				onSuccess = { r ->
					buildString {
						append(getString(R.string.foreign_backup_done, r.imported))
						if (r.notFound.isNotEmpty()) {
							append("\n\n")
							append(getString(R.string.foreign_backup_mihon_not_found, r.notFound.take(40).joinToString(", ")))
						}
						if (r.unsupported > 0) append("\n").append(getString(R.string.foreign_backup_mihon_unsupported, r.unsupported))
					}
				},
				onFailure = { getString(R.string.foreign_backup_failed, it.message.orEmpty()) },
			)
		}
	}

	private fun showAction(label: String?, onClick: (() -> Unit)?) {
		pendingAction = onClick
		action.text = label
		action.isVisible = label != null
	}
}
