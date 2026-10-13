package org.koitharu.kotatsu.backups.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * A Mangayomi backup (`.backup`: a ZIP holding one JSON document; a bare `.json` export works too).
 *
 * Mangayomi writes some numbers as strings and renamed fields between releases (`isManga` became
 * `itemType`), so every field is read leniently and anything unusable is skipped instead of failing
 * the whole file.
 */
internal class MangayomiBackup(
	val manga: List<Manga>,
	val chapters: List<Chapter>,
	val categories: List<Category>,
	val history: List<History>,
	val extensions: List<Extension>,
) {

	data class Manga(
		val id: Long,
		val name: String,
		val link: String,
		val imageUrl: String?,
		val source: String,
		val lang: String,
		val sourceId: Long?,
		/** 0 manga, 1 anime, 2 novel — the same codes as `JsItemType`. */
		val itemType: Int,
		val favorite: Boolean,
		val categoryIds: List<Long>,
		val author: String?,
		val artist: String?,
		val description: String?,
		val genres: List<String>,
		val status: Int?,
		val dateAdded: Long,
	)

	data class Chapter(
		val id: Long,
		val mangaId: Long,
		val url: String,
		val name: String,
		val isRead: Boolean,
		val lastPageRead: Int,
		val dateUpload: Long,
	)

	data class Category(val id: Long, val name: String, val position: Int)

	data class History(val mangaId: Long, val chapterId: Long, val date: Long)

	data class Extension(val id: Long, val name: String, val lang: String, val itemType: Int)

	companion object {

		private const val MAX_JSON_BYTES = 64L * 1024 * 1024
		private val json = Json { isLenient = true }

		/** Reads either a Mangayomi `.backup` ZIP or its raw JSON. */
		fun read(input: InputStream): MangayomiBackup {
			val buffered = input.buffered()
			buffered.mark(4)
			val magic = ByteArray(2)
			val n = buffered.read(magic)
			buffered.reset()
			val text = if (n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
				readZip(ZipInputStream(buffered))
			} else {
				readBounded(buffered)
			}
			return parse(text)
		}

		private fun readZip(zip: ZipInputStream): String {
			while (true) {
				val entry = zip.nextEntry ?: break
				if (!entry.isDirectory) {
					val text = readBounded(zip)
					if (text.trimStart().startsWith("{")) return text
				}
				zip.closeEntry()
			}
			throw IOException("No Mangayomi backup data in this archive")
		}

		private fun readBounded(input: InputStream): String {
			val out = java.io.ByteArrayOutputStream()
			val buffer = ByteArray(32 * 1024)
			var total = 0L
			while (true) {
				val n = input.read(buffer)
				if (n < 0) break
				total += n
				if (total > MAX_JSON_BYTES) throw IOException("Backup is too large")
				out.write(buffer, 0, n)
			}
			return out.toString(Charsets.UTF_8.name())
		}

		fun parse(text: String): MangayomiBackup {
			val root = try {
				json.parseToJsonElement(text) as? JsonObject
			} catch (e: Exception) {
				throw IOException("Not a Mangayomi backup", e)
			} ?: throw IOException("Not a Mangayomi backup")
			val manga = root.array("manga", "mangas").mapNotNull { it.obj()?.toManga() }
			if (manga.isEmpty() && root.array("history").isEmpty()) {
				throw IOException("This Mangayomi backup has no library or history")
			}
			return MangayomiBackup(
				manga = manga,
				chapters = root.array("chapters").mapNotNull { it.obj()?.toChapter() },
				categories = root.array("categories").mapNotNull { it.obj()?.toCategory() },
				history = root.array("history").mapNotNull { it.obj()?.toHistory() },
				extensions = root.array("extensions", "sources").mapNotNull { it.obj()?.toExtension() },
			)
		}

		private fun JsonObject.toManga(): Manga? {
			val name = string("name")?.takeIf { it.isNotBlank() } ?: return null
			val link = string("link")?.takeIf { it.isNotBlank() } ?: return null
			val source = string("source")?.takeIf { it.isNotBlank() } ?: return null
			return Manga(
				id = long("id") ?: return null,
				name = name.trim(),
				link = link,
				imageUrl = string("customCoverImage")?.takeIf { it.startsWith("http") }
					?: string("imageUrl")?.takeIf { it.isNotBlank() },
				source = source,
				lang = string("lang").orEmpty().lowercase(Locale.ROOT),
				sourceId = long("sourceId"),
				itemType = itemType(),
				favorite = bool("favorite") ?: false,
				categoryIds = array("categories").mapNotNull { (it as? JsonPrimitive)?.lenientLong() },
				author = string("author")?.takeIf { it.isNotBlank() },
				artist = string("artist")?.takeIf { it.isNotBlank() },
				description = string("description")?.takeIf { it.isNotBlank() },
				genres = array("genre").mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) },
				status = long("status")?.toInt(),
				dateAdded = long("dateAdded") ?: 0L,
			)
		}

		private fun JsonObject.toChapter(): Chapter? {
			return Chapter(
				id = long("id") ?: return null,
				mangaId = long("mangaId") ?: return null,
				url = string("url")?.takeIf { it.isNotBlank() } ?: return null,
				name = string("name").orEmpty(),
				isRead = bool("isRead") ?: false,
				lastPageRead = string("lastPageRead")?.toDoubleOrNull()?.toInt() ?: 0,
				dateUpload = long("dateUpload") ?: 0L,
			)
		}

		private fun JsonObject.toCategory(): Category? {
			return Category(
				id = long("id") ?: return null,
				name = string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
				position = long("pos")?.toInt() ?: 0,
			)
		}

		private fun JsonObject.toHistory(): History? {
			return History(
				mangaId = long("mangaId") ?: return null,
				chapterId = long("chapterId") ?: return null,
				date = long("date") ?: 0L,
			)
		}

		private fun JsonObject.toExtension(): Extension? {
			return Extension(
				id = long("id") ?: return null,
				name = string("name")?.takeIf { it.isNotBlank() } ?: return null,
				lang = string("lang").orEmpty().lowercase(Locale.ROOT),
				itemType = itemType(),
			)
		}

		/** `itemType` (newer) or `isManga` (older: true = manga, false = anime). */
		private fun JsonObject.itemType(): Int {
			long("itemType")?.let { return it.toInt().coerceIn(0, 2) }
			return if (bool("isManga") == false) 1 else 0
		}

		private fun JsonElement.obj(): JsonObject? = this as? JsonObject

		private fun JsonObject.array(vararg keys: String): JsonArray {
			for (key in keys) {
				(this[key] as? JsonArray)?.let { return it }
			}
			return JsonArray(emptyList())
		}

		private fun JsonObject.string(key: String): String? =
			(this[key] as? JsonPrimitive)?.contentOrNull

		private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.lenientLong()

		private fun JsonObject.bool(key: String): Boolean? {
			val p = this[key] as? JsonPrimitive ?: return null
			return p.booleanOrNull ?: when (p.contentOrNull?.lowercase(Locale.ROOT)) {
				"1" -> true
				"0" -> false
				else -> null
			}
		}

		private fun JsonPrimitive.lenientLong(): Long? =
			longOrNull ?: contentOrNull?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
	}
}

