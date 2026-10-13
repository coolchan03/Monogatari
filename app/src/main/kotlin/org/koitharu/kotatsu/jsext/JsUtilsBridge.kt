package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** `log`, and the crypto helpers of Mangayomi's JS runtime (eval/javascript/utils.dart). */
internal class JsUtilsBridge(
	private val logger: JsLogger,
	private val dateParser: JsDateParser = JsDateParser(),
) {

	fun handle(name: String, a: JsonArray): Any? = when (name) {
		"log" -> {
			logger.log(a.getOrNull(0).str().orEmpty())
			null
		}

		"cryptoHandler" -> cryptoHandler(
			text = a[0].str().orEmpty(),
			iv = a[1].str().orEmpty(),
			key = a[2].str().orEmpty(),
			encrypt = a[3].bool() ?: false,
		)

		"encryptAESCryptoJS" -> JsCrypto.encryptAesCryptoJs(a.arg(0), a.arg(1))
		"decryptAESCryptoJS" -> JsCrypto.decryptAesCryptoJs(a.arg(0), a.arg(1))
		"decryptAESGCM" -> JsCrypto.decryptAesGcm(a.arg(0), a.arg(1), a.arg(2), a.arg(3))
		"deobfuscateJsPassword" -> JsCrypto.deobfuscateJsPassword(a.arg(0))
		"unpackJs" -> JsUnpacker.unpack(a.arg(0)).orEmpty()
		"unpackJsAndCombine" -> JsUnpacker.unpackAndCombine(a.arg(0)).orEmpty()

		// Returned as JSON text; utils.js parses it back into an array.
		"parseDates" -> {
			val values = (a.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { it.str() ?: it.long()?.toString() }
			val dates = dateParser.parseAll(values, a.arg(1), a.arg(2))
			Json.encodeToString(JsonArray.serializer(), JsonArray(dates.map { JsonPrimitive(it) }))
		}

		// One observation at the dispatch boundary, not twice in the helper and dispatcher.
		else -> Unit
	}

	/** AES/CBC/PKCS7 with UTF-8 key and IV; on any failure the input comes back, like upstream. */
	private fun cryptoHandler(text: String, iv: String, key: String, encrypt: Boolean): String = try {
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		val keySpec = SecretKeySpec(key.toByteArray(), "AES")
		val ivSpec = IvParameterSpec(iv.toByteArray())
		if (encrypt) {
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
			cipher.doFinal(text.toByteArray()).toByteString().base64()
		} else {
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
			val bytes = text.decodeBase64()?.toByteArray() ?: error("Not base64")
			String(cipher.doFinal(bytes))
		}
	} catch (e: Exception) {
		text
	}

	private fun JsonArray.arg(index: Int): String = getOrNull(index).str().orEmpty()

	companion object {

		/** Calls that exist upstream but are not implemented here yet; they fail loudly when used. */
		val UNSUPPORTED = setOf("evaluateJavascriptViaWebview")
	}
}
