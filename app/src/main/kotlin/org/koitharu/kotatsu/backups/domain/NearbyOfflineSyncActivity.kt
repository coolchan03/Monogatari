package org.koitharu.kotatsu.backups.domain

import org.koitharu.kotatsu.sync.conflict.FavouriteConflictSheet
import org.koitharu.kotatsu.sync.conflict.FavouriteConflicts
import org.koitharu.kotatsu.sync.conflict.HistoryConflictRepository
import org.koitharu.kotatsu.sync.conflict.HistoryConflictSheet
import org.koitharu.kotatsu.sync.conflict.HistoryConflicts
import org.koitharu.kotatsu.sync.profile.SettingsGroupsDialog
import org.koitharu.kotatsu.sync.profile.SettingsTransferProfile
import org.koitharu.kotatsu.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
    private lateinit var fixes: LinearLayout
    private val discovered = LinkedHashMap<String, String>()
    private val headers = HashMap<Long, NearbySyncProtocol.Header>()
    private val received = HashMap<Long, Payload>()
    private val finished = HashSet<Long>()
    private var connected: String? = null
    private var outgoing: File? = null
    private var outgoingId: Long? = null
    /** True while the file being sent is this device's library backup, which counts as a backup once delivered. */
    private var outgoingIsLibraryBackup = false
    private var pendingSave: Pair<File, String>? = null
    private var pendingAction: (() -> Unit)? = null
    private var busy = false

    // Remembered devices: the trust file, and per-connection handshake state.
    private val trust by lazy { NearbyTrustStore(this) }
    private lateinit var trusted: LinearLayout
    private val endpointNames = HashMap<String, String>()
    private val incomingEndpoints = HashSet<String>()
    private val digitConfirmed = HashSet<String>()
    private val handshakes = HashMap<String, NearbyTrustHandshake>()
    private val handshakeDevices = HashMap<String, NearbyTrust.Device>()
    private val trustFailedEndpoints = HashSet<String>()
    private val untrustedNames = HashSet<String>()
    private var trustPending = false
    private var offeredSecret: Pair<String, String>? = null

    // Sending several files one after another.
    private enum class SendOutcome { SENT, FAILED, SKIPPED }
    private lateinit var queueControls: LinearLayout
    private var sendQueue: ArrayDeque<Uri>? = null
    private var queueTotal = 0
    private var queueIndex = 0
    private var announcedBatch = false
    private var leftOut = 0
    private var currentName = ""
    private var cancelRest = false
    private var tally = NearbyBatch.Tally()
    private val ignoredPayloads = HashSet<Long>()

    // Receiving several files: one destination for all of them.
    private enum class FolderDecision { UNDECIDED, FOLDER, ONE_BY_ONE }
    private var batchActive = false
    private var batchEnded = false
    private var batchExpected = 0
    private var batchSeen = 0
    private var batchBytes = 0L
    private val batchOrdinal = HashMap<Long, Int>()
    private val rejected = HashSet<Long>()
    private val receiveTally = NearbyBatch.Tally()
    private var folderDecision = FolderDecision.UNDECIDED
    private var batchFolder: Uri? = null
    private var pendingWork = 0
    private val stagedFiles = ArrayList<Pair<File, String>>()
    private val reviewQueue = ArrayDeque<Pair<File, String>>()
    private var reviewing = false

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingAction
        pendingAction = null
        val missing = missingPermissions()
        if (missing.isEmpty()) action?.invoke()
        else {
            // No rationale after a request means Android will not show the dialog again.
            val permanent = missing.any { !shouldShowRequestPermissionRationale(it) }
            val names = NearbyPreflight.permissionNames(missing)
            say(getString(
                if (permanent) R.string.nearby_diag_permissions_denied_permanent
                else R.string.nearby_diag_permissions_denied, names))
            showFixes(listOf(NearbyPreflight.Requirement.PERMISSIONS))
        }
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) sendPickedFiles(uris)
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        onFolderChosen(uri)
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
            reviewDone()
        } else {
            item?.first?.delete()
            reviewDone()
        }
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
        fixes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(fixes)
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
            else if (connectedPeer() != null) filePicker.launch(arrayOf("*/*"))
        }
        queueControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        queueControls.addSecondaryButton(getString(R.string.nearby_multi_skip)) { skipCurrent(false) }
        queueControls.addSecondaryButton(getString(R.string.nearby_multi_cancel_rest)) { skipCurrent(true) }
        transfer.addView(queueControls)
        transfer.addSecondaryButton(getString(R.string.nearby_multi_forget_folder)) {
            forgetSaveFolder()
            say(getString(R.string.nearby_multi_folder_forgotten))
        }
        transfer.addSecondaryButton(getString(R.string.mono_nearby_stop_and_disconnect)) { stop(); say("Nearby transfer stopped.") }
        trusted = body.addSectionCard(getString(R.string.nearby_trust_section))
        refreshTrusted()
        val help = body.addSectionCard(getString(R.string.nearby_diag_section))
        help.addBodyText(getString(R.string.nearby_diag_checklist))
        help.addSecondaryButton(getString(R.string.nearby_diag_check_button)) { checkThisDevice() }
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

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun missingPermissions(): List<String> =
        NearbyPreflight.requiredPermissions(Build.VERSION.SDK_INT).filterNot(::hasPermission)

    /** Preflight, then permissions, then [action]. A missing radio or service is reported by name. */
    private fun withPermissions(action: () -> Unit) {
        fixes.removeAllViews()
        val snapshot = NearbyPreflight.snapshot(this)
        val problems = NearbyPreflight.missingRequirements(snapshot)
            .filter { it != NearbyPreflight.Requirement.PERMISSIONS }
        if (problems.isNotEmpty()) {
            say(getString(R.string.nearby_diag_blocked) + "\n" +
                problems.joinToString("\n") { "- " + problemText(it, snapshot) })
            showFixes(problems)
            return
        }
        val missing = missingPermissions()
        if (missing.isEmpty()) action()
        else {
            pendingAction = action
            permissions.launch(missing.toTypedArray())
        }
    }

    private fun problemText(requirement: NearbyPreflight.Requirement, snapshot: NearbyPreflight.Snapshot): String =
        when (requirement) {
            NearbyPreflight.Requirement.PLAY_SERVICES -> NearbyPreflight.withCode(
                getString(NearbyPreflight.problemRes(requirement)), snapshot.playServicesStatus)
            NearbyPreflight.Requirement.PERMISSIONS -> getString(
                NearbyPreflight.problemRes(requirement),
                NearbyPreflight.permissionNames(
                    NearbyPreflight.missingPermissions(snapshot.sdk, snapshot.grantedPermissions)))
            else -> getString(NearbyPreflight.problemRes(requirement))
        }

    /** The "Check this device" button: lists every prerequisite as OK or PROBLEM. */
    private fun checkThisDevice() {
        fixes.removeAllViews()
        val snapshot = NearbyPreflight.snapshot(this)
        val problems = NearbyPreflight.missingRequirements(snapshot)
        val lines = NearbyPreflight.Requirement.entries.map {
            if (it in problems) getString(R.string.nearby_diag_line_problem, problemText(it, snapshot))
            else getString(R.string.nearby_diag_line_ok, getString(NearbyPreflight.okRes(it)))
        }
        say(getString(R.string.nearby_diag_check_title) + "\n" + lines.joinToString("\n") + "\n" +
            getString(if (problems.isEmpty()) R.string.nearby_diag_all_ok else R.string.nearby_diag_some_problems))
        showFixes(problems)
    }

    /** One button per problem that opens the screen where the user can fix it. */
    private fun showFixes(problems: List<NearbyPreflight.Requirement>) {
        fixes.removeAllViews()
        problems.forEach { requirement ->
            fixes.addView(secondaryButton(getString(NearbyPreflight.actionRes(requirement))) {
                openFix(requirement)
            }, buttonParams())
        }
    }

    private fun openFix(requirement: NearbyPreflight.Requirement) {
        if (requirement == NearbyPreflight.Requirement.PLAY_SERVICES) {
            val availability = GoogleApiAvailability.getInstance()
            val code = availability.isGooglePlayServicesAvailable(this)
            if (code != 0 && availability.isUserResolvableError(code)) {
                val dialog = availability.getErrorDialog(this, code, 9001)
                if (dialog != null) {
                    dialog.show()
                    return
                }
            }
        }
        val intent = NearbyPreflight.fixIntent(requirement, packageName)
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        }
    }

    /** Fresh session: no leftover advertising, discovery, endpoints or half-finished transfer. */
    private fun resetTransfer() {
        outgoingId?.let(client::cancelPayload)
        outgoing?.delete()
        outgoing = null
        outgoingId = null
        busy = false
        headers.clear()
        received.clear()
        finished.clear()
        sendQueue = null
        announcedBatch = false
        cancelRest = false
        ignoredPayloads.clear()
        batchActive = false
        batchEnded = false
        batchOrdinal.clear()
        rejected.clear()
        if (::queueControls.isInitialized) queueControls.visibility = View.GONE
    }

    private fun advertise() {
        stop()
        discovered.clear()
        peers.removeAllViews()
        client.startAdvertising(
            deviceLabel, serviceId, connectionEvents,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnSuccessListener {
            say(getString(R.string.nearby_diag_step_visible))
            keepAwake(true)
        }.addOnFailureListener {
            say(getString(R.string.nearby_diag_cannot_receive, NearbyPreflight.errorMessage(this, it)))
        }
    }

    private fun discover() {
        stop()
        discovered.clear()
        peers.removeAllViews()
        client.startDiscovery(
            serviceId, discoveryEvents,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        ).addOnSuccessListener {
            say(getString(R.string.nearby_diag_step_searching))
            keepAwake(true)
        }.addOnFailureListener {
            say(getString(R.string.nearby_diag_cannot_search, NearbyPreflight.errorMessage(this, it)))
        }
    }

    private val discoveryEvents = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            runOnUiThread {
                discovered[id] = info.endpointName
                refreshPeers()
                say(getString(R.string.nearby_diag_step_found, info.endpointName))
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
                    .addOnFailureListener {
                        say(getString(R.string.nearby_diag_connect_failed, NearbyPreflight.errorMessage(this, it)))
                    }
            }, buttonParams())
        }
    }

    private val connectionEvents = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            runOnUiThread {
                endpointNames[id] = info.endpointName
                trustFailedEndpoints.remove(id)
                if (info.isIncomingConnection) incomingEndpoints.add(id) else incomingEndpoints.remove(id)
                // A remembered name only selects which secret to test: nothing is allowed until the challenge passes.
                val known = if (info.endpointName in untrustedNames) null
                    else NearbyTrust.byEndpointName(trust.devices(), info.endpointName)
                if (known != null) {
                    handshakes[id] = NearbyTrustHandshake(
                        NearbyTrust.fromHex(known.secret), trust.localId(), known.id,
                        initiator = !info.isIncomingConnection, binding = info.authenticationDigits)
                    handshakeDevices[id] = known
                    say(getString(R.string.nearby_trust_verifying, known.name))
                    client.acceptConnection(id, payloadEvents)
                        .addOnFailureListener {
                            say(getString(R.string.nearby_diag_pairing_failed,
                                NearbyPreflight.errorMessage(this@NearbyOfflineSyncActivity, it)))
                        }
                    return@runOnUiThread
                }
                say(getString(R.string.nearby_diag_step_confirm))
                MaterialAlertDialogBuilder(this@NearbyOfflineSyncActivity)
                    .setTitle(getString(R.string.mono_nearby_verify_nearby_device))
                    .setMessage(getString(R.string.mono_nearby_device) + info.endpointName +
                        "\nPairing code: " + info.authenticationDigits +
                        "\n\nCompare the code on BOTH screens. Accept only when they match.")
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.mono_nearby_codes_match)) { _, _ ->
                        digitConfirmed.add(id)
                        client.acceptConnection(id, payloadEvents)
                            .addOnFailureListener {
                                say(getString(R.string.nearby_diag_pairing_failed,
                                    NearbyPreflight.errorMessage(this@NearbyOfflineSyncActivity, it)))
                            }
                    }
                    .setNegativeButton(getString(R.string.mono_nearby_reject)) { _, _ -> client.rejectConnection(id) }
                    .show()
            }
        }

        override fun onConnectionResult(id: String, resolution: ConnectionResolution) {
            runOnUiThread {
                if (resolution.status.isSuccess) {
                    resetTransfer()
                    connected = id
                    client.stopDiscovery()
                    client.stopAdvertising()
                    discovered.clear()
                    refreshPeers()
                    keepAwake(true)
                    val handshake = handshakes[id]
                    if (handshake != null) {
                        trustPending = true
                        say(getString(R.string.nearby_trust_verifying, handshakeDevices[id]?.name.orEmpty()))
                        sendMessage(id, handshake.hello())
                        armTrustTimeout(id)
                    } else {
                        trustPending = false
                        say(getString(R.string.nearby_diag_step_paired))
                        offerRemember(id)
                    }
                } else {
                    resetTransfer()
                    handshakes.remove(id)
                    handshakeDevices.remove(id)
                    digitConfirmed.remove(id)
                    if (connected == id) {
                        connected = null
                        trustPending = false
                    }
                    say(getString(R.string.nearby_diag_connection_failed,
                        NearbyPreflight.statusMessage(this@NearbyOfflineSyncActivity, resolution.status.statusCode)))
                }
            }
        }

        override fun onDisconnected(id: String) {
            runOnUiThread {
                val verifyingNow = handshakes[id]?.state == NearbyTrustHandshake.State.PENDING
                val droppedName = endpointNames[id]
                val wasTransferring = busy || outgoingId != null || headers.isNotEmpty() || received.isNotEmpty()
                if (connected == id) {
                    connected = null
                    trustPending = false
                    resetTransfer()
                }
                handshakes.remove(id)
                handshakeDevices.remove(id)
                digitConfirmed.remove(id)
                incomingEndpoints.remove(id)
                endpointNames.remove(id)
                if (offeredSecret?.first == id) offeredSecret = null
                keepAwake(false)
                if (trustFailedEndpoints.remove(id)) return@runOnUiThread // the failure message is already shown
                if (verifyingNow) {
                    droppedName?.let(untrustedNames::add)
                    say(getString(R.string.nearby_trust_failed))
                } else say(if (wasTransferring) getString(R.string.nearby_diag_dropped)
                    else "The nearby device disconnected.")
            }
        }
    }

    // ---- Remembered devices -----------------------------------------------------------------------------------

    private fun sendMessage(endpoint: String, message: NearbySyncProtocol.Message) {
        client.sendPayload(endpoint, Payload.fromBytes(NearbySyncProtocol.encodeMessage(message)))
    }

    private fun armTrustTimeout(id: String) {
        lifecycleScope.launch {
            delay(15_000)
            if (handshakes[id]?.state == NearbyTrustHandshake.State.PENDING && connected == id) trustFailed(id, true)
        }
    }

    /** Verification failed or timed out: nothing was transferred. The next connection asks for the digits again. */
    private fun trustFailed(endpoint: String, notifyPeer: Boolean) {
        if (notifyPeer) sendMessage(endpoint, NearbySyncProtocol.Message.TrustFail)
        endpointNames[endpoint]?.let(untrustedNames::add)
        handshakes.remove(endpoint)
        handshakeDevices.remove(endpoint)
        trustFailedEndpoints.add(endpoint)
        trustPending = false
        if (connected == endpoint) connected = null
        client.disconnectFromEndpoint(endpoint)
        resetTransfer()
        keepAwake(false)
        say(getString(R.string.nearby_trust_failed))
    }

    private fun checkHandshake(endpoint: String, handshake: NearbyTrustHandshake) {
        when (handshake.state) {
            NearbyTrustHandshake.State.VERIFIED -> {
                val device = handshakeDevices[endpoint]
                if (device != null) {
                    val name = endpointNames[endpoint] ?: device.endpointName
                    trust.update { NearbyTrust.touch(it, device.id, name, System.currentTimeMillis()) }
                    refreshTrusted()
                }
                if (connected == endpoint) trustPending = false
                say(getString(R.string.nearby_trust_remembered_status))
            }
            NearbyTrustHandshake.State.FAILED -> trustFailed(endpoint, true)
            NearbyTrustHandshake.State.PENDING -> Unit
        }
    }

    /** After a code-confirmed pairing, the side that requested the connection offers to remember the device. */
    private fun offerRemember(id: String) {
        if (id !in digitConfirmed || id in incomingEndpoints) return
        val name = endpointNames[id].orEmpty()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nearby_trust_offer_title))
            .setMessage(getString(R.string.nearby_trust_offer_message, name))
            .setNegativeButton(getString(R.string.nearby_trust_not_now), null)
            .setPositiveButton(getString(R.string.nearby_trust_remember)) { _, _ ->
                if (connected == id) {
                    val secret = NearbyTrust.toHex(NearbyTrust.randomBytes(NearbyTrust.SECRET_BYTES))
                    offeredSecret = id to secret
                    sendMessage(id, NearbySyncProtocol.Message.PairOffer(trust.localId(), deviceLabel, secret))
                }
            }.show()
    }

    private fun handlePairOffer(endpoint: String, offer: NearbySyncProtocol.Message.PairOffer) {
        if (endpoint !in digitConfirmed || endpoint in handshakes) return
        val name = endpointNames[endpoint] ?: offer.name
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nearby_trust_offer_title))
            .setMessage(getString(R.string.nearby_trust_offer_message, name))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.nearby_trust_not_now)) { _, _ ->
                sendMessage(endpoint, NearbySyncProtocol.Message.PairDecline)
            }
            .setPositiveButton(getString(R.string.nearby_trust_remember)) { _, _ ->
                if (connected == endpoint) {
                    rememberDevice(offer.deviceId, offer.name, endpointNames[endpoint].orEmpty(), offer.secret)
                    sendMessage(endpoint, NearbySyncProtocol.Message.PairAck(trust.localId(), deviceLabel))
                    say(getString(R.string.nearby_trust_paired, offer.name))
                }
            }.show()
    }

    private fun rememberDevice(deviceId: String, name: String, endpointName: String, secret: String) {
        val now = System.currentTimeMillis()
        trust.update { NearbyTrust.merge(it, NearbyTrust.Device(deviceId, name, endpointName, secret, now, now)) }
        untrustedNames.remove(endpointName)
        refreshTrusted()
    }

    private fun refreshTrusted() {
        trusted.removeAllViews()
        val devices = trust.devices()
        if (devices.isEmpty()) {
            trusted.addBodyText(getString(R.string.nearby_trust_empty))
            return
        }
        val now = System.currentTimeMillis()
        devices.forEach { device ->
            val seen = DateUtils.getRelativeTimeSpanString(device.lastSeen, now, DateUtils.MINUTE_IN_MILLIS)
            trusted.addBodyText(device.name + "\n" + getString(R.string.nearby_trust_last_seen, seen))
            trusted.addSecondaryButton(getString(R.string.nearby_trust_forget)) { confirmForget(device) }
        }
    }

    private fun confirmForget(device: NearbyTrust.Device) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nearby_trust_forget_title))
            .setMessage(getString(R.string.nearby_trust_forget_message, device.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.nearby_trust_forget)) { _, _ ->
                trust.update { NearbyTrust.forget(it, device.id) }
                refreshTrusted()
            }.show()
    }

    private fun connectedPeer(): String? {
        if (connected != null && trustPending) {
            say(getString(R.string.nearby_trust_wait))
            return null
        }
        return connected.also { if (it == null) say("Connect and confirm the pairing code first.") }
    }

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

    /** Name (cleaned) and reported size of a picked document; the size is null when the provider hides it. */
    private fun describeUri(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            contentResolver.query(uri, arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE
            ), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        return NearbySyncProtocol.safeFileName(name ?: uri.lastPathSegment.orEmpty()) to size?.takeIf { it >= 0L }
    }

    /** Queues the picked files and sends them one after another over the current connection. */
    private fun sendPickedFiles(uris: List<Uri>) {
        val endpoint = connectedPeer() ?: return
        if (busy) { say("Finish the current transfer first."); return }
        busy = true
        say("Preparing selected offline files...")
        lifecycleScope.launch {
            val described = withContext(Dispatchers.IO) { uris.map { describeUri(it) } }
            val admission = NearbyBatch.admit(described.map { NearbyBatch.Entry(it.first, it.second) })
            val problem = when {
                admission.tooMany -> getString(R.string.nearby_multi_too_many, NearbySyncProtocol.MAX_BATCH_FILES)
                admission.overTotal -> getString(R.string.nearby_multi_over_total)
                admission.accepted.isEmpty() -> getString(R.string.nearby_multi_nothing_to_send)
                connected != endpoint -> "Disconnected before transfer could start."
                else -> null
            }
            if (problem != null) {
                busy = false
                say(problem)
                return@launch
            }
            tally = NearbyBatch.Tally(failed = admission.tooLarge.size)
            leftOut = admission.tooLarge.size
            queueTotal = admission.accepted.size
            queueIndex = 0
            cancelRest = false
            sendQueue = ArrayDeque(admission.accepted.map { uris[it] })
            announcedBatch = queueTotal > 1
            if (announcedBatch) {
                val total = admission.accepted.sumOf { described[it].second ?: 0L }
                sendMessage(endpoint, NearbySyncProtocol.Message.Batch(queueTotal, total))
                queueControls.visibility = View.VISIBLE
            }
            sendNext()
        }
    }

    /** Prepares and sends the next queued file, or finishes the queue; a failed file never stops the rest. */
    private fun sendNext() {
        val endpoint = connected
        val queue = sendQueue
        if (endpoint == null || queue == null) return
        if (cancelRest) {
            tally.skipped += queue.size
            queue.clear()
        }
        val uri = queue.removeFirstOrNull()
        if (uri == null) {
            finishQueue(endpoint)
            return
        }
        queueIndex++
        val index = queueIndex
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val displayName = describeUri(uri).first
                if (queueTotal > 1) say(getString(R.string.nearby_multi_preparing, index, queueTotal, displayName))
                else say("Preparing selected offline file...")
                val temp = File.createTempFile("nearby-file-", ".tmp", cacheDir)
                try {
                    contentResolver.openInputStream(uri)?.use {
                        BackupArchiveInspector.copyBounded(it, temp, NearbySyncProtocol.MAX_BYTES)
                    } ?: throw IOException("Cannot open the selected file")
                    require(temp.length() > 0) { "Selected file is empty" }
                    temp to displayName
                } catch (error: Exception) {
                    temp.delete()
                    throw error
                }
            } }
            if (sendQueue !== queue) {
                // Disconnected or stopped while preparing.
                result.getOrNull()?.first?.delete()
                return@launch
            }
            result.onSuccess { (file, name) ->
                sendPrepared(endpoint, file, NearbySyncProtocol.Kind.FILE, name)
            }.onFailure {
                tally.failed++
                sendNext()
            }
        }
    }

    private fun finishQueue(endpoint: String) {
        if (announcedBatch) sendMessage(endpoint, NearbySyncProtocol.Message.BatchEnd)
        val result = tally
        val single = queueTotal == 1 && result.sent == 1 && result.failed == 0 && result.skipped == 0
        sendQueue = null
        announcedBatch = false
        cancelRest = false
        busy = false
        queueControls.visibility = View.GONE
        if (single) {
            say("Sent successfully. The other device must still review the import.")
            return
        }
        val summary = NearbyBatch.summary(result,
            { getString(R.string.nearby_multi_sent_count, it) },
            { getString(R.string.nearby_multi_failed_count, it) },
            { getString(R.string.nearby_multi_skipped_count, it) })
        say(getString(R.string.nearby_multi_done, summary) +
            if (leftOut > 0) "\n" + getString(R.string.nearby_multi_over_limit, leftOut) else "")
    }

    /** "Skip this file" and "Cancel remaining files". */
    private fun skipCurrent(all: Boolean) {
        if (sendQueue == null) return
        if (all) cancelRest = true
        val id = outgoingId ?: return
        ignoredPayloads.add(id)
        client.cancelPayload(id)
        outgoingFinished(SendOutcome.SKIPPED, null)
    }

    /** One outgoing file is done. Single sends report [message]; queued sends count it and move on. */
    private fun outgoingFinished(outcome: SendOutcome, message: String?) {
        if (outcome == SendOutcome.SENT && outgoingIsLibraryBackup) BackupStatus.markBackedUp(this)
        outgoingIsLibraryBackup = false
        outgoing?.delete()
        outgoing = null
        outgoingId = null
        if (sendQueue == null) {
            busy = false
            if (message != null) say(message)
            return
        }
        when (outcome) {
            SendOutcome.SENT -> tally.sent++
            SendOutcome.FAILED -> tally.failed++
            SendOutcome.SKIPPED -> tally.skipped++
        }
        sendNext()
    }

    private fun sendPrepared(endpoint: String, file: File, kind: NearbySyncProtocol.Kind,
                             name: String) {
        if (connected != endpoint) {
            file.delete()
            busy = false
            say("Disconnected before transfer could start.")
            return
        }
        if (sendQueue != null && cancelRest) {
            file.delete()
            tally.skipped++
            sendNext()
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
                outgoingIsLibraryBackup = kind == NearbySyncProtocol.Kind.BACKUP
                currentName = header.name
                say(if (sendQueue != null && queueTotal > 1) {
                    getString(R.string.nearby_multi_sending, queueIndex, queueTotal, header.name, 0)
                } else "Sending " + header.name + "...")
                client.sendPayload(endpoint, Payload.fromBytes(NearbySyncProtocol.encode(header)))
                    .addOnSuccessListener {
                        client.sendPayload(endpoint, payload)
                            .addOnFailureListener { sendError(NearbyPreflight.errorMessage(this@NearbyOfflineSyncActivity, it)) }
                    }.addOnFailureListener { sendError(NearbyPreflight.errorMessage(this@NearbyOfflineSyncActivity, it)) }
            }.onFailure {
                file.delete()
                if (sendQueue != null) {
                    tally.failed++
                    sendNext()
                } else {
                    busy = false
                    say("Could not send: " + it.message)
                }
            }
        }
    }

    private fun sendError(message: String) {
        val id = outgoingId ?: return
        ignoredPayloads.add(id)
        client.cancelPayload(id)
        outgoingFinished(SendOutcome.FAILED, getString(R.string.nearby_diag_transfer_failed, message))
    }

    private fun progressText(id: Long, percent: Int): String {
        if (outgoingId == id && sendQueue != null && queueTotal > 1) {
            return getString(R.string.nearby_multi_sending, queueIndex, queueTotal, currentName, percent)
        }
        val ordinal = batchOrdinal[id]
        val header = headers[id]
        if (batchActive && ordinal != null && header != null) {
            return getString(R.string.nearby_multi_receiving, ordinal, batchExpected, header.name, percent)
        }
        return "Transferring: " + percent + "%"
    }

    private val payloadEvents = object : PayloadCallback() {
        override fun onPayloadReceived(endpoint: String, payload: Payload) {
            runOnUiThread {
                if (endpoint != connected && endpoint !in handshakes) return@runOnUiThread
                when (payload.type) {
                    Payload.Type.BYTES -> {
                        runCatching { NearbySyncProtocol.decodeMessage(payload.asBytes()
                            ?: throw IOException("Empty transfer header")) }
                            .onSuccess { handleMessage(endpoint, it) }
                            .onFailure { say("Invalid transfer metadata: " + it.message) }
                    }
                    Payload.Type.FILE -> {
                        if (endpoint != connected || trustPending || payload.id in rejected) {
                            client.cancelPayload(payload.id)
                            return@runOnUiThread
                        }
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
                val ended = update.status != PayloadTransferUpdate.Status.IN_PROGRESS
                if (id in ignoredPayloads) {
                    // Our own skip / error cancel: already counted.
                    if (ended) ignoredPayloads.remove(id)
                    return@runOnUiThread
                }
                if (id in rejected) {
                    if (ended) {
                        rejected.remove(id)
                        maybeFinishBatch()
                    }
                    return@runOnUiThread
                }
                if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                    if (outgoingId == id) {
                        outgoingFinished(SendOutcome.SENT,
                            "Sent successfully. The other device must still review the import.")
                    } else {
                        finished.add(id)
                        checkComplete(id)
                    }
                } else if (update.status == PayloadTransferUpdate.Status.FAILURE ||
                    update.status == PayloadTransferUpdate.Status.CANCELED) {
                    val canceled = update.status == PayloadTransferUpdate.Status.CANCELED
                    val reason = if (canceled) getString(R.string.nearby_diag_transfer_canceled)
                        else NearbyPreflight.statusMessage(this@NearbyOfflineSyncActivity,
                            ConnectionsStatusCodes.STATUS_PAYLOAD_IO_ERROR)
                    if (outgoingId == id) {
                        if (canceled) outgoingFinished(SendOutcome.SKIPPED, reason)
                        else sendError(reason)
                    } else {
                        val wasIncoming = headers.containsKey(id) || received.containsKey(id)
                        headers.remove(id)
                        received.remove(id)
                        finished.remove(id)
                        batchOrdinal.remove(id)
                        if (batchActive && wasIncoming) receiveTally.failed++
                        say(if (canceled) reason
                            else getString(R.string.nearby_diag_incoming_failed, reason))
                        maybeFinishBatch()
                    }
                } else if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS &&
                    update.totalBytes > NearbySyncProtocol.MAX_BYTES && outgoingId != id) {
                    client.cancelPayload(id)
                    if (batchActive && (headers.containsKey(id) || received.containsKey(id))) receiveTally.failed++
                    received.remove(id)
                    headers.remove(id)
                    finished.remove(id)
                    batchOrdinal.remove(id)
                    rejected.add(id)
                    say("Incoming transfer rejected: larger than 256 MB.")
                } else if (update.status == PayloadTransferUpdate.Status.IN_PROGRESS &&
                    update.totalBytes > 0) {
                    say(progressText(id, NearbyBatch.percent(update.bytesTransferred, update.totalBytes)))
                }
            }
        }
    }

    /** Typed control messages. Anything but the challenge-response is refused until a remembered peer is verified. */
    private fun handleMessage(endpoint: String, message: NearbySyncProtocol.Message) {
        val handshake = handshakes[endpoint]
        if (handshake != null && handshake.state == NearbyTrustHandshake.State.PENDING) {
            when (message) {
                NearbySyncProtocol.Message.TrustFail -> trustFailed(endpoint, false)
                is NearbySyncProtocol.Message.Challenge, is NearbySyncProtocol.Message.Response -> {
                    handshake.receive(message).forEach { sendMessage(endpoint, it) }
                    checkHandshake(endpoint, handshake)
                }
                else -> trustFailed(endpoint, true)
            }
            return
        }
        if (endpoint != connected) return
        when (message) {
            is NearbySyncProtocol.Message.Transfer -> receiveHeader(message.header)
            is NearbySyncProtocol.Message.Batch -> startReceivingBatch(message)
            NearbySyncProtocol.Message.BatchEnd -> {
                batchEnded = true
                maybeFinishBatch()
            }
            is NearbySyncProtocol.Message.PairOffer -> handlePairOffer(endpoint, message)
            is NearbySyncProtocol.Message.PairAck -> {
                val offered = offeredSecret
                if (offered != null && offered.first == endpoint) {
                    offeredSecret = null
                    rememberDevice(message.deviceId, message.name, endpointNames[endpoint].orEmpty(), offered.second)
                    say(getString(R.string.nearby_trust_paired, message.name))
                }
            }
            NearbySyncProtocol.Message.PairDecline -> {
                if (offeredSecret?.first == endpoint) {
                    offeredSecret = null
                    say(getString(R.string.nearby_trust_declined))
                }
            }
            // The peer remembers this device but this device does not (any more): tell it, nothing is transferred.
            is NearbySyncProtocol.Message.Challenge, is NearbySyncProtocol.Message.Response ->
                if (handshake == null) sendMessage(endpoint, NearbySyncProtocol.Message.TrustFail)
            NearbySyncProtocol.Message.TrustFail -> Unit
        }
    }

    private fun receiveHeader(header: NearbySyncProtocol.Header) {
        if (trustPending) return
        if (batchActive) {
            batchSeen++
            batchBytes += header.length
            if (batchSeen > batchExpected || batchBytes > NearbySyncProtocol.MAX_BATCH_BYTES) {
                rejected.add(header.id)
                client.cancelPayload(header.id)
                receiveTally.failed++
                say("Incoming transfer rejected: more than the announced files or 2 GB in total.")
                return
            }
            batchOrdinal[header.id] = batchSeen
        }
        headers[header.id] = header
        checkComplete(header.id)
    }

    // ---- Receiving several files ------------------------------------------------------------------------------

    private fun startReceivingBatch(batch: NearbySyncProtocol.Message.Batch) {
        batchActive = true
        batchEnded = false
        batchExpected = batch.count
        batchSeen = 0
        batchBytes = 0L
        batchOrdinal.clear()
        receiveTally.sent = 0
        receiveTally.failed = 0
        receiveTally.skipped = 0
        batchFolder = rememberedFolder()
        folderDecision = if (batchFolder != null) FolderDecision.FOLDER else FolderDecision.UNDECIDED
        if (batchFolder != null) {
            say(getString(R.string.nearby_multi_saving_to))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.nearby_multi_folder_title))
            .setMessage(getString(R.string.nearby_multi_folder_message, batch.count))
            .setCancelable(false)
            .setNegativeButton(getString(R.string.nearby_multi_one_by_one)) { _, _ -> onFolderChosen(null) }
            .setPositiveButton(getString(R.string.nearby_multi_choose_folder)) { _, _ -> folderPicker.launch(null) }
            .show()
    }

    /** The remembered save folder, only while Android still holds our write grant for it. */
    private fun rememberedFolder(): Uri? {
        val raw = trust.saveFolder() ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val granted = uri != null && contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        if (!granted) trust.setSaveFolder(null)
        return if (granted) uri else null
    }

    /** Clears the remembered folder and gives its Android write grant back, so old choices do not pile up. */
    private fun forgetSaveFolder() {
        trust.saveFolder()?.let { raw ->
            runCatching {
                contentResolver.releasePersistableUriPermission(Uri.parse(raw),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        trust.setSaveFolder(null)
    }

    private fun onFolderChosen(uri: Uri?) {
        val granted = uri != null && runCatching {
            contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (uri != null && granted) {
            if (trust.saveFolder() != uri.toString()) forgetSaveFolder()
            trust.setSaveFolder(uri.toString())
            batchFolder = uri
            folderDecision = FolderDecision.FOLDER
        } else {
            // No folder, or the grant could not be kept: fall back to one save dialog per file.
            batchFolder = null
            folderDecision = FolderDecision.ONE_BY_ONE
        }
        val waiting = ArrayList(stagedFiles)
        stagedFiles.clear()
        waiting.forEach { (file, name) -> routeReceivedFile(file, name) }
        maybeFinishBatch()
    }

    private fun routeReceivedFile(file: File, name: String) {
        if (!batchActive) {
            enqueueReview(file, name)
            return
        }
        when (folderDecision) {
            FolderDecision.FOLDER -> saveToFolder(file, name)
            FolderDecision.ONE_BY_ONE -> enqueueReview(file, name)
            FolderDecision.UNDECIDED -> stagedFiles.add(file to name)
        }
    }

    /** Writes a verified file into the chosen folder; on any failure the file goes to the per-file dialogs instead. */
    private fun saveToFolder(file: File, name: String) {
        val tree = batchFolder
        if (tree == null) {
            enqueueReview(file, name)
            return
        }
        pendingWork++
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val extension = name.substringAfterLast('.', "").lowercase()
                val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: "application/octet-stream"
                val target = DocumentsContract.createDocument(contentResolver, parent, mime, name)
                    ?: throw IOException("Cannot create the file in that folder")
                try {
                    contentResolver.openOutputStream(target)?.use { output ->
                        file.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException("Destination cannot be written")
                } catch (error: Exception) {
                    runCatching { DocumentsContract.deleteDocument(contentResolver, target) }
                    throw error
                }
            } }
            pendingWork--
            result.onSuccess {
                file.delete()
                receiveTally.sent++
            }.onFailure {
                forgetSaveFolder()
                batchFolder = null
                folderDecision = FolderDecision.ONE_BY_ONE
                say("Save to folder failed: " + it.message)
                enqueueReview(file, name)
            }
            maybeFinishBatch()
        }
    }

    private fun maybeFinishBatch() {
        if (!batchActive || !batchEnded || pendingWork > 0 || received.isNotEmpty()) return
        if (folderDecision == FolderDecision.UNDECIDED) return
        headers.clear()
        batchActive = false
        if (folderDecision == FolderDecision.FOLDER) {
            val summary = NearbyBatch.summary(receiveTally,
                { getString(R.string.nearby_multi_saved_count, it) },
                { getString(R.string.nearby_multi_failed_count, it) },
                { getString(R.string.nearby_multi_skipped_count, it) })
            say(getString(R.string.nearby_multi_received_done, summary))
        }
    }

    /** Verified files wait here so only one review / save dialog is open at a time. */
    private fun enqueueReview(file: File, name: String) {
        reviewQueue.addLast(file to name)
        nextReview()
    }

    private fun nextReview() {
        if (reviewing) return
        val next = reviewQueue.removeFirstOrNull() ?: return
        reviewing = true
        showFileReview(next.first, next.second)
    }

    private fun reviewDone() {
        reviewing = false
        nextReview()
    }

    private fun checkComplete(id: Long) {
        val header = headers[id] ?: return
        val payload = received[id] ?: return
        if (id !in finished) return
        headers.remove(id)
        received.remove(id)
        finished.remove(id)
        batchOrdinal.remove(id)
        pendingWork++
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
            pendingWork--
            result.onSuccess { (staged, report) ->
                if (report != null) showBackupReview(staged, report)
                else routeReceivedFile(staged, header.name)
            }.onFailure {
                if (batchActive) receiveTally.failed++
                say("Incoming transfer rejected: " + it.message)
            }
            maybeFinishBatch()
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
                } else reviewConflicts(file, sections) { peerId, resolution, favourites ->
                    if (BackupSection.SETTINGS in sections) {
                        chooseSettingsGroups { groups ->
                            mergeReceived(file, sections, groups, peerId, resolution, favourites)
                        }
                    } else {
                        mergeReceived(file, sections, null, peerId, resolution, favourites)
                    }
                }
            }.show()
    }

    /**
     * v1.2: when history or favourites are merged, titles changed on both devices since their last sync are listed so
     * the user picks which side wins (newest by default): history first, then favourites in a second sheet.
     * [onResolved] gets the other device's id for the last-sync record.
     */
    private fun reviewConflicts(
        file: File,
        sections: Set<BackupSection>,
        onResolved: (String?, HistoryConflicts.Resolution, FavouriteConflicts.Resolution) -> Unit,
    ) {
        val wantsFavourites = BackupSection.FAVOURITES in sections && BackupSection.CATEGORIES in sections
        if (BackupSection.HISTORY !in sections && !wantsFavourites) {
            onResolved(null, HistoryConflicts.Resolution.NONE, FavouriteConflicts.Resolution.NONE)
            return
        }
        say(getString(R.string.sync_conflict_checking))
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { historyConflicts.review(file, sections) } }
            result.onSuccess { review ->
                val discard = {
                    file.delete()
                    say("Received archive discarded. No changes were made.")
                }
                fun askFavourites(history: HistoryConflicts.Resolution) {
                    if (review.favouriteConflicts.isEmpty()) {
                        onResolved(review.deviceId, history, FavouriteConflicts.Resolution.NONE)
                    } else {
                        FavouriteConflictSheet.show(
                            this@NearbyOfflineSyncActivity, review.favouriteConflicts,
                            onApply = { favourites -> onResolved(review.deviceId, history, favourites) },
                            onCancel = discard,
                        )
                    }
                }
                if (review.conflicts.isEmpty()) askFavourites(HistoryConflicts.Resolution.NONE)
                else HistoryConflictSheet.show(
                    this@NearbyOfflineSyncActivity, review,
                    onApply = { resolution -> askFavourites(resolution) },
                    onCancel = discard,
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                // Never merge unreviewed: a failed comparison is not "no conflicts".
                file.delete()
                say(getString(R.string.sync_conflict_review_failed))
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
        favourites: FavouriteConflicts.Resolution = FavouriteConflicts.Resolution.NONE,
    ) {
        say("Merging selected backup sections...")
        lifecycleScope.launch {
            // Once started, a merge runs to the end even if the screen is closed: stopping halfway leaves a mixed library.
            val result = withContext(Dispatchers.IO + NonCancellable) {
                try {
                    runCatching { ZipInputStream(file.inputStream()).use {
                        backups.restoreBackup(it, sections, null, groups,
                            keepLocalHistoryIds = resolution.keepLocal,
                            takeIncomingHistoryIds = resolution.takeIncoming,
                            keepLocalFavouriteIds = favourites.keepLocal,
                            takeIncomingFavouriteIds = favourites.takeIncoming,
                            unionFavouriteIds = favourites.union)
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
                        reviewDone()
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
            reviewDone()
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
        handshakes.clear()
        handshakeDevices.clear()
        digitConfirmed.clear()
        trustPending = false
        offeredSecret = null
        discovered.clear()
        if (::peers.isInitialized) refreshPeers()
        keepAwake(false)
        resetTransfer()
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
        stagedFiles.forEach { it.first.delete() }
        reviewQueue.forEach { it.first.delete() }
        super.onDestroy()
    }
}
