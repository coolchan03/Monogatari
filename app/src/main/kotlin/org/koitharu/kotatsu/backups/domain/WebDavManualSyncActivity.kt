package org.koitharu.kotatsu.backups.domain

import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
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
        title = "Manual WebDAV backup transfer"
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply {
            text = "Only manually selected ZIP archives are transferred. No automatic merge or restore."
        }
        root.addView(status)
        fun button(label: String, action: () -> Unit) {
            root.addView(MaterialButton(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }
        button("Configure secure WebDAV account") { configure() }
        button("Upload selected backup ZIP") {
            if (account() != null) AlertDialog.Builder(this)
                .setTitle("Replace this device's remote backup?")
                .setMessage("Uploading replaces the existing ZIP for this device. Existing remote files from other devices are not changed. Keep another local backup before continuing.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Choose backup ZIP") { _, _ ->
                    uploadPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }.show()
        }
        button("List and download backup ZIP") { listRemote() }
        button("Remove account from this device") {
            AlertDialog.Builder(this).setTitle("Remove WebDAV credentials?")
                .setMessage("This removes the saved account from this device. Server files are not deleted.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove") { _, _ ->
                    accountStore.clear()
                    status.text = "Account removed"
                }.show()
        }
        setContentView(root)
    }

    private fun account(): WebDavAccount? = runCatching { accountStore.load() }.getOrNull().also {
        if (it == null) status.text = "Configure your HTTPS WebDAV account first"
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
        AlertDialog.Builder(this).setTitle("Encrypted WebDAV credentials").setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Save") { _, _ ->
                runCatching { accountStore.save(fields[0].text.toString().trim(),
                    fields[1].text.toString(), fields[2].text.toString()) }
                    .onSuccess { status.text = "Credentials encrypted using Android Keystore" }
                    .onFailure { status.text = "Configuration failed: ${it.message}" }
            }.show()
    }

    private fun uploadPicked(uri: Uri) {
        val account = account() ?: return
        if (busy) return
        busy = true
        status.text = "Uploading selected ZIP…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val file = File.createTempFile("dav-upload", ".zip", cacheDir)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                require(total <= 128L * 1024 * 1024) { "Backup exceeds 128 MB" }
                                output.write(buffer, 0, n)
                            }
                        }
                    } ?: error("Cannot read selected backup")
                    require(java.util.zip.ZipFile(file).use { it.entries().hasMoreElements() }) { "Invalid or empty ZIP" }
                    transport.upload(account.endpoint, account.auth, deviceId, file)
                } finally { file.delete() }
            } }
            busy = false
            result.onSuccess { status.text = "Backup ZIP uploaded for this device" }
                .onFailure { status.text = "Upload failed: ${it.message}" }
        }
    }

    private fun listRemote() {
        val account = account() ?: return
        if (busy) return
        busy = true
        status.text = "Listing backups…"
        lifecycleScope.launch {
            val result = runCatching { transport.list(account.endpoint, account.auth) }
            busy = false
            result.onSuccess { ids ->
                if (ids.isEmpty()) { status.text = "No device backups found"; return@onSuccess }
                AlertDialog.Builder(this@WebDavManualSyncActivity)
                    .setTitle("Select a device ZIP to download")
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
        status.text = "Downloading ZIP…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val temporary = File.createTempFile("dav-download", ".zip", cacheDir)
                try {
                    transport.download(account.endpoint, account.auth, id, temporary)
                    require(java.util.zip.ZipFile(temporary).use { it.entries().hasMoreElements() }) {
                        "Downloaded archive is invalid"
                    }
                    contentResolver.openOutputStream(uri)?.use { out -> temporary.inputStream().use { it.copyTo(out) } }
                        ?: error("Could not write selected destination")
                } finally { temporary.delete() }
            } }
            busy = false
            result.onSuccess { status.text = "Backup ZIP saved. Restore it only through the normal restore-review flow." }
                .onFailure { status.text = "Download failed: ${it.message}" }
        }
    }
}
