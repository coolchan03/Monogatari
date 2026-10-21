package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.local.ui.ImportService
import java.io.File
import java.util.Locale

/**
 * "Add files": routes the user's own files to the right part of the library.
 * Books are copied to the book shelf, comics go through the existing local manga import
 * ("On device"), videos and audio stay where they are and are remembered by [LocalMediaShelf].
 */
object LocalFileImporter {

	enum class Kind { BOOK, COMIC, VIDEO, AUDIO }

	/** Same formats the book shelf lists and the readers open. */
	val BOOK_EXTENSIONS = setOf("epub", "pdf", "txt", "text", "md", "markdown", "html", "htm", "xhtml")

	/** What the local manga importer accepts for single files (see CbzFilter.hasZipExtension). */
	val COMIC_EXTENSIONS = setOf("cbz", "zip")
	val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "m4v", "mov", "3gp")
	val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav")

	/** Book MIME types, used only when a picked file has no extension, mapped to the extension it is saved with. */
	private val BOOK_MIME_EXTENSIONS = mapOf(
		"application/epub+zip" to "epub",
		"application/pdf" to "pdf",
		"text/plain" to "txt",
		"text/markdown" to "md",
		"text/x-markdown" to "md",
		"text/html" to "html",
		"application/xhtml+xml" to "xhtml",
	)

	/** Picker filter; octet-stream keeps CBZ and Markdown files that providers do not type selectable. */
	val PICKER_MIME_TYPES = arrayOf(
		"application/epub+zip",
		"application/pdf",
		"text/*",
		"application/xhtml+xml",
		"application/zip",
		"application/x-cbz",
		"application/vnd.comicbook+zip",
		"application/octet-stream",
		"video/*",
		"audio/*",
	)

	/** Which part of the library a file belongs to, from its name and (when the name is not enough) its MIME type. */
	fun classify(name: String?, mime: String?): Kind? {
		val ext = extensionOf(name)
		val type = normalizedMime(mime)
		val major = type.substringBefore('/')
		if (ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS) {
			// Containers such as mp4, webm and ogg hold either; trust the provider's audio/video type when given.
			return when (major) {
				"video" -> Kind.VIDEO
				"audio" -> Kind.AUDIO
				else -> if (ext in VIDEO_EXTENSIONS) Kind.VIDEO else Kind.AUDIO
			}
		}
		if (ext in BOOK_EXTENSIONS) return Kind.BOOK
		if (ext in COMIC_EXTENSIONS) return Kind.COMIC
		return when {
			major == "video" -> Kind.VIDEO
			major == "audio" -> Kind.AUDIO
			ext.isEmpty() && type in BOOK_MIME_EXTENSIONS -> Kind.BOOK
			else -> null
		}
	}

	/** Name a book is saved under: adds the extension when only the MIME type identified it. */
	fun bookFileName(name: String, mime: String?): String {
		if (extensionOf(name) in BOOK_EXTENSIONS) return name
		val ext = BOOK_MIME_EXTENSIONS[normalizedMime(mime)] ?: return name
		return "$name.$ext"
	}

	/** Playback position as m:ss or h:mm:ss. */
	fun formatPosition(positionMs: Long): String {
		val total = (positionMs / 1000L).coerceAtLeast(0L)
		val hours = total / 3600
		val minutes = (total % 3600) / 60
		val seconds = total % 60
		return if (hours > 0) {
			String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
		} else {
			String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
		}
	}

	private fun extensionOf(name: String?): String =
		name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)

	private fun normalizedMime(mime: String?): String =
		mime.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)

	class BookResult(val message: String, val added: Boolean)

	/** Copies one picked or dropped book into the local shelf (shared by the Books catalog and Add files). */
	fun importBook(context: Context, uri: Uri, displayName: String? = null): BookResult {
		val contentResolver = context.contentResolver
		return try {
			val name = displayName ?: queryDisplayName(context, uri)
			if (name.substringAfterLast('.', "").lowercase() !in BOOK_EXTENSIONS) {
				return BookResult(context.getString(R.string.monogatari_catalog_import_bad_format, name), false)
			}
			val dir = File(context.filesDir, "imported-books").apply { mkdirs() }
			val safeName = name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(120)
			val dest = File.createTempFile("book-", "-$safeName", dir)
			var completed = false
			try {
				contentResolver.openInputStream(uri)?.use { input ->
					dest.outputStream().use { input.copyTo(it) }
				} ?: error(context.getString(R.string.monogatari_catalog_import_read_failed))
				require(dest.length() > 0) { "The selected book is empty" }
				completed = true
			} finally {
				// Failed or cancelled copies must never appear on the user's book shelf.
				if (!completed) dest.delete()
			}
			BookResult(context.getString(R.string.monogatari_catalog_imported, name), true)
		} catch (cancel: CancellationException) {
			throw cancel
		} catch (e: Exception) {
			BookResult(
				context.getString(R.string.monogatari_catalog_import_failed, uri.lastPathSegment.orEmpty(), e.message.orEmpty()),
				false,
			)
		}
	}

	fun queryDisplayName(context: Context, uri: Uri): String {
		context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
			if (it.moveToFirst()) return it.getString(0) ?: "book"
		}
		return "book"
	}

	class Summary(
		val books: Int,
		val comics: Int,
		val videos: Int,
		val audio: Int,
		val unsupported: List<String>,
		val failed: List<String>,
	)

	/** Imports everything that was picked; call off the main thread. */
	fun importAll(context: Context, uris: List<Uri>): Summary {
		var books = 0
		var videos = 0
		var audio = 0
		val comics = mutableListOf<Pair<Uri, String>>()
		val unsupported = mutableListOf<String>()
		val failed = mutableListOf<String>()
		for (uri in uris) {
			val (queriedName, size) = queryNameAndSize(context, uri)
			val name = queriedName ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" }
			val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
			when (val kind = classify(name, mime)) {
				Kind.BOOK -> {
					if (importBook(context, uri, bookFileName(name, mime)).added) books++ else failed.add(name)
				}
				Kind.COMIC -> comics.add(uri to name)
				Kind.VIDEO, Kind.AUDIO -> {
					val type = if (kind == Kind.VIDEO) MediaType.VIDEO else MediaType.AUDIO
					if (LocalMediaShelf.add(context, uri, name, size, type, mime)) {
						if (kind == Kind.VIDEO) videos++ else audio++
					} else {
						failed.add(name)
					}
				}
				null -> unsupported.add(name)
			}
		}
		var comicCount = 0
		if (comics.isNotEmpty()) {
			// Same path as the "On device" import dialog: keep access, then copy in the import service.
			comics.forEach { (uri, _) ->
				runCatching {
					context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
			}
			if (ImportService.start(context, comics.map { it.first })) {
				comicCount = comics.size
			} else {
				failed += comics.map { it.second }
			}
		}
		return Summary(books, comicCount, videos, audio, unsupported, failed)
	}

	fun queryNameAndSize(context: Context, uri: Uri): Pair<String?, Long> = runCatching {
		context.contentResolver.query(
			uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
		)?.use { cursor ->
			if (!cursor.moveToFirst()) return@use null
			val name = if (cursor.isNull(0)) null else cursor.getString(0)
			val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
			name to size
		}
	}.getOrNull() ?: (null to -1L)

	/** "Added 2 books, 1 comic, 3 videos; 1 unsupported (name)". */
	fun describe(context: Context, summary: Summary): String {
		val res = context.resources
		val parts = buildList {
			if (summary.books > 0) add(res.getQuantityString(R.plurals.add_files_books, summary.books, summary.books))
			if (summary.comics > 0) add(res.getQuantityString(R.plurals.add_files_comics, summary.comics, summary.comics))
			if (summary.videos > 0) add(res.getQuantityString(R.plurals.add_files_videos, summary.videos, summary.videos))
			if (summary.audio > 0) add(res.getQuantityString(R.plurals.add_files_audio, summary.audio, summary.audio))
		}
		val text = StringBuilder()
		text.append(
			if (parts.isEmpty()) context.getString(R.string.add_files_nothing_added)
			else context.getString(R.string.add_files_added, parts.joinToString(", ")),
		)
		if (summary.unsupported.isNotEmpty()) {
			text.append(context.getString(R.string.add_files_unsupported, summary.unsupported.size, names(summary.unsupported)))
		}
		if (summary.failed.isNotEmpty()) {
			text.append(context.getString(R.string.add_files_failed, summary.failed.size, names(summary.failed)))
		}
		if (summary.comics > 0) {
			text.append("\n\n").append(context.getString(R.string.add_files_comics_hint))
		}
		return text.toString()
	}

	private fun names(list: List<String>): String =
		list.take(5).joinToString(", ") + if (list.size > 5) ", …" else ""
}

