package org.koitharu.kotatsu.backups.domain

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Restricts sync operations to an HTTPS collection and UUID-named files. */
internal class WebDavEndpoint private constructor(val collection: HttpUrl) {
    companion object {
        fun parse(raw: String): WebDavEndpoint {
            val url = raw.trim().toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid WebDAV URL")
            require(url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()) {
                "WebDAV requires HTTPS without embedded credentials"
            }
            require(url.query == null && url.fragment == null) {
                "WebDAV URL must not contain a query or fragment"
            }
            return WebDavEndpoint(url.newBuilder().addPathSegment("Monogatari")
                .addPathSegment("").build())
        }

        fun validDeviceId(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }
    fun file(deviceId: String): HttpUrl {
        require(validDeviceId(deviceId)) { "Invalid WebDAV device identifier" }
        return collection.newBuilder().addPathSegment("$deviceId.zip").build()
    }

    /** Never trust DAV hrefs as download destinations; reconstruct URLs from UUIDs. */
    fun deviceIdFromHref(href: String): String? {
        val actual = collection.resolve(href) ?: return null
        if (actual.scheme != collection.scheme || actual.host != collection.host ||
            actual.port != collection.port || actual.username.isNotEmpty() ||
            actual.password.isNotEmpty() || actual.query != null || actual.fragment != null) return null
        val prefix = collection.encodedPath
        if (!actual.encodedPath.startsWith(prefix)) return null
        val name = actual.encodedPath.removePrefix(prefix)
        if (name.contains('/') || name.contains('%') || !name.endsWith(".zip")) return null
        return name.removeSuffix(".zip").takeIf(::validDeviceId)
    }
}
