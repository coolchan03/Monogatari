package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive-only metadata cache; all existing user library tables are retained. */
class Migration28To29 : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS book_metadata (
                lookup_key TEXT NOT NULL PRIMARY KEY,
                cover_url TEXT,
                description TEXT,
                page_count INTEGER,
                cached_at INTEGER NOT NULL
            )""",
        )
    }
}
