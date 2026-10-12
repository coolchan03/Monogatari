package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Keep enabled state, pinned sources, library and parser preferences during health migration. */
class Migration29To30 : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sources ADD COLUMN fail_count INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sources ADD COLUMN last_success_at INTEGER NOT NULL DEFAULT 0")
    }
}
