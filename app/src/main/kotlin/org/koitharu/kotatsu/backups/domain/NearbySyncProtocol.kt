package org.koitharu.kotatsu.backups.domain

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Protocol for explicitly approved, encrypted Nearby Connections transfers.
 * This is intentionally a one-shot transfer, not background/automatic syncing.
 */
internal object NearbySyncProtocol {
    const val VERSION = 1
    const val MAX_BYTES = 256L * 1024 * 1024
    private const val MAX_HEADER_CHARS = 2048
    private val namePattern = Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,119}")
    private val shaPattern = Regex("[0-9a-f]{64}")

    enum class Kind { BACKUP, FILE }

    data class Header(
        val id: Long,
        val kind: Kind,
        val name: String,
        val length: Long,
        val sha256: String,
    ) {
        init {
            require(length in 1L..MAX_BYTES) { "Transfer exceeds 256 MB limit" }
            require(namePattern.matches(name) && name != "..") { "Invalid incoming filename" }
            require(kind != Kind.BACKUP || name.endsWith(".bk.zip")) { "Invalid backup filename" }
            require(shaPattern.matches(sha256)) { "Invalid transfer checksum" }
        }
    }

    fun safeFileName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trimStart('.', ' ')
            .take(120)
            .ifBlank { "received-file" }

    fun encode(header: Header): ByteArray = JSONObject()
        .put("protocol", VERSION)
        .put("id", header.id)
        .put("kind", header.kind.name)
        .put("name", header.name)
        .put("length", header.length)
        .put("sha256", header.sha256)
        .toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Header {
        require(bytes.size in 1..MAX_HEADER_CHARS) { "Invalid nearby transfer header" }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.getInt("protocol") == VERSION) { "Unsupported nearby protocol" }
        return Header(
            id = json.getLong("id"),
            kind = Kind.valueOf(json.getString("kind")),
            name = json.getString("name"),
            length = json.getLong("length"),
            sha256 = json.getString("sha256"),
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val data = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(data)
                if (n < 0) break
                if (n > 0) digest.update(data, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
