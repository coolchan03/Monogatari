package org.koitharu.kotatsu.sourcerepo

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.koitharu.kotatsu.sourcerepo.domain.readBoundedBytes
import org.koitharu.kotatsu.sourcerepo.domain.readBoundedString
import java.io.ByteArrayInputStream
import java.io.IOException

class BoundedBodyReaderTest {

	@Test
	fun readsUpToExactLimit() {
		val data = ByteArray(100) { it.toByte() }
		assertArrayEquals(data, ByteArrayInputStream(data).readBoundedBytes(100))
	}

	@Test
	fun rejectsOneByteOverLimit() {
		assertThrows(IOException::class.java) {
			ByteArrayInputStream(ByteArray(101)).readBoundedBytes(100)
		}
	}

	@Test
	fun unknownLengthBodyIsStillBounded() {
		// asResponseBody(contentType, contentLength = -1) models a chunked/decompressed body.
		val body = Buffer().writeUtf8("x".repeat(50)).asResponseBody("text/plain".toMediaType(), -1)
		assertThrows(IOException::class.java) { body.readBoundedString(10) }
	}

	@Test
	fun declaredLengthOverLimitFailsEarly() {
		val body = Buffer().writeUtf8("x".repeat(50)).asResponseBody("text/plain".toMediaType(), 50)
		assertThrows(IOException::class.java) { body.readBoundedString(10) }
	}

	@Test
	fun decodesMultibyteUtf8WithinByteLimit() {
		val text = "漫画"
		val bytes = text.toByteArray(Charsets.UTF_8)
		val body = Buffer().write(bytes).asResponseBody("text/plain".toMediaType(), -1)
		assertEquals(text, body.readBoundedString(bytes.size.toLong()))
	}
}
