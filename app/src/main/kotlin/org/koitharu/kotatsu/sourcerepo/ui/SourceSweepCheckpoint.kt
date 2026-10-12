package org.koitharu.kotatsu.sourcerepo.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream

/** Append-only progress journal: a killed process loses at most the active probes. */
internal class SourceSweepCheckpoint(private val file: File) {

    data class Record(val key: String, val status: String, val detail: String)

    fun read(): Map<String, Record> {
        if (!file.isFile) return emptyMap()
        val result = LinkedHashMap<String, Record>()
        file.forEachLine(Charsets.UTF_8) { line ->
            val data = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull()
                ?: return@forEachLine
            val key = (data["key"] as? JsonPrimitive)?.contentOrNull ?: return@forEachLine
            val status = (data["status"] as? JsonPrimitive)?.contentOrNull ?: return@forEachLine
            val detail = (data["detail"] as? JsonPrimitive)?.contentOrNull ?: return@forEachLine
            if (key.isNotBlank() && status in setOf("PASS", "PARTIAL", "FAIL")) {
                result[key] = Record(key, status, detail)
            }
        }
        return result
    }

    fun append(record: Record) {
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            put("key", record.key)
            put("status", record.status)
            put("detail", record.detail)
        }
        // The leading newline also recovers from a truncated final JSON record after a crash.
        FileOutputStream(file, true).use { stream ->
            stream.write(("\n" + json.toString()).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    fun clear() {
        if (file.exists() && !file.delete()) error("Could not reset saved source tests")
    }

    fun exists(): Boolean = file.isFile && file.length() > 0L
}
