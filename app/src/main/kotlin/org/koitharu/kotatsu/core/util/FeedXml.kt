package org.koitharu.kotatsu.core.util

import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.nio.charset.Charset
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lenient but safe RSS/Atom handling shared by the news and podcast feed parsers (pure, unit-tested).
 *
 * Real feeds often start with a BOM or blank lines, carry an RSS 0.91 DOCTYPE, use HTML-only entities such as
 * `&nbsp;` or `&rsquo;` and contain bare ampersands. Those are repaired before parsing. Entity declarations and
 * inline DTDs are still refused, and the parser runs with DTDs and external entities disabled.
 */
internal object FeedXml {

	private const val CDATA_START = "<![CDATA["
	private const val CDATA_END = "]]>"

	private val ENTITY_DECLARATION = Regex("<!\\s*ENTITY", RegexOption.IGNORE_CASE)
	/** A DOCTYPE without an internal subset, e.g. the Netscape RSS 0.91 one. */
	private val SIMPLE_DOCTYPE = Regex("<!DOCTYPE[^>\\[]*>", RegexOption.IGNORE_CASE)
	private val DOCTYPE_START = Regex("<!\\s*DOCTYPE", RegexOption.IGNORE_CASE)
	private val NAMED_ENTITY = Regex("&([A-Za-z][A-Za-z0-9]{1,31});")
	private val BARE_AMPERSAND = Regex("&(?!(?:[A-Za-z][A-Za-z0-9]{1,31}|#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6});)")
	private val XML_ENTITIES = setOf("amp", "lt", "gt", "quot", "apos")

