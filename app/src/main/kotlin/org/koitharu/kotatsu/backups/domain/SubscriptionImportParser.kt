package org.koitharu.kotatsu.backups.domain

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Reads a list of followed channels from a Google Takeout `subscriptions.csv`, a NewPipe `newpipe.json`
 * (or the NewPipe export ZIP holding it) or a plain list of `youtube.com/channel/UC...` links.
 * Pure parsing: no network, no Android classes beyond org.json. Only YouTube channels with a valid
 * `UC` channel id are kept; everything else is counted so the preview can say what will be skipped.
 */
internal object SubscriptionImportParser {

	const val MAX_ENTRIES = 5000
	const val MAX_BYTES = 8L * 1024 * 1024
	private const val MAX_ZIP_ENTRIES = 200
	private const val MAX_NAME_LENGTH = 200

	/** NewPipe service ids (NewPipeExtractor ServiceList order). */
	private const val SERVICE_YOUTUBE = 0
	private const val SERVICE_SOUNDCLOUD = 1
	private const val SERVICE_PEERTUBE = 3

	private val CHANNEL_ID = Regex("""UC[A-Za-z0-9_-]{22}""")
	private val CHANNEL_URL = Regex(
		"""https?://(?:www\.|m\.)?youtube\.com/channel/(UC[A-Za-z0-9_-]{22})(?:[/?#].*)?""",
		RegexOption.IGNORE_CASE,
	)

	class Channel(val channelId: String, val name: String) {
		val url: String get() = channelUrl(channelId)
	}

	/**
	 * @param channels unique YouTube channels, at most [MAX_ENTRIES]
	 * @param soundCloud entries on SoundCloud, [peerTube] on PeerTube, [otherServices] on any other service
	 * @param invalid entries that were not a usable channel link (handles, malformed ids, non-https links)
	 * @param duplicates repeated channels that were dropped
	 * @param truncated true when channels beyond [MAX_ENTRIES] were ignored
	 */
	class Result(
		val channels: List<Channel>,
		val soundCloud: Int,
		val peerTube: Int,
		val otherServices: Int,
		val invalid: Int,
		val duplicates: Int,
		val truncated: Boolean,
	) {
		val skipped: Int get() = soundCloud + peerTube + otherServices + invalid
	}

	/** Collects channels with duplicate removal and the entry cap. */
	class Collector(private val maxEntries: Int = MAX_ENTRIES) {
		private val seen = LinkedHashMap<String, Channel>()
		private var soundCloud = 0
		private var peerTube = 0
		private var other = 0
		private var invalid = 0
		private var duplicates = 0
		private var truncated = false

		fun addChannel(channelId: String, name: String) {
			if (channelId in seen) {
				duplicates++
			} else if (seen.size >= maxEntries) {
				truncated = true
			} else {
				seen[channelId] = Channel(channelId, cleanName(name).ifEmpty { channelId })
			}
		}

		fun addServiceSkip(serviceId: Int) {
			when (serviceId) {
				SERVICE_SOUNDCLOUD -> soundCloud++
				SERVICE_PEERTUBE -> peerTube++
				else -> other++
			}
		}

		fun addInvalid() {
			invalid++
		}

		fun result() = Result(seen.values.toList(), soundCloud, peerTube, other, invalid, duplicates, truncated)
	}

	/** Reads at most [MAX_BYTES]; a larger input is rejected before it is held in memory. */
	@Throws(IOException::class)
	fun readBounded(input: InputStream, limit: Long = MAX_BYTES): ByteArray {
		val out = ByteArrayOutputStream()
		val buffer = ByteArray(32 * 1024)
		var total = 0L
		while (true) {
			val n = input.read(buffer)
			if (n < 0) break
			total += n
			if (total > limit) throw IOException("Subscription file exceeds size limit")
			out.write(buffer, 0, n)
		}
		return out.toByteArray()
	}

	/** Detects the format from the content itself, since file names and MIME types are unreliable. */
	@Throws(IOException::class)
	fun parse(bytes: ByteArray, maxEntries: Int = MAX_ENTRIES): Result {
		val text = if (isZip(bytes)) unzip(bytes) else bytes.toString(Charsets.UTF_8)
		return parseText(text, maxEntries)
	}

