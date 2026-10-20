package org.koitharu.kotatsu.backups.domain

import org.koitharu.kotatsu.sync.conflict.HistoryConflictRepository
import org.koitharu.kotatsu.sync.conflict.HistoryConflictSheet
import org.koitharu.kotatsu.sync.conflict.HistoryConflicts
import org.koitharu.kotatsu.sync.profile.SettingsGroupsDialog
import org.koitharu.kotatsu.sync.profile.SettingsTransferProfile
import org.koitharu.kotatsu.R
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.backups.data.BackupRepository
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addPrimaryButton
import org.koitharu.kotatsu.core.ui.addSecondaryButton
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.buttonParams
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.scrollable
import org.koitharu.kotatsu.core.ui.secondaryButton
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Explicit, foreground-only, two-device transfer using encrypted Nearby Connections.
 * Both people must compare the Nearby authentication code and approve. The receiver
 * chooses which archive sections to merge; nothing is silently restored.
 */
@AndroidEntryPoint
class NearbyOfflineSyncActivity : AppCompatActivity() {
    @Inject lateinit var backups: BackupRepository
    @Inject lateinit var historyConflicts: HistoryConflictRepository
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val serviceId by lazy { packageName + ".nearby.backups.v1" }
    private val deviceLabel by lazy { "Monogatari - " + Build.MODEL.take(35) }
    private lateinit var status: TextView
    private lateinit var peers: LinearLayout
    private val discovered = LinkedHashMap<String, String>()
    private val headers = HashMap<Long, NearbySyncProtocol.Header>()
    private val received = HashMap<Long, Payload>()
    private val finished = HashSet<Long>()
    private var connected: String? = null
    private var outgoing: File? = null
    private var outgoingId: Long? = null
    private var pendingSave: Pair<File, String>? = null
    private var pendingAction: (() -> Unit)? = null
    private var busy = false

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingAction
        pendingAction = null
        if (requiredPermissions().all(::hasPermission)) action?.invoke()
        else say("Nearby permissions were denied. Discovery is unavailable.")
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sendPickedFile(uri)
    }

    private val savePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = pendingSave
        pendingSave = null
        if (uri != null && item != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        item.first.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException("Destination cannot be written")
                }
            }
            item?.first?.delete()
            result.onSuccess { say("Saved " + (item?.second ?: "file")) }
                .onFailure { say("Save failed: " + it.message) }
        } else item?.first?.delete()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.screen_title_nearby)
        val body = pageColumn()
        // The header body is the status line, so every step of the transfer appears next to Kitsune.
        status = body.addHeaderCard(
            MascotForm.DEFAULT,
            getString(R.string.mono_screen_header_nearby),
            getString(R.string.mono_nearby_no_cloud_or_internet_needed_turn) +
                "Confirm the same code on both devices before transferring.",
        ).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        // Receive / Send are the two main choices (filled); later steps are outlined, like Settings dialogs.
        val connect = body.addSectionCard(getString(R.string.mono_section_nearby_connect))
        connect.addPrimaryButton(getString(R.string.mono_nearby_receive_make_this_device_visible)) { withPermissions(::advertise) }
        connect.addPrimaryButton(getString(R.string.mono_nearby_send_find_a_nearby_device)) { withPermissions(::discover) }
        peers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        connect.addView(peers)
        val transfer = body.addSectionCard(getString(R.string.mono_section_nearby_transfer))
        transfer.addSecondaryButton(getString(R.string.mono_nearby_send_library_and_settings)) { sendLibrary() }
        transfer.addSecondaryButton(getString(R.string.mono_nearby_send_an_offline_book_or_file)) {
            if (connected == null) say("Connect to the other device first.")
            else filePicker.launch(arrayOf("*/*"))
        }
        transfer.addSecondaryButton(getString(R.string.mono_nearby_stop_and_disconnect)) { stop(); say("Nearby transfer stopped.") }
        body.addSectionCard(getString(R.string.mono_section_nearby_shared)).addBodyText(
            getString(R.string.mono_nearby_library_transfer_includes_reading_history_favourites) +
                "source subscriptions, and preferences. Files are sent separately. " +
                "The private community identity is NOT shared automatically. " +
                "You must approve each receive and restore.",
        )
        setMonogatariContent(body.scrollable(), MonogatariChrome.TEXT_MAX_WIDTH_DP)
    }

    private fun say(message: String) {
        runOnUiThread { status.text = message }
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT in 29..32) add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }.toTypedArray()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun withPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filterNot(::hasPermission)
        if (missing.isEmpty()) action()
        else {
            pendingAction = action
            permissions.launch(missing.toTypedArray())
        }
    }

    private fun advertise() {
        client.stopDiscovery()
        discovered.clear()
        peers.removeAllViews()
        client.startAdvertising(
            deviceLabel, serviceId, connectionEvents,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnSuccessListener {
            say("Visible to nearby Monogatari devices. Wait for a connection request.")
            keepAwake(true)
        }.addOnFailureListener {
            say("Cannot receive: " + it.localizedMessage + ". Enable Bluetooth and Wi-Fi.")
        }
    }

    private fun discover() {
        client.stopAdvertising()
        discovered.clear()
        peers.removeAllViews()
        client.startDiscovery(
            serviceId, discoveryEvents,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnSuccessListener {
            say("Searching nearby. Tap the other device when it appears.")
            keepAwake(true)
        }.addOnFailureListener {
            say("Cannot search: " + it.localizedMessage + ". Enable Bluetooth and Wi-Fi.")
        }
    }

    private val discoveryEvents = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            runOnUiThread {
                discovered[id] = info.endpointName
                refreshPeers()
            }
        }
        override fun onEndpointLost(id: String) {
            runOnUiThread {
                discovered.remove(id)
                refreshPeers()
            }
        }
    }

    private fun refreshPeers() {
        peers.removeAllViews()
        discovered.forEach { (id, name) ->
            peers.addView(secondaryButton(getString(R.string.mono_nearby_connect) + name) {
                client.stopDiscovery()
                client.requestConnection(deviceLabel, id, connectionEvents)
                    .addOnFailureListener { say("Connection failed: " + it.localizedMessage) }
            }, buttonParams())
        }
    }

    private val connectionEvents = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            runOnUiThread {
                MaterialAlertDialogBuilder(this@NearbyOfflineSyncActivity)
                    .setTitle(getString(R.string.mono_nearby_verify_nearby_device))
                    .setMessage(getString(R.string.mono_nearby_device) + info.endpointName +
                        "\nPairing code: " + info.authenticationDigits +
                        "\n\nCompare the code on BOTH screens. Accept only when they match.")
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.mono_nearby_codes_match)) { _, _ ->
                        client.acceptConnection(id, payloadEvents)
                            .addOnFailureListener {
                                say("Pairing failed: " + it.localizedMessage)
                            }
                    }
                    .setNegativeButton(getString(R.string.mono_nearby_reject)) { _, _ -> client.rejectConnection(id) }
                    .show()
            }
        }

        override fun onConnectionResult(id: String, resolution: ConnectionResolution) {
            runOnUiThread {
                if (resolution.status.isSuccess) {
                    connected = id
                    client.stopDiscovery()
                    client.stopAdvertising()
                    discovered.clear()
                    refreshPeers()
                    say("Paired securely. Either device may send a library or file.")
                    keepAwake(true)
                } else say("Connection declined or failed. Try again.")
            }
        }

        override fun onDisconnected(id: String) {
            runOnUiThread {
                if (connected == id) connected = null
                keepAwake(false)
                say("The nearby device disconnected.")
            }
        }
    }

    private fun connectedPeer(): String? =
        connected.also { if (it == null) say("Connect and confirm the pairing code first.") }

    private fun sendLibrary() {
        val endpoint = connectedPeer() ?: return
        if (busy) { say("Finish the current transfer first."); return }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mono_nearby_send_your_library_and_settings))
            .setMessage(getString(R.string.mono_nearby_this_creates_a_backup_of_saved) +
                "bookmarks and settings. Your private community identity is excluded. " +
                "Downloaded chapter files are not in this backup. Send files separately.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.mono_nearby_create_and_send)) { _, _ ->
                busy = true
                say("Preparing your library backup...")
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching {
                        val archive = File.createTempFile("nearby-library-", ".bk.zip", cacheDir)
                        try {
                            ZipOutputStream(archive.outputStream()).use { output ->
                                backups.createBackup(output, null,
                                    BackupSection.entries.filter {
                                        it != BackupSection.IDENTITY
                                    }.toSet()
                                )
                            }
                            BackupArchiveInspector.inspect(archive, requireIndex = true)
                            archive
                        } catch (error: Exception) {
                            archive.delete()
                            throw error
                        }
                    } }
                    result.onSuccess { sendPrepared(endpoint, it, NearbySyncProtocol.Kind.BACKUP,
                        "monogatari-library.bk.zip") }
                        .onFailure { busy = false; say("Backup failed: " + it.message) }
                }
            }.show()
    }

    private fun sendPickedFile(uri: Uri) {
        val endpoint = connectedPeer() ?: return
        if (busy) { say("Finish the current transfer first."); return }
        busy = true
        say("Preparing selected offline file...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val displayName = contentResolver.query(uri, arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                val temp = File.createTempFile("nearby-file-", ".tmp", cacheDir)
                try {
                    contentResolver.openInputStream(uri)?.use {
                        BackupArchiveInspector.copyBounded(it, temp, NearbySyncProtocol.MAX_BYTES)
                    } ?: throw IOException("Cannot open the selected file")
                    require(temp.length() > 0) { "Selected file is empty" }
                    temp to NearbySyncProtocol.safeFileName(displayName)
                } catch (error: Exception) {
                    temp.delete()
                    throw error
                }
            } }
            result.onSuccess { (file, name) ->
                sendPrepared(endpoint, file, NearbySyncProtocol.Kind.FILE, name)
            }.onFailure { busy = false; say("File preparation failed: " + it.message) }
        }
    }

    private fun sendPrepared(endpoint: String, file: File, kind: NearbySyncProtocol.Kind,
                             name: String) {
        if (connected != endpoint) {
            file.delete()
            busy = false
            say("Disconnected before transfer could start.")
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                require(file.length() in 1L..NearbySyncProtocol.MAX_BYTES) {
                    "Transfer exceeds 256 MB limit"
                }
                val sha = NearbySyncProtocol.sha256(file)
                val payload = Payload.fromFile(file)
                payload to NearbySyncProtocol.Header(
                    payload.id, kind, name, file.length(), sha
                )
            } }
            result.onSuccess { (payload, header) ->
                outgoing = file
                outgoingId = payload.id
                say("Sending " + header.name + "...")
                client.sendPayload(endpoint, Payload.fromBytes(NearbySyncProtocol.encode(header)))
                    .addOnSuccessListener {
                        client.sendPayload(endpoint, payload)
                            .addOnFailureListener { sendError(it.localizedMessage) }
                    }.addOnFailureListener { sendError(it.localizedMessage) }
            }.onFailure {
                busy = false
                file.delete()
                say("Could not send: " + it.message)
            }
        }
    }

    private fun sendError(message: String?) {
        outgoingId?.let(client::cancelPayload)
        outgoing?.delete()
        outgoing = null
        outgoingId = null
        busy = false
        say("Transfer failed: " + message)
    }

    private val payloadEvents = object : PayloadCallback() {
        override fun onPayloadReceived(endpoint: String, payload: Payload) {
            runOnUiThread {
                if (endpoint != connected) return@runOnUiThread
                when (payload.type) {
                    Payload.Type.BYTES -> {
                        runCatching { NearbySyncProtocol.decode(payload.asBytes()
                            ?: throw IOException("Empty transfer header")) }
                            .onSuccess { header ->
                                headers[header.id] = header
                                checkComplete(header.id)
                            }
                            .onFailure { say("Invalid transfer metadata: " + it.message) }
                    }
                    Payload.Type.FILE -> {
                        received[payload.id] = payload
                        checkComplete(payload.id)
                    }
                    else -> client.cancelPayload(payload.id)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpoint: String, update: PayloadTransferUpdate) {
            runOnUiThread {
                if (endpoint != connected) return@runOnUiThread
                val id = update.payloadId
                if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                    if (outgoingId == id) {
                        outgoing?.delete()
                        outgoing = null
                        outgoingId = null
                        busy = false
                        say("Sent successfully. The other device must still review the import.")
                    } else {
                        finished.add(id)
                        checkComplete(id)
                    }
                } else if (update.status == PayloadTransferUpdate.Status.FAILURE ||
                    update.status == PayloadTransferUpdate.Status.CANCELED) {
                    if (outgoingId == id) sendError("Transfer interrupted")
                    else {
                        headers.remove(id)
                        received.remove(id)
                        finished.remove(id)
                        say("Incoming transfer interrupted.")
                    }
                } else if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS &&
                    update.totalBytes > NearbySyncProtocol.MAX_BYTES && outgoingId != id) {
                    client.cancelPayload(id)
                    received.remove(id)
                    headers.remove(id)
                    finished.remove(id)
                    say("Incoming transfer rejected: larger than 256 MB.")
                } else if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS &&
                    update.totalBytes > 0) {
                    val pct = (100 * update.bytesTransferred / update.totalBytes)
                        .coerceIn(0, 100)
                    say("Transferring: " + pct + "%")
                }
            }
        }
    }

    private fun checkComplete(id: Long) {
        val header = headers[id] ?: return
        val payload = received[id] ?: return
        if (id !in finished) return
        headers.remove(id)
        received.remove(id)
        finished.remove(id)
        say("Verifying received " + header.name + "...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val staged = File.createTempFile("nearby-received-", ".tmp", cacheDir)
                try {
                    val originalUri = payload.asFile()?.asUri()
                        ?: throw IOException("Missing incoming file URI")
                    contentResolver.openInputStream(originalUri)?.use { input ->
                        BackupArchiveInspector.copyBounded(input, staged, NearbySyncProtocol.MAX_BYTES)
                    } ?: throw IOException("Cannot read incoming file")
                    runCatching { contentResolver.delete(originalUri, null, null) }
                    require(staged.length() == header.length) { "Transfer size mismatch" }
                    require(NearbySyncProtocol.sha256(staged) == header.sha256) {
                        "Transfer checksum mismatch"
                    }
                    if (header.kind == NearbySyncProtocol.Kind.BACKUP) {
                        val report = BackupArchiveInspector.inspect(staged, requireIndex = true)
                        staged to report
                    } else staged to null
                } catch (error: Exception) {
                    staged.delete()
                    throw error
                }
            } }
            result.onSuccess { (staged, report) ->
                if (report != null) showBackupReview(staged, report)
                else showFileReview(staged, header.name)
            }.onFailure { say("Incoming transfer rejected: " + it.message) }
        }
    }

    private fun showBackupReview(file: File, report: BackupArchiveInspector.Report) {
        val choices = report.sections.filter {
            it != BackupSection.INDEX && it != BackupSection.IDENTITY
        }.sortedBy { it.name }
        val selected = BooleanArray(choices.size) { true }
        if (choices.isEmpty()) {
            file.delete()
            say("No importable backup sections were received.")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mono_nearby_review_received_library_and_settings))
            .setMessage(getString(R.string.mono_nearby_archive_inspected_and_verified_select_sections) +
                "device. Private community identity is never imported through nearby transfers. " +
                "Keep a backup of this device before importing.")
            .setMultiChoiceItems(choices.map { it.name.replace('_', ' ') }.toTypedArray(),
                selected) { _, which, checked -> selected[which] = checked }
            .setCancelable(false)
            .setNegativeButton(getString(R.string.mono_nearby_keep_existing_library)) { _, _ ->
                file.delete()
                say("Received archive discarded. No changes were made.")
            }
            .setPositiveButton(getString(R.string.mono_nearby_merge_selected)) { _, _ ->
                val sections = choices.filterIndexed { index, _ -> selected[index] }.toSet()
                if (sections.isEmpty()) {
                    file.delete()
                    say("Nothing selected for import.")
                } else reviewConflicts(file, sections) { peerId, resolution ->
                    if (BackupSection.SETTINGS in sections) {
                        chooseSettingsGroups { groups -> mergeReceived(file, sections, groups, peerId, resolution) }
                    } else {
                        mergeReceived(file, sections, null, peerId, resolution)
                    }
                }
            }.show()
    }

    /**
     * v1.2: when history is merged, titles read on both devices since their last sync are listed so the user picks
     * whose progress wins (newest by default). [onResolved] gets the other device's id for the last-sync record.
     */
    private fun reviewConflicts(
        file: File,
        sections: Set<BackupSection>,
        onResolved: (String?, HistoryConflicts.Resolution) -> Unit,
    ) {
        if (BackupSection.HISTORY !in sections) {
            onResolved(null, HistoryConflicts.Resolution.NONE)
            return
        }
        say(getString(R.string.sync_conflict_checking))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { historyConflicts.review(file) } }
            result.onSuccess { review ->
                if (review.conflicts.isEmpty()) onResolved(review.deviceId, HistoryConflicts.Resolution.NONE)
                else HistoryConflictSheet.show(
                    this@NearbyOfflineSyncActivity, review,
                    onApply = { resolution -> onResolved(review.deviceId, resolution) },
                    onCancel = {
                        file.delete()
                        say("Received archive discarded. No changes were made.")
                    },
                )
            }.onFailure {
                say(getString(R.string.sync_conflict_check_failed))
                onResolved(null, HistoryConflicts.Resolution.NONE)
            }
        }
    }

    /** v1.1: pick which kinds of settings to take from the other device; secrets never travel. */
    private fun chooseSettingsGroups(onChosen: (Set<SettingsTransferProfile.Group>) -> Unit) {
        SettingsGroupsDialog.show(this, onChosen = onChosen)
    }

    private fun mergeReceived(
        file: File,
        sections: Set<BackupSection>,
        groups: Set<SettingsTransferProfile.Group>?,
        peerId: String? = null,
        resolution: HistoryConflicts.Resolution = HistoryConflicts.Resolution.NONE,
    ) {
        say("Merging selected backup sections...")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching { ZipInputStream(file.inputStream()).use {
                        backups.restoreBackup(it, sections, null, groups,
                            keepLocalHistoryIds = resolution.keepLocal,
                            takeIncomingHistoryIds = resolution.takeIncoming)
                    } }
                } finally { file.delete() }
            }
            result.onSuccess { summary ->
                // Later syncs with this device only ask about titles changed on both sides after this point.
                if (summary.isAllSuccess && peerId != null) historyConflicts.markSynced(peerId)
                if (summary.isAllSuccess) say("Selected library sections merged. " +
                    "Restart Monogatari to apply updated settings.")
                else say("${summary.failures.size} import operations failed. " +
                    "Review your library and preserve your original backup.")
                // Two-way sync: this device now holds both libraries, so sending it back makes them match.
                if (summary.isAllSuccess && connected != null) offerSendBack()
            }.onFailure { say("Import failed: " + it.message) }
        }
    }

    private fun offerSendBack() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nearby_two_way_title)
            .setMessage(R.string.nearby_two_way_message)
            .setNegativeButton(R.string.nearby_two_way_skip, null)
            .setPositiveButton(R.string.nearby_two_way_send) { _, _ -> sendLibrary() }
            .show()
    }

    private fun showFileReview(file: File, name: String) {
        val extension = name.substringAfterLast('.', "").lowercase()
        val isBook = extension in setOf("epub", "pdf", "cbz", "cbr", "txt", "html", "htm")
        val options = if (isBook) arrayOf("Add to Books shelf", "Save elsewhere", "Discard")
            else arrayOf("Save elsewhere", "Discard")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.mono_nearby_received) + name)
            .setMessage(getString(R.string.mono_nearby_checksum_verified_this_file_is_stored))
            .setItems(options) { _, index ->
                when (options[index]) {
                    "Add to Books shelf" -> addBook(file, name)
                    "Save elsewhere" -> {
                        pendingSave?.first?.delete()
                        pendingSave = file to name
                        savePicker.launch(name)
                    }
                    else -> {
                        file.delete()
                        say("Received file discarded.")
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun addBook(file: File, name: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val folder = File(filesDir, "imported-books").apply { mkdirs() }
                var destination = File(folder, name)
                var index = 1
                while (destination.exists()) {
                    destination = File(folder, "nearby-" + index++ + "-" + name)
                }
                try {
                    file.inputStream().use { input ->
                        destination.outputStream().use { input.copyTo(it) }
                    }
                    require(destination.length() == file.length()) {
                        "Imported book does not match received file size"
                    }
                    destination
                } catch (error: Exception) {
                    destination.delete()
                    throw error
                }
            } }
            file.delete()
            result.onSuccess { say("Added to Books shelf: " + it.name) }
                .onFailure { say("Book import failed: " + it.message) }
        }
    }

    private fun keepAwake(active: Boolean) {
        if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun stop() {
        client.stopAllEndpoints()
        client.stopDiscovery()
        client.stopAdvertising()
        connected = null
        discovered.clear()
        if (::peers.isInitialized) refreshPeers()
        keepAwake(false)
        outgoing?.delete()
        outgoing = null
        outgoingId = null
        busy = false
        headers.clear()
        received.clear()
        finished.clear()
    }

    override fun onStop() {
        // Discovery must not remain active after the app leaves the foreground. Existing
        // approved transfers may continue while Android opens the system file picker.
        client.stopAdvertising()
        client.stopDiscovery()
        keepAwake(false)
        super.onStop()
    }

    override fun onDestroy() {
        stop()
        pendingSave?.first?.delete()
        super.onDestroy()
    }
}
