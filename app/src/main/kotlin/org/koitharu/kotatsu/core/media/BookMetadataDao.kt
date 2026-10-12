package org.koitharu.kotatsu.core.media

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BookMetadataDao {
    @Query("SELECT * FROM book_metadata WHERE lookup_key = :key LIMIT 1")
    suspend fun find(key: String): BookMetadataEntity?

    @Upsert
    suspend fun upsert(value: BookMetadataEntity)
}
