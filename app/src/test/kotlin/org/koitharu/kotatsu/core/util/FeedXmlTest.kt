package org.koitharu.kotatsu.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedXmlTest {

	@Test
	fun latin1EntityTableIsInCodePointOrder() {
		assertEquals(0xA0, FeedXml.HTML_ENTITIES["nbsp"])
		assertEquals(0xA9, FeedXml.HTML_ENTITIES["copy"])
		assertEquals(0xD7, FeedXml.HTML_ENTITIES["times"])
		assertEquals(0xE9, FeedXml.HTML_ENTITIES["eacute"])
		assertEquals(0xF7, FeedXml.HTML_ENTITIES["divide"])
		assertEquals(0xFF, FeedXml.HTML_ENTITIES["yuml"])
		assertEquals(8217, FeedXml.HTML_ENTITIES["rsquo"])
	}

	@Test
	fun prepareRewritesHtmlEntitiesButKeepsXmlOnes() {
		val prepared = FeedXml.prepare("<t>a&nbsp;b &amp; &lt;i&gt; &quot;&apos; &#8217; &#x2019; &mdash; &bogus; R&D</t>")
		assertEquals("<t>a&#160;b &amp; &lt;i&gt; &quot;&apos; &#8217; &#x2019; &#8212; &amp;bogus; R&amp;D</t>", prepared)
	}

	@Test
	fun prepareLeavesCdataUntouched() {
		val xml = "<t><![CDATA[Tom & Jerry&nbsp;<b>]]>&hellip;</t>"
		assertEquals("<t><![CDATA[Tom & Jerry&nbsp;<b>]]>&#8230;</t>", FeedXml.prepare(xml))
	}

	@Test
	fun prepareStripsBomWhitespaceAndSimpleDoctype() {
		val xml = "﻿ \r\n<?xml version=\"1.0\"?><!DOCTYPE rss SYSTEM \"http://example.org/rss.dtd\"><rss/>"
		assertEquals("<?xml version=\"1.0\"?><rss/>", FeedXml.prepare(xml))
	}

	@Test
	fun prepareRefusesEntityDeclarationsAndInlineDtds() {
		assertThrows(IllegalArgumentException::class.java) {
			FeedXml.prepare("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss>&x;</rss>")
		}
		assertThrows(IllegalArgumentException::class.java) {
			FeedXml.prepare("<!DOCTYPE rss [<!ELEMENT rss ANY>]><rss/>")
		}
	}

	@Test
	fun parsedDocumentDecodesRewrittenEntities() {
		val document = FeedXml.parseDocument("﻿<rss><title>Caf&eacute; &ldquo;night&rdquo;</title></rss>")
		assertEquals("Café “night”", document.documentElement.textContent)
	}

	@Test
	fun decodeUsesContentTypeThenPrologThenUtf8() {
		val latin1 = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><t>café</t>".toByteArray(Charsets.ISO_8859_1)
		assertTrue(FeedXml.decode(latin1, "text/xml").endsWith("<t>café</t>"))
		assertTrue(FeedXml.decode(latin1, "application/rss+xml; charset=iso-8859-1").endsWith("café</t>"))
		val utf8 = "<t>café</t>".toByteArray(Charsets.UTF_8)
		assertEquals("<t>café</t>", FeedXml.decode(utf8, null))
		assertEquals("<t>café</t>", FeedXml.decode(utf8, "text/xml; charset=\"utf-8\""))
		assertEquals("<t>café</t>", FeedXml.decode(utf8, "text/xml; charset=unknown-charset"))
	}

	@Test
	fun articleUrlUpgradesResolvesAndRejects() {
		assertEquals("https://example.org/a", FeedXml.articleUrl("http://example.org/a#comments"))
		assertEquals("https://example.org/b", FeedXml.articleUrl("//example.org/b"))
		assertEquals("https://example.org/news/c", FeedXml.articleUrl("c", "https://example.org/news/feed"))
		assertNull(FeedXml.articleUrl("https://user:pass@example.org/"))
		assertNull(FeedXml.articleUrl("ftp://example.org/"))
		assertNull(FeedXml.articleUrl("relative/only"))
	}

	@Test
	fun parsesRssAndIsoDates() {
		val expected = 1_791_885_600_000L // 2026-10-13T10:00:00Z
		assertEquals(expected, FeedXml.parseDate("Tue, 13 Oct 2026 10:00:00 GMT"))
		assertEquals(expected, FeedXml.parseDate("Tue, 13 Oct 2026 10:00:00 +0000"))
		assertEquals(expected, FeedXml.parseDate("13 Oct 2026 12:00:00 +0200"))
		assertEquals(expected, FeedXml.parseDate("Tue, 13 Oct 2026 06:00:00 -0400"))
		assertEquals(expected, FeedXml.parseDate("2026-10-13T10:00:00Z"))
		assertEquals(expected, FeedXml.parseDate("2026-10-13T10:00:00.123456Z"))
		assertEquals(expected, FeedXml.parseDate("2026-10-13T12:00:00.5+02:00"))
		assertEquals(expected, FeedXml.parseDate("2026-10-13T10:00Z"))
		assertEquals(1_791_849_600_000L, FeedXml.parseDate("2026-10-13"))
		assertNull(FeedXml.parseDate("Tuesday"))
		assertNull(FeedXml.parseDate(""))
	}
}
