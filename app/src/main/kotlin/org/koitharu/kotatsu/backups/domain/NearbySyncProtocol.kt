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

    /** Several files can be queued in one go, but never more than this many bytes or files in total. */
    const val MAX_BATCH_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_BATCH_FILES = 100
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

    // ---- Typed control messages (BYTES payloads) ----------------------------------------------------------
    // A transfer header has no "type" key and keeps its original layout, so older builds still understand it.
    // Everything else carries {"protocol":1,"type":...}; each message is at most MAX_HEADER_CHARS bytes.

    private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val secretPattern = Regex("[0-9a-f]{${NearbyTrust.SECRET_BYTES * 2}}")
    private val noncePattern = Regex("[0-9a-f]{${NearbyTrust.NONCE_BYTES * 2}}")
    private val macPattern = Regex("[0-9a-f]{${NearbyTrust.MAC_BYTES * 2}}")

    sealed interface Message {
        data class Transfer(val header: Header) : Message

        /** Announces a multi-file send so the receiver can ask for one destination. */
        data class Batch(val count: Int, val totalBytes: Long) : Message {
            init {
                require(count in 2..MAX_BATCH_FILES) { "Invalid file count" }
                require(totalBytes in 0L..MAX_BATCH_BYTES) { "Transfer exceeds the total size limit" }
            }
        }

        data object BatchEnd : Message

        /** First pairing, sent after both users confirmed the digits. [secret] is lower-case hex. */
        data class PairOffer(val deviceId: String, val name: String, val secret: String) : Message {
            init {
                require(idPattern.matches(deviceId)) { "Invalid device id" }
                require(secretPattern.matches(secret)) { "Invalid pairing secret" }
            }
        }

        data class PairAck(val deviceId: String, val name: String) : Message {
            init {
                require(idPattern.matches(deviceId)) { "Invalid device id" }
            }
        }

        data object PairDecline : Message

        /** Start of the challenge-response with a remembered device; [nonce] is lower-case hex. */
        data class Challenge(val deviceId: String, val nonce: String) : Message {
            init {
                require(idPattern.matches(deviceId)) { "Invalid device id" }
                require(noncePattern.matches(nonce)) { "Invalid nonce" }
            }
        }

        data class Response(val mac: String) : Message {
            init {
                require(macPattern.matches(mac)) { "Invalid response" }
            }
        }

        /** The sender does not (or no longer) trust the peer; the session is about to close. */
        data object TrustFail : Message
    }

    fun cleanDisplayName(raw: String): String =
        raw.filterNot { it.isISOControl() }.trim().take(60).ifBlank { "Unknown device" }

    fun encodeMessage(message: Message): ByteArray {
        if (message is Message.Transfer) return encode(message.header)
        val json = JSONObject().put("protocol", VERSION)
        when (message) {
            is Message.Transfer -> Unit
            is Message.Batch -> json.put("type", "batch").put("count", message.count)
                .put("total", message.totalBytes)
            Message.BatchEnd -> json.put("type", "batch_end")
            is Message.PairOffer -> json.put("type", "pair_offer").put("device", message.deviceId)
                .put("name", message.name).put("secret", message.secret)
            is Message.PairAck -> json.put("type", "pair_ack").put("device", message.deviceId)
                .put("name", message.name)
            Message.PairDecline -> json.put("type", "pair_decline")
            is Message.Challenge -> json.put("type", "challenge").put("device", message.deviceId)
                .put("nonce", message.nonce)
            is Message.Response -> json.put("type", "response").put("mac", message.mac)
            Message.TrustFail -> json.put("type", "trust_fail")
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    /** Throws on anything malformed, oversized, of another protocol version or of an unknown type. */
    fun decodeMessage(bytes: ByteArray): Message {
        require(bytes.size in 1..MAX_HEADER_CHARS) { "Invalid nearby transfer header" }
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.getInt("protocol") == VERSION) { "Unsupported nearby protocol" }
        if (!json.has("type")) return Message.Transfer(decode(bytes))
        return when (val type = json.getString("type")) {
            "batch" -> Message.Batch(json.getInt("count"), json.getLong("total"))
            "batch_end" -> Message.BatchEnd
            "pair_offer" -> Message.PairOffer(
                json.getString("device"), cleanDisplayName(json.getString("name")), json.getString("secret"))
            "pair_ack" -> Message.PairAck(json.getString("device"), cleanDisplayName(json.getString("name")))
            "pair_decline" -> Message.PairDecline
            "challenge" -> Message.Challenge(json.getString("device"), json.getString("nonce"))
            "response" -> Message.Response(json.getString("mac"))
            "trust_fail" -> Message.TrustFail
            else -> throw IllegalArgumentException("Unknown nearby message: $type")
        }
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
