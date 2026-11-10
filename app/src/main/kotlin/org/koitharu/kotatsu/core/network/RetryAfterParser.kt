package org.koitharu.kotatsu.core.network

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Parses an HTTP `Retry-After` header into a bounded delay in milliseconds. */
object RetryAfterParser {

	/** A hostile or misconfigured server must not park a download for days. */
	val MAX_DELAY_MS: Long = TimeUnit.HOURS.toMillis(1)

	/**
	 * @param value header value: delta-seconds ("120") or an HTTP-date
	 * @return delay in ms, clamped to 0..[MAX_DELAY_MS]; 0 when absent, malformed or in the past
	 */
	fun parseDelayMillis(value: String?, nowMillis: Long = System.currentTimeMillis()): Long {
		val text = value?.trim().orEmpty()
		if (text.isEmpty()) return 0L
		val seconds = text.toLongOrNull()
		val delay = if (seconds != null) {
			if (seconds <= 0L) 0L else if (seconds > MAX_DELAY_MS / 1000) MAX_DELAY_MS else seconds * 1000
		} else {
			val target = runCatching {
				ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
			}.getOrNull() ?: return 0L
			target - nowMillis
		}
		return delay.coerceIn(0L, MAX_DELAY_MS)
	}
}
