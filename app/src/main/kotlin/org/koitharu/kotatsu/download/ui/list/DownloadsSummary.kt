package org.koitharu.kotatsu.download.ui.list

/** Coarse state of a queue row, as counted by the summary header. */
enum class DownloadBucket {

	DOWNLOADING,
	QUEUED,
	PAUSED,
	FAILED,
	FINISHED,
}

/** Remaining time rounded to something a person can read. */
data class DownloadEta(
	val unit: Scale,
	val amount: Int,
) {

	enum class Scale {

		LESS_THAN_MINUTE,
		MINUTES,
		HOURS,
	}
}

/** Counts shown in the header of the downloads screen. Pure logic, no Android dependencies. */
data class DownloadsSummary(
	val downloading: Int = 0,
	val queued: Int = 0,
	val paused: Int = 0,
	val failed: Int = 0,
	/** Time left for the running downloads in milliseconds, or 0 when unknown. */
	val remainingMs: Long = 0L,
) {

	/** True when there is anything worth summarizing (finished rows are not). */
	val isVisible: Boolean
		get() = downloading + queued + paused + failed > 0

	val eta: DownloadEta?
		get() = formatEta(remainingMs)

	companion object {

		private const val MINUTE_MS = 60_000L

		/**
		 * @param items bucket of each row and its ETA as an absolute timestamp (millis) or a value <= 0 when unknown
		 * @param now current time in millis
		 */
		fun of(items: List<Pair<DownloadBucket, Long>>, now: Long): DownloadsSummary {
			var downloading = 0
			var queued = 0
			var paused = 0
			var failed = 0
			var latestEta = 0L
			for ((bucket, eta) in items) {
				when (bucket) {
					DownloadBucket.DOWNLOADING -> {
						downloading++
						if (eta > latestEta) {
							latestEta = eta
						}
					}

					DownloadBucket.QUEUED -> queued++
					DownloadBucket.PAUSED -> paused++
					DownloadBucket.FAILED -> failed++
					DownloadBucket.FINISHED -> Unit
				}
			}
			return DownloadsSummary(
				downloading = downloading,
				queued = queued,
				paused = paused,
				failed = failed,
				remainingMs = if (latestEta > now) latestEta - now else 0L,
			)
		}

		/** Returns null for an unknown or elapsed ETA. */
		fun formatEta(remainingMs: Long): DownloadEta? {
			if (remainingMs <= 0L) {
				return null
			}
			if (remainingMs < MINUTE_MS) {
				return DownloadEta(DownloadEta.Scale.LESS_THAN_MINUTE, 0)
			}
			val minutes = ((remainingMs + MINUTE_MS - 1) / MINUTE_MS).toInt()
			if (minutes < 60) {
				return DownloadEta(DownloadEta.Scale.MINUTES, minutes)
			}
			return DownloadEta(DownloadEta.Scale.HOURS, ((minutes + 30) / 60).coerceAtLeast(1))
		}
	}
}
