package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import java.io.File

/** Shared local ebook shelf for the Books tab and catalog screen. */
internal object BookShelf {
    fun list(context: Context, filter: String = ""): List<File> =
        File(context.filesDir, "imported-books").listFiles().orEmpty()
            .filter { it.isFile && it.name.contains(filter.trim(), ignoreCase = true) }
            .sortedBy { it.name.lowercase() }

    fun title(file: File): String = file.name.substringAfter('-', file.name)

    fun readerIntent(context: Context, file: File): Intent {
        val reader = when (file.extension.lowercase()) {
            "epub" -> LocalEpubReaderActivity::class.java
            "pdf" -> LocalPdfReaderActivity::class.java
            else -> LocalTextReaderActivity::class.java
        }
        return Intent(context, reader).putExtra("local_book_file", file.absolutePath)
    }
    fun progress(context: Context, file: File): String = when (file.extension.lowercase()) {
        "epub" -> {
            val chapter = context.getSharedPreferences("epub_progress", Context.MODE_PRIVATE)
                .getInt(file.name, -1)
            if (chapter >= 0) "Chapter ${chapter + 1}" else "Not started"
        }
        "pdf" -> {
            val page = context.getSharedPreferences("pdf_progress", Context.MODE_PRIVATE)
                .getInt(file.name, -1)
            if (page >= 0) "Page ${page + 1}" else "Not started"
        }
        else -> {
            val saved = context.getSharedPreferences("book_scroll", Context.MODE_PRIVATE)
                .contains(file.name)
            if (saved) "Continue reading" else "Not started"
        }
    }
}
