package org.koitharu.kotatsu.builtin.twitch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwitchRepositoryTest {

	@Test
	fun parsesChannelSearchAndSkipsBadLogins() {
		val body = JSONObject("""{"data":[
			{"id":"123","broadcaster_login":"anime_fan","display_name":"AnimeFan","title":"Live!","game_name":"Just Chatting","thumbnail_url":"https://static-cdn.jtvnw.net/x-{width}x{height}.png"},
			{"id":"9","broadcaster_login":"bad login!","display_name":"Bad"}]}""")
		val channels = TwitchRepository.parseChannels(body)
		assertEquals(1, channels.size)
		assertEquals("AnimeFan", channels[0].name)
		assertEquals("123", channels[0].id)
		assertEquals("https://static-cdn.jtvnw.net/x-640x360.png", channels[0].thumbnail)
	}

	@Test
	fun parsesStreamsAndVideos() {
		val streams = TwitchRepository.parseStreams(JSONObject("""{"data":[{"user_id":"7","user_login":"chan","user_name":"Chan","title":"T","game_name":"G","thumbnail_url":"https://t/{width}x{height}.jpg"}]}"""))
		assertEquals("chan", streams.single().login)
		val videos = TwitchRepository.parseVideos(JSONObject("""{"data":[{"id":"555","title":"Finale","published_at":"2026-10-01T12:00:00Z"},{"id":"abc","title":"bad"}]}"""))
		assertEquals(listOf("2026-10-01 · Finale"), videos.map { it.title })
		assertEquals("https://www.twitch.tv/videos/555", videos.single().url)
	}

	@Test
	fun parsesOfficialClipsAndRejectsMalformedIds() {
		val response = JSONObject("""{"data":[
			{"id":"CleverClip-abc_123","title":"Funny scene","created_at":"2026-10-15T10:00:00Z"},
			{"id":"../../untrusted","title":"Invalid"},
			{"id":"CleverClip-abc_123","title":"Duplicate"},
			{"id":"OtherClip999","title":"Another"}
		]}""")
		val clips = TwitchRepository.parseClips(response)
		assertEquals(2, clips.size)
		assertEquals("2026-10-15 - Clip: Funny scene", clips.first().title)
		assertEquals("https://clips.twitch.tv/CleverClip-abc_123", clips.first().url)
		assertEquals("https://clips.twitch.tv/OtherClip999", clips.last().url)
	}

	@Test
	fun storedUrlsAreValidated() {
		assertEquals("chan" to "7", TwitchRepository.splitUrl("/channel/chan/7"))
		assertEquals("chan" to null, TwitchRepository.splitUrl("/channel/chan"))
		assertNull(TwitchRepository.splitUrl("/channel/ba d/7"))
		assertNull(TwitchRepository.splitUrl("/channel/chan/x"))
	}
}