	private val CONTENT_TYPE_CHARSET = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9._:-]+)", RegexOption.IGNORE_CASE)
	private val PROLOG_ENCODING = Regex("encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']", RegexOption.IGNORE_CASE)

	/** HTML 4 / common HTML 5 named entities that XML does not define, mapped to code points. */
	internal val HTML_ENTITIES: Map<String, Int> = buildMap {
		// Latin-1 block, U+00A0 to U+00FF, in code point order.
		listOf(
			"nbsp", "iexcl", "cent", "pound", "curren", "yen", "brvbar", "sect",
			"uml", "copy", "ordf", "laquo", "not", "shy", "reg", "macr",
			"deg", "plusmn", "sup2", "sup3", "acute", "micro", "para", "middot",
			"cedil", "sup1", "ordm", "raquo", "frac14", "frac12", "frac34", "iquest",
			"Agrave", "Aacute", "Acirc", "Atilde", "Auml", "Aring", "AElig", "Ccedil",
			"Egrave", "Eacute", "Ecirc", "Euml", "Igrave", "Iacute", "Icirc", "Iuml",
			"ETH", "Ntilde", "Ograve", "Oacute", "Ocirc", "Otilde", "Ouml", "times",
			"Oslash", "Ugrave", "Uacute", "Ucirc", "Uuml", "Yacute", "THORN", "szlig",
			"agrave", "aacute", "acirc", "atilde", "auml", "aring", "aelig", "ccedil",
			"egrave", "eacute", "ecirc", "euml", "igrave", "iacute", "icirc", "iuml",
			"eth", "ntilde", "ograve", "oacute", "ocirc", "otilde", "ouml", "divide",
			"oslash", "ugrave", "uacute", "ucirc", "uuml", "yacute", "thorn", "yuml",
		).forEachIndexed { index, name -> put(name, 0xA0 + index) }
		put("OElig", 338); put("oelig", 339); put("Scaron", 352); put("scaron", 353)
		put("Yuml", 376); put("fnof", 402); put("circ", 710); put("tilde", 732)
		put("ensp", 8194); put("emsp", 8195); put("thinsp", 8201); put("zwnj", 8204)
		put("zwj", 8205); put("lrm", 8206); put("rlm", 8207); put("hyphen", 8208)
		put("ndash", 8211); put("mdash", 8212); put("horbar", 8213)
		put("lsquo", 8216); put("rsquo", 8217); put("sbquo", 8218)
		put("ldquo", 8220); put("rdquo", 8221); put("bdquo", 8222)
		put("dagger", 8224); put("Dagger", 8225); put("bull", 8226); put("hellip", 8230)
		put("permil", 8240); put("prime", 8242); put("Prime", 8243)
		put("lsaquo", 8249); put("rsaquo", 8250); put("oline", 8254); put("frasl", 8260)
		put("euro", 8364); put("trade", 8482); put("larr", 8592); put("uarr", 8593)
		put("rarr", 8594); put("darr", 8595); put("harr", 8596); put("minus", 8722)
		put("infin", 8734); put("ne", 8800); put("le", 8804); put("ge", 8805)
		put("star", 9734); put("starf", 9733); put("hearts", 9829); put("check", 10003)
	}

	/** Decodes feed bytes: BOM, then the Content-Type charset, then the XML prolog encoding, else UTF-8. */
	fun decode(bytes: ByteArray, contentType: String?): String {
		val charset = bomCharset(bytes)
			?: contentType?.let { CONTENT_TYPE_CHARSET.find(it)?.groupValues?.get(1) }?.let(::charsetOrNull)
			?: prologCharset(bytes)
			?: Charsets.UTF_8
		return String(bytes, charset)
	}

	/**
	 * Makes a feed parseable by a strict XML parser without weakening its safety:
	 * strips a BOM and leading whitespace, removes a simple DOCTYPE, rewrites HTML named entities to numeric
	 * references and escapes bare ampersands. CDATA sections are copied unchanged.
	 * @throws IllegalArgumentException for entity declarations or inline DTDs.
	 */
	fun prepare(xml: String): String {
		val text = xml.trimStart { it == '\uFEFF' || it.isWhitespace() }
		val out = StringBuilder(text.length + 64)
		var index = 0
		while (index < text.length) {
			val start = text.indexOf(CDATA_START, index)
			if (start < 0) {
				out.append(cleanMarkup(text.substring(index)))
				break
			}
			out.append(cleanMarkup(text.substring(index, start)))
			val end = text.indexOf(CDATA_END, start + CDATA_START.length)
			val stop = if (end < 0) text.length else end + CDATA_END.length
			out.append(text, start, stop)
			index = stop
		}
		return out.toString()
	}

	/** Parses [xml] after [prepare] with DTDs, external entities and XInclude disabled. */
	fun parseDocument(xml: String): Document {
		val prepared = prepare(xml)
		val factory = DocumentBuilderFactory.newInstance().apply {
			isNamespaceAware = false
			isValidating = false
			isExpandEntityReferences = false
			runCatching { isXIncludeAware = false }
			// Android's built-in parser rejects unknown features; DOCTYPEs are already removed above.
			trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
			trySetFeature("http://xml.org/sax/features/external-general-entities", false)
			trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
			trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		}
		return factory.newDocumentBuilder().parse(InputSource(StringReader(prepared)))
	}

	/**
	 * An absolute HTTPS article address: relative links resolve against [baseUrl], `http` is upgraded to `https`,
	 * fragments are dropped. Null for credentials, other schemes or malformed input.
	 */
	fun articleUrl(raw: String?, baseUrl: String? = null): String? {
		val candidate = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } ?: return null
		val resolved = runCatching {
			val parsed = URI(candidate)
			when {
				parsed.isAbsolute -> parsed
				candidate.startsWith("//") -> URI("https:$candidate")
				baseUrl != null -> URI(baseUrl).resolve(parsed)
				else -> null
			}
		}.getOrNull() ?: return null
		val scheme = resolved.scheme?.lowercase() ?: return null
		if (scheme != "https" && scheme != "http") return null
		val text = resolved.toString().substringBefore('#')
		val secure = "https" + text.substring(scheme.length)
		val uri = runCatching { URI(secure) }.getOrNull() ?: return null
		if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
		return uri.toASCIIString().takeIf { it.length <= 2048 }
	}

	/** Epoch millis for RFC 822 (RSS) and ISO 8601 (Atom) dates, with or without fractional seconds; null if unknown. */
	fun parseDate(value: String?): Long? {
		val text = value?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() && it.length <= 90 }
			?: return null
		ISO_DATE.matchEntire(text)?.let { match ->
			val (date, time, seconds, zone) = match.destructured
			val offset = when {
				zone.isEmpty() || zone.equals("Z", ignoreCase = true) -> "+0000"
				zone.length == 3 -> zone + "00"
				else -> zone.replace(":", "")
			}
			return parseExactly("${date}T${time.ifEmpty { "00:00" }}:${seconds.ifEmpty { "00" }}$offset", "yyyy-MM-dd'T'HH:mm:ssZ")
		}
		val rfc = text.replace(DAY_NAME, "")
			.replace(Regex(" UTC?$"), " GMT")
			.replace(Regex(" Z$"), " +0000")
			.replace(Regex("(\\d{2}:\\d{2})\\.\\d+"), "$1")
		for (pattern in RFC_PATTERNS) {
			parseExactly(rfc, pattern)?.let { return it }
		}
		return null
	}

	private val ISO_DATE = Regex(
		"^(\\d{4}-\\d{2}-\\d{2})(?:[T ](\\d{2}:\\d{2})(?::(\\d{2}))?(?:[.,]\\d+)?\\s*(Z|z|[+-]\\d{2}(?::?\\d{2})?)?)?$",
	)
	private val DAY_NAME = Regex("^[A-Za-z]{2,9}\\.?,?\\s*(?=\\d)")
	private val RFC_PATTERNS = listOf(
		"d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss z", "d MMM yyyy HH:mm Z", "d MMM yyyy HH:mm z",
		"d MMM yy HH:mm:ss Z", "d MMM yy HH:mm:ss z", "d MMM yyyy HH:mm:ss", "d MMM yyyy HH:mm", "d MMM yyyy",
	)

	private fun parseExactly(text: String, pattern: String): Long? = runCatching {
		val position = ParsePosition(0)
		val format = SimpleDateFormat(pattern, Locale.US).apply {
			isLenient = false
			timeZone = TimeZone.getTimeZone("UTC")
		}
		val date = format.parse(text, position)
		if (date != null && position.index == text.length) date.time else null
	}.getOrNull()

	private fun cleanMarkup(part: String): String {
		require(!ENTITY_DECLARATION.containsMatchIn(part)) { "XML entity declarations are not allowed" }
		val withoutDoctype = SIMPLE_DOCTYPE.replace(part, "")
		require(!DOCTYPE_START.containsMatchIn(withoutDoctype)) { "Inline DTDs are not allowed" }
		val named = NAMED_ENTITY.replace(withoutDoctype) { match ->
			val name = match.groupValues[1]
			when (name) {
				in XML_ENTITIES -> match.value
				else -> HTML_ENTITIES[name]?.let { "&#$it;" } ?: "&amp;$name;"
			}
		}
		return BARE_AMPERSAND.replace(named, "&amp;")
	}

	private fun bomCharset(bytes: ByteArray): Charset? = when {
		bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
		bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE
		bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE
		else -> null
	}

	private fun prologCharset(bytes: ByteArray): Charset? {
		val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
		val prolog = head.trimStart().takeIf { it.startsWith("<?xml") }?.substringBefore("?>") ?: return null
		// A prolog readable as ASCII cannot really be UTF-16 or UTF-32.
		val name = PROLOG_ENCODING.find(prolog)?.groupValues?.get(1)?.takeUnless { it.startsWith("UTF-16", true) || it.startsWith("UTF-32", true) }
		return name?.let(::charsetOrNull)
	}

	private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

	private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
		size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

	private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
		runCatching { setFeature(name, value) }
	}
}
