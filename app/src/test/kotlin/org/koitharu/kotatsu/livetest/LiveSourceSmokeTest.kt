package org.koitharu.kotatsu.livetest

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.ParsersBundle
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.core.ParsersBundleImpl
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.network.UserAgents
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Bounded live-provider smoke test. NOT part of the deterministic suite: it does nothing unless
 * MONOGATARI_LIVE_TESTS=1 is set, because it talks to real websites and depends on the network and on the site.
 *
 * For each source it makes at most one list request, one details request and one pages request, each with a hard
 * timeout and no retries, and records only counts and failure categories (never titles or images).
 *
 * MONOGATARI_LIVE_SOURCES  comma-separated source ids (required)
 * MONOGATARI_LIVE_REPORT   file the report is written to (required)
 */
class LiveSourceSmokeTest {

	@Test
	fun smoke() = runBlocking {
		assumeTrue(System.getenv("MONOGATARI_LIVE_TESTS") == "1")
		val ids = System.getenv("MONOGATARI_LIVE_SOURCES").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
		val report = File(requireNotNull(System.getenv("MONOGATARI_LIVE_REPORT")) { "MONOGATARI_LIVE_REPORT" })
		val client = OkHttpClient.Builder()
			.callTimeout(30, TimeUnit.SECONDS)
			.followRedirects(true)
			.addInterceptor { chain ->
				val request = chain.request()
				chain.proceed(
					if (request.header("User-Agent") == null) {
						request.newBuilder().header("User-Agent", UserAgents.FIREFOX_MOBILE).build()
					} else {
						request
					},
				)
			}
			.build()
		val context = JvmLoaderContext(client)
		val lines = ArrayList<String>()
		for (id in ids) {
			val source = MangaParserSource.find(id)
			if (source == null) {
				lines += "$id\tNOT_IN_PARSERS"
				continue
			}
			lines += "$id\t" + probe(context, source)
			report.writeText(lines.joinToString("\n") + "\n")
		}
		report.writeText(lines.joinToString("\n") + "\n")
	}

	private suspend fun probe(context: MangaLoaderContext, source: MangaParserSource): String {
		val stages = StringBuilder()
		try {
			val parser = context.newParserInstance(source)
			val order = parser.availableSortOrders.let { orders ->
				listOf(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST).firstOrNull { it in orders } ?: orders.first()
			}
			val list = withTimeout(45_000) { parser.getList(0, order, MangaListFilter.EMPTY) }
			stages.append("list=${list.size}")
			if (list.isEmpty()) return stages.append(" EMPTY").toString()
			val details = withTimeout(45_000) { parser.getDetails(list.first()) }
			val chapters = details.chapters.orEmpty()
			stages.append(" chapters=${chapters.size}")
			if (chapters.isEmpty()) return stages.append(" NO_CHAPTERS").toString()
			val pages = withTimeout(45_000) { parser.getPages(chapters.first()) }
			stages.append(" pages=${pages.size}")
			return stages.append(if (pages.isEmpty()) " NO_PAGES" else " OK").toString()
		} catch (e: Throwable) {
			val msg = e.message.orEmpty().replace(Regex("\\s+"), " ").take(90)
			return stages.append(" FAIL ${e.javaClass.simpleName}: $msg").toString()
		}
	}

	private class MemoryCookieJar : CookieJar {

		private val store = ConcurrentHashMap<String, List<Cookie>>()

		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			store[url.host] = cookies
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty()
	}

	/** Minimal desktop-JVM stand-in for the app's loader context: no WebView, so verification pages cannot be solved. */
	private class JvmLoaderContext(override val httpClient: OkHttpClient) : MangaLoaderContext() {

		override val cookieJar: CookieJar = MemoryCookieJar()
		override val parsers: ParsersBundle = ParsersBundleImpl

		override suspend fun evaluateJs(script: String): String? = null

		override suspend fun evaluateJs(baseUrl: String, script: String, timeout: Long): String? = null

		override fun getConfig(source: MangaSource): MangaSourceConfig = object : MangaSourceConfig {
			override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
		}

		override fun getDefaultUserAgent(): String = UserAgents.FIREFOX_MOBILE

		override fun redrawImageResponse(response: Response, redraw: (image: Bitmap) -> Bitmap): Response =
			throw UnsupportedOperationException("images are not decoded in the live smoke test")

		override fun createBitmap(width: Int, height: Int): Bitmap =
			throw UnsupportedOperationException("images are not decoded in the live smoke test")
	}
}
