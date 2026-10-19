package org.koitharu.kotatsu.backups.domain

import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Read-only decoder for Mihon / Tachiyomi `.tachibk` backups (gzip-compressed protobuf).
 *
 * Only the fields a library migration needs are read: manga (source id, url, title, author, genres, favourite flag,
 * categories), chapters (url, name, number, read, bookmark, last page), reading history, categories and the
 * source-id -> source-name table. Unknown fields are skipped, so newer backup versions still decode.
 * Field numbers follow Mihon's `Backup.kt` protobuf schema.
 */
internal object MihonBackup {

	/** Expanded-size guard: a library backup with tens of thousands of titles is still far below this. */
	const val MAX_EXPANDED_BYTES = 64 * 1024 * 1024

	data class Backup(
		val manga: List<Manga>,
		val categories: List<Category>,
		val sources: Map<Long, String>,
	)

	data class Manga(
		val sourceId: Long,
		val url: String,
		val title: String,
		val author: String?,
		val artist: String?,
		val description: String?,
		val genres: List<String>,
		val thumbnailUrl: String?,
		val favorite: Boolean,
		val dateAdded: Long,
		val categoryOrders: List<Long>,
		val chapters: List<Chapter>,
		val history: List<History>,
	)

	data class Chapter(
		val url: String,
		val name: String,
		val number: Float,
		val read: Boolean,
		val bookmark: Boolean,
		val lastPageRead: Long,
	)

	data class History(val chapterUrl: String, val lastRead: Long)

	data class Category(val name: String, val order: Long)

	fun read(input: InputStream): Backup {
		val bytes = GZIPInputStream(input).use { readBounded(it) }
		return decode(bytes)
	}

	/** Decodes an already-decompressed protobuf `Backup` message. */
	fun decode(bytes: ByteArray): Backup {
		val manga = ArrayList<Manga>()
		val categories = ArrayList<Category>()
		val sources = LinkedHashMap<Long, String>()
		Reader(bytes, 0, bytes.size).forEachField { field, wire, reader ->
			when {
				field == 1 && wire == WIRE_LEN -> manga += decodeManga(reader.message())
				field == 2 && wire == WIRE_LEN -> decodeCategory(reader.message())?.let { categories += it }
				field == 101 && wire == WIRE_LEN -> decodeSource(reader.message())?.let { sources[it.first] = it.second }
				else -> reader.skip(wire)
			}
		}
		return Backup(manga, categories, sources)
	}

	private fun decodeManga(r: Reader): Manga {
		var source = 0L
		var url = ""
		var title = ""
		var artist: String? = null
		var author: String? = null
		var description: String? = null
		val genres = ArrayList<String>()
		var thumbnail: String? = null
		var dateAdded = 0L
		var favorite = true // proto2 default in Mihon's schema
		val categories = ArrayList<Long>()
		val chapters = ArrayList<Chapter>()
		val history = ArrayList<History>()
		r.forEachField { field, wire, reader ->
			when (field) {
				1 -> source = reader.varintOr(wire, source)
				2 -> url = reader.stringOr(wire, url)
				3 -> title = reader.stringOr(wire, title)
				4 -> artist = reader.stringOr(wire, "").ifBlank { null }
				5 -> author = reader.stringOr(wire, "").ifBlank { null }
				6 -> description = reader.stringOr(wire, "").ifBlank { null }
				7 -> reader.stringOr(wire, "").takeIf { it.isNotBlank() }?.let { genres += it }
				9 -> thumbnail = reader.stringOr(wire, "").ifBlank { null }
				13 -> dateAdded = reader.varintOr(wire, dateAdded)
				16 -> if (wire == WIRE_LEN) chapters += decodeChapter(reader.message()) else reader.skip(wire)
				17 -> reader.repeatedVarints(wire, categories)
				100 -> favorite = reader.varintOr(wire, 1L) != 0L
				104 -> if (wire == WIRE_LEN) decodeHistory(reader.message())?.let { history += it } else reader.skip(wire)
				else -> reader.skip(wire)
			}
		}
		return Manga(source, url, title, author, artist, description, genres, thumbnail, favorite, dateAdded,
			categories, chapters, history)
	}

	private fun decodeChapter(r: Reader): Chapter {
		var url = ""
		var name = ""
		var number = -1f
		var read = false
		var bookmark = false
		var lastPage = 0L
		r.forEachField { field, wire, reader ->
			when (field) {
				1 -> url = reader.stringOr(wire, url)
				2 -> name = reader.stringOr(wire, name)
				4 -> read = reader.varintOr(wire, 0L) != 0L
				5 -> bookmark = reader.varintOr(wire, 0L) != 0L
				6 -> lastPage = reader.varintOr(wire, 0L)
				9 -> number = if (wire == WIRE_I32) Float.fromBits(reader.fixed32()) else { reader.skip(wire); number }
				else -> reader.skip(wire)
			}
		}
		return Chapter(url, name, number, read, bookmark, lastPage)
	}

