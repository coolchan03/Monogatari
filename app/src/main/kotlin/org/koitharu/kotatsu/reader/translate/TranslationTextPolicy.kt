package org.koitharu.kotatsu.reader.translate

import java.security.MessageDigest

/** Reversible display policy: never mutate stored manga titles or descriptions. */
internal object TranslationTextPolicy {
    private val latinLetter = Regex("[A-Za-z]")
    private val nonLatinLetter = Regex("[^\\p{InBasic_Latin}\\p{Punct}\\p{Digit}\\s]")

    fun preferredEnglishTitle(original: String, alternatives: Collection<String>): String =
        alternatives.firstOrNull { title ->
            title.isNotBlank() && latinLetter.containsMatchIn(title) && !nonLatinLetter.containsMatchIn(title)
        } ?: original

    /** Stable content cache key, without retaining raw sensitive text in lookup keys. */
    fun sha1Key(sourceLanguage: String, targetLanguage: String, text: String): String {
        val input = "${sourceLanguage.lowercase()}\u0000${targetLanguage.lowercase()}\u0000$text"
        return MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
