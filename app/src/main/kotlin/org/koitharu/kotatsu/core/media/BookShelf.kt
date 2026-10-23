package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import org.koitharu.kotatsu.core.media.epub.EpubBook
import java.io.File

/** Shared local ebook shelf for the Books tab and catalog screen. */
internal object BookShelf {
    /** Every way of adding a book (Add files, catalogs, search, nearby sync) stores it in this folder. */
    const val DIR_NAME = "imported-books"

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun list(context: Context, filter: String = ""): List<File> =
        dir(context).listFiles().orEmpty()
            .filter { isShelfFile(it.name) && it.isFile &&
                title(it).contains(filter.trim(), ignoreCase = true) }
            .sortedBy { it.name.lowercase() }

    /** Readable book formats; download leftovers (.partial) and other files are not books. */
    fun isShelfFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in LocalFileImporter.BOOK_EXTENSIONS

    /**
     * Older versions cut long imported names before their extension, so those books never showed up.
     * Gives such files their extension back (from the file's first bytes); call off the main thread.
     */
    fun recoverUnlisted(context: Context): Int {
        var recovered = 0
        for (file in dir(context).listFiles().orEmpty()) {
            if (!file.isFile || isShelfFile(file.name) || file.name.endsWith(".partial", ignoreCase = true)) continue
            val header = runCatching {
                file.inputStream().use { input ->
                    val buffer = ByteArray(HEADER_BYTES)
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    buffer.copyOf(read)
                }
            }.getOrNull() ?: continue
            val ext = sniffExtension(header) ?: continue
            val target = File(file.parentFile, repairedName(file.name, ext))
            if (!target.exists() && file.renameTo(target)) recovered++
        }
        return recovered
    }

    /** Book format from a file's first bytes: EPUB (zip with the EPUB mimetype first) or PDF. */
    fun sniffExtension(header: ByteArray): String? {
        if (header.size >= 4 && header[0] == '%'.code.toByte() && header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() && header[3] == 'F'.code.toByte()) return "pdf"
        val isZip = header.size >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2].toInt() == 3 && header[3].toInt() == 4
        if (isZip && String(header, Charsets.ISO_8859_1).contains("application/epub+zip")) return "epub"
        return null
    }

    /** "book-1-Long title.ep" + "epub" -> "book-1-Long title.epub": drops a cut-off piece of the extension. */
    fun repairedName(name: String, ext: String): String {
        val tail = name.substringAfterLast('.', "")
        val stem = if (tail.isNotEmpty() && ext.startsWith(tail.lowercase())) name.substringBeforeLast('.') else name
        return "$stem.$ext"
    }

    private const val NOT_STARTED = "Not started"
    private const val HEADER_BYTES = 96
    private const val COVER_DIR = "library-covers"
    private const val MAX_COVER_SOURCE = 64L * 1024 * 1024

    private fun coverFile(context: Context, file: File) =
        File(File(context.cacheDir, COVER_DIR), LibraryShelf.coverCacheName(file) + ".img")

    private fun noCoverMarker(context: Context, file: File) =
        File(File(context.cacheDir, COVER_DIR), LibraryShelf.coverCacheName(file) + ".none")

    /** The EPUB's own cover if it was already extracted; never reads the book itself. */
    fun cachedCover(context: Context, file: File): File? =
        coverFile(context, file).takeIf { it.isFile && it.length() > 0L }

    /** Whether [extractCover] still has to look inside this book. */
    fun needsCoverLookup(context: Context, file: File): Boolean =
        file.extension.equals("epub", ignoreCase = true) && cachedCover(context, file) == null &&
            !noCoverMarker(context, file).exists() && file.length() in 1L..MAX_COVER_SOURCE

    /** Extracts and caches the EPUB cover once (also remembering books without one); call off the main thread. */
    fun extractCover(context: Context, file: File): File? {
        cachedCover(context, file)?.let { return it }
        if (!needsCoverLookup(context, file)) return null
        val target = coverFile(context, file)
        val bytes = runCatching { EpubBook.open(file).coverBytes }.getOrNull()
        return runCatching {
            target.parentFile?.mkdirs()
            if (bytes == null || bytes.isEmpty()) {
                noCoverMarker(context, file).createNewFile()
                null
            } else {
                target.writeBytes(bytes)
                target
            }
        }.getOrNull()
    }

    /** Display the original filename, not the temporary import prefix or file extension. */
    fun title(file: File): String = file.name
        .replaceFirst(Regex("^book-[A-Za-z0-9]+-"), "")
        .substringBeforeLast('.', file.name)

    fun readerIntent(context: Context, file: File): Intent {
        val reader = when (file.extension.lowercase()) {
            "epub" -> LocalEpubReaderActivity::class.java
            "pdf" -> LocalPdfReaderActivity::class.java
            else -> LocalTextReaderActivity::class.java
        }
        return Intent(context, reader).putExtra("local_book_file", file.absolutePath)
    }

    /** Whether the reader has saved a position in this book. */
    fun hasProgress(context: Context, file: File): Boolean = progress(context, file) != NOT_STARTED

    fun progress(context: Context, file: File): String = when (file.extension.lowercase()) {
        "epub" -> {
            val chapter = context.getSharedPreferences("epub_progress", Context.MODE_PRIVATE)
                .getInt(file.name, -1)
            if (chapter >= 0) "Chapter ${chapter + 1}" else NOT_STARTED
        }
        "pdf" -> {
            val page = context.getSharedPreferences("pdf_progress", Context.MODE_PRIVATE)
                .getInt(file.name, -1)
            if (page >= 0) "Page ${page + 1}" else NOT_STARTED
        }
        else -> {
            val saved = context.getSharedPreferences("book_scroll", Context.MODE_PRIVATE)
                .contains(file.name)
            if (saved) "Continue reading" else NOT_STARTED
        }
    }
}
