package org.koitharu.kotatsu.tracker.data

import androidx.room.ColumnInfo

/** One "new chapters found" moment of a tracked title; see [TracksDao.findReleaseLogs]. */
data class ReleaseLogRow(
	@ColumnInfo(name = "manga_id") val mangaId: Long,
	@ColumnInfo(name = "title") val title: String,
	@ColumnInfo(name = "created_at") val createdAt: Long,
)
