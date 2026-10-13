package org.koitharu.kotatsu.reader.translate

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers, per title, which script the user chose for page translation. */
@Singleton
class PageTranslationPrefs @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	fun getScript(mangaId: Long): PageTranslator.Script? =
		prefs.getString(key(mangaId), null)?.let { name -> PageTranslator.Script.entries.find { it.name == name } }

	fun setScript(mangaId: Long, script: PageTranslator.Script) {
		prefs.edit { putString(key(mangaId), script.name) }
	}

	private fun key(mangaId: Long) = "script_$mangaId"

	companion object {

		private const val PREFS_NAME = "page_translation"

		/** Script implied by a source language code, or null when unknown (the user is asked). */
		fun scriptForLanguage(language: String?): PageTranslator.Script? = when (language?.lowercase()) {
			null, "", "all", "multi" -> null
			"ja" -> PageTranslator.Script.JAPANESE
			"ko" -> PageTranslator.Script.KOREAN
			"zh" -> PageTranslator.Script.CHINESE
			// Cyrillic, Arabic, Thai etc. aren't covered by the Latin recognizer: ask instead of guessing.
			"ru", "uk", "be", "bg", "ar", "th", "he", "fa", "hi" -> null
			else -> PageTranslator.Script.LATIN
		}
	}
}
