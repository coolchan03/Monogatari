package org.koitharu.kotatsu.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve reading sessions when replacing the history FK with a manga FK. */
class Migration30To31 : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS stats_new (
            manga_id INTEGER NOT NULL, started_at INTEGER NOT NULL,
            duration INTEGER NOT NULL, pages INTEGER NOT NULL,
            chapter_id INTEGER, words INTEGER NOT NULL DEFAULT 0,
            media_ms INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(manga_id, started_at),
            FOREIGN KEY(manga_id) REFERENCES manga(manga_id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("""INSERT INTO stats_new(manga_id,started_at,duration,pages)
            SELECT s.manga_id,s.started_at,s.duration,s.pages FROM stats s
            INNER JOIN manga m ON m.manga_id=s.manga_id""")
        db.execSQL("DROP TABLE stats")
        db.execSQL("ALTER TABLE stats_new RENAME TO stats")
        db.execSQL("""CREATE TABLE IF NOT EXISTS chapter_meta (
            chapter_id INTEGER NOT NULL, manga_id INTEGER NOT NULL,
            pages INTEGER NOT NULL, words INTEGER NOT NULL,
            duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            PRIMARY KEY(chapter_id, manga_id),
            FOREIGN KEY(manga_id) REFERENCES manga(manga_id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chapter_meta_manga_id ON chapter_meta(manga_id)")
    }
}
