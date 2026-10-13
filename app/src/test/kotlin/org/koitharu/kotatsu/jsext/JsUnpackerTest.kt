package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsUnpackerTest {

	private val packed = """eval(function(p,a,c,k,e,d){e=function(c){return c.toString(36)};if(!''.replace(/^/,String)){while(c--){d[c.toString(a)]=k[c]||c.toString(a)}k=[function(e){return d[e]}];e=function(){return'\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\b'+e(c)+'\\b','g'),k[c])}}return p}('0 1="2";3(1)',4,4,'var|file|hello|play'.split('|'),0,{}))"""

	@Test
	fun unpacksDeanEdwardsPacker() {
		// Expected output produced by evaluating the packed script in Node.
		assertEquals("""var file="hello";play(file)""", JsUnpacker.unpack(packed))
	}

	@Test
	fun unpacksBase62Words() {
		val script = """}('a b',62,12,'||||||||||sources|file'.split('|'),0,{}))"""
		assertEquals("sources file", JsUnpacker.unpack(script))
	}

	@Test
	fun combinesSeveralBlocksAndIgnoresPlainScripts() {
		assertEquals("""var file="hello";play(file) var file="hello";play(file)""", JsUnpacker.unpackAndCombine("$packed;\n$packed"))
		assertNull(JsUnpacker.unpack("var x = 1;"))
	}
}
