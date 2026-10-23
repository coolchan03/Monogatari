package org.koitharu.kotatsu.backups.domain

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addPrimaryButton
import org.koitharu.kotatsu.core.ui.addSecondaryButton
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.scrollable
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Explicit manual WebDAV backup transfer. Never automatically restores the database. */
@AndroidEntryPoint
class WebDavManualSyncActivity : AppCompatActivity() {
    @Inject internal lateinit var accountStore: WebDavAccountStore
    @Inject internal lateinit var transport: WebDavSnapshotTransport
    private lateinit var status: TextView
    private val deviceId by lazy {
        val prefs = getSharedPreferences("monogatari_webdav_device", MODE_PRIVATE)
        prefs.getString("uuid", null)?.takeIf(WebDavEndpoint::validDeviceId)
            ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("uuid", it).apply()
        }
    }
    private var downloadId: String? = null
    private var busy = false
    private val uploadPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uploadPicked(uri)
    }
    private val downloadPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = downloadId
        downloadId = null
        if (uri != null && id != null) downloadPicked(id, uri)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingDownloadId", downloadId)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadId = savedInstanceState?.getString("pendingDownloadId")
            ?.takeIf(WebDavEndpoint::validDeviceId)
        title = getString(R.string.screen_title_webdav)
        val root = pageColumn()
        // The header body is the status line, so progress and errors appear next to Kitsune.
        status = root.addHeaderCard(
            MascotForm.DEFAULT,
            getString(R.string.mono_screen_header_webdav),
            getString(R.string.mono_webdav_only_manually_selected_zip_archives_are),
        ).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val accountSection = root.addSectionCard(getString(R.string.mono_section_webdav_account))
        val backupsSection = root.addSectionCard(getString(R.string.mono_section_webdav_backups))
        // Account setup is the one filled action; the rest are outlined, full width like the Nearby screen.
        accountSection.addPrimaryButton(getString(R.string.mono_webdav_configure_secure_webdav_account)) { configure() }
        backupsSection.addSecondaryButton(getString(R.string.mono_webdav_upload_selected_backup_zip)) {
            if (account() != null) MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.mono_webdav_replace_this_device_s_remote_backup))
                .setMessage(getString(R.string.mono_webdav_uploading_replaces_the_existing_zip_for))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.mono_webdav_choose_backup_zip)) { _, _ ->
                    uploadPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }.show()
        }
        backupsSection.addSecondaryButton(getString(R.string.mono_webdav_list_and_download_backup_zip)) { listRemote() }
        accountSection.addSecondaryButton(getString(R.string.mono_webdav_remove_account_from_this_device)) {
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_webdav_remove_webdav_credentials))
                .setMessage(getString(R.string.mono_webdav_this_removes_the_saved_account_from))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.mono_webdav_remove)) { _, _ ->
                    accountStore.clear()
                    status.text = getString(R.string.mono_webdav_account_removed)
                }.show()
        }
        setMonogatariContent(root.scrollable(), MonogatariChrome.TEXT_MAX_WIDTH_DP)
    }

    private fun account(): WebDavAccount? = runCatching { accountStore.load() }.getOrNull().also {
        if (it == null) status.text = getString(R.string.mono_webdav_configure_your_https_webdav_account_first)
    }

    private fun configure() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val fields = listOf("HTTPS WebDAV folder URL", "Username", "App password").map { label ->
            EditText(this).apply {
                hint = label
                isSingleLine = true
                if (label == "App password") inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                root.addView(this)
            }
        }
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_webdav_encrypted_webdav_credentials)).setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.mono_webdav_save)) { _, _ ->
                runCatching { accountStore.save(fields[0].text.toString().trim(),
                    fields[1].text.toString(), fields[2].text.toString()) }
                    .onSuccess { status.text = getString(R.string.mono_webdav_credentials_encrypted_using_android_keystore) }
                    .onFailure { status.text = "Configuration failed: ${it.message}" }
            }.show()
    }

    private fun uploadPicked(uri: Uri) {
        val account = account() ?: return
        if (busy) return
        busy = true
        status.text = getString(R.string.mono_webdav_uploading_selected_zip)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val file = File.createTempFile("dav-upload", ".zip", cacheDir)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        BackupArchiveInspector.copyBounded(input, file, 128L * 1024 * 1024)
                    } ?: error("Cannot read selected backup")
                    // Verify each section and CRC before replacing the remote device snapshot.
                    BackupArchiveInspector.inspect(file, requireIndex = true,
                        maxArchiveBytes = 128L * 1024 * 1024)
                    transport.upload(account.endpoint, account.auth, deviceId, file)
                } finally { file.delete() }
            } }
            busy = false
            result.onSuccess { status.text = getString(R.string.mono_webdav_backup_zip_uploaded_for_this_device) }
                .onFailure { status.text = "Upload failed: ${it.message}" }
        }
    }

    private fun listRemote() {
        val account = account() ?: return
        if (busy) return
        busy = true
        status.text = getString(R.string.mono_webdav_listing_backups)
        lifecycleScope.launch {
            val result = runCatching { transport.list(account.endpoint, account.auth) }
            busy = false
            result.onSuccess { ids ->
                if (ids.isEmpty()) { status.text = getString(R.string.mono_webdav_no_device_backups_found); return@onSuccess }
                MaterialAlertDialogBuilder(this@WebDavManualSyncActivity)
                    .setTitle(getString(R.string.mono_webdav_select_a_device_zip_to_download))
                    .setItems(ids.toTypedArray()) { _, index ->
                        downloadId = ids[index]
                        downloadPicker.launch("monogatari-${ids[index]}.zip")
                    }.setNegativeButton(android.R.string.cancel, null).show()
                status.text = "${ids.size} backup archives available"
            }.onFailure { status.text = "Listing failed: ${it.message}" }
        }
    }

    private fun downloadPicked(id: String, uri: Uri) {
        val account = account() ?: return
        if (busy) return
        require(WebDavEndpoint.validDeviceId(id)) { "Invalid backup device" }
        busy = true
        status.text = getString(R.string.mono_webdav_downloading_zip)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val temporary = File.createTempFile("dav-download", ".zip", cacheDir)
                try {
                    transport.download(account.endpoint, account.auth, id, temporary)
                    // Never write corrupt or unrelated remote data into the user's SAF file.
                    BackupArchiveInspector.inspect(temporary, requireIndex = true,
                        maxArchiveBytes = 128L * 1024 * 1024)
                    contentResolver.openOutputStream(uri)?.use { out -> temporary.inputStream().use { it.copyTo(out) } }
                        ?: error("Could not write selected destination")
                } finally { temporary.delete() }
            } }
            busy = false
            result.onSuccess { status.text = getString(R.string.mono_webdav_backup_zip_saved_restore_it_only) }
                .onFailure { status.text = "Download failed: ${it.message}" }
        }
    }
}
