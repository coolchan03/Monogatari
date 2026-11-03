package org.koitharu.kotatsu.builtin.podcasts

import org.json.JSONObject

/** Pure JSON parsing for the Apple iTunes podcast directory (search, lookup and top charts). */
internal object PodcastDirectoryParser {
	data class Podcast(
		val title: String,
		val author: String,
		val feedUrl: String,
		val artworkUrl: String?,
		val genre: String?,
	)

	/** Search and lookup share one shape: {"results": [{collectionName, artistName, feedUrl, ...}]}. */
	fun parseResults(json: String): List<Podcast> {
		val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
		val seen = HashSet<String>()
		val out = ArrayList<Podcast>()
		for (index in 0 until results.length()) {
			val item = results.optJSONObject(index) ?: continue
			val feed = item.optString("feedUrl").trim()
			// Only secure feeds can be subscribed; Apple still lists some plain-http ones.
			if (!feed.startsWith("https://") || !seen.add(feed)) continue
			val title = item.optString("collectionName").ifBlank { item.optString("trackName") }.trim()
			if (title.isEmpty()) continue
			out += Podcast(
				title = title,
				author = item.optString("artistName").trim(),
				feedUrl = feed,
				artworkUrl = (item.optString("artworkUrl600").ifBlank { item.optString("artworkUrl100") })
					.takeIf { it.startsWith("https://") },
				genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() },
			)
		}
		return out
	}

	/** Collection ids from the top-charts feed; their feeds come from a follow-up lookup call. */
	fun parseTopIds(json: String): List<String> {
		val entries = JSONObject(json).optJSONObject("feed")?.optJSONArray("entry") ?: return emptyList()
		val ids = ArrayList<String>()
		for (index in 0 until entries.length()) {
			val id = entries.optJSONObject(index)?.optJSONObject("id")?.optJSONObject("attributes")
				?.optString("im:id").orEmpty()
			if (id.isNotEmpty() && id.all(Char::isDigit)) ids += id
		}
		return ids
	}
}
