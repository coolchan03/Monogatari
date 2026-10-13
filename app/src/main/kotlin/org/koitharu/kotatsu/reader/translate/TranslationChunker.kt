package org.koitharu.kotatsu.reader.translate

/** Bounded paragraph splitting for future opt-in offline novel translation. */
internal object TranslationChunker {
    fun split(text: String, maxChars: Int = 1500): List<String> {
        require(maxChars in 32..4000)
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxChars).coerceAtMost(text.length)
            val delimiter = if (end < text.length) text.lastIndexOf(' ', end).takeIf { it > start } else null
            val boundary = delimiter ?: end
            result += text.substring(start, boundary)
            start = boundary
            if (start < text.length && text[start] == ' ') start++
        }
        return result
    }
}
