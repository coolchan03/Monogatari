package org.koitharu.kotatsu.backups.domain

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.NearbyPreflight.Requirement

class NearbyPreflightTest {

    private fun snapshot(
        sdk: Int,
        playServices: Int = ConnectionResult.SUCCESS,
        airplane: Boolean = false,
        bluetooth: Boolean = true,
        wifi: Boolean = true,
        location: Boolean = true,
        granted: Boolean = true,
    ) = NearbyPreflight.Snapshot(
        sdk = sdk,
        playServicesStatus = playServices,
        airplaneMode = airplane,
        bluetoothOn = bluetooth,
        wifiOn = wifi,
        locationOn = location,
        grantedPermissions = if (granted) NearbyPreflight.requiredPermissions(sdk).toSet() else emptySet(),
    )

    @Test fun permissionsPerSdkLevel() {
        assertEquals(listOf("android.permission.ACCESS_COARSE_LOCATION"), NearbyPreflight.requiredPermissions(28))
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), NearbyPreflight.requiredPermissions(30))
        assertEquals(
            listOf(
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.ACCESS_FINE_LOCATION",
            ),
            NearbyPreflight.requiredPermissions(32),
        )
        assertEquals(
            listOf(
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.NEARBY_WIFI_DEVICES",
            ),
            NearbyPreflight.requiredPermissions(34),
        )
        val latest = NearbyPreflight.requiredPermissions(37)
        assertTrue("android.permission.ACCESS_LOCAL_NETWORK" in latest)
        assertFalse("android.permission.ACCESS_FINE_LOCATION" in latest)
    }

    @Test fun missingPermissionsListsOnlyWhatIsNotGranted() {
        val granted = setOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        val missing = NearbyPreflight.missingPermissions(34, granted)
        assertEquals(listOf("android.permission.BLUETOOTH_ADVERTISE", "android.permission.NEARBY_WIFI_DEVICES"), missing)
        assertEquals("BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES", NearbyPreflight.permissionNames(missing))
    }

    @Test fun healthyDeviceHasNoProblems() {
        for (sdk in listOf(26, 29, 31, 32, 33, 35, 37)) {
            assertEquals(emptyList<Requirement>(), NearbyPreflight.missingRequirements(snapshot(sdk)))
        }
    }

    @Test fun locationOnlyMattersOnAndroid12LAndBelow() {
        assertEquals(
            listOf(Requirement.LOCATION_OFF),
            NearbyPreflight.missingRequirements(snapshot(32, location = false)),
        )
        assertEquals(
            listOf(Requirement.LOCATION_OFF),
            NearbyPreflight.missingRequirements(snapshot(28, location = false)),
        )
        assertEquals(emptyList<Requirement>(), NearbyPreflight.missingRequirements(snapshot(33, location = false)))
        assertTrue(NearbyPreflight.locationServicesRequired(32))
        assertFalse(NearbyPreflight.locationServicesRequired(33))
    }

    @Test fun reportsRadiosPlayServicesAndPermissionsSeparately() {
        assertEquals(
            listOf(Requirement.BLUETOOTH_OFF),
            NearbyPreflight.missingRequirements(snapshot(34, bluetooth = false)),
        )
        assertEquals(
            listOf(Requirement.WIFI_OFF),
            NearbyPreflight.missingRequirements(snapshot(34, wifi = false)),
        )
        assertEquals(
            listOf(Requirement.PLAY_SERVICES),
            NearbyPreflight.missingRequirements(snapshot(34, playServices = ConnectionResult.SERVICE_MISSING)),
        )
        assertEquals(
            listOf(Requirement.PLAY_SERVICES),
            NearbyPreflight.missingRequirements(
                snapshot(34, playServices = ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED),
            ),
        )
        assertEquals(
            listOf(Requirement.PERMISSIONS),
            NearbyPreflight.missingRequirements(snapshot(34, granted = false)),
        )
    }

    @Test fun airplaneModeOnlyCountsWhenARadioIsOff() {
        assertEquals(emptyList<Requirement>(), NearbyPreflight.missingRequirements(snapshot(34, airplane = true)))
        assertEquals(
            listOf(Requirement.AIRPLANE_MODE, Requirement.BLUETOOTH_OFF, Requirement.WIFI_OFF),
            NearbyPreflight.missingRequirements(snapshot(34, airplane = true, bluetooth = false, wifi = false)),
        )
    }

    @Test fun everythingWrongListsEveryRequirement() {
        val all = NearbyPreflight.missingRequirements(
            snapshot(
                30, playServices = ConnectionResult.SERVICE_DISABLED, airplane = true,
                bluetooth = false, wifi = false, location = false, granted = false,
            ),
        )
        assertEquals(Requirement.entries.toList(), all)
    }

    @Test fun everyRequirementHasTextAndAction() {
        for (requirement in Requirement.entries) {
            assertTrue(NearbyPreflight.problemRes(requirement) != 0)
            assertTrue(NearbyPreflight.okRes(requirement) != 0)
            assertTrue(NearbyPreflight.actionRes(requirement) != 0)
        }
    }

    @Test fun documentedStatusCodeValues() {
        assertEquals(8000, ConnectionsStatusCodes.STATUS_NETWORK_NOT_CONNECTED)
        assertEquals(8001, ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING)
        assertEquals(8002, ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING)
        assertEquals(8003, ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT)
        assertEquals(8004, ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED)
        assertEquals(8007, ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR)
        assertEquals(8009, ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL)
        assertEquals(8011, ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN)
        assertEquals(8012, ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR)
        assertEquals(13, ConnectionsStatusCodes.STATUS_ERROR)
    }

    @Test fun describesNearbyStatusCodes() {
        assertEquals(R.string.nearby_diag_status_network_not_connected, NearbyPreflight.describe(8000))
        assertEquals(R.string.nearby_diag_status_already_advertising, NearbyPreflight.describe(8001))
        assertEquals(R.string.nearby_diag_status_already_discovering, NearbyPreflight.describe(8002))
        assertEquals(R.string.nearby_diag_status_already_connected, NearbyPreflight.describe(8003))
        assertEquals(R.string.nearby_diag_status_rejected, NearbyPreflight.describe(8004))
        assertEquals(R.string.nearby_diag_status_bluetooth_error, NearbyPreflight.describe(8007))
        assertEquals(R.string.nearby_diag_status_out_of_order, NearbyPreflight.describe(8009))
        assertEquals(R.string.nearby_diag_status_endpoint_unknown, NearbyPreflight.describe(8011))
        assertEquals(R.string.nearby_diag_status_endpoint_io, NearbyPreflight.describe(8012))
        assertEquals(R.string.nearby_diag_status_payload_io, NearbyPreflight.describe(8013))
        assertEquals(R.string.nearby_diag_status_error, NearbyPreflight.describe(13))
        assertEquals(R.string.nearby_diag_status_location_off, NearbyPreflight.describe(8025))
    }

    @Test fun missingPermissionCodesAllMapToPermissionMessage() {
        for (code in listOf(8029, 8030, 8031, 8032, 8033, 8034, 8036, 8037, 8038, 8039)) {
            assertEquals(R.string.nearby_diag_status_permission, NearbyPreflight.describe(code))
        }
    }

    @Test fun playServicesAndUnknownCodes() {
        assertEquals(R.string.nearby_diag_status_play_services, NearbyPreflight.describe(ConnectionResult.SERVICE_MISSING))
        assertEquals(R.string.nearby_diag_status_play_services, NearbyPreflight.describe(2))
        assertEquals(R.string.nearby_diag_status_play_services, NearbyPreflight.describe(3))
        assertEquals(R.string.nearby_diag_status_not_connected_api, NearbyPreflight.describe(17))
        assertEquals(R.string.nearby_diag_status_unknown, NearbyPreflight.describe(123456))
    }

    @Test fun numericCodeIsAppendedInBrackets() {
        assertEquals("Lost contact [8012]", NearbyPreflight.withCode("Lost contact", 8012))
    }
}
