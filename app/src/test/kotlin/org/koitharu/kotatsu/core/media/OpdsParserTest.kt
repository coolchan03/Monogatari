package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class OpdsParserTest {

	private val base = URI("https://lib.example.org/opds/root.xml")

	@Test
	fun atomEntriesCoversNextAndSearch() {
		val xml = """<feed xmlns="http://www.w3.org/2005/Atom">
			<link rel="next" href="page2.xml" type="application/atom+xml"/>
			<link rel="search" href="/search?q={searchTerms}" type="application/atom+xml"/>
			<entry><title>Dracula</title><author><name>Bram Stoker</name></author>
			  <link rel="http://opds-spec.org/acquisition" href="/dl/dracula.epub" type="application/epub+zip"/>
			  <link rel="http://opds-spec.org/image" href="/img/dracula.jpg" type="image/jpeg"/></entry>
			<entry><title>Horror</title><link rel="subsection" href="/opds/horror.xml" type="application/atom+xml"/></entry>
		</feed>"""
		val page = OpdsParser.parseAtom(xml, base)
		assertEquals(2, page.entries.size)
		assertEquals("https://lib.example.org/dl/dracula.epub", page.entries[0].href)
		assertEquals("https://lib.example.org/img/dracula.jpg", page.entries[0].cover)
		assertTrue(page.entries[0].acquisition)
		assertEquals("https://lib.example.org/opds/page2.xml", page.next)
		assertEquals("https://lib.example.org/search?q={searchTerms}", page.searchTemplate)
		assertEquals("https://lib.example.org/search?q=dark%20tales", OpdsParser.searchUrl(page.searchTemplate!!, "dark tales"))
	}

	@Test
	fun opensearchDescriptionTemplateIsFound() {
		val description = """<OpenSearchDescription><Url type="text/html" template="/web?q={searchTerms}"/>
			<Url type="application/atom+xml" template="/opds/search?query={searchTerms}{&amp;page}"/></OpenSearchDescription>"""
		val template = OpdsParser.searchTemplateFromDescription(description, base)
		assertEquals("https://lib.example.org/opds/search?query={searchTerms}", template)
		assertEquals("https://lib.example.org/opds/search?query=a%20b", OpdsParser.searchUrl(template!!, "a b"))
	}

	@Test
	fun opds2JsonPublicationsNavigationAndNext() {
		val json = """{"metadata":{"title":"Catalog"},
			"links":[{"rel":"next","href":"?page=2"},{"rel":"search","href":"/search{?query}","templated":true}],
			"navigation":[{"title":"Fantasy","href":"/opds/fantasy","type":"application/opds+json"}],
			"publications":[{"metadata":{"title":"Emma","author":{"name":"Jane Austen"}},
			  "links":[{"rel":"http://opds-spec.org/acquisition/open-access","href":"/dl/emma.epub","type":"application/epub+zip"}],
			  "images":[{"href":"https://img.example.org/emma.jpg"}]}]}"""
		val page = OpdsParser.parse(json, base, "application/opds+json")
		assertEquals(listOf("Fantasy", "Emma"), page.entries.map { it.title })
		val emma = page.entries[1]
		assertTrue(emma.acquisition)
		assertEquals("Jane Austen", emma.author)
		assertEquals("https://img.example.org/emma.jpg", emma.cover)
		assertEquals("https://lib.example.org/opds/root.xml?page=2", page.next)
		assertEquals("https://lib.example.org/search?query={searchTerms}", page.searchTemplate)
	}

	@Test
	fun credentialsInTheUrlBecomeABasicHeader() {
		val (clean, header) = OpdsParser.splitCredentials("https://reader:s3cret@lib.example.org/opds?x=1")
		assertEquals("https://lib.example.org/opds?x=1", clean)
		assertEquals("Basic cmVhZGVyOnMzY3JldA==", header)
		val (same, none) = OpdsParser.splitCredentials("https://lib.example.org/opds")
		assertEquals("https://lib.example.org/opds", same)
		assertNull(none)
	}

	@Test
	fun nonWebLinksAreDropped() {
		val xml = """<feed><entry><title>Local</title><link rel="http://opds-spec.org/acquisition" href="file:///etc/passwd" type="application/epub+zip"/></entry></feed>"""
		assertTrue(OpdsParser.parseAtom(xml, base).entries.isEmpty())
	}
}
