package org.koitharu.kotatsu.jsext.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koitharu.kotatsu.jsext.JsItemType
import java.io.File
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption

/**
 * Installed JS sources, one `<id>.js` plus `<id>.json` (metadata) per source under [dir].
 * Plain `java.io.File` on purpose, so it is testable on a JVM.
 */
class JsSourceStore(private val dir: File) {

	data class Installed(val entry: JsSourceEntry, val code: String)

	private val _changes = MutableStateFlow(0)

	/** Ticks whenever a source is installed, updated or removed. */
	val changes: StateFlow<Int> = _changes.asStateFlow()

    init {
        recoverInterruptedInstalls()
        JsSourceRegistry.replaceAll(list())
    }

	private fun publish() {
		JsSourceRegistry.replaceAll(list())
		_changes.value++
	}

    /**
     * Stage both files before replacement. A marker survives a process crash; initialization
     * then restores the previous complete pair instead of starting with mismatched code/metadata.
     */
    @Synchronized
    fun install(entry: JsSourceEntry, code: String) {
        dir.mkdirs()
        val id = entry.id
        val stagedJs = File(dir, "$id.js.new")
        val stagedMeta = File(dir, "$id.json.new")
        val currentJs = File(dir, "$id.js")
        val currentMeta = File(dir, "$id.json")
        stagedJs.writeText(code)
        stagedMeta.writeText(toJson(entry).toString())
        val previousExists = currentJs.isFile && currentMeta.isFile
        if (previousExists) {
            currentJs.copyTo(File(dir, "$id.js.prev"), overwrite = true)
            currentMeta.copyTo(File(dir, "$id.json.prev"), overwrite = true)
        } else {
            File(dir, "$id.js.prev").delete()
            File(dir, "$id.json.prev").delete()
        }
        val marker = File(dir, "$id.updating")
        marker.writeText("pending")
        try {
            replace(stagedJs, currentJs)
            replace(stagedMeta, currentMeta)
            marker.delete()
            if (previousExists) {
                File(dir, "$id.updated_at").writeText(System.currentTimeMillis().toString())
            } else {
                File(dir, "$id.updated_at").delete()
            }
            publish()
        } catch (error: Exception) {
            recoverInterruptedInstalls()
            publish()
            throw error
        }
    }

    @Synchronized
    fun hasPrevious(id: Long): Boolean =
        File(dir, "$id.js.prev").isFile && File(dir, "$id.json.prev").isFile

