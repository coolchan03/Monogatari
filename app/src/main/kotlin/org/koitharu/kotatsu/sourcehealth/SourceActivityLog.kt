package org.koitharu.kotatsu.sourcehealth

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A short in-memory log of what each source just did (worked or failed, and why), so a broken site can be understood
 * and visited in the browser instead of just disappearing from results. Nothing is stored or uploaded.
 */
@Singleton
class SourceActivityLog @Inject constructor() {

	data class Entry(val timeMs: Long, val source: String, val ok: Boolean, val message: String?)

	private val entries = ArrayDeque<Entry>()

	@Synchronized
	fun record(source: String, ok: Boolean, message: String?, now: Long = System.currentTimeMillis()) {
		entries.addFirst(Entry(now, source, ok, message?.take(300)))
		while (entries.size > MAX_ENTRIES) entries.removeLast()
	}

	/** Newest first. */
	@Synchronized
	fun snapshot(): List<Entry> = entries.toList()

	@Synchronized
	fun clear() = entries.clear()

	companion object {

		const val MAX_ENTRIES = 300
	}
}

/** Pure formatting and summarising for the log screen (unit-tested). */
internal object SourceLogFormat {

	/** "just now", "5 min ago", "3 h ago", "2 d ago". */
	fun ago(now: Long, then: Long): String {
		val minutes = ((now - then).coerceAtLeast(0L) / 60_000L)
		return when {
			minutes < 1 -> "just now"
			minutes < 60 -> "$minutes min ago"
			minutes < 60 * 24 -> "${minutes / 60} h ago"
			else -> "${minutes / (60 * 24)} d ago"
		}
	}

	fun line(entry: SourceActivityLog.Entry, now: Long): String {
		val state = if (entry.ok) "OK" else "FAILED"
		val detail = entry.message?.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
		return "${ago(now, entry.timeMs)} · ${entry.source} · $state$detail"
	}

	data class Summary(val source: String, val lastOkMs: Long?, val lastErrorMs: Long?, val lastError: String?) {
		/** Working = the most recent attempt succeeded; limited = it failed but it worked before; unreachable = never worked. */
		val status: String
			get() = when {
				lastErrorMs == null -> "Working"
				lastOkMs != null && lastOkMs > lastErrorMs -> "Working"
				lastOkMs != null -> "Limited"
				else -> "Unreachable"
			}
	}

	/** One summary per source, in order of first appearance (entries are newest first). */
	fun summarize(entries: List<SourceActivityLog.Entry>): List<Summary> =
		entries.groupBy { it.source }.map { (source, list) ->
			val lastOk = list.firstOrNull { it.ok }?.timeMs
			val lastErr = list.firstOrNull { !it.ok }
			Summary(source, lastOk, lastErr?.timeMs, lastErr?.message)
		}
}
