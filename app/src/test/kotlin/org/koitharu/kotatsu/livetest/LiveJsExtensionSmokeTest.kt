package org.koitharu.kotatsu.livetest

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsExtension
import org.koitharu.kotatsu.jsext.JsHttpRequest
import org.koitharu.kotatsu.jsext.JsHttpResponse
import org.koitharu.kotatsu.jsext.JsHttpTransport
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.JsPreferenceStore
import org.koitharu.kotatsu.jsext.JsSourceInfo
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bounded live smoke test for Mangayomi JavaScript extensions, run inside the app's real QuickJS host. NOT part of the
 * deterministic suite: it does nothing unless MONOGATARI_LIVE_TESTS=1.
 *
 * MONOGATARI_LIVE_JS      comma-separated "<index url>|<source id>" pairs
 * MONOGATARI_LIVE_REPORT  file the report is written to
 *
 * One popular-list request, one details request and one pages request per extension, hard timeouts, no retries; the
 * report holds counts and failure categories only.
 */
class LiveJsExtensionSmokeTest {

	private val client = OkHttpClient.Builder()
		.callTimeout(40, TimeUnit.SECONDS)
		.followRedirects(true)
		.build()

	private val store = object : JsPreferenceStore {
		private val data = HashMap<String, String>()
		override fun getString(key: String) = data[key]
		override fun setString(key: String, value: String) {
			data[key] = value
		}
	}

	private val trace = ArrayList<String>()

	private val transport = JsHttpTransport { r ->
		val builder = Request.Builder().url(r.url)
		r.headers.forEach { (k, v) -> builder.header(k, v) }
		if (r.headers.keys.none { it.equals("User-Agent", true) }) {
			builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
		}
		val body = r.body?.toRequestBody(r.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.toMediaTypeOrNull())
		builder.method(r.method.uppercase(), body ?: if (r.method.uppercase() in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody(null) else null)
		client.newCall(builder.build()).execute().use { res ->
			val bytes = res.body.bytes()
			trace += "${r.method} ${res.request.url.host}${res.request.url.encodedPath.take(40)} -> ${res.code} ${bytes.size}B"
			JsHttpResponse(
				statusCode = res.code,
				reasonPhrase = res.message,
				headers = res.headers.toMultimap().mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(", ") },
				body = if (r.binary) "" else String(bytes, Charsets.UTF_8),
				finalUrl = res.request.url.toString(),
				bodyBytes = bytes,
			)
		}
	}

	@Test
	fun smoke() = runBlocking {
		assumeTrue(System.getenv("MONOGATARI_LIVE_TESTS") == "1")
		val pairs = System.getenv("MONOGATARI_LIVE_JS").orEmpty().split(',').filter { it.contains('|') }
		val report = File(requireNotNull(System.getenv("MONOGATARI_LIVE_REPORT")))
		val lines = ArrayList<String>()
		for (pair in pairs) {
			val (indexUrl, id) = pair.split('|', limit = 2)
			lines += "$id\t" + probe(indexUrl, id.toLong())
			report.writeText(lines.joinToString("\n") + "\n")
		}
		report.writeText(lines.joinToString("\n") + "\n")
	}

	private fun fetch(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

	private suspend fun probe(indexUrl: String, id: Long): String {
		val stages = StringBuilder()
		try {
			val entry: JsonObject = Json.parseToJsonElement(fetch(indexUrl)).jsonArray
				.map { it.jsonObject }.first { it["id"]?.jsonPrimitive?.long == id }
			fun s(key: String) = entry[key]?.jsonPrimitive?.contentOrNull.orEmpty()
			val info = JsSourceInfo(
				id = id, name = s("name"), baseUrl = s("baseUrl"), lang = s("lang"), apiUrl = s("apiUrl"),
				iconUrl = s("iconUrl"), dateFormat = s("dateFormat"), dateFormatLocale = s("dateFormatLocale"),
				additionalParams = s("additionalParams"), version = s("version"),
				itemType = JsItemType.fromCode(entry["itemType"]?.jsonPrimitive?.int),
			)
			stages.append("${info.name}/v${info.version} ")
			val code = fetch(s("sourceCodeUrl"))
			val logs = ArrayList<String>()
			trace.clear()
			JsExtension(info, code, transport, store) { logs += it.take(120) }.use { ext ->
				val popular = withTimeout(60_000) { ext.getPopular(1) }
				stages.append("list=${popular.list.size}")
				// Popular lists often start with titles that have no readable chapters (licensed or removed), so the
				// latest-updates list is tried too. At most 5 + 8 detail requests in total.
				var detail: org.koitharu.kotatsu.jsext.JsManga? = null
				var tried = 0
				val latestResult = runCatching { withTimeout(60_000) { ext.getLatestUpdates(1) } }
				val latest = latestResult.getOrNull()
				val searched = runCatching { withTimeout(60_000) { ext.search("the", 1) } }.getOrNull()
				val candidates = popular.list.filter { !it.link.isNullOrEmpty() }.take(5) +
					latest?.list.orEmpty().filter { !it.link.isNullOrEmpty() }.take(6) +
					searched?.list.orEmpty().filter { !it.link.isNullOrEmpty() }.take(6)
				stages.append(" latest=${latest?.list?.size ?: -1} search=${searched?.list?.size ?: -1}")
				latestResult.exceptionOrNull()?.let { stages.append(" latestErr=${it.javaClass.simpleName}:${it.message.orEmpty().replace(Regex("\\s+"), " ").take(70)}") }
				for (item in candidates) {
					tried++
					val d = withTimeout(60_000) { ext.getDetail(item.link!!) }
					if (d.chapters.isNotEmpty()) {
						detail = d
						break
					}
				}
				if (detail == null) {
					return stages.append(" NO_CHAPTERS_IN_FIRST_$tried requests=${trace.takeLast(4)} logs=${logs.take(3)}").toString()
				}
				stages.append(" detailsTried=$tried chapters=${detail.chapters.size}")
				val chapter = detail.chapters.first()
				val pages = withTimeout(60_000) { ext.getPageList(chapter.url) }
				stages.append(" pages=${pages.size}")
				return stages.append(if (pages.isEmpty()) " NO_PAGES" else " OK").toString()
			}
		} catch (e: Throwable) {
			return stages.append(" FAIL ${e.javaClass.simpleName}: ${e.message.orEmpty().replace(Regex("\\s+"), " ").take(100)}").toString()
		}
	}
}
