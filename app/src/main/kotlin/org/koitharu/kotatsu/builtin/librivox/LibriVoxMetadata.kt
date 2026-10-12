package org.koitharu.kotatsu.builtin.librivox

import org.json.JSONObject

/** Keep LibriVox's real author and genre structures separate from repository IO. */
internal object LibriVoxMetadata {
    fun authors(book: JSONObject): Set<String> {
        val array = book.optJSONArray("authors") ?: return emptySet()
        return (0 until array.length()).mapNotNull { index ->
            val author = array.optJSONObject(index) ?: return@mapNotNull null
            listOf(author.optString("first_name"), author.optString("last_name"))
                .filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
        }.toSet()
    }

    fun genres(book: JSONObject): Set<String> {
        val array = book.optJSONArray("genres") ?: return emptySet()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }
        }.toSet()
    }
}
