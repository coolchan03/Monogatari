package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koitharu.kotatsu.R

/**
 * The user's own videos and audio files. They are never copied (they can be huge): the app keeps
 * read access to the picked document and remembers it here, in SharedPreferences as JSON.
 */
internal object LocalMediaShelf {

	private const val PREFS = "monogatari_local_media"
	private const val KEY_ENTRIES = "entries"

	@Serializable
	data class Entry(
		val uri: String,
		val name: String,
		val size: Long = -1L,
		/** [MediaType.VIDEO] or [MediaType.AUDIO] name. */
		val kind: String,
		val mime: String? = null,
		val addedAt: Long = 0L,
	) {
		val mediaType: MediaType
			get() = if (kind == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO
	}

	private val json = Json { ignoreUnknownKeys = true }
	private val serializer = ListSerializer(Entry.serializer())

	fun encode(entries: List<Entry>): String = json.encodeToString(serializer, entries)

	fun decode(raw: String?): List<Entry> {
		if (raw.isNullOrBlank()) return emptyList()
		return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
	}

	private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	fun list(context: Context): List<Entry> = decode(prefs(context).getString(KEY_ENTRIES, null))

	/** Keeps read access to [uri] and remembers it; false when the provider does not allow lasting access. */
	@Synchronized
	fun add(context: Context, uri: Uri, name: String, size: Long, kind: MediaType, mime: String?): Boolean {
		try {
			context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
		} catch (e: SecurityException) {
			return false
		}
		val entry = Entry(
			uri = uri.toString(),
			name = name,
			size = size,
			kind = kind.name,
			// Only a real audio/video type helps the player; generic types are left for it to sniff.
			mime = mime?.takeIf { it.startsWith("video/") || it.startsWith("audio/") },
			addedAt = System.currentTimeMillis(),
		)
		// Picking the same file again refreshes its entry instead of listing it twice.
		val updated = list(context).filterNot { it.uri == entry.uri } + entry
		prefs(context).edit().putString(KEY_ENTRIES, encode(updated)).apply()
		return true
	}

	/** Forgets the file (the file itself is not touched) and gives back the read access. */
	@Synchronized
	fun remove(context: Context, uri: String) {
		val updated = list(context).filterNot { it.uri == uri }
		prefs(context).edit().putString(KEY_ENTRIES, encode(updated)).apply()
		runCatching {
			context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
	}

	/**
	 * Whether the file can still be opened. Only asks the provider for its name, so cloud files
	 * are not downloaded just to show the library.
	 */
	fun isReadable(context: Context, entry: Entry): Boolean = runCatching {
		val uri = Uri.parse(entry.uri)
		val resolver = context.contentResolver
		val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
		granted && resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
			?.use { it.moveToFirst() } == true
	}.getOrDefault(false)

	fun title(entry: Entry): String = entry.name.substringBeforeLast('.', entry.name).ifBlank { entry.name }

	fun detail(context: Context, entry: Entry, readable: Boolean): String {
		if (!readable) return context.getString(R.string.add_files_missing)
		val kind = context.getString(
			if (entry.mediaType == MediaType.AUDIO) R.string.add_files_local_audio else R.string.add_files_local_video,
		)
		val parts = mutableListOf(kind)
		if (entry.size > 0) parts += Formatter.formatShortFileSize(context, entry.size)
		val position = AudioResumeStore.read(context, entry.uri)
		parts += if (position > 0L) {
			context.getString(R.string.add_files_resume_at, LocalFileImporter.formatPosition(position))
		} else {
			context.getString(R.string.add_files_not_started)
		}
		return parts.joinToString(" · ")
	}

	/** Plays in the in-app player, which saves the resume position by uri in [AudioResumeStore]. */
	fun playerIntent(context: Context, entry: Entry): Intent =
		Intent(context, MediaFeedPlayerActivity::class.java)
			.putExtra(MediaFeedPlayerActivity.EXTRA_LOCAL_URI, entry.uri)
			.putExtra(MediaFeedPlayerActivity.EXTRA_TITLE, title(entry))
			.putExtra(MediaFeedPlayerActivity.EXTRA_MIME_TYPE, entry.mime)
			.putExtra(
				MediaFeedPlayerActivity.EXTRA_KIND,
				if (entry.mediaType == MediaType.AUDIO) MediaFeedParser.Kind.AUDIO.name else MediaFeedParser.Kind.VIDEO.name,
			)
}
