package org.koitharu.kotatsu.core.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_metadata")
data class BookMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "lookup_key") val lookupKey: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
