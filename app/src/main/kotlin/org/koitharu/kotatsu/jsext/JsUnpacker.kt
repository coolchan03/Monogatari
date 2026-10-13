package org.koitharu.kotatsu.jsext

/**
 * Unpacker for Dean Edwards' `eval(function(p,a,c,k,e,d){...})` packer, used by many video hosts.
 * Mirrors what Mangayomi's `unpackJs` (js_packer) and `unpackJsAndCombine` (JsUnpacker) return.
 */
internal object JsUnpacker {

	private val PACKED_ARGS = Regex(
		"""}\s*\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+|\[])\s*,\s*(\d+)\s*,\s*'((?:[^'\\]|\\.)*)'\s*\.split\(\s*'\|'\s*\)""",
		RegexOption.DOT_MATCHES_ALL,
	)
	private val WORD = Regex("""\b\w+\b""")
	private const val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	private const val ALPHABET_95 =
		" !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"

	/** Unpacks the first packed block in [packed], or returns `null` when there is none. */
	fun unpack(packed: String): String? = PACKED_ARGS.find(packed)?.let { unpackMatch(it) }

	/** Unpacks every packed block in [script] and joins the results; `null` when there is nothing to unpack. */
	fun unpackAndCombine(script: String): String? {
		val parts = PACKED_ARGS.findAll(script).mapNotNull { unpackMatch(it) }.toList()
		return if (parts.isEmpty()) null else parts.joinToString(" ")
	}

	private fun unpackMatch(match: MatchResult): String? {
		val payload = match.groupValues[1].unescape()
		val radix = match.groupValues[2].toIntOrNull() ?: 62
		if (match.groupValues[3].toIntOrNull() == null) return null
		val symbols = match.groupValues[4].unescape().split('|')
		return WORD.replace(payload) { word ->
			val index = unbase(word.value, radix)
			symbols.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: word.value
		}
	}

	private fun unbase(word: String, radix: Int): Int {
		if (radix <= 36) return word.toIntOrNull(radix) ?: -1
		val alphabet = if (radix > 62) ALPHABET_95 else ALPHABET_62
		var value = 0
		for (c in word) {
			val digit = alphabet.indexOf(c)
			if (digit < 0 || digit >= radix) return -1
			value = value * radix + digit
		}
		return value
	}

	private fun String.unescape(): String = replace("\\\\", "\u0000").replace("\\'", "'").replace("\u0000", "\\")
}
