package org.koitharu.kotatsu.settings.userdata

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.domain.ForeignBackupReviewActivity
import android.text.format.DateUtils
import android.text.format.Formatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.backups.domain.UpdateSnapshots
import java.io.File
import org.koitharu.kotatsu.backups.ui.backup.BackupService
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.tryLaunch

@AndroidEntryPoint
class BackupsSettingsFragment : BasePreferenceFragment(R.string.backup_restore),
    ActivityResultCallback<Uri?> {

    private val viewModel: BackupsSettingsViewModel by viewModels()

    private val backupSelectCall = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
        this,
    )

    private val backupCreateCall = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            if (!BackupService.start(requireContext(), uri)) {
                Snackbar.make(
                    listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_backups)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindPeriodicalBackupSummary()
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        return when (preference.key) {
            AppSettings.KEY_BACKUP -> {
                if (!backupCreateCall.tryLaunch(BackupUtils.generateFileName(preference.context))) {
                    Snackbar.make(
                        listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                    ).show()
                }
                true
            }

            AppSettings.KEY_RESTORE -> {
                if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
                    Snackbar.make(
                        listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
                    ).show()
                }
                true
            }

            "monogatari_update_snapshots" -> {
                showUpdateSnapshots()
                true
            }

            "monogatari_foreign_backup_review" -> {
                startActivity(Intent(requireContext(), ForeignBackupReviewActivity::class.java))
                true
            }

            else -> super.onPreferenceTreeClick(preference)
        }
    }

    override fun onActivityResult(result: Uri?) {
        if (result != null) {
            router.showBackupRestoreDialog(result)
        }
    }

    private fun showUpdateSnapshots() {
        val context = requireContext()
        val snapshots = UpdateSnapshots(File(context.applicationInfo.dataDir), context.filesDir).list()
        if (snapshots.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_snapshots_title)
                .setMessage(R.string.update_snapshots_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val labels = snapshots.map { snapshot ->
            getString(
                R.string.update_snapshots_item,
                snapshot.fromVersionCode,
                DateUtils.formatDateTime(context, snapshot.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
                Formatter.formatShortFileSize(context, snapshot.sizeBytes),
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_snapshots_title)
            .setItems(labels) { _, which -> confirmUpdateRestore(snapshots[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmUpdateRestore(snapshot: UpdateSnapshots.Snapshot) {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_snapshots_title)
            .setMessage(R.string.update_snapshots_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.update_snapshots_restore) { _, _ ->
                UpdateSnapshots(File(context.applicationInfo.dataDir), context.filesDir).scheduleRestore(snapshot)
                // The copy happens on the next start, before the database is opened.
                requireActivity().finishAffinity()
                Runtime.getRuntime().exit(0)
            }
            .show()
    }

    private fun bindPeriodicalBackupSummary() {
        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED) ?: return
        val entries = resources.getStringArray(R.array.backup_frequency)
        val entryValues = resources.getStringArray(R.array.values_backup_frequency)
        viewModel.periodicalBackupFrequency.observe(viewLifecycleOwner) { freq ->
            preference.summary = if (freq == 0L) {
                getString(R.string.disabled)
            } else {
                val index = entryValues.indexOf(freq.toString())
                entries.getOrNull(index)
            }
        }
    }
}
