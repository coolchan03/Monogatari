package org.koitharu.kotatsu.videodl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.DashKey
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.HlsKind
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.Protocol
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.Reason

class OfflineStreamPolicyTest {

	@Test
	fun classifiesByExtensionAndMime() {
		assertEquals(Protocol.PROGRESSIVE, OfflineStreamPolicy.classify("https://a.test/v.mp4", null).protocol)
		assertEquals(Protocol.PROGRESSIVE, OfflineStreamPolicy.classify("https://a.test/videoplayback?id=1", "video/mp4").protocol)
		assertEquals(Protocol.HLS, OfflineStreamPolicy.classify("https://a.test/master.m3u8?token=x", null).protocol)
		assertEquals(Protocol.HLS, OfflineStreamPolicy.classify("https://a.test/play", "application/x-mpegURL").protocol)
		assertEquals(Protocol.DASH, OfflineStreamPolicy.classify("https://a.test/manifest.mpd", null).protocol)
		assertEquals(Protocol.DASH, OfflineStreamPolicy.classify("https://a.test/m", "application/dash+xml").protocol)
	}

	@Test
	fun refusesNonWebAndUnsupportedStreams() {
		assertEquals(Reason.NO_URL, OfflineStreamPolicy.classify("  ", null).reason)
		assertEquals(Reason.NO_URL, OfflineStreamPolicy.classify(null, null).reason)
		assertEquals(Reason.NOT_HTTP, OfflineStreamPolicy.classify("content://media/1", null).reason)
		assertEquals(Reason.NOT_HTTP, OfflineStreamPolicy.classify("rtmp://live.test/app", null).reason)
		assertEquals(Reason.NOT_HTTP, OfflineStreamPolicy.classify("file:///sdcard/a.mp4", null).reason)
		assertEquals(Reason.UNSUPPORTED_FORMAT, OfflineStreamPolicy.classify("https://a.test/v.ism/Manifest", null).reason)
		assertEquals(Reason.UNSUPPORTED_FORMAT, OfflineStreamPolicy.classify("https://a.test/x", "application/vnd.ms-sstr+xml").reason)
		assertFalse(OfflineStreamPolicy.classify("ftp://a.test/v.mp4", null).allowed)
		assertTrue(OfflineStreamPolicy.classify("http://a.test/v.mp4", null).allowed)
	}

	@Test
	fun hlsVodLiveAndMaster() {
		val vod = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val typedVod = "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:6,\na.ts\n"
		val live = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:100\n#EXTINF:6,\na.ts\n"
		val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2400000\nhigh.m3u8\n"
		assertEquals(HlsKind.VOD, OfflineStreamPolicy.inspectHls(vod))
		assertEquals(HlsKind.VOD, OfflineStreamPolicy.inspectHls(typedVod))
		assertEquals(HlsKind.LIVE, OfflineStreamPolicy.inspectHls(live))
		assertEquals(HlsKind.MASTER, OfflineStreamPolicy.inspectHls(master))
	}

	@Test
	fun hlsDrmKeysAreRefusedButPlainAesIsNot() {
		val plainAes = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://a.test/key\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val sampleAes = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val widevine = "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES-CTR,URI=\"data:text/plain;base64,AAAA\",KEYFORMAT=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val identityFormat = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\",KEYFORMAT=\"identity\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val noEncryption = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n"
		val sessionKey = "#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"skd://y\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n"
		assertEquals(HlsKind.VOD, OfflineStreamPolicy.inspectHls(plainAes))
		assertEquals(HlsKind.VOD, OfflineStreamPolicy.inspectHls(identityFormat))
		assertEquals(HlsKind.VOD, OfflineStreamPolicy.inspectHls(noEncryption))
		assertEquals(HlsKind.DRM, OfflineStreamPolicy.inspectHls(sampleAes))
		assertEquals(HlsKind.DRM, OfflineStreamPolicy.inspectHls(widevine))
		assertEquals(HlsKind.DRM, OfflineStreamPolicy.inspectHls(sessionKey))
	}

	@Test
	fun picksBestHlsVariantAndDetectsSeparateAudio() {
		val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=900000\nlow.m3u8\n\n" +
			"#EXT-X-STREAM-INF:BANDWIDTH=2400000\nhigh/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1200000\nmid.m3u8\n"
		assertEquals("high/index.m3u8", OfflineStreamPolicy.bestHlsVariant(master))
		assertNull(OfflineStreamPolicy.bestHlsVariant("#EXTM3U\n#EXTINF:6,\na.ts\n"))
		assertFalse(OfflineStreamPolicy.hasSeparateHlsAudio(master))
		val withAudio = master + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"en\",URI=\"audio.m3u8\"\n"
		assertTrue(OfflineStreamPolicy.hasSeparateHlsAudio(withAudio))
		val muxedAudioOnly = master + "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"en\"\n"
		assertFalse(OfflineStreamPolicy.hasSeparateHlsAudio(muxedAudioOnly))
	}

	@Test
	fun resolvesRelativePlaylistUris() {
		assertEquals(
			"https://a.test/hls/high/index.m3u8",
			OfflineStreamPolicy.resolve("https://a.test/hls/master.m3u8?x=1", "high/index.m3u8"),
		)
		assertEquals("https://b.test/v.m3u8", OfflineStreamPolicy.resolve("https://a.test/m.m3u8", "https://b.test/v.m3u8"))
	}

	@Test
	fun dashLiveAndProtectedManifestsAreRefused() {
		val vod = "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" mediaPresentationDuration=\"PT10M\"><Period/></MPD>"
		val live = "<?xml version=\"1.0\"?>\n<MPD type=\"dynamic\" minimumUpdatePeriod=\"PT2S\"><Period/></MPD>"
		val drm = vod.replace("<Period/>", "<Period><AdaptationSet><ContentProtection schemeIdUri=\"urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed\"/></AdaptationSet></Period>")
		assertNull(OfflineStreamPolicy.inspectDash(vod))
		assertEquals(Reason.LIVE, OfflineStreamPolicy.inspectDash(live))
		assertEquals(Reason.DRM, OfflineStreamPolicy.inspectDash(drm))
	}

	@Test
	fun dashKeepsOnlyTheBestRepresentationOfEachAdaptationSet() {
		val manifest = """
			<MPD type="static">
			  <Period>
			    <AdaptationSet mimeType="video/mp4">
			      <Representation id="v0" bandwidth="500000"/>
			      <Representation id="v1" bandwidth="3000000"><SegmentBase/></Representation>
			      <Representation id="v2" bandwidth="1500000"/>
			    </AdaptationSet>
			    <AdaptationSet mimeType="audio/mp4">
			      <Representation id="a0" bandwidth="64000"/>
			      <Representation id="a1" bandwidth="128000"/>
			    </AdaptationSet>
			  </Period>
			  <Period>
			    <AdaptationSet mimeType="video/mp4">
			      <Representation id="w0" bandwidth="900000"/>
			    </AdaptationSet>
			  </Period>
			</MPD>
		""".trimIndent()
		assertEquals(
			listOf(DashKey(0, 0, 1), DashKey(0, 1, 1), DashKey(1, 0, 0)),
			OfflineStreamPolicy.dashStreamKeys(manifest),
		)
		assertTrue(OfflineStreamPolicy.dashStreamKeys("not a manifest").isEmpty())
	}

	@Test
	fun dashKeyRoundTrips() {
		val key = DashKey(2, 3, 4)
		assertEquals(key, DashKey.decode(key.encode()))
		assertNull(DashKey.decode("1:2"))
		assertNull(DashKey.decode("a:b:c"))
	}
}
