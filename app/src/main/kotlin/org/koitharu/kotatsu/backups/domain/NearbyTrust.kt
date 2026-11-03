package org.koitharu.kotatsu.backups.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Remembered ("trusted") Nearby devices. After a first pairing where both people confirmed the digits, the two devices
 * keep a random shared secret. Later connections skip the digit dialog but must pass a mutual challenge-response
 * ([NearbyTrustHandshake]) before anything is transferred. The device name Nearby shows can be spoofed, so it only
 * selects which secret to test; it never grants trust on its own.
 */
internal object NearbyTrust {
    const val SECRET_BYTES = 32
    const val NONCE_BYTES = 16
    const val MAC_BYTES = 32
    const val MAX_DEVICES = 32
    const val ROLE_INITIATOR = "A"
    const val ROLE_RESPONDER = "B"

    data class Device(
        val id: String,
        val name: String,
        /** The Nearby endpoint name this device advertised last time; used only to pick a secret to test. */
        val endpointName: String,
        /** Lower-case hex of [SECRET_BYTES] random bytes. */
        val secret: String,
        val pairedAt: Long,
        val lastSeen: Long,
    ) {
        override fun toString() = "Device(id=$id, name=$name)" // never print the secret
    }

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun newDeviceId(): String = UUID.randomUUID().toString()

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex" }
        return ByteArray(hex.length / 2) { index ->
            val high = Character.digit(hex[index * 2], 16)
            val low = Character.digit(hex[index * 2 + 1], 16)
            require(high >= 0 && low >= 0) { "Invalid hex" }
            ((high shl 4) or low).toByte()
        }
    }

    /** HMAC-SHA256(secret, role || nonceA || nonceB); nonceA belongs to the side that asked for the connection. */
    fun proof(secret: ByteArray, role: String, nonceA: ByteArray, nonceB: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(role.toByteArray(Charsets.UTF_8))
        mac.update(nonceA)
        mac.update(nonceB)
        return mac.doFinal()
    }

    /** Constant-time comparison. */
    fun verify(expected: ByteArray, actual: ByteArray): Boolean = MessageDigest.isEqual(expected, actual)

    // ---- list operations (pure) -------------------------------------------------------------------------------

    /** Adds or replaces the device with the same id; newest-seen first, bounded to [MAX_DEVICES]. */
    fun merge(list: List<Device>, device: Device): List<Device> =
        (list.filterNot { it.id == device.id } + device).sortedByDescending { it.lastSeen }.take(MAX_DEVICES)

    fun forget(list: List<Device>, id: String): List<Device> = list.filterNot { it.id == id }

    fun touch(list: List<Device>, id: String, endpointName: String, now: Long): List<Device> =
        list.map { if (it.id == id) it.copy(endpointName = endpointName, lastSeen = now) else it }

    /** The one remembered device that advertised [endpointName]; two or none means "ask for the digits". */
    fun byEndpointName(list: List<Device>, endpointName: String): Device? =
        list.filter { it.endpointName == endpointName }.singleOrNull()

    fun encode(list: List<Device>): String {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("endpoint", it.endpointName)
                    .put("secret", it.secret).put("paired", it.pairedAt).put("seen", it.lastSeen),
            )
        }
        return array.toString()
    }

    /** Lenient: damaged entries are dropped instead of failing the whole list. */
    fun decode(raw: String?): List<Device> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = ArrayList<Device>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val secret = item.optString("secret")
            val id = item.optString("id")
            if (id.isBlank() || secret.length != SECRET_BYTES * 2 || runCatching { fromHex(secret) }.isFailure) continue
            result += Device(
                id = id,
                name = item.optString("name").ifBlank { "Unknown device" },
                endpointName = item.optString("endpoint"),
                secret = secret,
                pairedAt = item.optLong("paired"),
                lastSeen = item.optLong("seen"),
            )
        }
        return result.take(MAX_DEVICES)
    }
}

