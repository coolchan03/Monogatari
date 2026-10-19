package org.koitharu.kotatsu.core.media

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Reads paragraphs aloud one after another with Android's text-to-speech, with an optional sleep timer.
 * [onFinished] runs on the main thread after the last paragraph (used to continue into the next chapter).
 */
internal class ParagraphNarrator(context: Context, private val onFinished: () -> Unit = {}) {

	private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
	private var ready = false
	private var paragraphs = emptyList<String>()
	private var index = 0
	private var generation = 0
	private var sleepEndAt = Long.MAX_VALUE
	private val tts = TextToSpeech(context.applicationContext) { status ->
		ready = status == TextToSpeech.SUCCESS
		if (ready) engine()?.language = Locale.getDefault()
	}

	private fun engine(): TextToSpeech? = tts

	val isSpeaking: Boolean get() = tts.isSpeaking

	fun speak(text: List<String>) {
		if (!ready) return
		paragraphs = text.map { it.trim() }.filter { it.isNotEmpty() }.take(2000)
		if (paragraphs.isEmpty()) return
		index = 0
		val current = ++generation
		tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
			override fun onStart(utteranceId: String?) = Unit

			@Deprecated("Required by older API levels")
			override fun onError(utteranceId: String?) = Unit

			override fun onDone(utteranceId: String?) {
				mainHandler.post {
					if (current != generation) return@post
					index++
					when {
						SystemClock.elapsedRealtime() >= sleepEndAt -> Unit
						index < paragraphs.size -> next()
						else -> onFinished()
					}
				}
			}
		})
		next()
	}

	fun stop() {
		generation++
		tts.stop()
	}

	fun sleepAfterMinutes(minutes: Int) {
		sleepEndAt = if (minutes <= 0) Long.MAX_VALUE else SystemClock.elapsedRealtime() + minutes * 60_000L
	}

	fun shutdown() {
		stop()
		tts.shutdown()
	}

	private fun next() {
		if (SystemClock.elapsedRealtime() >= sleepEndAt) return
		tts.speak(paragraphs[index], TextToSpeech.QUEUE_FLUSH, null, "narration-$index")
	}
}
