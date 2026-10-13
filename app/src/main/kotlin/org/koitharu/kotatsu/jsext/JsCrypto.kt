package org.koitharu.kotatsu.jsext

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto helpers of Mangayomi's extension runtime (`MBridge`/`CryptoAES`/`Deobfuscator`, Apache License 2.0),
 * re-implemented on javax.crypto. Base64 goes through okio because java.util.Base64 needs API 26.
 */
internal object JsCrypto {

	private const val SALTED = "Salted__"
	private const val KEY_SIZE = 32
	private const val IV_SIZE = 16

	/** CryptoJS `AES.decrypt(text, passphrase)`: OpenSSL "Salted__" layout, key and IV from EVP_BytesToKey (MD5). */
	fun decryptAesCryptoJs(encrypted: String, passphrase: String): String = try {
		val data = encrypted.trim().decodeBase64()?.toByteArray() ?: error("Not base64")
		require(data.size > 16 && String(data, 0, 8, Charsets.ISO_8859_1) == SALTED) { "Missing salt header" }
		val salt = data.copyOfRange(8, 16)
		val (key, iv) = evpBytesToKey(passphrase.toByteArray(), salt)
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
		String(cipher.doFinal(data, 16, data.size - 16), Charsets.UTF_8)
	} catch (e: Exception) {
		""
	}

	/** CryptoJS `AES.encrypt(text, passphrase).toString()`. */
	fun encryptAesCryptoJs(plainText: String, passphrase: String): String = try {
		val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }
		val (key, iv) = evpBytesToKey(passphrase.toByteArray(), salt)
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
		val body = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
		(SALTED.toByteArray(Charsets.ISO_8859_1) + salt + body).toByteString().base64()
	} catch (e: Exception) {
		""
	}

	/**
	 * AES-GCM with a base64 ciphertext and hex key/IV/tag. An empty tag means the tag is already appended to the
	 * ciphertext. On failure the input comes back unchanged, like upstream.
	 */
	fun decryptAesGcm(encrypted: String, keyHex: String, ivHex: String, tagHex: String): String = try {
		val body = encrypted.trim().decodeBase64()?.toByteArray() ?: error("Not base64")
		val key = keyHex.trim().lowercase().decodeHex().toByteArray()
		require(key.size == 16 || key.size == 24 || key.size == 32) { "Bad key size" }
		val iv = ivHex.trim().lowercase().decodeHex().toByteArray()
		val tag = if (tagHex.isBlank()) ByteArray(0) else tagHex.trim().lowercase().decodeHex().toByteArray()
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
		String(cipher.doFinal(body + tag), Charsets.UTF_8)
	} catch (e: Exception) {
		encrypted
	}

	/** OpenSSL EVP_BytesToKey with MD5 and one iteration, as used by CryptoJS. */
	private fun evpBytesToKey(password: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
		val md5 = MessageDigest.getInstance("MD5")
		val out = ByteArray(KEY_SIZE + IV_SIZE)
		var generated = 0
		var block = ByteArray(0)
		while (generated < out.size) {
			md5.reset()
			md5.update(block)
			md5.update(password)
			md5.update(salt)
			block = md5.digest()
			val n = minOf(block.size, out.size - generated)
			System.arraycopy(block, 0, out, generated, n)
			generated += n
		}
		return out.copyOfRange(0, KEY_SIZE) to out.copyOfRange(KEY_SIZE, KEY_SIZE + IV_SIZE)
	}

	/**
	 * Decodes JSFuck-style digit passwords such as `[+!+[]+!+[]]+(...)`: each `[...]` group is a digit equal to the
	 * number of `!+[]` it contains (`+[]` alone is 0) and each `(...)` group is a dot. Port of the Aniyomi/Mangayomi
	 * `Deobfuscator.deobfuscateJsPassword`.
	 */
	fun deobfuscateJsPassword(input: String): String {
		val result = StringBuilder()
		var idx = 0
		while (idx < input.length) {
			val chr = input[idx]
			if (chr != '[' && chr != '(') {
				idx++
				continue
			}
			val closing = matchingBracket(input, idx)
			if (closing < 0) break
			if (chr == '[') {
				result.append(digitOf(input.substring(idx, closing)))
			} else {
				result.append('.')
				if (input.getOrNull(closing + 1) == '[') {
					val skip = matchingBracket(input, closing + 1)
					if (skip < 0) break
					idx = skip + 1
					continue
				}
			}
			idx = closing + 1
		}
		return result.toString()
	}

	private fun matchingBracket(input: String, opening: Int): Int {
		val open = input[opening]
		val close = if (open == '[') ']' else ')'
		var depth = 0
		for (i in opening until input.length) {
			when (input[i]) {
				open -> depth++
				close -> depth--
			}
			if (depth == 0) return i
			if (depth < 0) return -1
		}
		return -1
	}

	private fun digitOf(group: String): Char {
		val count = Regex("""!\+\[]""").findAll(group).count()
		return when {
			count == 0 && Regex("""\+\[]""").findAll(group).count() == 1 -> '0'
			count in 1..9 -> '0' + count
			else -> '-'
		}
	}
}
