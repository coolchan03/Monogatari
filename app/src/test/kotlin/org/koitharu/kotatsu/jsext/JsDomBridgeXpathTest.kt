package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class JsDomBridgeXpathTest {

	private val html = """<html><body><div class="list"><a href="/one">One</a><a href="/two">Two <b>!</b></a></div></body></html>"""

	private fun call(name: String, vararg args: String): Any? =
		JsDomBridge().handle(name, JsonArray(args.map { JsonPrimitive(it) }))

	@Test
	fun xpathReturnsAttributesTextAndElementText() {
		assertEquals("/one", call("doc_xpath_first", html, "//div[@class='list']/a/@href"))
		assertEquals("""["/one","/two"]""", call("doc_xpath", html, "//a/@href"))
		assertEquals("Two", call("doc_xpath_first", html, "//a[2]/text()"))
		assertEquals("Two !", call("doc_xpath_first", html, "//a[2]"))
		assertEquals("", call("doc_xpath_first", html, "//a/@missing"))
	}
}
