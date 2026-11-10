package org.koitharu.kotatsu.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMatcherTest {

	@Test
	fun exactAndSubdomainMatch() {
		assertTrue(HostMatcher.isSameSite("example.org", "example.org"))
		assertTrue(HostMatcher.isSameSite("www.example.org", "example.org"))
		assertTrue(HostMatcher.isSameSite("CDN.Example.ORG.", "example.org"))
	}

	@Test
	fun deceptiveHostnamesAreRejected() {
		listOf(
			"example.org.evil.test",
			"notexample.org",
			"evil-example.org",
			"example.org@evil.test",
			"evil.test",
			"example.orgx",
			"",
		).forEach { assertFalse(it, HostMatcher.isSameSite(it, "example.org")) }
	}

	@Test
	fun nullAndBlankNeverMatch() {
		assertFalse(HostMatcher.isSameSite(null, "example.org"))
		assertFalse(HostMatcher.isSameSite("example.org", null))
		assertFalse(HostMatcher.isSameSite("example.org", " "))
		assertFalse(HostMatcher.isSameSite(null, null))
	}

	@Test
	fun parentIsNotChildSite() {
		assertFalse(HostMatcher.isSameSite("example.org", "www.example.org"))
	}

	@Test
	fun onlyHttpSchemesAreWebSchemes() {
		assertTrue(HostMatcher.isWebScheme("https"))
		assertTrue(HostMatcher.isWebScheme("HTTP"))
		listOf("intent", "file", "javascript", "data", "content", "market", "", null).forEach {
			assertFalse(it, HostMatcher.isWebScheme(it))
		}
	}
}
