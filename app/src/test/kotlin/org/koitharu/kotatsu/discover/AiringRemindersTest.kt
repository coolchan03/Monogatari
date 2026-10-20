package org.koitharu.kotatsu.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.discover.AniListDiscover.AiredEpisode
import org.koitharu.kotatsu.discover.reminders.AiringReminders

class AiringRemindersTest {

	@Test
	fun onlyEpisodesAfterTheLastAnnouncementAreNew() {
		val aired = listOf(
			AiredEpisode(7, "Given", 6, 2000L),
			AiredEpisode(7, "Given", 5, 1000L),
			AiredEpisode(8, "Frieren", 3, 1500L),
		)
		val fresh = AiringReminders.newEpisodes(aired, mapOf(7 to 1000L, 8 to 1500L))
		assertEquals(listOf(AiredEpisode(7, "Given", 6, 2000L)), fresh)
	}

	@Test
	fun unfollowedShowsAndDuplicatesAreSkipped() {
		val aired = listOf(
			AiredEpisode(9, "Not followed", 1, 3000L),
			AiredEpisode(7, "Given", 6, 2500L),
			AiredEpisode(7, "Given", 6, 2500L),
			AiredEpisode(8, "Frieren", 4, 2000L),
		)
		val fresh = AiringReminders.newEpisodes(aired, mapOf(7 to 1000L, 8 to 1000L))
		// One per episode, oldest first.
		assertEquals(listOf(8 to 4, 7 to 6), fresh.map { it.mediaId to it.episode })
		assertTrue(AiringReminders.newEpisodes(emptyList(), mapOf(7 to 0L)).isEmpty())
	}

	@Test
	fun windowStartsAnHourBeforeLastCheckButAtMostAWeekBack() {
		val now = 10_000_000L
		assertEquals(now - 3 * 3_600L, AiringReminders.windowStart(lastCheck = 0L, now = now))
		assertEquals(now - 2 * 3_600L, AiringReminders.windowStart(lastCheck = now - 3_600L, now = now))
		assertEquals(now - 7 * 86_400L, AiringReminders.windowStart(lastCheck = 1L, now = now))
	}
}
