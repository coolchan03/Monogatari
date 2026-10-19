package org.koitharu.kotatsu.core.util

import org.jsoup.parser.Parser
import java.nio.charset.Charset

/**
 * Cleans text that came from feeds, catalogs and the translator before it is shown or drawn.
 *
 * - HTML entities (`&#39;`, `&rsquo;`, `&amp;`), including double-escaped ones (`&amp;#39;`) that feeds and the ML Kit
 *   translator produce, become the real characters.
 * - UTF-8 text that a server mislabelled as Windows-1252/Latin-1 (`donâ€™t`, `CafÃ©`) is repaired, but only when
 *   re-decoding produces valid text without replacement characters.
 */
object DisplayText {

	private val WINDOWS_1252: Charset? = runCatching { Charset.forName("windows-1252") }.getOrNull()
	private val MOJIBAKE = Regex("[ÃÂâ][\u0080-¿ŒœŠšŸŽžƒˆ˜–-›€™]")
	private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});")

	fun clean(text: String): String = repairMojibake(decodeEntities(text))

	fun decodeEntities(text: String): String {
		var current = text
		// Two passes cover double escaping (&amp;#39; -> &#39; -> ').
		repeat(2) {
			if (!ENTITY.containsMatchIn(current)) return current
			current = Parser.unescapeEntities(current, false)
		}
		return current
	}

	fun repairMojibake(text: String): String {
		val charset = WINDOWS_1252 ?: return text
		if (!MOJIBAKE.containsMatchIn(text)) return text
		val encoder = charset.newEncoder()
		if (!encoder.canEncode(text)) return text
		val repaired = runCatching { String(text.toByteArray(charset), Charsets.UTF_8) }.getOrNull() ?: return text
		return if ('�' in repaired || repaired.length >= text.length) text else repaired
	}
}