	private fun decodeHistory(r: Reader): History? {
		var url = ""
		var lastRead = 0L
		r.forEachField { field, wire, reader ->
			when (field) {
				1 -> url = reader.stringOr(wire, url)
				2 -> lastRead = reader.varintOr(wire, 0L)
				else -> reader.skip(wire)
			}
		}
		return if (url.isBlank()) null else History(url, lastRead)
	}

	private fun decodeCategory(r: Reader): Category? {
		var name = ""
		var order = 0L
		r.forEachField { field, wire, reader ->
			when (field) {
				1 -> name = reader.stringOr(wire, name)
				2 -> order = reader.varintOr(wire, 0L)
				else -> reader.skip(wire)
			}
		}
		return if (name.isBlank()) null else Category(name, order)
	}

	private fun decodeSource(r: Reader): Pair<Long, String>? {
		var name = ""
		var id = 0L
		r.forEachField { field, wire, reader ->
			when (field) {
				1 -> name = reader.stringOr(wire, name)
				2 -> id = reader.varintOr(wire, 0L)
				else -> reader.skip(wire)
			}
		}
		return if (name.isBlank()) null else id to name
	}

	private fun readBounded(input: InputStream): ByteArray {
		val out = java.io.ByteArrayOutputStream()
		val buffer = ByteArray(32 * 1024)
		while (true) {
			val n = input.read(buffer)
			if (n < 0) break
			if (out.size() + n > MAX_EXPANDED_BYTES) throw IOException("Mihon backup exceeds the 64 MB limit")
			out.write(buffer, 0, n)
		}
		return out.toByteArray()
	}

	private const val WIRE_VARINT = 0
	private const val WIRE_I64 = 1
	private const val WIRE_LEN = 2
	private const val WIRE_I32 = 5

	/** Minimal protobuf wire-format reader over [bytes] in `[start, end)`. Malformed input throws IOException. */
	private class Reader(private val bytes: ByteArray, start: Int, private val end: Int) {
		private var pos = start

		fun forEachField(block: (field: Int, wire: Int, reader: Reader) -> Unit) {
			while (pos < end) {
				val key = varint()
				val field = (key ushr 3).toInt()
				val wire = (key and 7).toInt()
				if (field <= 0) throw IOException("Corrupt Mihon backup (field $field)")
				block(field, wire, this)
			}
		}

		fun varint(): Long {
			var result = 0L
			var shift = 0
			while (shift < 64) {
				if (pos >= end) throw IOException("Truncated Mihon backup")
				val b = bytes[pos++].toInt()
				result = result or ((b and 0x7F).toLong() shl shift)
				if (b and 0x80 == 0) return result
				shift += 7
			}
			throw IOException("Corrupt Mihon backup (varint)")
		}

		fun fixed32(): Int {
			if (pos + 4 > end) throw IOException("Truncated Mihon backup")
			val v = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
				((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
			pos += 4
			return v
		}

		private fun lengthDelimited(): Pair<Int, Int> {
			val length = varint()
			if (length < 0 || length > end - pos) throw IOException("Truncated Mihon backup")
			val from = pos
			pos += length.toInt()
			return from to pos
		}

		fun message(): Reader {
			val (from, to) = lengthDelimited()
			return Reader(bytes, from, to)
		}

		fun varintOr(wire: Int, fallback: Long): Long = if (wire == WIRE_VARINT) varint() else { skip(wire); fallback }

		fun stringOr(wire: Int, fallback: String): String = if (wire == WIRE_LEN) {
			val (from, to) = lengthDelimited()
			String(bytes, from, to - from, Charsets.UTF_8)
		} else {
			skip(wire); fallback
		}

		/** Repeated int64: accepts both packed and unpacked encodings. */
		fun repeatedVarints(wire: Int, out: MutableList<Long>) {
			when (wire) {
				WIRE_VARINT -> out += varint()
				WIRE_LEN -> {
					val packed = message()
					while (packed.pos < packed.end) out += packed.varint()
				}
				else -> skip(wire)
			}
		}

		fun skip(wire: Int) {
			when (wire) {
				WIRE_VARINT -> varint()
				WIRE_I64 -> advance(8)
				WIRE_LEN -> lengthDelimited()
				WIRE_I32 -> advance(4)
				else -> throw IOException("Unsupported protobuf wire type $wire")
			}
		}

		private fun advance(n: Int) {
			if (pos + n > end) throw IOException("Truncated Mihon backup")
			pos += n
		}
	}
}
