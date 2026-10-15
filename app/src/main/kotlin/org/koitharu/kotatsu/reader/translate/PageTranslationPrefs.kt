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

	/** Language the series is written in (an ML Kit tag), or null to detect it automatically. */
	fun getSourceLanguage(mangaId: Long): String? = prefs.getString("source_$mangaId", null)

	/** Language to translate into for this series, or null to use the device language. */
	fun getTargetLanguage(mangaId: Long): String? = prefs.getString("target_$mangaId", null)

	fun setLanguages(mangaId: Long, source: String?, target: String?) {
		prefs.edit {
			if (source == null) remove("source_$mangaId") else putString("source_$mangaId", source)
			if (target == null) remove("target_$mangaId") else putString("target_$mangaId", target)
		}
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
