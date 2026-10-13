package org.koitharu.kotatsu.stats.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.koitharu.kotatsu.core.db.entity.MangaEntity

@Entity(tableName = "chapter_meta", primaryKeys = ["chapter_id", "manga_id"],
    indices = [Index("manga_id")],
    foreignKeys = [ForeignKey(entity = MangaEntity::class, parentColumns = ["manga_id"],
        childColumns = ["manga_id"], onDelete = ForeignKey.CASCADE)])
data class ChapterMetaEntity(
    @ColumnInfo(name = "chapter_id") val chapterId: Long,
    @ColumnInfo(name = "manga_id") val mangaId: Long,
    @ColumnInfo(name = "pages") val pages: Int = 0,
    @ColumnInfo(name = "words") val words: Int = 0,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)
