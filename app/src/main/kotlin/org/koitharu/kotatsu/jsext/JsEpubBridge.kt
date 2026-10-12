package org.koitharu.kotatsu.jsext

import org.koitharu.kotatsu.core.media.epub.EpubBook
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.ArrayDeque
import java.util.zip.ZipInputStream

/**
 * Host implementation of Mangayomi's parseEpub()/parseEpubChapter() helpers.
 *
 * Upstream downloads the whole EPUB, exposes its title/author/chapter names, then returns the raw
 * XHTML for a selected chapter. Keep one parsed book cached per extension so moving between
 * chapters does not download and unzip the same file repeatedly.
 */
internal class JsEpubBridge(
	private val transport: JsHttpTransport? = null,
) {

	private var cachedUrl: String? = null
	private var cachedBook: EpubBook? = null

	suspend fun handle(name: String, args: JsonArray): String = when (name) {
		"parseEpub" -> {
			val book = load(args)
			buildJsonObject {
				put("title", book.title)
				book.author?.let { put("author", it) }
				put("chapters", JsonArray(book.chapters.map { JsonPrimitive(it.title) }))
			}.toString()
		}

		"parseEpubChapter" -> {
			val chapterTitle = args.getOrNull(3).str().orEmpty()
			load(args).chapters.firstOrNull { it.title == chapterTitle }?.content.orEmpty()
		}

		else -> throw JsExtensionException("Unknown EPUB host call: $name")
	}

	private suspend fun load(args: JsonArray): EpubBook {
		val url = args.getOrNull(1).str()?.takeIf { it.isNotBlank() }
			?: throw JsExtensionException("EPUB: missing URL")
		if (cachedUrl == url) {
			cachedBook?.let { return it }
		}
		val headers = args.getOrNull(2).stringMap().orEmpty()
		val response = requireNotNull(transport) { "EPUB network transport unavailable" }.execute(
			JsHttpRequest(
				method = "GET",
				url = url,
				headers = headers,
				body = null,
				options = null,
				binary = true,
			),
		)
		if (response.statusCode !in 200..299) {
			throw JsExtensionException("EPUB download failed: HTTP ${response.statusCode} ${response.reasonPhrase.orEmpty()}".trim())
		}
		val bytes = response.bodyBytes ?: response.body.toByteArray(Charsets.ISO_8859_1)
		if (bytes.isEmpty()) {
			throw JsExtensionException("EPUB download returned an empty file")
		}
		if (bytes.size > MAX_EPUB_BYTES) {
			throw JsExtensionException("EPUB is too large to open safely")
		}
		val book = parse(bytes)
		cachedUrl = url
		cachedBook = book
		return book
	}

	internal fun parse(bytes: ByteArray): EpubBook = try {
		EpubBook.fromBytes(bytes)
	} catch (e: IllegalArgumentException) {
		throw JsExtensionException(e.message ?: "Invalid EPUB")
	}

	private companion object {
		const val MAX_EPUB_BYTES = 128 * 1024 * 1024
	}
}
