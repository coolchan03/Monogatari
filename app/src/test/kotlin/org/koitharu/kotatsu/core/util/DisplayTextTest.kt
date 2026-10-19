package org.koitharu.kotatsu.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTextTest {

	@Test
	fun decodesEntitiesFromFeedsAndTheTranslator() {
		assertEquals("Don't stop", DisplayText.clean("Don&#39;t stop"))
		assertEquals("Don’t stop", DisplayText.clean("Don&rsquo;t stop"))
		assertEquals("Don’t stop", DisplayText.clean("Don&#8217;t stop"))
		assertEquals("\"Run!\" she said", DisplayText.clean("&quot;Run!&quot; she said"))
		assertEquals("Tom & Jerry", DisplayText.clean("Tom &amp; Jerry"))
		assertEquals("It's", DisplayText.clean("It&amp;#39;s"))
		assertEquals("Wait…", DisplayText.clean("Wait&hellip;"))
	}

	@Test
	fun repairsUtf8ReadAsWindows1252() {
		assertEquals("don’t", DisplayText.clean("donâ€™t"))
		assertEquals("Café", DisplayText.clean("CafÃ©"))
		assertEquals("\u201CHello", DisplayText.clean("â€œHello"))
	}

	@Test
	fun leavesNormalTextAlone() {
		for (text in listOf("Plain title", "Café au lait", "Ångström", "AT&T", "5 & 6", "Tom &amp", "日本語のタイトル", "Âge tendre")) {
			assertEquals(text, DisplayText.clean(text))
		}
	}
}
