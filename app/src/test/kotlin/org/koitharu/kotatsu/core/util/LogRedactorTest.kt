package org.koitharu.kotatsu.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

	private fun assertNoSecret(secret: String, text: String) =
		assertFalse("leaked '$secret' in: $text", LogRedactor.redact(text).contains(secret))

	@Test
	fun queryParametersAreRedactedButOthersKept() {
		val out = LogRedactor.redact("https://x.test/api?page=3&token=abc123SECRET&q=naruto&api_key=k9")
		assertTrue(out.contains("page=3"))
		assertTrue(out.contains("q=naruto"))
		assertFalse(out.contains("abc123SECRET"))
		assertFalse(out.contains("k9"))
	}

	@Test
	fun userinfoInUrlIsRemoved() {
		assertNoSecret("hunter2", "https://alice:hunter2@host.test/opds")
		assertTrue(LogRedactor.redact("https://alice:hunter2@host.test/opds").contains("host.test/opds"))
	}

	@Test
	fun jsonBodiesAreRedacted() {
		val body = """{"username":"alice","password":"p@ss w0rd","nested":{"access_token":"tok123","n":5},"refreshToken":"r456"}"""
		val out = LogRedactor.redact(body)
		assertTrue(out.contains("alice"))
		assertTrue(out.contains("\"n\":5"))
		listOf("p@ss w0rd", "tok123", "r456").forEach { assertFalse(out, out.contains(it)) }
	}

	@Test
	fun unquotedAndNumericJsonSecretsAreRedacted() {
		val out = LogRedactor.redact("""{"pin": 123456, "secret": true}""")
		assertFalse(out.contains("123456"))
		assertFalse(out.contains("true"))
	}

	@Test
	fun formEncodedCredentialsAreRedacted() {
		val out = LogRedactor.redact("username=alice&password=s3cr%26t&remember=1&csrf_token=zzz")
		assertTrue(out.contains("username=alice"))
		assertTrue(out.contains("remember=1"))
		assertFalse(out.contains("s3cr"))
		assertFalse(out.contains("zzz"))
	}

	@Test
	fun headerDumpsAreRedacted() {
		val dump = "Authorization: Bearer abcdef123456\nCookie: sessionid=xyz; a=b\nAccept: text/html\nX-Api-Key: kkk111\nSet-Cookie: sid=777"
		val out = LogRedactor.redact(dump)
		assertTrue(out.contains("Accept: text/html"))
		listOf("abcdef123456", "xyz", "kkk111", "777").forEach { assertFalse(out, out.contains(it)) }
	}

	@Test
	fun inlineBearerInsideExceptionMessage() {
		assertNoSecret("eyJhbGciOi.payload.sig", "401 for request with Bearer eyJhbGciOi.payload.sig rejected")
	}

	@Test
	fun headerHelperMasksOnlySensitiveHeaders() {
		assertEquals(LogRedactor.MASK, LogRedactor.header("Authorization", "Bearer x"))
		assertEquals(LogRedactor.MASK, LogRedactor.header("cookie", "a=b"))
		assertEquals("text/html", LogRedactor.header("Accept", "text/html"))
	}

	@Test
	fun ordinaryTextAndBlankAreUntouched() {
		assertEquals("Chapter 12 failed: HTTP 503", LogRedactor.redact("Chapter 12 failed: HTTP 503"))
		assertEquals("", LogRedactor.url(null))
		assertEquals("https://x.test/a/b?page=2", LogRedactor.url("https://x.test/a/b?page=2"))
	}

	@Test
	fun malformedAndOddPayloadsDoNotThrow() {
		listOf("", "{", "}}}", "=&=&", "\"password\"", "password=", "{\"password\":", "\u0000\u0001").forEach {
			LogRedactor.redact(it)
		}
	}
}
