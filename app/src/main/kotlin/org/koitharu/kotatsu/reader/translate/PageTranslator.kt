package org.koitharu.kotatsu.reader.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates the text on a manga page entirely on the device: ML Kit text recognition finds the text blocks,
 * ML Kit Translate translates them and [TranslatedPageRenderer] draws the result onto a copy of the page.
 * Nothing is sent to a server; models are downloaded once by Google Play services / ML Kit.
 */
@Singleton
class PageTranslator @Inject constructor(
	@ApplicationContext private val context: Context,
) {

	enum class Script(val isCjk: Boolean, val defaultLanguage: String?) {
		LATIN(false, null),
		JAPANESE(true, TranslateLanguage.JAPANESE),
		KOREAN(true, TranslateLanguage.KOREAN),
		CHINESE(true, TranslateLanguage.CHINESE),
	}

	class Result(
		val file: File,
		/** The page as decoded for OCR (same pixel grid as the block boxes), shown by the original/translated toggle. */
		val originalFile: File,
		/** JSON [TranslationMeta] with the recognised and translated blocks. */
		val metaFile: File,
		val blockCount: Int,
		val sourceLanguage: String,
		val targetLanguage: String,
	)

	class TranslationException(message: String) : Exception(message)

	/** The device language when ML Kit can translate into it, otherwise English. */
	val targetLanguage: String
		get() = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH

	/** Language tag to translate into: the user's per-series choice if valid, else [targetLanguage]. */
	fun resolveTarget(override: String?): String =
		override?.let { TranslateLanguage.fromLanguageTag(it) } ?: targetLanguage

	/** A page translated earlier (same page URL, script, languages), if still cached. */
	fun findCached(cacheKey: String, script: Script, sourceLanguageHint: String?, targetOverride: String?): Result? {
		val target = resolveTarget(targetOverride)
		val output = outputFile(cacheKey, script, target, sourceLanguageHint)
		return cachedResult(output, sourceLanguageHint.orEmpty(), target)
	}

	private fun cachedResult(output: File, source: String, target: String): Result? {
		val original = originalOf(output)
		val meta = metaOf(output)
		if (!output.exists() || output.length() == 0L || !original.exists() || !meta.exists()) return null
		val blocks = TranslationSidecar.decode(meta.readText())?.blocks?.size ?: return null
		return Result(output, original, meta, blocks, source, target)
	}

	/** Reads the block record of a translated page, or null if it is missing or damaged. */
	internal fun readMeta(result: Result): TranslationMeta? =
		runCatching { TranslationSidecar.decode(result.metaFile.readText()) }.getOrNull()

	/** Redraws the page from the stored original and blocks (after the user corrected some text). */
	internal suspend fun rewrite(result: Result, meta: TranslationMeta) = withContext(Dispatchers.Default) {
		val bitmap = BitmapFactory.decodeFile(result.originalFile.path)
			?: throw TranslationException(context.getString(R.string.page_translation_unreadable))
		try {
			val rendered = TranslatedPageRenderer.render(bitmap, meta)
			try {
				writeJpeg(rendered, result.file)
			} finally {
				rendered.recycle()
			}
		} finally {
			bitmap.recycle()
		}
		result.metaFile.writeText(TranslationSidecar.encode(meta))
	}

	/**
	 * @param page the page image file
	 * @param cacheKey stable identity of the page (its URL), so translating it again is instant
	 */
	suspend fun translate(
		page: File,
		cacheKey: String,
		script: Script,
		sourceLanguageHint: String?,
		targetOverride: String? = null,
		onStatus: (Int) -> Unit = {},
	): Result = withContext(Dispatchers.Default) {
		val target = resolveTarget(targetOverride)
		val output = outputFile(cacheKey, script, target, sourceLanguageHint)
		cachedResult(output, sourceLanguageHint.orEmpty(), target)?.let { return@withContext it }
		val bitmap = decode(page) ?: throw TranslationException(context.getString(R.string.page_translation_unreadable))
		try {
			onStatus(R.string.page_translation_reading)
			val blocks = recognize(bitmap, script)
			if (blocks.isEmpty()) {
				throw TranslationException(context.getString(R.string.page_translation_no_text))
			}
			val source = resolveSourceLanguage(script, sourceLanguageHint, blocks)
			if (source == target) {
				throw TranslationException(context.getString(R.string.page_translation_same_language))
			}
			val translations = translateTexts(blocks.map { it.text }, source, target, onStatus)
			val meta = TranslationMeta(
				source, target,
				blocks.mapIndexed { i, b ->
					TranslatedBlock(
						b.box.left, b.box.top, b.box.right, b.box.bottom,
						original = b.text, translation = translations[i], confidence = b.confidence,
					)
				},
			)
			val rendered = TranslatedPageRenderer.render(bitmap, meta)
			try {
				writeJpeg(bitmap, originalOf(output))
				writeJpeg(rendered, output)
				metaOf(output).writeText(TranslationSidecar.encode(meta))
			} finally {
				rendered.recycle()
			}
			Result(output, originalOf(output), metaOf(output), blocks.size, source, target)
		} finally {
			bitmap.recycle()
		}
	}

	private class OcrBlock(val box: TranslationLayout.Box, val text: String, val confidence: Float)

	private suspend fun recognize(bitmap: Bitmap, script: Script): List<OcrBlock> {
		val recognizer = recognizerFor(script)
		try {
			val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
			val pieces = result.textBlocks.mapNotNull { block ->
				val rect = block.boundingBox ?: return@mapNotNull null
				val text = TranslationLayout.joinLines(block.lines.map { it.text }, script.isCjk)
				if (!TranslationLayout.isMeaningful(text)) return@mapNotNull null
				val confidence = TranslationSidecar.averageConfidence(block.lines.map { it.confidence })
				TranslationLayout.Piece(TranslationLayout.Box(rect.left, rect.top, rect.right, rect.bottom), text, confidence)
			}
			return TranslationLayout.groupPieces(pieces, script.isCjk).map { OcrBlock(it.box, it.text, it.confidence) }
		} catch (e: com.google.mlkit.common.MlKitException) {
			throw TranslationException(context.getString(R.string.page_translation_model_unavailable))
		} finally {
			recognizer.close()
		}
	}

	private fun recognizerFor(script: Script): TextRecognizer = when (script) {
		Script.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
		Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
		Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
		Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
	}

	private suspend fun resolveSourceLanguage(script: Script, hint: String?, blocks: List<OcrBlock>): String {
		hint?.let { TranslateLanguage.fromLanguageTag(it) }?.let { return it }
		script.defaultLanguage?.let { return it }
		val sample = blocks.joinToString(" ") { it.text }.take(2000)
		val identifier = LanguageIdentification.getClient()
		try {
			val tag = identifier.identifyLanguage(sample).await()
			return TranslateLanguage.fromLanguageTag(tag)
				?: throw TranslationException(context.getString(R.string.page_translation_unknown_language))
		} finally {
			identifier.close()
		}
	}

	private suspend fun translateTexts(
		texts: List<String>,
		source: String,
		target: String,
		onStatus: (Int) -> Unit,
	): List<String> {
		val translator = Translation.getClient(
			TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
		)
		try {
			onStatus(R.string.page_translation_downloading)
			translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
			onStatus(R.string.page_translation_translating)
			val cache = HashMap<String, String>()
			return texts.map { text -> cache.getOrPut(text) { translator.translate(text).await() } }
		} catch (e: com.google.mlkit.common.MlKitException) {
			throw TranslationException(context.getString(R.string.page_translation_model_unavailable))
		} finally {
			translator.close()
		}
	}

	private fun decode(file: File): Bitmap? {
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeFile(file.path, bounds)
		val sample = sampleSize(bounds.outWidth, bounds.outHeight)
		val options = BitmapFactory.Options().apply {
			inSampleSize = sample
			inPreferredConfig = Bitmap.Config.ARGB_8888
		}
		BitmapFactory.decodeFile(file.path, options)?.let { return it }
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			// AVIF/HEIF and other formats BitmapFactory can't read on this device.
			return runCatching {
				ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
					decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
					val s = sampleSize(info.size.width, info.size.height)
					if (s > 1) decoder.setTargetSize(info.size.width / s, info.size.height / s)
				}
			}.getOrNull()
		}
		return null
	}

	private fun sampleSize(width: Int, height: Int): Int {
		if (width <= 0 || height <= 0) return 1
		var sample = 1
		while (width / sample > MAX_SIDE || height / sample > MAX_SIDE * 4 ||
			(width.toLong() / sample) * (height.toLong() / sample) > MAX_PIXELS
		) {
			sample *= 2
		}
		return sample
	}

	private fun writeJpeg(bitmap: Bitmap, file: File) {
		file.parentFile?.mkdirs()
		val temp = File(file.path + ".tmp")
		temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
		if (!temp.renameTo(file)) {
			temp.delete()
			throw TranslationException(context.getString(R.string.page_translation_unreadable))
		}
	}

	private fun originalOf(output: File) = File(output.path.removeSuffix(".jpg") + ".orig.jpg")

	private fun metaOf(output: File) = File(output.path.removeSuffix(".jpg") + ".json")

	private fun outputFile(cacheKey: String, script: Script, target: String, sourceHint: String?): File {
		val digest = MessageDigest.getInstance("SHA-256").digest("$cacheKey|$script|$target|${sourceHint.orEmpty()}".toByteArray())
		val name = digest.take(12).joinToString("") { "%02x".format(it) }
		val dir = File(context.cacheDir, CACHE_DIR)
		pruneCache(dir)
		return File(dir, "page_$name.jpg")
	}

	/** Keeps the cache of translated pages bounded. */
	private fun pruneCache(dir: File) {
		val files = dir.listFiles()?.filter { it.isFile } ?: return
		if (files.size <= MAX_CACHED_PAGES) return
		files.sortedBy { it.lastModified() }.take(files.size - MAX_CACHED_PAGES).forEach { it.delete() }
	}

	private companion object {

		const val CACHE_DIR = "translated_pages"
		const val MAX_CACHED_PAGES = 600
		const val MAX_SIDE = 2048
		const val MAX_PIXELS = 12_000_000L
	}
}

/** Suspends until a Play services [Task] completes. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
	addOnSuccessListener { result -> cont.resume(result) }
	addOnFailureListener { error -> cont.resumeWithException(error) }
	addOnCanceledListener { cont.cancel() }
}
