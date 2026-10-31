package org.koitharu.kotatsu.backups.domain

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import org.koitharu.kotatsu.R

/**
 * Checks that this device can run Nearby Connections before Receive/Send starts, and turns Nearby
 * status codes into plain language. The decisions are pure functions (unit-tested); the code that
 * reads the real device state is kept separate in [snapshot].
 */
internal object NearbyPreflight {

    enum class Requirement {
        PLAY_SERVICES,
        AIRPLANE_MODE,
        BLUETOOTH_OFF,
        WIFI_OFF,
        LOCATION_OFF,
        PERMISSIONS,
    }

    /** Everything the decision logic needs; [playServicesStatus] is a [ConnectionResult] code. */
    data class Snapshot(
        val sdk: Int,
        val playServicesStatus: Int,
        val airplaneMode: Boolean,
        val bluetoothOn: Boolean,
        val wifiOn: Boolean,
        val locationOn: Boolean,
        val grantedPermissions: Set<String>,
    )

    // ---- Pure decision logic ----

    /** Runtime permissions Nearby Connections (P2P_POINT_TO_POINT) needs on [sdk]. */
    fun requiredPermissions(sdk: Int): List<String> = buildList {
        if (sdk >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (sdk >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (sdk in 29..32) add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (sdk < 29) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdk >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }

    fun missingPermissions(sdk: Int, granted: Set<String>): List<String> =
        requiredPermissions(sdk).filterNot { it in granted }

    /** Nearby needs the Location switch on only up to Android 12L (API 32). */
    fun locationServicesRequired(sdk: Int): Boolean = sdk <= 32

    fun missingRequirements(snapshot: Snapshot): List<Requirement> = buildList {
        if (snapshot.playServicesStatus != ConnectionResult.SUCCESS) add(Requirement.PLAY_SERVICES)
        if (snapshot.airplaneMode && (!snapshot.bluetoothOn || !snapshot.wifiOn)) add(Requirement.AIRPLANE_MODE)
        if (!snapshot.bluetoothOn) add(Requirement.BLUETOOTH_OFF)
        if (!snapshot.wifiOn) add(Requirement.WIFI_OFF)
        if (locationServicesRequired(snapshot.sdk) && !snapshot.locationOn) add(Requirement.LOCATION_OFF)
        if (missingPermissions(snapshot.sdk, snapshot.grantedPermissions).isNotEmpty()) add(Requirement.PERMISSIONS)
    }

    /** Short permission names for messages, e.g. "BLUETOOTH_SCAN, NEARBY_WIFI_DEVICES". */
    fun permissionNames(permissions: List<String>): String =
        permissions.joinToString(", ") { it.substringAfterLast('.') }

    @StringRes
    fun problemRes(requirement: Requirement): Int = when (requirement) {
        Requirement.PLAY_SERVICES -> R.string.nearby_diag_play_services
        Requirement.AIRPLANE_MODE -> R.string.nearby_diag_airplane
        Requirement.BLUETOOTH_OFF -> R.string.nearby_diag_bluetooth_off
        Requirement.WIFI_OFF -> R.string.nearby_diag_wifi_off
        Requirement.LOCATION_OFF -> R.string.nearby_diag_location_off
        Requirement.PERMISSIONS -> R.string.nearby_diag_permissions_missing
    }

    @StringRes
    fun okRes(requirement: Requirement): Int = when (requirement) {
        Requirement.PLAY_SERVICES -> R.string.nearby_diag_ok_play_services
        Requirement.AIRPLANE_MODE -> R.string.nearby_diag_ok_airplane
        Requirement.BLUETOOTH_OFF -> R.string.nearby_diag_ok_bluetooth
        Requirement.WIFI_OFF -> R.string.nearby_diag_ok_wifi
        Requirement.LOCATION_OFF -> R.string.nearby_diag_ok_location
        Requirement.PERMISSIONS -> R.string.nearby_diag_ok_permissions
    }

    @StringRes
    fun actionRes(requirement: Requirement): Int = when (requirement) {
        Requirement.PLAY_SERVICES -> R.string.nearby_diag_action_play_services
        Requirement.AIRPLANE_MODE -> R.string.nearby_diag_action_airplane
        Requirement.BLUETOOTH_OFF -> R.string.nearby_diag_action_bluetooth
        Requirement.WIFI_OFF -> R.string.nearby_diag_action_wifi
        Requirement.LOCATION_OFF -> R.string.nearby_diag_action_location
        Requirement.PERMISSIONS -> R.string.nearby_diag_action_app_settings
    }

    /** Plain-language string for a Nearby / Google Play services status code. */
    @StringRes
    fun describe(statusCode: Int): Int = when (statusCode) {
        ConnectionsStatusCodes.STATUS_NETWORK_NOT_CONNECTED -> R.string.nearby_diag_status_network_not_connected
        ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING -> R.string.nearby_diag_status_already_advertising
        ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING -> R.string.nearby_diag_status_already_discovering
        ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> R.string.nearby_diag_status_already_connected
        ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> R.string.nearby_diag_status_rejected
        ConnectionsStatusCodes.STATUS_NOT_CONNECTED_TO_ENDPOINT -> R.string.nearby_diag_status_not_connected
        // STATUS_RADIO_ERROR has the same value (8007).
        ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR -> R.string.nearby_diag_status_bluetooth_error
        ConnectionsStatusCodes.STATUS_ALREADY_HAVE_ACTIVE_STRATEGY -> R.string.nearby_diag_status_active_strategy
        ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> R.string.nearby_diag_status_out_of_order
        ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN -> R.string.nearby_diag_status_endpoint_unknown
        ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR -> R.string.nearby_diag_status_endpoint_io
        ConnectionsStatusCodes.STATUS_PAYLOAD_IO_ERROR -> R.string.nearby_diag_status_payload_io
        ConnectionsStatusCodes.STATUS_ERROR -> R.string.nearby_diag_status_error
        ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON -> R.string.nearby_diag_status_location_off
        ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
        ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
        ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
        ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT -> R.string.nearby_diag_status_permission
        ConnectionsStatusCodes.API_CONNECTION_FAILED_ALREADY_IN_USE -> R.string.nearby_diag_status_in_use
        ConnectionResult.SERVICE_MISSING,
        CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED,
        CommonStatusCodes.SERVICE_DISABLED -> R.string.nearby_diag_status_play_services
        CommonStatusCodes.API_NOT_CONNECTED -> R.string.nearby_diag_status_not_connected_api
        CommonStatusCodes.TIMEOUT -> R.string.nearby_diag_status_timeout
        else -> R.string.nearby_diag_status_unknown
    }

    /** "<reason> [8012]" so the owner can report the numeric code. */
    fun withCode(text: String, statusCode: Int): String = "$text [$statusCode]"

    fun statusMessage(context: Context, statusCode: Int): String =
        withCode(context.getString(describe(statusCode)), statusCode)

    /** Message for any failure; Nearby failures are ApiExceptions that carry a status code. */
    fun errorMessage(context: Context, error: Throwable): String {
        val code = (error as? ApiException)?.statusCode
        return if (code != null) statusMessage(context, code)
        else error.localizedMessage ?: error.javaClass.simpleName
    }

    // ---- Android-specific gathering ----

    fun snapshot(context: Context): Snapshot {
        val sdk = Build.VERSION.SDK_INT
        val granted = requiredPermissions(sdk).filter {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }.toSet()
        val bluetooth = runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled
        }.getOrNull() ?: false
        // Unknown (no Wi-Fi hardware, or access refused) is treated as fine so we never block wrongly.
        val wifi = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled
        }.getOrNull() ?: true
        val location = runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            manager == null || LocationManagerCompat.isLocationEnabled(manager)
        }.getOrDefault(true)
        val airplane = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        }.getOrDefault(false)
        return Snapshot(
            sdk = sdk,
            playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context),
            airplaneMode = airplane,
            bluetoothOn = bluetooth,
            wifiOn = wifi,
            locationOn = location,
            grantedPermissions = granted,
        )
    }

    /** The system screen that lets the user fix [requirement]; for PLAY_SERVICES it is the Play Store page. */
    fun fixIntent(requirement: Requirement, packageName: String): Intent = when (requirement) {
        Requirement.PLAY_SERVICES ->
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
        Requirement.AIRPLANE_MODE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        Requirement.BLUETOOTH_OFF -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        Requirement.WIFI_OFF ->
            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI)
            else Intent(Settings.ACTION_WIFI_SETTINGS)
        Requirement.LOCATION_OFF -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        Requirement.PERMISSIONS ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
}
