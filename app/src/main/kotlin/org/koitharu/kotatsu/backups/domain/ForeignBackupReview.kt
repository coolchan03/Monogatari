package org.koitharu.kotatsu.backups.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Bounded, read-only format review. Foreign IDs are not applied to the local
 * database and no archive is extracted. Actual foreign import requires a schema
 * and a separate, user-approved transactional importer.
 */
internal object ForeignBackupReview {
    private const val JSON_LIMIT = 8L * 1024 * 1024
    private const val GZIP_EXPANDED_LIMIT = 64L * 1024 * 1024
    private val json = Json { isLenient = false }

    data class Report(
        val format: ForeignBackupFormat,
        val counts: Map<String, Int>,
        val details: String,
        val importSupported: Boolean = false,
    )

    fun inspect(file: File, fileName: String): Report {
        if (!file.isFile || file.length() !in 1L..BackupArchiveInspector.MAX_ZIP_BYTES) {
            throw IOException("Selected foreign archive is empty or too large")
        }
        val prefix = ByteArray(8192)
        val read = file.inputStream().use { it.read(prefix) }
        val format = ForeignBackupPreflight.detect(fileName,
            if (read > 0) prefix.copyOf(read) else byteArrayOf())
        return when (format) {
            ForeignBackupFormat.KOTATSU_ZIP -> {
                val archive = BackupArchiveInspector.inspect(file)
                Report(format, mapOf("Known sections" to archive.sections.size),
                    "Native backup ZIP passed section, size and checksum review. " +
                        "Use Settings > Backup & Restore to choose sections and restore.")
            }
            ForeignBackupFormat.MANGAYOMI_JSON -> inspectMangayomi(file)
            ForeignBackupFormat.MIHON_PROTOBUF_GZIP -> inspectMihonContainer(file)
            ForeignBackupFormat.UNKNOWN -> Report(format, emptyMap(),
                "Unrecognized file type. No records have been imported.")
        }
    }

    private fun inspectMangayomi(file: File): Report {
        if (file.length() > JSON_LIMIT) {
            throw IOException("Mangayomi JSON exceeds the 8 MB preview limit")
        }
        val root = try {
            json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject
        } catch (e: Exception) {
            throw IOException("Mangayomi JSON could not be parsed", e)
        } ?: throw IOException("Mangayomi JSON root must be an object")

        val groups = linkedMapOf(
            "Manga" to listOf("manga", "mangas"),
            "Sources" to listOf("sources", "extensions"),
            "History" to listOf("history"),
            "Categories" to listOf("categories"),
        )
        val counts = linkedMapOf<String, Int>()
        for ((title, aliases) in groups) {
            val value = root.entries.firstOrNull { (key, _) ->
                aliases.any { it.equals(key, ignoreCase = true) }
            }?.value
            val count = when (value) {
                is JsonArray -> value.size
                is JsonObject -> value.size
                else -> null
            }
            if (count != null) counts[title] = count
        }
        if (counts.size < 2) {
            throw IOException("Unrecognized Mangayomi JSON collection schema")
        }
        return Report(ForeignBackupFormat.MANGAYOMI_JSON, counts,
            "Collection sizes are a read-only preview, not verified compatible records. " +
                "Source IDs, progress and favourites cannot be applied until mapping is reviewed.")
    }

    private fun inspectMihonContainer(file: File): Report {
        var total = 0L
        val bytes = ByteArray(32 * 1024)
        try {
            GZIPInputStream(file.inputStream()).use { source ->
                while (true) {
                    val n = source.read(bytes)
                    if (n < 0) break
                    if (n == 0) continue
                    total += n
                    if (total > GZIP_EXPANDED_LIMIT) {
                        throw IOException("Mihon archive exceeds 64 MB expanded preview limit")
                    }
                }
            }
        } catch (e: IOException) {
            throw IOException("Mihon gzip preview failed integrity check: ${e.message}", e)
        }
        return Report(ForeignBackupFormat.MIHON_PROTOBUF_GZIP,
            mapOf("Unpacked bytes" to total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
            "Gzip integrity checked. Tachiyomi/Mihon protobuf records are not decoded or imported.")
    }
}
