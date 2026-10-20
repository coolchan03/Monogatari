package org.koitharu.kotatsu.backups.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.koitharu.kotatsu.jsext.repo.compareVersions
import org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * A Mangayomi backup (`.backup`: a ZIP holding one `<name>.backup.db` JSON document, written by Mangayomi's
 * `writeMangayomiBackupZip`; a bare `.json` export works too).
 *
 * Mangayomi writes some numbers as strings and renamed fields between releases (`isManga` became
 * `itemType`, `forManga` became `forItemType`), so every field is read leniently and anything unusable is
 * skipped instead of failing the whole file.
 */
internal class MangayomiBackup(
	val manga: List<Manga>,
	val chapters: List<Chapter>,
	val categories: List<Category>,
	val history: List<History>,
	val extensions: List<Extension>,
	/** Extension repository index URLs from the backup's settings (`manga/anime/novelExtensionsRepo`), unvalidated. */
	val settingsRepoUrls: List<String> = emptyList(),
) {

	/** Mangayomi encrypts backups (AES ZIP) when "Encrypt backups" is on; that can't be read here. */
	class EncryptedBackupException : IOException("This Mangayomi backup is password-protected")

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

	/** [itemType] is Mangayomi's `forItemType` (older: `forManga`); category ids are unique across item types. */
	data class Category(val id: Long, val name: String, val position: Int, val itemType: Int = 0)

	data class History(val mangaId: Long, val chapterId: Long, val date: Long)

	/**
	 * One entry of the backup's `extensions` list (Mangayomi's `Source.toJson`). Mangayomi lists every source of
	 * its repositories there; [isAdded] marks the ones the user installed, and those carry their [sourceCode].
	 */
	data class Extension(
		val id: Long,
		val name: String,
		val lang: String,
		val itemType: Int,
		val isAdded: Boolean = false,
		/** Mangayomi's `SourceCodeLanguage` index: 0 dart, 1 javascript, 2 mihon, 3 lnreader, 4 aidoku; `null` in old backups. */
		val codeLanguage: Int? = null,
		val sourceCode: String? = null,
		val sourceCodeUrl: String = "",
		val baseUrl: String = "",
		val apiUrl: String = "",
		val iconUrl: String = "",
		val version: String = "",
		val dateFormat: String = "",
		val dateFormatLocale: String = "",
		val additionalParams: String = "",
		val notes: String = "",
		val isNsfw: Boolean = false,
		val hasCloudflare: Boolean = false,
		/** `repo.jsonUrl`: the index this extension was installed from. */
		val repoUrl: String? = null,
	) {

		/** Only Mangayomi JavaScript extensions run here (unmodified, in QuickJS). */
		val isJavaScript: Boolean
			get() = when (codeLanguage) {
				LANGUAGE_JAVASCRIPT -> true
				null -> sourceCode?.contains("DefaultExtension") == true || sourceCodeUrl.endsWith(".js", ignoreCase = true)
				else -> false
			}
	}

	companion object {

		const val LANGUAGE_JAVASCRIPT = 1

		private const val MAX_JSON_BYTES = 64L * 1024 * 1024
		private const val MAX_INPUT_BYTES = 768L * 1024 * 1024

		/**
		 * Longer strings are blanked while reading. Installed Mihon/Aidoku extensions are stored in the backup as
		 * base64 APKs of several MB each, which would otherwise push a normal backup past [MAX_JSON_BYTES];
		 * nothing longer than this can be installed or imported anyway.
		 */
		private const val MAX_STRING_BYTES = 4 * 1024 * 1024
		private const val QUOTE = '"'.code.toByte()
		private const val BACKSLASH = '\\'.code.toByte()
		private val json = Json { isLenient = true }

		/** Reads either a Mangayomi `.backup` ZIP or its raw JSON. */
		fun read(input: InputStream): MangayomiBackup {
			val buffered = input.buffered()
			buffered.mark(8)
			val head = ByteArray(8)
			var n = 0
			while (n < head.size) {
				val r = buffered.read(head, n, head.size - n)
				if (r < 0) break
				n += r
			}
			buffered.reset()
			val text = if (isZip(head, n)) {
				if (isEncryptedZip(head, n)) throw EncryptedBackupException()
				readZip(ZipInputStream(buffered))
			} else {
				readScrubbed(buffered)
			}
			return parse(text)
		}

		/**
		 * Reads a staged backup file. ZIPs go through the central directory ([ZipFile]), which also copes with
		 * stored entries that use data descriptors, something a streaming [ZipInputStream] rejects.
		 */
		fun read(file: File): MangayomiBackup {
			val head = ByteArray(8)
			val n = file.inputStream().use { input ->
				var total = 0
				while (total < head.size) {
					val r = input.read(head, total, head.size - total)
					if (r < 0) break
					total += r
				}
				total
			}
			if (!isZip(head, n)) return file.inputStream().use { read(it) }
			if (isEncryptedZip(head, n)) throw EncryptedBackupException()
			val text = try {
				ZipFile(file).use { zip ->
					val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
					val ordered = entries.filter { it.name.endsWith(".backup.db", ignoreCase = true) } +
						entries.filterNot { it.name.endsWith(".backup.db", ignoreCase = true) }
					ordered.firstNotNullOfOrNull { entry ->
						zip.getInputStream(entry).use { readScrubbed(it) }.takeIf { it.trimStart().startsWith("{") }
					} ?: throw IOException("No Mangayomi backup data in this archive")
				}
			} catch (e: java.util.zip.ZipException) {
				// Fall back to the streaming reader for archives the central directory parser dislikes.
				file.inputStream().use { input -> readZip(ZipInputStream(input.buffered())) }
			}
			return parse(text)
		}

		private fun isZip(head: ByteArray, n: Int) =
			n >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

		/** Bit 0 of the first local header's general purpose flags. */
		private fun isEncryptedZip(head: ByteArray, n: Int) =
			n >= 8 && head[2] == 0x03.toByte() && head[3] == 0x04.toByte() && (head[6].toInt() and 1) == 1

		private fun readZip(zip: ZipInputStream): String {
			while (true) {
				val entry = try {
					zip.nextEntry
				} catch (e: java.util.zip.ZipException) {
					if (e.message?.contains("encrypted", ignoreCase = true) == true) throw EncryptedBackupException()
					throw e
				} ?: break
				if (!entry.isDirectory) {
					val text = readScrubbed(zip)
					if (text.trimStart().startsWith("{")) return text
				}
				zip.closeEntry()
			}
			throw IOException("No Mangayomi backup data in this archive")
		}

		/**
		 * Copies UTF-8 JSON while replacing every string value longer than [MAX_STRING_BYTES] with `""`. The quote
		 * and backslash bytes never occur inside multi-byte UTF-8 sequences, so a byte-level scan is exact.
		 */
		internal fun readScrubbed(input: InputStream): String {
			val out = ByteArrayOutputStream()
			val string = ByteArrayOutputStream()
			val buffer = ByteArray(32 * 1024)
			var total = 0L
			var inString = false
			var escape = false
			var dropping = false
			while (true) {
				val n = input.read(buffer)
				if (n < 0) break
				total += n
				if (total > MAX_INPUT_BYTES) throw IOException("Backup is too large")
				var i = 0
				while (i < n) {
					val b = buffer[i++]
					if (!inString) {
						// Copy structural runs in one go.
						var end = i
						while (end < n && buffer[end] != QUOTE) end++
						out.write(b.toInt())
						if (b == QUOTE) {
							inString = true
							dropping = false
							string.reset()
						} else {
							out.write(buffer, i, end - i)
							i = end
						}
						continue
					}
					if (escape) {
						escape = false
					} else if (b == BACKSLASH) {
						escape = true
					} else if (b == QUOTE) {
						inString = false
						if (!dropping) string.writeTo(out)
						string.reset()
						out.write(b.toInt())
						continue
					}
					if (!dropping) {
						string.write(b.toInt())
						if (string.size() > MAX_STRING_BYTES) {
							dropping = true
							string.reset()
						}
					}
				}
				if (out.size() > MAX_JSON_BYTES) throw IOException("Backup is too large")
			}
			if (inString && !dropping) string.writeTo(out)
			return out.toString(Charsets.UTF_8.name())
		}

		fun parse(text: String): MangayomiBackup {
			val root = try {
				json.parseToJsonElement(text) as? JsonObject
			} catch (e: Exception) {
				throw IOException("Not a Mangayomi backup", e)
			} ?: throw IOException("Not a Mangayomi backup")
			val manga = root.array("manga", "mangas").mapNotNull { it.obj()?.toManga() }
			val extensions = root.array("extensions", "sources").mapNotNull { it.obj()?.toExtension() }
			// A sources-only backup is still worth importing: it moves the user's extensions over.
			if (manga.isEmpty() && root.array("history").isEmpty() && extensions.none { it.isAdded }) {
				throw IOException("This Mangayomi backup has no library, history or extensions")
			}
			return MangayomiBackup(
				manga = manga,
				chapters = root.array("chapters").mapNotNull { it.obj()?.toChapter() },
				categories = root.array("categories").mapNotNull { it.obj()?.toCategory() },
				history = root.array("history").mapNotNull { it.obj()?.toHistory() },
				extensions = extensions,
				settingsRepoUrls = settingsRepoUrls(root),
			)
		}

		/** `settings` is a list holding one Settings object; each repo list is `[{jsonUrl,…}]` or, in old builds, a string. */
		private fun settingsRepoUrls(root: JsonObject): List<String> {
			val settings = when (val value = root["settings"]) {
				is JsonArray -> value.mapNotNull { it.obj() }
				is JsonObject -> listOf(value)
				else -> emptyList()
			}
			val keys = listOf("mangaExtensionsRepo", "animeExtensionsRepo", "novelExtensionsRepo")
			return settings.flatMap { s ->
				keys.flatMap { key ->
					when (val repos = s[key]) {
						is JsonPrimitive -> listOfNotNull(repos.contentOrNull)
						is JsonArray -> repos.mapNotNull { r ->
							(r as? JsonPrimitive)?.contentOrNull ?: r.obj()?.string("jsonUrl")
						}
						else -> emptyList()
					}
				}
			}.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
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
				itemType = long("forItemType")?.toInt()?.coerceIn(0, 2) ?: if (bool("forManga") == false) 1 else 0,
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
			val code = string("sourceCode")?.takeIf { it.isNotBlank() }
			return Extension(
				id = long("id") ?: return null,
				name = string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
				lang = string("lang").orEmpty().lowercase(Locale.ROOT),
				itemType = itemType(),
				// Older exports have no isAdded; only installed sources carry their code.
				isAdded = bool("isAdded") ?: (code != null),
				codeLanguage = long("sourceCodeLanguage")?.toInt(),
				sourceCode = code,
				sourceCodeUrl = string("sourceCodeUrl").orEmpty().trim(),
				baseUrl = string("baseUrl").orEmpty(),
				apiUrl = string("apiUrl").orEmpty(),
				iconUrl = string("iconUrl").orEmpty(),
				version = string("version").orEmpty(),
				dateFormat = string("dateFormat").orEmpty(),
				dateFormatLocale = string("dateFormatLocale").orEmpty(),
				additionalParams = string("additionalParams").orEmpty(),
				notes = string("notes").orEmpty(),
				isNsfw = bool("isNsfw") ?: false,
				hasCloudflare = bool("hasCloudflare") ?: false,
				repoUrl = (this["repo"] as? JsonObject)?.string("jsonUrl")?.trim()?.takeIf { it.isNotEmpty() },
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
	data class Source(
		val id: Long,
		val name: String,
		val lang: String,
		val itemType: Int,
		val installed: Boolean,
		val version: String = "",
	)

	/** Ways to install one of the backup's extensions, tried in [ExtensionInstall.attempts] order. */
	enum class InstallVia {
		/** The user's (or the backup's) repositories; checked against the index's SHA-256 when it has one. */
		REPOSITORY,

		/** The JavaScript code saved in the backup: the exact version the user ran in Mangayomi. */
		BACKUP_CODE,

		/** The extension's own https `sourceCodeUrl`. */
		SOURCE_URL,
	}

	data class ExtensionInstall(val extension: MangayomiBackup.Extension, val attempts: List<InstallVia>)

	data class Plan(
		/** Mangayomi manga id -> the JS source it maps to. */
		val matched: Map<Long, Source>,
		/** Repository sources needed by titles that are not already covered by [extensionInstalls]. */
		val toInstall: List<Source>,
		/** Titles with no runnable extension here; kept with their old source name so they can be migrated. */
		val unmatched: List<MangayomiBackup.Manga>,
		/** Titles that will be imported (library favourites and anything with reading history). */
		val titles: List<MangayomiBackup.Manga>,
		/** Extensions installed in Mangayomi that will be installed here. */
		val extensionInstalls: List<ExtensionInstall> = emptyList(),
		/** Extensions installed in Mangayomi that can't run here (Dart, Mihon, LNReader, Aidoku). */
		val unsupportedExtensions: List<MangayomiBackup.Extension> = emptyList(),
		/** Extensions installed in Mangayomi that are installed here already (left untouched). */
		val alreadyInstalled: Int = 0,
		/** Normalised https repository URLs from the backup that aren't configured here yet. */
		val newRepoUrls: List<String> = emptyList(),
	)

	/**
	 * @param available installed JS sources plus the runnable ones the user's repositories offer.
	 * @param knownRepos repository URLs already configured here.
	 */
	fun plan(
		backup: MangayomiBackup,
		available: Collection<Source>,
		knownRepos: Collection<String> = emptyList(),
	): Plan {
		val installedIds = available.filter { it.installed }.mapTo(HashSet()) { it.id }
		val offered = available.filterNot { it.installed }.groupBy { it.id }
		val installs = ArrayList<ExtensionInstall>()
		val unsupported = ArrayList<MangayomiBackup.Extension>()
		var alreadyInstalled = 0
		for (ext in backup.extensions.filter { it.isAdded }.distinctBy { it.id }) {
			if (ext.id in installedIds) {
				alreadyInstalled++
				continue
			}
			val repo = offered[ext.id]?.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
			if (!ext.isJavaScript && repo == null) {
				unsupported += ext
				continue
			}
			val code = ext.sourceCode?.takeIf { ext.isJavaScript && isInstallableCode(it) }
			// A repository copy at least as new as the backup's wins: it is current and hash-checked.
			val repoFirst = repo != null && (code == null || compareVersions(repo.version, ext.version) >= 0)
			val attempts = buildList {
				if (repoFirst) add(InstallVia.REPOSITORY)
				if (code != null) add(InstallVia.BACKUP_CODE)
				if (ext.isJavaScript && normalizeRepoUrl(ext.sourceCodeUrl) != null) add(InstallVia.SOURCE_URL)
				// Last resort: the repositories restored from this backup may offer it once they are added.
				if (!repoFirst) add(InstallVia.REPOSITORY)
			}
			installs += ExtensionInstall(ext, attempts)
		}
		val fromBackup = installs.map {
			Source(it.extension.id, it.extension.name, it.extension.lang, it.extension.itemType, installed = false, it.extension.version)
		}
		val candidates = available + fromBackup.filter { it.id !in offered }
		val withHistory = backup.history.mapTo(HashSet()) { it.mangaId }
		val titles = backup.manga.filter { it.favorite || it.id in withHistory }
		val extensionIds = backup.extensions.associateBy({ key(it.name, it.lang, it.itemType) }, { it.id })
		val byId = candidates.groupBy { it.id }
		val byKey = candidates.groupBy { key(it.name, it.lang, it.itemType) }
		val byName = candidates.groupBy { normalize(it.name) to it.itemType }
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
		val covered = installs.mapTo(HashSet()) { it.extension.id }
		val toInstall = matched.values.filterNot { it.installed || it.id in covered }.distinctBy { it.id }
		return Plan(
			matched = matched,
			toInstall = toInstall,
			unmatched = unmatched,
			titles = titles,
			extensionInstalls = installs,
			unsupportedExtensions = unsupported,
			alreadyInstalled = alreadyInstalled,
			newRepoUrls = repoUrls(backup, knownRepos),
		)
	}

	/**
	 * Repositories to add: the backup's settings lists plus the repos its JavaScript extensions came from, https only
	 * (the same [normalizeRepoUrl] check as adding a repository by hand). A repo referenced only by extensions that
	 * can't run here (e.g. a Mihon APK index) is left out.
	 */
	fun repoUrls(backup: MangayomiBackup, knownRepos: Collection<String> = emptyList()): List<String> {
		val known = knownRepos.mapNotNullTo(HashSet()) { normalizeRepoUrl(it) }
		val referencedBy = backup.extensions.filter { it.repoUrl != null }
			.groupBy { normalizeRepoUrl(it.repoUrl.orEmpty()) }
		val fromExtensions = backup.extensions.filter { it.isAdded && it.isJavaScript }.mapNotNull { it.repoUrl }
		return (backup.settingsRepoUrls + fromExtensions)
			.mapNotNull { normalizeRepoUrl(it) }
			.distinct()
			.filter { url -> referencedBy[url]?.none { it.isJavaScript } != true }
			.filterNot { it in known }
	}

	/** The same sanity checks `SourceRepoRepository.installJsSource` applies to downloaded code. */
	fun isInstallableCode(code: String): Boolean =
		code.length <= MAX_SOURCE_CHARS && code.contains("DefaultExtension")

	private const val MAX_SOURCE_CHARS = 2 * 1024 * 1024

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
