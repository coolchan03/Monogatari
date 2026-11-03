package org.koitharu.kotatsu.main.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.main.ui.SharedLinkClassifier.Result

class SharedLinkClassifierTest {

	@Test
	fun extractsFirstLinkAndTrimsPunctuation() {
		assertEquals(
			"https://example.org/a/b?x=1",
			SharedLinkClassifier.extractLink("Look at this (https://example.org/a/b?x=1), then https://other.org"),
		)
		assertNull(SharedLinkClassifier.extractLink("no link here, ftp://example.org"))
		assertNull(SharedLinkClassifier.extractLink(null))
	}

	@Test
	fun onlySetupIsAcceptedFromTheAppScheme() {
		assertNull(SharedLinkClassifier.extractLink("monogatari://open?x=1"))
		assertEquals(
			"https://example.org/x",
			SharedLinkClassifier.extractLink("monogatari://other https://example.org/x"),
		)
		assertEquals(
			Result.RepoSetup("monogatari://setup?repos=https%3A%2F%2Fa.org%2Findex.json"),
			SharedLinkClassifier.classify("monogatari://setup?repos=https%3A%2F%2Fa.org%2Findex.json"),
		)
	}

	@Test
	fun blankIsEmptyAndPlainTextStaysText() {
		assertEquals(Result.Empty, SharedLinkClassifier.classify("   "))
		assertEquals(Result.Empty, SharedLinkClassifier.classify(null))
		assertEquals(Result.Text("blue lock"), SharedLinkClassifier.classify("  blue lock "))
	}

	@Test
	fun youTubeVideoLinks() {
		val watch = "https://www.youtube.com/watch?v=abc123&t=5"
		assertEquals(Result.YouTube(watch), SharedLinkClassifier.classify("Cool video $watch"))
		assertEquals(
			Result.YouTube("https://youtu.be/abc123"),
			SharedLinkClassifier.classify("https://youtu.be/abc123?si=zzz".substringBefore('?')),
		)
		assertEquals(
			Result.YouTube("https://music.youtube.com/watch?v=abc123"),
			SharedLinkClassifier.classify("https://music.youtube.com/watch?v=abc123"),
		)
		assertEquals(
			Result.YouTube("https://m.youtube.com/shorts/xyz"),
			SharedLinkClassifier.classify("https://m.youtube.com/shorts/xyz"),
		)
	}

	@Test
	fun youTubeChannelsAndPlaylists() {
		assertEquals(
			Result.YouTube("channel:SomeName"),
			SharedLinkClassifier.classify("https://www.youtube.com/@SomeName"),
		)
		assertEquals(
			Result.YouTube("channel:UC123"),
			SharedLinkClassifier.classify("https://www.youtube.com/channel/UC123/videos"),
		)
		assertEquals(
			Result.YouTube("playlist:PL99"),
			SharedLinkClassifier.classify("https://www.youtube.com/playlist?list=PL99"),
		)
		assertEquals(
			Result.YouTube("playlist:PL99"),
			SharedLinkClassifier.classify("https://www.youtube.com/watch?list=PL99"),
		)
	}

	@Test
	fun unsupportedYouTubePagesFallBackToText() {
		val text = "https://www.youtube.com/feed/subscriptions"
		assertEquals(Result.Text(text), SharedLinkClassifier.classify(text))
	}

	@Test
	fun httpsJsonIsARepositoryIndex() {
		assertEquals(
			Result.RepoIndex("https://raw.example.org/repo/index.json"),
			SharedLinkClassifier.classify("https://raw.example.org/repo/index.json"),
		)
		// Plain http and comma-containing addresses cannot go through the setup dialog.
		assertEquals(
			Result.WebLink("http://example.org/index.json", "http://example.org/index.json"),
			SharedLinkClassifier.classify("http://example.org/index.json"),
		)
		assertEquals(
			Result.WebLink("https://example.org/a,b.json", "https://example.org/a,b.json"),
			SharedLinkClassifier.classify("https://example.org/a,b.json"),
		)
	}

	@Test
	fun otherLinksAreWebLinksWithOriginalText() {
		val text = "Read this: https://mangasite.org/title/42."
		assertEquals(
			Result.WebLink("https://mangasite.org/title/42", text),
			SharedLinkClassifier.classify(text),
		)
	}

	@Test
	fun linksWithCredentialsAreNotFollowed() {
		val text = "https://user:pw@example.org/x"
		assertEquals(Result.Text(text), SharedLinkClassifier.classify(text))
	}
}
