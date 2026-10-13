package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class WebDavEndpointTest {
    private val device = UUID.randomUUID().toString()

    @Test fun rejectsInsecureEndpointsAndEmbeddedCredentials() {
        listOf(
            "http://cloud.example/dav/",
            "https://user:password@cloud.example/dav/",
            "https://cloud.example/dav/?token=secret",
            "https://cloud.example/dav/#fragment",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { WebDavEndpoint.parse(url) }
        }
    }

    @Test fun constructsAndValidatesDeviceScopedPaths() {
        val endpoint = WebDavEndpoint.parse("https://cloud.example/dav/")
        assertEquals("https://cloud.example/dav/Monogatari/$device.zip", endpoint.file(device).toString())
        assertEquals(device, endpoint.deviceIdFromHref("$device.zip"))
    }
    @Test fun neverFollowsForeignOrEscapingHrefs() {
        val endpoint = WebDavEndpoint.parse("https://cloud.example/dav/")
        listOf(
            "https://attacker.example/dav/Monogatari/$device.zip",
            "../$device.zip",
            "../../Monogatari/$device.zip",
            "https://cloud.example/elsewhere/$device.zip",
            "$device.zip?download=1",
            "subdir/$device.zip",
            "not-a-uuid.zip",
        ).forEach { href ->
            assertNull("Unexpected href: $href", endpoint.deviceIdFromHref(href))
        }
    }

    @Test fun rejectsInvalidDeviceIdentifiers() {
        val endpoint = WebDavEndpoint.parse("https://cloud.example/dav/")
        listOf("../other", "ABC", "", "$device/../../other").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { endpoint.file(id) }
        }
    }
}
