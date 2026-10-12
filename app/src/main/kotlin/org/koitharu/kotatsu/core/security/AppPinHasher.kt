package org.koitharu.kotatsu.core.security

import org.koitharu.kotatsu.parsers.util.md5
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A salted app-lock PIN hash; supports upgrading existing Kotatsu MD5 values on unlock. */
object AppPinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val PREFIX = "pbkdf2"

    data class Verification(val matches: Boolean, val upgradedHash: String? = null)

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return "${PREFIX}$" + Base64.getEncoder().encodeToString(salt) +
            "$" + Base64.getEncoder().encodeToString(derive(pin, salt))
    }

    fun verify(pin: String, storedHash: String): Verification {
        if (storedHash.startsWith("${PREFIX}$")) {
            val fields = storedHash.split('$')
            if (fields.size != 3) return Verification(false)
            val salt: ByteArray
            val expected: ByteArray
            try {
                salt = Base64.getDecoder().decode(fields[1])
                expected = Base64.getDecoder().decode(fields[2])
            } catch (_: IllegalArgumentException) {
                return Verification(false)
            }
            if (salt.size != SALT_BYTES || expected.size != KEY_BITS / 8) return Verification(false)
            return Verification(MessageDigest.isEqual(expected, derive(pin, salt)))
        }
        if (storedHash.isBlank()) return Verification(false)
        val legacyMatch = MessageDigest.isEqual(
            pin.md5().toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8),
        )
        return if (legacyMatch) Verification(true, hash(pin)) else Verification(false)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