/**
 * The trust file: this install's random device id, the remembered devices and their secrets, and the folder chosen
 * for received files. It is app-private and excluded from backups (see backup_rules.xml and backup_content.xml).
 */
internal class NearbyTrustStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun localId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = NearbyTrust.newDeviceId()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    @Synchronized
    fun devices(): List<NearbyTrust.Device> = NearbyTrust.decode(prefs.getString(KEY_DEVICES, null))

    @Synchronized
    fun update(change: (List<NearbyTrust.Device>) -> List<NearbyTrust.Device>): List<NearbyTrust.Device> {
        val updated = change(devices())
        prefs.edit().putString(KEY_DEVICES, NearbyTrust.encode(updated)).apply()
        return updated
    }

    fun saveFolder(): String? = prefs.getString(KEY_SAVE_FOLDER, null)

    fun setSaveFolder(uri: String?) {
        prefs.edit().apply { if (uri == null) remove(KEY_SAVE_FOLDER) else putString(KEY_SAVE_FOLDER, uri) }.apply()
    }

    companion object {
        const val NAME = "monogatari_nearby_trust"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICES = "devices"
        private const val KEY_SAVE_FOLDER = "save_folder"
    }
}

/**
 * One challenge-response with a remembered device. Both sides send a fresh nonce, then prove knowledge of the secret
 * with HMAC-SHA256(secret, role || nonceA || nonceB), where role is "A" for the side that requested the connection and
 * "B" for the side that advertised, and nonceA / nonceB are those sides' nonces. The role makes a reflected proof
 * useless. Only [State.VERIFIED] unlocks transfers; any malformed, repeated or out-of-order message fails the session.
 */
internal class NearbyTrustHandshake(
    private val secret: ByteArray,
    private val localId: String,
    private val peerId: String,
    private val initiator: Boolean,
    private val localNonce: ByteArray = NearbyTrust.randomBytes(NearbyTrust.NONCE_BYTES),
) {
    enum class State { PENDING, VERIFIED, FAILED }

    var state = State.PENDING
        private set
    private var peerNonce: ByteArray? = null
    private var peerProof: ByteArray? = null

    fun hello(): NearbySyncProtocol.Message = NearbySyncProtocol.Message.Challenge(localId, NearbyTrust.toHex(localNonce))

    /** Feeds a message from the peer; returns what to send back. Check [state] afterwards. */
    fun receive(message: NearbySyncProtocol.Message): List<NearbySyncProtocol.Message> {
        if (state != State.PENDING) return emptyList()
        val out = ArrayList<NearbySyncProtocol.Message>()
        when (message) {
            is NearbySyncProtocol.Message.Challenge -> {
                if (peerNonce != null || message.deviceId != peerId) return fail()
                val nonce = NearbyTrust.fromHex(message.nonce)
                peerNonce = nonce
                out += NearbySyncProtocol.Message.Response(NearbyTrust.toHex(proof(localRole(), nonce)))
            }
            is NearbySyncProtocol.Message.Response -> {
                if (peerProof != null) return fail()
                peerProof = NearbyTrust.fromHex(message.mac)
            }
            else -> return fail()
        }
        val nonce = peerNonce
        val actual = peerProof
        if (nonce != null && actual != null) {
            state = if (NearbyTrust.verify(proof(peerRole(), nonce), actual)) State.VERIFIED else State.FAILED
        }
        return out
    }

    fun fail(): List<NearbySyncProtocol.Message> {
        state = State.FAILED
        return emptyList()
    }

    private fun localRole() = if (initiator) NearbyTrust.ROLE_INITIATOR else NearbyTrust.ROLE_RESPONDER

    private fun peerRole() = if (initiator) NearbyTrust.ROLE_RESPONDER else NearbyTrust.ROLE_INITIATOR

    /** The proof for [role]; [other] is the other side's nonce. */
    private fun proof(role: String, other: ByteArray): ByteArray {
        val a = if (initiator) localNonce else other
        val b = if (initiator) other else localNonce
        return NearbyTrust.proof(secret, role, a, b)
    }
}
