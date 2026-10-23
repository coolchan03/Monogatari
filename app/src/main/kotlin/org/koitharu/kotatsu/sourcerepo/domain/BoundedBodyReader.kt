package org.koitharu.kotatsu.sourcerepo.domain

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [maxBytes] from this stream. Fails with [IOException] as soon as more bytes
 * arrive, so a hostile or decompressed-oversized body is never fully buffered.
 */
internal fun InputStream.readBoundedBytes(maxBytes: Long): ByteArray {
	val output = ByteArrayOutputStream()
	val buffer = ByteArray(8192)
	var total = 0L
	while (true) {
		val read = read(buffer)
		if (read < 0) break
		total += read
		if (total > maxBytes) throw IOException("Response is unexpectedly large")
		output.write(buffer, 0, read)
	}
	return output.toByteArray()
}

/** Bounded replacement for [ResponseBody.string]; honours the declared charset (UTF-8 default). */
internal fun ResponseBody.readBoundedString(maxBytes: Long): String {
	val declared = contentLength()
	if (declared > maxBytes) throw IOException("Response is unexpectedly large")
	val bytes = byteStream().use { it.readBoundedBytes(maxBytes) }
	return String(bytes, contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}
