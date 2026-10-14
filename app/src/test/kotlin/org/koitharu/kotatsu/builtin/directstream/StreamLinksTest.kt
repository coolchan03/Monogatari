package org.koitharu.kotatsu.builtin.directstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.builtin.directstream.StreamLinks.Kind

class StreamLinksTest {

	@Test
	fun classifiesByPathIgnoringQuery() {
		assertEquals(Kind.HLS, StreamLinks.kindFromUrl("https://x/live/index.M3U8?token=a.mpd"))
		assertEquals(Kind.DASH, StreamLinks.kindFromUrl("https://x/v/manifest.mpd#t=1"))
		assertEquals(Kind.PLAYLIST_FILE, StreamLinks.kindFromUrl("http://x/list.m3u"))
		assertEquals(Kind.FILE, StreamLinks.kindFromUrl("https://x/movie.mp4"))
		assertEquals("video/mp4", StreamLinks.mimeFor("https://x/movie.mp4?x=1"))
		assertNull(StreamLinks.mimeFor("https://x/stream"))
	}

	@Test
	fun onlyWebLinksAreAccepted() {
		assertTrue(StreamLinks.isHttpUrl(" https://example.com/a.m3u8 "))
		assertFalse(StreamLinks.isHttpUrl("file:///sdcard/a.m3u8"))
		assertFalse(StreamLinks.isHttpUrl("rtsp://x/a"))
		assertFalse(StreamLinks.isHttpUrl("just words"))
		assertFalse(StreamLinks.isHttpUrl("https://"))
	}

	private val iptv = """#EXTM3U
#EXTINF:-1 tvg-logo="https://img/a.png" group-title="X",First Channel
https://cdn/a.m3u8
#EXTINF:-1,Second
rel/b.m3u8
#EXTINF:-1,Bad
ftp://nope/c
https://cdn/untitled.mp4
"""

	@Test
	fun parsesEntryListsWithLogosRelativeUrlsAndSkipsNonWeb() {
		assertTrue(StreamLinks.isEntryList(iptv))
		val entries = StreamLinks.parseEntries(iptv, base = "https://host/lists/all.m3u")
		assertEquals(listOf("First Channel", "Second", "untitled.mp4"), entries.map { it.title })
		assertEquals("https://img/a.png", entries[0].logo)
		assertEquals("https://host/lists/rel/b.m3u8", entries[1].url)
	}

	private val master = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
low/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
https://other/high.m3u8
"""

	@Test
	fun parsesHlsVariantsBestFirstWithDiagnostics() {
		assertTrue(StreamLinks.isMasterPlaylist(master))
		assertFalse(StreamLinks.isEntryList(master))
		val variants = StreamLinks.parseHlsVariants(master, "https://host/v/master.m3u8")
		assertEquals(listOf("https://other/high.m3u8", "https://host/v/low/index.m3u8"), variants.map { it.url })
		assertEquals("720p · avc1.64001f,mp4a.40.2 · 2.8 Mbps", variants[0].label)
		assertEquals("360p · avc1.4d401e,mp4a.40.2 · 800 kbps", variants[1].label)
	}

	@Test
	fun mediaPlaylistIsNotAnEntryList() {
		val media = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nseg1.ts\n"
		assertFalse(StreamLinks.isEntryList(media))
		assertFalse(StreamLinks.isMasterPlaylist(media))
	}

	@Test
	fun dashLabelsListVideoRepresentationsOnly() {
		val mpd = """<MPD><Period><AdaptationSet mimeType="video/mp4">
			<Representation id="1" bandwidth="1500000" height="720" codecs="avc1.64001f"/>
			<Representation id="2" bandwidth="500000" height="360" codecs="avc1.4d401e"/>
			</AdaptationSet><AdaptationSet mimeType="audio/mp4">
			<Representation id="3" bandwidth="128000" codecs="mp4a.40.2" mimeType="audio/mp4"/>
			</AdaptationSet></Period></MPD>"""
		assertEquals(listOf("720p · avc1.64001f · 1.5 Mbps", "360p · avc1.4d401e · 500 kbps"), StreamLinks.parseDashLabels(mpd))
	}

	@Test
	fun describeFallsBackToHls() {
		assertEquals("HLS", StreamLinks.describe(0, 0, null))
	}
}
