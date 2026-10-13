package org.koitharu.kotatsu.backups.domain

/** Pure selection policy: a full snapshot supersedes older snapshots from the same device. */
internal object FolderSyncPlanner {
    data class Snapshot(val uri: String, val deviceId: String, val createdAt: Long)

    private const val CLOCK_TOLERANCE_MS = 24L * 60 * 60 * 1000

    fun pending(
        snapshots: Collection<Snapshot>,
        localDeviceId: String,
        mergedThrough: Map<String, Long>,
        now: Long,
    ): List<Snapshot> = snapshots.asSequence()
        .filter { it.deviceId.isNotBlank() && it.deviceId != localDeviceId }
        .filter { it.createdAt > 0L && it.createdAt <= now + CLOCK_TOLERANCE_MS }
        .filter { it.createdAt > (mergedThrough[it.deviceId] ?: 0L) }
        .groupBy { it.deviceId }
        .values.mapNotNull { devices ->
            devices.maxWithOrNull(compareBy<Snapshot> { it.createdAt }.thenBy { it.uri })
        }
        .sortedWith(compareBy<Snapshot> { it.createdAt }.thenBy { it.deviceId })
}