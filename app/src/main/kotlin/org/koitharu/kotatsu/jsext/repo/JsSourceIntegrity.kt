package org.koitharu.kotatsu.jsext.repo

import java.io.IOException
import java.security.MessageDigest

/** Content hash and trust-on-first-use version policy for installed JS extensions. */
object JsSourceIntegrity {
    fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Reject changed code reissued under an already installed version. */
    fun validate(
        version: String,
        contentHash: String,
        installedVersion: String?,
        installedHash: String?,
        declaredHash: String?,
    ) {
        if (declaredHash != null) {
            if (!declaredHash.matches(Regex("[A-Fa-f0-9]{64}"))) {
                throw IOException("Invalid SHA-256 supplied by the source index")
            }
            if (!contentHash.equals(declaredHash, ignoreCase = true)) {
                throw IOException("Source file SHA-256 does not match its index")
            }
        }
        if (installedVersion != null) {
            val comparison = compareVersions(version, installedVersion)
            if (comparison < 0) throw IOException("Refusing extension version downgrade")
            if (comparison == 0 && installedHash != null && contentHash != installedHash) {
                throw IOException("Source file changed without a version bump")
            }
        }
    }
}
