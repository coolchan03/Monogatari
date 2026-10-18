package org.koitharu.kotatsu.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {

	@Test
	fun allowsOnlyWebAddressesWithAHost() {
		assertTrue(ExternalLinks.isAllowed("https://storyweaver.org.in/stories/1"))
		assertTrue(ExternalLinks.isAllowed("http://example.org/feed?x=1"))
		assertTrue(ExternalLinks.isAllowed("  HTTPS://Example.org/  "))
	}

	@Test
	fun refusesSchemesThatCanStartOtherComponents() {
		assertFalse(ExternalLinks.isAllowed("intent://scan/#Intent;scheme=zxing;package=com.example;end"))
		assertFalse(ExternalLinks.isAllowed("file:///data/data/org.koitharu.kotatsu/shared_prefs/x.xml"))
		assertFalse(ExternalLinks.isAllowed("content://com.example.provider/secret"))
		assertFalse(ExternalLinks.isAllowed("javascript:alert(1)"))
		assertFalse(ExternalLinks.isAllowed("market://details?id=x"))
		assertFalse(ExternalLinks.isAllowed("/relative/path"))
		assertFalse(ExternalLinks.isAllowed("https:///no-host"))
		assertFalse(ExternalLinks.isAllowed("https://user:password@example.org/"))
		assertFalse(ExternalLinks.isAllowed(""))
		assertFalse(ExternalLinks.isAllowed(null))
		assertFalse(ExternalLinks.isAllowed("https://exa mple.org/"))
	}
}
