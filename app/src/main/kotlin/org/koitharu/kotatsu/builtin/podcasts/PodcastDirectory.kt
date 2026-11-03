package org.koitharu.kotatsu.builtin.podcasts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.media.MediaFeedRepository
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.sourcerepo.domain.readBoundedString
import javax.inject.Inject

/** Free Apple iTunes Search API (no key). Responses are size-bounded and fetched over HTTPS only. */
class PodcastDirectory @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {

	internal suspend fun search(term: String): List<PodcastDirectoryParser.Podcast> {
		val query = term.trim()
		if (query.isEmpty()) return emptyList()
		val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
			.addQueryParameter("media", "podcast")
			.addQueryParameter("term", query)
			.addQueryParameter("limit", "25")
			.build().toString()
		return PodcastDirectoryParser.parseResults(get(url))
	}

	internal suspend fun top(): List<PodcastDirectoryParser.Podcast> {
		val ids = PodcastDirectoryParser.parseTopIds(get("https://itunes.apple.com/us/rss/toppodcasts/limit=25/json"))
		if (ids.isEmpty()) return emptyList()
		val lookup = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
			.addQueryParameter("id", ids.joinToString(","))
			.addQueryParameter("entity", "podcast")
			.build().toString()
		return PodcastDirectoryParser.parseResults(get(lookup))
	}

	private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url)
			.header("Accept", "application/json")
			.header("User-Agent", MediaFeedRepository.FEED_USER_AGENT)
			.build()
		client.newCall(request).execute().use { response ->
			require(response.isSuccessful) { "HTTP ${response.code}" }
			response.body.readBoundedString(MAX_BYTES)
		}
	}

	private companion object {
		const val MAX_BYTES = 2L * 1024 * 1024
	}
}
