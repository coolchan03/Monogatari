package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubscriptionImportParserTest {

	private val a = "UCaaaaaaaaaaaaaaaaaaaaaa"
	private val b = "UCbbbbbbbbbbbbbbbbbbbbbb"
	private val c = "UCcccccccccccccccccccccc"

	@Test
	fun takeoutCsvWithBomCrlfAndQuotes() {
		val csv = "﻿Channel ID,Channel URL,Channel title\r\n" +
			"$a,http://www.youtube.com/channel/$a,\"Cats, \"\"Dogs\"\" & Co\"\r\n" +
			"$b,http://www.youtube.com/channel/$b,Plain\r\n"
		val r = SubscriptionImportParser.parseText(csv)
		assertEquals(listOf(a, b), r.channels.map { it.channelId })
		assertEquals("Cats, \"Dogs\" & Co", r.channels[0].name)
		assertEquals("https://www.youtube.com/channel/$a", r.channels[0].url)
		assertEquals(0, r.skipped)
	}

	@Test
	fun csvFallsBackToUrlColumnAndCountsBadRows() {
		val csv = "Channel ID,Channel URL,Channel title\n" +
			"nope,https://www.youtube.com/channel/$c,From url\n" +
			"bad,https://example.com/x,Bad\n" +
			",,\n"
		val r = SubscriptionImportParser.parseText(csv)
		assertEquals(listOf(c), r.channels.map { it.channelId })
		assertEquals(1, r.invalid)
	}

	@Test
	fun csvQuotedFieldMayContainNewline() {
		val rows = SubscriptionImportParser.parseCsv("a,\"b\nc\",d\n")
		assertEquals(listOf(listOf("a", "b\nc", "d")), rows)
	}

	@Test
	fun newPipeJsonSplitsServices() {
		val json = """{"app_version":"0.27","subscriptions":[
			{"service_id":0,"url":"https://www.youtube.com/channel/$a","name":"One"},
			{"service_id":1,"url":"https://soundcloud.com/x","name":"SC"},
			{"service_id":3,"url":"https://peertube.example/c/y","name":"PT"},
			{"service_id":2,"url":"https://media.ccc.de/c/z","name":"CCC"},
			{"service_id":0,"url":"https://www.youtube.com/@handle","name":"Handle"},
			{"service_id":0,"url":"https://www.youtube.com/channel/$a","name":"Dup"}
		]}"""
		val r = SubscriptionImportParser.parseText(json)
		assertEquals(listOf("One"), r.channels.map { it.name })
		assertEquals(1, r.soundCloud)
		assertEquals(1, r.peerTube)
		assertEquals(1, r.otherServices)
		assertEquals(1, r.invalid)
		assertEquals(1, r.duplicates)
		assertEquals(4, r.skipped)
	}

	@Test
	fun newPipeZipIsReadFromEntry() {
		val json = """{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/$b","name":"Zip"}]}"""
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use {
			it.putNextEntry(ZipEntry("newpipe.db"))
			it.write(byteArrayOf(1, 2, 3))
			it.closeEntry()
			it.putNextEntry(ZipEntry("newpipe.json"))
			it.write(json.toByteArray())
			it.closeEntry()
		}
		val r = SubscriptionImportParser.parse(out.toByteArray())
		assertEquals(listOf(b), r.channels.map { it.channelId })
	}

	@Test
	fun zipWithoutKnownEntryFails() {
		val out = ByteArrayOutputStream()
		ZipOutputStream(out).use {
			it.putNextEntry(ZipEntry("other.txt"))
			it.write(1)
			it.closeEntry()
		}
		try {
			SubscriptionImportParser.parse(out.toByteArray())
			fail()
		} catch (_: IOException) {
		}
	}

	@Test
	fun jsonWithoutSubscriptionsFails() {
		try {
			SubscriptionImportParser.parseText("""{"a":1}""")
			fail()
		} catch (_: IOException) {
		}
	}

	@Test
	fun plainUrlListIgnoresCommentsAndJunk() {
		val text = "# my list\nhttps://www.youtube.com/channel/$a\n\nhttps://m.youtube.com/channel/$b/videos\nftp://x\nUCshort\n"
		val r = SubscriptionImportParser.parseText(text)
		assertEquals(listOf(a, b), r.channels.map { it.channelId })
		assertEquals(2, r.invalid)
	}

	@Test
	fun channelIdShapeIsValidated() {
		assertEquals(a, SubscriptionImportParser.channelIdOf("https://youtube.com/channel/$a?x=1"))
		assertNull(SubscriptionImportParser.channelIdOf("https://www.youtube.com/channel/UCtooShort"))
		assertNull(SubscriptionImportParser.channelIdOf("https://www.youtube.com/channel/XX${a.drop(2)}"))
		assertNull(SubscriptionImportParser.channelIdOf("https://evil.example/https://www.youtube.com/channel/$a"))
		assertNull(SubscriptionImportParser.channelIdOf("javascript:alert(1)"))
	}

	@Test
	fun capDropsExtrasAndFlagsTruncation() {
		val ids = (0 until 5).map { "UC" + it.toString().padStart(22, 'x') }
		val text = ids.joinToString("\n") { "https://www.youtube.com/channel/$it" } +
			"\nhttps://www.youtube.com/channel/${ids[0]}"
		val r = SubscriptionImportParser.parseText(text, maxEntries = 3)
		assertEquals(ids.take(3), r.channels.map { it.channelId })
		assertTrue(r.truncated)
		assertEquals(1, r.duplicates)
		assertFalse(SubscriptionImportParser.parseText(text, maxEntries = 10).truncated)
	}

	@Test
	fun blankNameFallsBackToChannelIdAndControlCharsAreRemoved() {
		val r = SubscriptionImportParser.parseText("https://www.youtube.com/channel/$a")
		assertEquals(a, r.channels[0].name)
		val collector = SubscriptionImportParser.Collector()
		collector.addChannel(b, "  Bad\u0000Name\n ")
		assertEquals("BadName", collector.result().channels[0].name)
	}

	@Test
	fun oversizedInputIsRejected() {
		try {
			SubscriptionImportParser.readBounded(ByteArray(100).inputStream(), limit = 50)
			fail()
		} catch (_: IOException) {
		}
		assertEquals(100, SubscriptionImportParser.readBounded(ByteArray(100).inputStream(), limit = 100).size)
	}
}