    @Synchronized
    fun isFreshUpdate(id: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!hasPrevious(id)) return false
        val at = File(dir, "$id.updated_at").takeIf { it.isFile }
            ?.readText()?.trim()?.toLongOrNull() ?: return false
        return now >= at && now - at <= 48L * 60 * 60 * 1000
    }

    @Synchronized
    fun confirmUpdate(id: Long) {
        if (!isFreshUpdate(id)) return
        File(dir, "$id.js.prev").delete()
        File(dir, "$id.json.prev").delete()
        File(dir, "$id.updated_at").delete()
    }

    @Synchronized
    fun isUpdatePaused(id: Long, now: Long = System.currentTimeMillis()): Boolean =
        File(dir, "$id.pause").takeIf { it.isFile }?.readText()
            ?.trim()?.toLongOrNull()?.let { it > now } == true

    /** A broken update returns to its last full installation and pauses automatic updates. */
    @Synchronized
    fun rollback(id: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!hasPrevious(id)) return false
        val marker = File(dir, "$id.updating")
        marker.writeText("rollback")
        restorePrevious(id)
        marker.delete()
        discardPrevious(id)
        File(dir, "$id.updated_at").delete()
        File(dir, "$id.pause").writeText((now + ROLLBACK_PAUSE_MS).toString())
        publish()
        return true
    }

    @Synchronized
    fun uninstall(id: Long) {
        listOf(".json", ".js", ".json.new", ".js.new", ".json.prev",
            ".js.prev", ".updating", ".pause", ".updated_at").forEach { ext ->
            File(dir, "$id$ext").delete()
        }
        publish()
    }

    private fun replace(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Repairs installations interrupted between the code and metadata rename. */
    private fun recoverInterruptedInstalls() {
        dir.listFiles { f -> f.name.endsWith(".updating") }.orEmpty().forEach { marker ->
            val id = marker.name.removeSuffix(".updating").toLongOrNull() ?: return@forEach
            if (hasPrevious(id)) {
                restorePrevious(id)
            } else {
                File(dir, "$id.js").delete()
                File(dir, "$id.json").delete()
            }
            marker.delete()
            discardPrevious(id)
            File(dir, "$id.js.new").delete()
            File(dir, "$id.json.new").delete()
        }
    }

    private fun restorePrevious(id: Long) {
        if (!hasPrevious(id)) return
        val stagedJs = File(dir, "$id.js.new")
        val stagedMeta = File(dir, "$id.json.new")
        File(dir, "$id.js.prev").copyTo(stagedJs, overwrite = true)
        File(dir, "$id.json.prev").copyTo(stagedMeta, overwrite = true)
        replace(stagedJs, File(dir, "$id.js"))
        replace(stagedMeta, File(dir, "$id.json"))
    }

    private fun discardPrevious(id: Long) {
        File(dir, "$id.js.prev").delete()
        File(dir, "$id.json.prev").delete()
    }

    private companion object {
        const val ROLLBACK_PAUSE_MS = 48L * 60 * 60 * 1000
    }

	@Synchronized
	fun list(): List<JsSourceEntry> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
		.mapNotNull { runCatching { fromJson(File(it.path).readText()) }.getOrNull() }
		.filter { File(dir, "${it.id}.js").isFile }
		.sortedBy { it.name.lowercase() }

	@Synchronized
	fun get(id: Long): Installed? {
		val meta = File(dir, "$id.json").takeIf { it.isFile } ?: return null
		val code = File(dir, "$id.js").takeIf { it.isFile } ?: return null
		val entry = runCatching { fromJson(meta.readText()) }.getOrNull() ?: return null
		return Installed(entry, code.readText())
	}

	fun installedVersion(id: Long): String? = get(id)?.entry?.version

	private fun toJson(e: JsSourceEntry): JsonObject = buildJsonObject {
		put("id", e.id)
		put("name", e.name)
		put("baseUrl", e.baseUrl)
		put("lang", e.lang)
		put("version", e.version)
		put("itemType", e.itemType.code)
		put("sourceCodeUrl", e.sourceCodeUrl)
		put("iconUrl", e.iconUrl)
		put("apiUrl", e.apiUrl)
		put("dateFormat", e.dateFormat)
		put("dateFormatLocale", e.dateFormatLocale)
		put("isNsfw", e.isNsfw)
		put("hasCloudflare", e.hasCloudflare)
		put("additionalParams", e.additionalParams)
		put("notes", e.notes)
		put("repoUrl", e.repoUrl)
		e.sha256?.let { put("sha256", it) }
	}

	private fun fromJson(text: String): JsSourceEntry {
		val o = Json.parseToJsonElement(text) as JsonObject
		fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
		fun b(k: String) = (o[k] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
		return JsSourceEntry(
			id = s("id").toLong(),
			name = s("name"),
			baseUrl = s("baseUrl"),
			lang = s("lang"),
			version = s("version"),
			itemType = JsItemType.fromCode(s("itemType").toIntOrNull()),
			language = SourceCodeLanguage.JAVASCRIPT,
			sourceCodeUrl = s("sourceCodeUrl"),
			iconUrl = s("iconUrl"),
			apiUrl = s("apiUrl"),
			dateFormat = s("dateFormat"),
			dateFormatLocale = s("dateFormatLocale"),
			isNsfw = b("isNsfw"),
			hasCloudflare = b("hasCloudflare"),
			additionalParams = s("additionalParams"),
			notes = s("notes"),
			repoUrl = s("repoUrl"),
			sha256 = s("sha256").ifEmpty { null },
		)
	}
}
