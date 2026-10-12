package org.koitharu.kotatsu.core.media.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.ArrayDeque
import java.util.zip.ZipInputStream

data class EpubChapter(val title: String, val path: String, val content: String) {
    val href: String get() = path
}

class EpubBook private constructor(
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
    val coverBytes: ByteArray?,
) {
    fun chapterHtml(href: String): String =
        chapters.firstOrNull { it.path == href || it.path.substringAfterLast('/') == href }?.content.orEmpty()

    companion object {
        fun open(file: File): EpubBook = fromBytes(file.readBytes())
        fun fromBytes(bytes: ByteArray): EpubBook = parse(bytes)
        private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
        private const val MAX_UNCOMPRESSED_BYTES = 256L * 1024 * 1024
	private fun parse(bytes: ByteArray): EpubBook {
		val entries = unzip(bytes)
		val container = entries["META-INF/container.xml"]
			?: throw IllegalArgumentException("EPUB is missing META-INF/container.xml")
		val containerDoc = xml(container)
		val opfPath = containerDoc.allElements
			.firstOrNull { it.localTag() == "rootfile" }
			?.attr("full-path")
			?.takeIf { it.isNotBlank() }
			?.let(::normalizePath)
			?: throw IllegalArgumentException("EPUB does not declare its package document")
		val opfBytes = entries[opfPath]
			?: throw IllegalArgumentException("EPUB package document '$opfPath' is missing")
		val opf = xml(opfBytes)

		val title = opf.firstByLocalTag("title")?.text()?.takeIf { it.isNotBlank() } ?: "Untitled"
		val author = opf.firstByLocalTag("creator")?.text()?.takeIf { it.isNotBlank() }

		val manifest = LinkedHashMap<String, ManifestItem>()
		opf.allElements.filter { it.localTag() == "item" }.forEach { item ->
			val id = item.attr("id")
			val href = item.attr("href")
			if (id.isNotBlank() && href.isNotBlank()) {
				manifest[id] = ManifestItem(
					href = href,
					mediaType = item.attr("media-type"),
					properties = item.attr("properties"),
				)
			}
		}

		val toc = readToc(entries, opfPath, manifest)
		val chapters = ArrayList<EpubChapter>()
		opf.allElements.filter { it.localTag() == "itemref" }.forEachIndexed { index, itemRef ->
			val idref = itemRef.attr("idref")
			val manifestItem = manifest[idref] ?: return@forEachIndexed
			val path = resolveRelative(opfPath, manifestItem.href)
			val contentBytes = entries[path] ?: return@forEachIndexed
			val chapterTitle = toc[path]?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
			chapters += EpubChapter(
				title = chapterTitle,
				path = path,
				content = decodeText(contentBytes),
			)
		}
		if (chapters.isEmpty()) {
			throw IllegalArgumentException("EPUB has no readable spine chapters")
		}
		val coverId = opf.allElements.firstOrNull { it.localTag() == "meta" && it.attr("name") == "cover" }?.attr("content")
		val cover = manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }
			?: coverId?.let { manifest[it] }
		return EpubBook(
			title = title,
			author = author,
			chapters = chapters,
			coverBytes = cover?.let { entries[resolveRelative(opfPath, it.href)] },
		)
	}

	private fun readToc(
		entries: Map<String, ByteArray>,
		opfPath: String,
		manifest: Map<String, ManifestItem>,
	): Map<String, String> {
		val result = LinkedHashMap<String, String>()

		// EPUB 3 navigation document.
		val navItem = manifest.values.firstOrNull { item ->
			item.properties.split(Regex("\\s+")).any { it == "nav" }
		}
		if (navItem != null) {
			val navPath = resolveRelative(opfPath, navItem.href)
			entries[navPath]?.let { bytes ->
				val nav = xml(bytes)
				nav.allElements.filter { it.localTag() == "a" }.forEach { link ->
					val href = link.attr("href").substringBefore('#')
					val label = link.text().trim()
					if (href.isNotBlank() && label.isNotBlank()) {
						result.putIfAbsent(resolveRelative(navPath, href), label)
					}
				}
			}
		}

		// EPUB 2 NCX fallback.
		val ncxItem = manifest.values.firstOrNull {
			it.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true)
		}
		if (ncxItem != null) {
			val ncxPath = resolveRelative(opfPath, ncxItem.href)
			entries[ncxPath]?.let { bytes ->
				val ncx = xml(bytes)
				ncx.allElements.filter { it.localTag() == "navPoint" }.forEach { point ->
					val label = point.allElements.firstOrNull { it.localTag() == "text" }?.text()?.trim()
					val src = point.allElements.firstOrNull { it.localTag() == "content" }
						?.attr("src")?.substringBefore('#')
					if (!label.isNullOrBlank() && !src.isNullOrBlank()) {
						result.putIfAbsent(resolveRelative(ncxPath, src), label)
					}
				}
			}
		}
		return result
	}

	private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
		val result = LinkedHashMap<String, ByteArray>()
		var total = 0L
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			while (true) {
				val entry = zip.nextEntry ?: break
				if (entry.isDirectory) {
					zip.closeEntry()
					continue
				}
				val path = normalizePath(entry.name)
				if (path.isBlank()) {
					zip.closeEntry()
					continue
				}
				val out = java.io.ByteArrayOutputStream()
				val buffer = ByteArray(16 * 1024)
				var entryTotal = 0L
				while (true) {
					val n = zip.read(buffer)
					if (n < 0) break
					entryTotal += n
					total += n
					if (entryTotal > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
						throw IllegalArgumentException("EPUB expands beyond the safe size limit")
					}
					out.write(buffer, 0, n)
				}
				result[path] = out.toByteArray()
				zip.closeEntry()
			}
		}
		return result
	}

	private fun xml(bytes: ByteArray): Document =
		Jsoup.parse(decodeText(bytes), "", Parser.xmlParser())

	private fun decodeText(bytes: ByteArray): String {
		val prefix = bytes.take(256).toByteArray().toString(Charsets.ISO_8859_1)
		val declared = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
			.find(prefix)?.groupValues?.getOrNull(1)
		val charset = declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
		return bytes.toString(charset)
	}

	private fun Document.firstByLocalTag(name: String): Element? =
		allElements.firstOrNull { it.localTag() == name }

	private fun Element.localTag(): String = tagName().substringAfterLast(':').lowercase()

	private fun resolveRelative(baseFile: String, href: String): String {
		val cleanHref = runCatching { URLDecoder.decode(href.substringBefore('#'), Charsets.UTF_8.name()) }
			.getOrDefault(href.substringBefore('#'))
			.replace('\\', '/')
		if (cleanHref.startsWith('/')) return normalizePath(cleanHref)
		val baseDir = baseFile.substringBeforeLast('/', "")
		return normalizePath(if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref")
	}

	private fun normalizePath(path: String): String {
		val stack = ArrayDeque<String>()
		path.replace('\\', '/').trimStart('/').split('/').forEach { segment ->
			when (segment) {
				"", "." -> Unit
				".." -> if (stack.isNotEmpty()) stack.removeLast()
				else -> stack.addLast(segment)
			}
		}
		return stack.joinToString("/")
	}

private data class ManifestItem(
        val href: String,
        val mediaType: String,
        val properties: String,
    )
    }
}