/**
 * Owns the "Add files" picker for a fragment. Create it as a property (before the fragment starts),
 * then call [launch] from a button or add [menuProvider] to the screen's menu.
 */
class LocalFileImportLauncher(
	private val fragment: Fragment,
	private val onImported: () -> Unit = {},
) {

	private val picker = fragment.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		handle(uris)
	}

	fun launch(): Boolean = picker.tryLaunch(LocalFileImporter.PICKER_MIME_TYPES)

	/** Launches the picker, or explains that the device has no document picker. */
	fun launchOrToast() {
		if (!launch()) {
			fragment.context?.let {
				Toast.makeText(it, R.string.operation_not_supported, Toast.LENGTH_SHORT).show()
			}
		}
	}

	fun menuProvider(): MenuProvider = object : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menu.add(Menu.NONE, MENU_ADD_FILES, 47, R.string.add_files)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			if (menuItem.itemId != MENU_ADD_FILES) return false
			launchOrToast()
			return true
		}
	}

	private fun handle(uris: List<Uri>) {
		if (uris.isEmpty()) return
		val appContext = fragment.context?.applicationContext ?: return
		fragment.lifecycleScope.launch {
			val summary = withContext(Dispatchers.IO) { LocalFileImporter.importAll(appContext, uris) }
			val context = fragment.context ?: return@launch
			if (!fragment.isAdded) return@launch
			MaterialAlertDialogBuilder(context)
				.setTitle(R.string.add_files)
				.setMessage(LocalFileImporter.describe(context, summary))
				.setPositiveButton(android.R.string.ok, null)
				.show()
			onImported()
		}
	}

	private companion object {
		const val MENU_ADD_FILES = 7601
	}
}
