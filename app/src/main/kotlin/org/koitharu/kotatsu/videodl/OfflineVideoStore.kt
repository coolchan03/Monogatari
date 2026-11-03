package org.koitharu.kotatsu.videodl

import android.content.Context
import androidx.media3.common.util.UnstableApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Small index of videos and audio saved for offline playback, kept in SharedPreferences
 * "monogatari_offline_video" as JSON (no Room). The media bytes themselves live in the Media3 cache
 * owned by [OfflineVideoManager]; this index only says what they are.
 *
 * Read API for the Library: [list] and [remove].
 */
object OfflineVideoStore {

	private const val PREFS = "monogatari_offline_video"
	private const val KEY_ENTRIES = "entries"

	@Serializable
	data class Entry(
		/** Download id of the video part; the separate audio part (if any) is "[id]:a". */
		val id: String,
		val title: String,
		val sourceName: String,
		val chapterId: Long,
		val chapterUrl: String,
		/** MediaType name: VIDEO or AUDIO. */
		val mediaType: String,
		val streamUrl: String,
		val audioUrl: String? = null,
		val mimeType: String? = null,
		/** [OfflineStreamPolicy.Protocol] name. */
		val protocol: String = OfflineStreamPolicy.Protocol.PROGRESSIVE.name,
		val sizeBytes: Long = 0L,
		val createdAt: Long = 0L,
		/** False while still downloading; [list] only returns completed entries. */
		val completed: Boolean = false,
		/** What was really cached when it differs from [streamUrl] (the chosen HLS variant playlist). */
		val cacheUrl: String? = null,
		/** DASH representations that were saved, as [OfflineStreamPolicy.DashKey.encode] strings. */
		val streamKeys: List<String> = emptyList(),
		/** Request headers the source needs (kept app-private; used to resume and to re-download). */
		val headers: Map<String, String> = emptyMap(),
	)

	private val json = Json { ignoreUnknownKeys = true }
	private val serializer = ListSerializer(Entry.serializer())

	fun encode(entries: List<Entry>): String = json.encodeToString(serializer, entries)

	fun decode(raw: String?): List<Entry> {
		if (raw.isNullOrBlank()) return emptyList()
		return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
	}

	/** Stable id for a stream (and its separate audio track), so the same stream is never saved twice. */
	fun entryId(streamUrl: String, audioUrl: String?): String =
		MessageDigest.getInstance("SHA-256")
			.digest("$streamUrl|${audioUrl.orEmpty()}".toByteArray(Charsets.UTF_8))
			.take(12).joinToString("") { "%02x".format(it) }

	/** The download id of the separate audio part of [entryId]. */
	fun audioPartId(entryId: String): String = "$entryId:a"

	/** Maps a Media3 download id (video or audio part) back to its entry id. */
	fun entryIdOf(downloadId: String): String = downloadId.removeSuffix(":a")

	private fun prefs(context: Context) =
		context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun all(context: Context): List<Entry> = decode(prefs(context).getString(KEY_ENTRIES, null))

	/** Fully downloaded items, newest first. */
	fun list(context: Context): List<Entry> =
		all(context).filter { it.completed }.sortedByDescending { it.createdAt }

	/** Every entry, including the ones still downloading. */
	fun listAll(context: Context): List<Entry> = all(context)

	fun get(context: Context, id: String): Entry? = all(context).firstOrNull { it.id == id }

	/** The completed download of this exact stream, if any. */
	fun findCompleted(context: Context, streamUrl: String, audioUrl: String? = null): Entry? =
		get(context, entryId(streamUrl, audioUrl))?.takeIf { it.completed }

	/** A completed download of this chapter, whichever stream it was saved from. */
	fun findCompletedForChapter(context: Context, sourceName: String, chapterId: Long): Entry? =
		list(context).firstOrNull { it.sourceName == sourceName && it.chapterId == chapterId }

	@Synchronized
	fun put(context: Context, entry: Entry) {
		write(context, all(context).filter { it.id != entry.id } + entry)
	}

	@Synchronized
	fun update(context: Context, id: String, change: (Entry) -> Entry) {
		val current = all(context)
		if (current.none { it.id == id }) return
		write(context, current.map { if (it.id == id) change(it) else it })
	}

	/** Forgets the entry only; use [remove] to delete the downloaded data too. */
	@Synchronized
	fun forget(context: Context, id: String) {
		val current = all(context)
		if (current.any { it.id == id }) write(context, current.filter { it.id != id })
	}

	/** Deletes the downloaded data and forgets the entry. */
	@OptIn(UnstableApi::class)
	fun remove(context: Context, entry: Entry) {
		OfflineVideoManager.removeData(context, entry.id)
		forget(context, entry.id)
	}

	private fun write(context: Context, entries: List<Entry>) {
		prefs(context).edit().putString(KEY_ENTRIES, encode(entries)).apply()
	}
}
