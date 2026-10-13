package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Test

class JsCryptoTest {

	@Test
	fun decryptsOpenSslSaltedCryptoJsText() {
		// Generated with: openssl enc -aes-256-cbc -md md5 -pass pass:hunter2 -base64
		assertEquals(
			"Monogatari secret text",
			JsCrypto.decryptAesCryptoJs("U2FsdGVkX19Gkxy2Rgh3QQiZ0gumPura4x1VfyND3jyqzfcRFTSup7ez1D8+W5Pw", "hunter2"),
		)
		assertEquals(
			"Monogatari secret text",
			JsCrypto.decryptAesCryptoJs("U2FsdGVkX18BAgMEBQYHCOn2mjV/JmMczLiduyKy7bUmqB+RJPQ1ovMsRIrxOCEs", "hunter2"),
		)
	}

	@Test
	fun cryptoJsRoundTripAndWrongPassword() {
		val encrypted = JsCrypto.encryptAesCryptoJs("こんにちは BL", "pass")
		assertEquals("こんにちは BL", JsCrypto.decryptAesCryptoJs(encrypted, "pass"))
		assertEquals("", JsCrypto.decryptAesCryptoJs("not base64 at all!", "pass"))
	}

	@Test
	fun decryptsGcmWithSeparateOrAppendedTag() {
		val key = "000102030405060708090a0b0c0d0e0f"
		val iv = "cafebabefacedbaddecaf888"
		assertEquals("gcm works", JsCrypto.decryptAesGcm("7hqqlvKY82rZ", key, iv, "7251d7e8d85a16fd2a3b1d808d839a5a"))
		assertEquals("gcm works", JsCrypto.decryptAesGcm("7hqqlvKY82rZclHX6NhaFv0qOx2AjYOaWg==", key, iv, ""))
		// Wrong tag: the input comes back unchanged, like upstream.
		assertEquals("7hqqlvKY82rZ", JsCrypto.decryptAesGcm("7hqqlvKY82rZ", key, iv, "00".repeat(16)))
	}

	@Test
	fun deobfuscatesDigitPasswords() {
		val two = "[!+[]+!+[]]"
		val zero = "[+[]]"
		val one = "[+!+[]]"
		assertEquals("20.1", JsCrypto.deobfuscateJsPassword("$two+$zero+(+[])+$one"))
	}
}
