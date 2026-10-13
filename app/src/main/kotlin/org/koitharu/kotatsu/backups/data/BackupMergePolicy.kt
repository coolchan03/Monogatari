package org.koitharu.kotatsu.backups.data

/** Newest-wins restore: deletion wins ties so old backups cannot resurrect deleted titles. */
internal object BackupMergePolicy {
    fun shouldReplace(localTime: Long?, localDeleted: Long, incomingTime: Long, incomingDeleted: Long): Boolean {
        if (localTime == null) return true
        val existing = maxOf(localTime, localDeleted)
        val incoming = maxOf(incomingTime, incomingDeleted)
        return incoming > existing || (incoming == existing && incomingDeleted != 0L && localDeleted == 0L)
    }
}