/**
 * Decides where each Mangayomi title goes. Monogatari runs Mangayomi's JavaScript extensions unchanged, so a
 * title whose extension is (or can be) installed here keeps its exact link and opens straight away.
 */
internal object MangayomiImportPlanner {

	/** A JS source known to this app: installed already, or offered by one of the user's repositories. */
	data class Source(val id: Long, val name: String, val lang: String, val itemType: Int, val installed: Boolean)

	data class Plan(
		/** Mangayomi manga id -> the JS source it maps to. */
		val matched: Map<Long, Source>,
		/** Sources that must be installed before importing. */
		val toInstall: List<Source>,
		/** Titles with no runnable extension here; kept with their old source name so they can be migrated. */
		val unmatched: List<MangayomiBackup.Manga>,
		/** Titles that will be imported (library favourites and anything with reading history). */
		val titles: List<MangayomiBackup.Manga>,
	)

	fun plan(backup: MangayomiBackup, available: Collection<Source>): Plan {
		val withHistory = backup.history.mapTo(HashSet()) { it.mangaId }
		val titles = backup.manga.filter { it.favorite || it.id in withHistory }
		val extensionIds = backup.extensions.associateBy({ key(it.name, it.lang, it.itemType) }, { it.id })
		val byId = available.groupBy { it.id }
		val byKey = available.groupBy { key(it.name, it.lang, it.itemType) }
		val byName = available.groupBy { normalize(it.name) to it.itemType }
		val matched = LinkedHashMap<Long, Source>()
		val unmatched = ArrayList<MangayomiBackup.Manga>()
		for (manga in titles) {
			val id = manga.sourceId ?: extensionIds[key(manga.source, manga.lang, manga.itemType)]
			val source = id?.let { byId[it]?.preferInstalled() }
				?: byKey[key(manga.source, manga.lang, manga.itemType)]?.preferInstalled()
				// "all"-language sources and sources whose language code changed between releases.
				?: byName[normalize(manga.source) to manga.itemType]?.singleOrNull()
			if (source != null) matched[manga.id] = source else unmatched += manga
		}
		val toInstall = matched.values.filterNot { it.installed }.distinctBy { it.id }
		return Plan(matched, toInstall, unmatched, titles)
	}

	/** Name the importer stores for a title whose source isn't available, so it still shows up for migration. */
	fun fallbackSourceName(manga: MangayomiBackup.Manga): String =
		"MANGAYOMI_" + manga.source.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]+"), "_").trim('_')

	/**
	 * Reading position for one title: the chapter Mangayomi last opened (or, without history, the latest
	 * read chapter), the page in it, and overall progress as the share of chapters marked read.
	 */
	data class Progress(val chapter: MangayomiBackup.Chapter, val page: Int, val percent: Float, val chapters: Int, val date: Long)

	fun progress(manga: MangayomiBackup.Manga, backup: MangayomiBackup): Progress? {
		val chapters = backup.chapters.filter { it.mangaId == manga.id }
		val last = backup.history.filter { it.mangaId == manga.id }.maxByOrNull { it.date }
		val chapter = last?.let { h -> chapters.firstOrNull { it.id == h.chapterId } }
			?: chapters.filter { it.isRead }.maxWithOrNull(compareBy({ it.dateUpload }, { it.id }))
			?: return null
		val read = chapters.count { it.isRead }
		val percent = if (chapters.isEmpty()) -1f else (read.toFloat() / chapters.size).coerceIn(0f, 1f)
		return Progress(
			chapter = chapter,
			page = (chapter.lastPageRead - 1).coerceAtLeast(0),
			percent = percent,
			chapters = chapters.size,
			date = last?.date?.takeIf { it > 0 } ?: manga.dateAdded,
		)
	}

	private fun List<Source>.preferInstalled(): Source? = firstOrNull { it.installed } ?: firstOrNull()

	private fun key(name: String, lang: String, itemType: Int) = Triple(normalize(name), lang.lowercase(Locale.ROOT), itemType)

	private fun normalize(name: String) = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
