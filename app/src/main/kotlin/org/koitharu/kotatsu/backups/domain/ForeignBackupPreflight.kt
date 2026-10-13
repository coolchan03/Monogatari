package org.koitharu.kotatsu.backups.domain

import java.util.Locale

/** Header-only identification. Never import foreign data directly into the live database. */
internal enum class ForeignBackupFormat { KOTATSU_ZIP, MIHON_PROTOBUF_GZIP, MANGAYOMI_JSON, UNKNOWN }

internal object ForeignBackupPreflight {
    /** Deliberately requires both the expected extension and magic bytes. */
    fun detect(fileName: String, firstBytes: ByteArray): ForeignBackupFormat {
        val name = fileName.lowercase(Locale.ROOT)
        if (firstBytes.size < 2) return ForeignBackupFormat.UNKNOWN
        if (name.endsWith(".tachibk") && firstBytes[0] == 0x1f.toByte() &&
            firstBytes[1] == 0x8b.toByte()) return ForeignBackupFormat.MIHON_PROTOBUF_GZIP
        if ((name.endsWith(".zip") || name.endsWith(".bk")) && firstBytes.size >= 4 &&
            firstBytes[0] == 0x50.toByte() && firstBytes[1] == 0x4b.toByte() &&
            firstBytes[2] == 0x03.toByte() && firstBytes[3] == 0x04.toByte())
            return ForeignBackupFormat.KOTATSU_ZIP
        if (name.endsWith(".json") && firstBytes.size <= 8192) {
            val snippet = firstBytes.toString(Charsets.UTF_8).trimStart()
            // A generic JSON document is not proof of a Mangayomi backup.
            // The recognizable shape is still a preflight hint, not an import guarantee.
            val recognizable = snippet.startsWith("{") &&
                listOf("manga", "mangas", "sources", "history", "categories")
                    .count { key -> snippet.contains("\"$key\"") } >= 2
            if (recognizable) return ForeignBackupFormat.MANGAYOMI_JSON
        }
        return ForeignBackupFormat.UNKNOWN
    }
}
