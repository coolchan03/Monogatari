package org.koitharu.kotatsu.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MangaDatabaseTest {

	@get:Rule
	val helper: MigrationTestHelper = MigrationTestHelper(
		InstrumentationRegistry.getInstrumentation(),
		MangaDatabase::class.java,
	)

	private val migrations = getDatabaseMigrations(InstrumentationRegistry.getInstrumentation().targetContext)

	@Test
	fun versions() {
		assertEquals(1, migrations.first().startVersion)
		repeat(migrations.size) { i ->
			assertEquals(i + 1, migrations[i].startVersion)
			assertEquals(i + 2, migrations[i].endVersion)
		}
		assertEquals(DATABASE_VERSION, migrations.last().endVersion)
	}

	@Test
	fun migrateAll() {
		helper.createDatabase(TEST_DB, 1).close()
		for (migration in migrations) {
			helper.runMigrationsAndValidate(
				TEST_DB,
				migration.endVersion,
				true,
				migration,
			).close()
		}
	}

	@Test
	fun migration30To31PreservesReadingSessionsAfterHistoryDeletion() {
		helper.createDatabase(TEST_DB, 30).use { db ->
			db.execSQL("""INSERT INTO manga (manga_id,title,alt_title,url,public_url,rating,nsfw,content_rating,cover_url,large_cover_url,state,author,source)
				VALUES (101,'Test',NULL,'/test','https://example.org/test',0,0,NULL,'',NULL,NULL,NULL,'TEST')""")
			db.execSQL("""INSERT INTO history (manga_id,created_at,updated_at,chapter_id,page,scroll,percent,deleted_at,chapters)
				VALUES (101,1000,1000,1,1,0,0,0,1)""")
			db.execSQL("INSERT INTO stats (manga_id,started_at,duration,pages) VALUES (101,1000,30000,4)")
		}
		helper.runMigrationsAndValidate(TEST_DB, 31, true, migrations.last()).use { db ->
			db.execSQL("DELETE FROM history WHERE manga_id = 101")
			db.query("SELECT duration,pages,words,media_ms FROM stats WHERE manga_id = 101").use { cursor ->
				assertEquals(true, cursor.moveToFirst())
				assertEquals(30000L, cursor.getLong(0))
				assertEquals(4, cursor.getInt(1))
				assertEquals(0, cursor.getInt(2))
				assertEquals(0L, cursor.getLong(3))
			}
		}
	}

	@Test
	fun prePopulate() {
		val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
		helper.createDatabase(TEST_DB, DATABASE_VERSION).use {
			DatabasePrePopulateCallback(resources).onCreate(it)
		}
	}

	private companion object {

		const val TEST_DB = "test-db"
	}
}
