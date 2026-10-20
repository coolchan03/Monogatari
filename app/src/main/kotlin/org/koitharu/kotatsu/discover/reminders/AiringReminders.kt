package org.koitharu.kotatsu.discover.reminders

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.discover.AniListDiscover
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anime the user follows from Discover's "Airing this week" list, keyed by AniList media id.
 * Kept in its own small preferences file; nothing leaves the device except the ids sent to AniList.
 */
@Singleton
class AiringReminders @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	fun followedIds(): Set<Int> = prefs.getStringSet(KEY_IDS, null)
		.orEmpty()
		.mapNotNullTo(HashSet()) { it.toIntOrNull() }

	fun isFollowed(mediaId: Int): Boolean = mediaId in followedIds()

	fun title(mediaId: Int): String? = prefs.getString(KEY_TITLE + mediaId, null)

	/** Epoch seconds of the newest episode already announced (or of the moment the show was followed). */
	fun lastNotifiedAiringAt(mediaId: Int): Long = prefs.getLong(KEY_LAST_AIRING + mediaId, 0L)

	fun lastNotifiedEpisode(mediaId: Int): Int = prefs.getInt(KEY_LAST_EPISODE + mediaId, 0)

	/** Epoch seconds of the last successful check, 0 before the first one. */
	var lastCheck: Long
		get() = prefs.getLong(KEY_LAST_CHECK, 0L)
		set(value) {
			prefs.edit().putLong(KEY_LAST_CHECK, value).apply()
		}

	/**
	 * Follows [mediaId]; episodes that aired before [nowSeconds] are never announced.
	 * Returns true when this is the first followed show.
	 */
	fun follow(mediaId: Int, title: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
		val ids = followedIds()
		val wasEmpty = ids.isEmpty()
		val editor = prefs.edit()
			.putStringSet(KEY_IDS, (ids + mediaId).mapTo(HashSet()) { it.toString() })
			.putString(KEY_TITLE + mediaId, title)
		if (lastNotifiedAiringAt(mediaId) < nowSeconds) {
			editor.putLong(KEY_LAST_AIRING + mediaId, nowSeconds)
		}
		if (wasEmpty) {
			editor.putLong(KEY_LAST_CHECK, nowSeconds)
		}
		editor.apply()
		return wasEmpty
	}

	fun unfollow(mediaId: Int) {
		val ids = followedIds() - mediaId
		prefs.edit()
			.putStringSet(KEY_IDS, ids.mapTo(HashSet()) { it.toString() })
			.remove(KEY_TITLE + mediaId)
			.remove(KEY_LAST_AIRING + mediaId)
			.remove(KEY_LAST_EPISODE + mediaId)
			.apply()
	}

	fun markNotified(episode: AniListDiscover.AiredEpisode) {
		if (episode.airingAt <= lastNotifiedAiringAt(episode.mediaId)) return
		prefs.edit()
			.putLong(KEY_LAST_AIRING + episode.mediaId, episode.airingAt)
			.putInt(KEY_LAST_EPISODE + episode.mediaId, maxOf(episode.episode, lastNotifiedEpisode(episode.mediaId)))
			.apply()
	}

	companion object {

		const val PREFS = "monogatari_airing_reminders"
		private const val KEY_IDS = "followed_ids"
		private const val KEY_TITLE = "title_"
		private const val KEY_LAST_AIRING = "last_airing_at_"
		private const val KEY_LAST_EPISODE = "last_episode_"
		private const val KEY_LAST_CHECK = "last_check"

		private const val HOUR = 3_600L
		private const val MAX_LOOKBACK = 7 * 86_400L

		/**
		 * Start of the AniList query window: an hour before the last check (AniList schedules shift a little),
		 * at most a week back after a long offline stretch, and three hours back before the first check.
		 */
		fun windowStart(lastCheck: Long, now: Long): Long = if (lastCheck <= 0L) {
			now - 3 * HOUR
		} else {
			maxOf(lastCheck - HOUR, now - MAX_LOOKBACK)
		}

		/**
		 * Episodes not announced yet: followed shows only, aired after the show's last announced airing time,
		 * one entry per (show, episode), oldest first.
		 */
		fun newEpisodes(
			aired: List<AniListDiscover.AiredEpisode>,
			lastNotifiedAiringAt: Map<Int, Long>,
		): List<AniListDiscover.AiredEpisode> = aired
			.filter { episode ->
				val last = lastNotifiedAiringAt[episode.mediaId] ?: return@filter false
				episode.airingAt > last
			}
			.distinctBy { it.mediaId to it.episode }
			.sortedBy { it.airingAt }
	}
}