	fun parseText(raw: String, maxEntries: Int = MAX_ENTRIES): Result {
		val text = raw.removePrefix("﻿").trim()
		val collector = Collector(maxEntries)
		when {
			text.startsWith("{") -> parseNewPipeJson(text, collector)
			text.lineSequence().firstOrNull().orEmpty().let {
				it.contains("channel id", ignoreCase = true) || it.contains("channel url", ignoreCase = true)
			} -> parseTakeoutCsv(text, collector)
			else -> parseUrlList(text, collector)
		}
		return collector.result()
	}

	/** Canonical link of a channel, the same form NewPipe reports for a YouTube channel. */
	fun channelUrl(channelId: String) = "https://www.youtube.com/channel/$channelId"

	fun channelIdOf(url: String): String? = CHANNEL_URL.matchEntire(url.trim())?.groupValues?.get(1)

	private fun isZip(bytes: ByteArray) =
		bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
			bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

	/** First `newpipe.json` or `subscriptions.csv` in the archive, read with a size bound. */
	private fun unzip(bytes: ByteArray): String {
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			var examined = 0
			while (examined++ < MAX_ZIP_ENTRIES) {
				val entry = zip.nextEntry ?: break
				if (entry.isDirectory) continue
				val name = entry.name.lowercase(Locale.ROOT)
				if (name.endsWith("newpipe.json") || name.endsWith("subscriptions.csv")) {
					return readBounded(zip).toString(Charsets.UTF_8)
				}
			}
		}
		throw IOException("The archive has no newpipe.json or subscriptions.csv")
	}

	private fun parseNewPipeJson(text: String, collector: Collector) {
		val array = try {
			JSONObject(text).optJSONArray("subscriptions")
		} catch (e: JSONException) {
			throw IOException("Not a valid NewPipe export", e)
		} ?: throw IOException("The file has no subscriptions list")
		for (i in 0 until array.length()) {
			val item = array.optJSONObject(i)
			if (item == null) {
				collector.addInvalid()
				continue
			}
			val serviceId = item.optInt("service_id", SERVICE_YOUTUBE)
			if (serviceId != SERVICE_YOUTUBE) {
				collector.addServiceSkip(serviceId)
				continue
			}
			val id = channelIdOf(item.optString("url"))
			if (id == null) collector.addInvalid() else collector.addChannel(id, item.optString("name"))
		}
	}

	private fun parseTakeoutCsv(text: String, collector: Collector) {
		val rows = parseCsv(text)
		// Column order is fixed by Google; the header row is dropped, other rows are read by position.
		for (row in rows.drop(1)) {
			if (row.all { it.isBlank() }) continue
			val id = row.getOrNull(0)?.trim()?.takeIf { CHANNEL_ID.matches(it) }
				?: row.getOrNull(1)?.let(::channelIdOf)
			if (id == null) collector.addInvalid() else collector.addChannel(id, row.getOrNull(2).orEmpty())
		}
	}

	private fun parseUrlList(text: String, collector: Collector) {
		for (line in text.lineSequence()) {
			val value = line.trim()
			if (value.isEmpty() || value.startsWith("#")) continue
			val id = channelIdOf(value)
			if (id == null) collector.addInvalid() else collector.addChannel(id, "")
		}
	}

	/** RFC 4180 style reader: quoted fields, doubled quotes, commas and line breaks inside quotes, CRLF. */
	fun parseCsv(text: String): List<List<String>> {
		val rows = ArrayList<List<String>>()
		var row = ArrayList<String>()
		val field = StringBuilder()
		var inQuotes = false
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < text.length && text[i + 1] == '"') {
						field.append('"')
						i++
					} else {
						inQuotes = false
					}
				} else {
					field.append(c)
				}
			} else when (c) {
				'"' -> inQuotes = true
				',' -> {
					row.add(field.toString())
					field.setLength(0)
				}
				'\r' -> Unit
				'\n' -> {
					row.add(field.toString())
					field.setLength(0)
					rows.add(row)
					row = ArrayList()
				}
				else -> field.append(c)
			}
			i++
		}
		if (field.isNotEmpty() || row.isNotEmpty()) {
			row.add(field.toString())
			rows.add(row)
		}
		return rows
	}

	private fun cleanName(name: String): String =
		name.filter { !it.isISOControl() }.trim().take(MAX_NAME_LENGTH)
}
