package org.koitharu.kotatsu.core.media

/** Official catalog ebook-access codes are advisory; they do not guarantee an available loan. */
internal object BookAccessMetadata {
    fun openLibraryAccess(code: String?): String? = when (code?.lowercase()) {
        "public" -> "Public online text"
        "borrowable" -> "Borrowing may be required"
        "printdisabled" -> "Restricted accessible edition"
        "no_ebook" -> "No digital copy listed"
        else -> null
    }
}
