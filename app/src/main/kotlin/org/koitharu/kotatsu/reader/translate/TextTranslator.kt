package org.koitharu.kotatsu.reader.translate

import android.content.Context
import android.util.LruCache
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device translation of short texts (titles, descriptions). Each text's language is detected on its own,
 * because a source labelled "English" often lists Korean or Japanese titles. Texts already in the target
 * language are returned unchanged.
 */
@Singleton
class TextTranslator @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	private val cache = LruCache<String, String>(512)

	val targetLanguage: String
		get() = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH

	/**
	 * @param languageHint language to assume when detection is unsure (e.g. the source's language)
	 * @return translated texts in the same order, or null if nothing needed translating
	 */
	suspend fun translate(texts: List<String>, languageHint: String?): List<String>? = withContext(Dispatchers.Default) {
		val target = targetLanguage
		val hint = languageHint?.let { TranslateLanguage.fromLanguageTag(it) }
		val languages = texts.map { text -> if (text.isBlank()) null else detect(text) ?: hint }
		if (languages.all { it == null || it == target }) {
			return@withContext null
		}
		val result = texts.toMutableList()
		for ((source, indices) in texts.indices.groupBy { languages[it] }) {
			if (source == null || source == target) continue
			val translator = Translation.getClient(
				TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
			)
			try {
				translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
				for (i in indices) {
					result[i] = translateParagraphs(texts[i], source, target) { translator.translate(it).await() }
				}
			} catch (e: MlKitException) {
				throw PageTranslator.TranslationException(context.getString(R.string.page_translation_model_unavailable))
			} finally {
				translator.close()
			}
		}
		result
	}

	/** Translates paragraph by paragraph (ML Kit works best on short inputs), keeping line breaks. */
	private suspend fun translateParagraphs(
		text: String,
		source: String,
		target: String,
		translate: suspend (String) -> String,
	): String {
		val out = ArrayList<String>()
		for (paragraph in text.split('\n')) {
			out += if (paragraph.isBlank()) {
				paragraph
			} else {
				val key = "$source>$target:$paragraph"
				cache.get(key) ?: translate(paragraph).also { cache.put(key, it) }
			}
		}
		return out.joinToString("\n")
	}

	private suspend fun detect(text: String): String? {
		val identifier = LanguageIdentification.getClient()
		return try {
			val tag = identifier.identifyLanguage(text.take(1000)).await()
			if (tag == UNDETERMINED) null else TranslateLanguage.fromLanguageTag(tag)
		} catch (e: MlKitException) {
			null
		} finally {
			identifier.close()
		}
	}

	private companion object {

		const val UNDETERMINED = "und"
	}
}
