package org.koitharu.kotatsu.backups.domain

/** Pure helpers for sending several files over one Nearby connection. */
internal object NearbyBatch {

    /** A picked file; [size] is null when the provider does not report one. */
    class Entry(val name: String, val size: Long?)

    /**
     * @property accepted indexes into the picked list that will be sent, in order.
     * @property tooLarge indexes skipped because one file is above the per-file limit.
     * @property overTotal the selection together is above the total limit, so nothing is accepted.
     * @property tooMany more files than allowed in one go, so nothing is accepted.
     */
    class Admission(
        val accepted: List<Int>,
        val tooLarge: List<Int>,
        val overTotal: Boolean,
        val tooMany: Boolean,
    )

    fun admit(
        entries: List<Entry>,
        maxEach: Long = NearbySyncProtocol.MAX_BYTES,
        maxTotal: Long = NearbySyncProtocol.MAX_BATCH_BYTES,
        maxFiles: Int = NearbySyncProtocol.MAX_BATCH_FILES,
    ): Admission {
        if (entries.size > maxFiles) return Admission(emptyList(), emptyList(), overTotal = false, tooMany = true)
        val tooLarge = entries.indices.filter { (entries[it].size ?: 0L) > maxEach }
        val accepted = entries.indices.filterNot { it in tooLarge }
        val total = accepted.sumOf { entries[it].size ?: 0L }
        if (total > maxTotal) return Admission(emptyList(), tooLarge, overTotal = true, tooMany = false)
        return Admission(accepted, tooLarge, overTotal = false, tooMany = false)
    }

    /** Counts shown in the final summary line. */
    class Tally(var sent: Int = 0, var failed: Int = 0, var skipped: Int = 0)

    /** "4 sent, 1 failed" (and ", 2 skipped" when any); zero failed/skipped counts are left out. */
    fun summary(
        tally: Tally,
        sent: (Int) -> String,
        failed: (Int) -> String,
        skipped: (Int) -> String,
    ): String = buildList {
        add(sent(tally.sent))
        if (tally.failed > 0) add(failed(tally.failed))
        if (tally.skipped > 0) add(skipped(tally.skipped))
    }.joinToString(", ")

    fun percent(transferred: Long, total: Long): Int =
        if (total <= 0L) 0 else (100 * transferred / total).toInt().coerceIn(0, 100)
}
