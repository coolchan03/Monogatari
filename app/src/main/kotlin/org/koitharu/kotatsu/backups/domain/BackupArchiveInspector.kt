package org.koitharu.kotatsu.backups.domain

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile

/**
 * Read-only, bounded check before restoring or transferring a backup ZIP.
 * No files are extracted and no application database state is changed here.
 */
internal object BackupArchiveInspector {
    const val MAX_ZIP_BYTES = 256L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 192L * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private const val MAX_INDEX_BYTES = 16L * 1024
    private val safeName = Regex("[A-Za-z0-9_.-]{1,96}")

    data class Report(
        val entryCount: Int,
        val sections: Set<BackupSection>,
        val unknownSections: Int,
        val compressedBytes: Long,
        val expandedBytes: Long,
    )

    fun inspect(file: File, requireIndex: Boolean = false,
        maxArchiveBytes: Long = MAX_ZIP_BYTES): Report {
        if (!file.isFile || file.length() !in 1L..maxArchiveBytes) {
            throw IOException("Backup ZIP is empty or exceeds the archive limit")
        }
        val recognized = LinkedHashSet<BackupSection>()
        val seen = HashSet<String>()
        var count = 0
        var unknown = 0
        var expanded = 0L
        val bytes = ByteArray(32 * 1024)
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                if (count > MAX_ENTRIES) throw IOException("Backup ZIP contains too many entries")
                val lower = entry.name.lowercase(Locale.ROOT)
                if (entry.isDirectory || !safeName.matches(entry.name) || !seen.add(lower)) {
                    throw IOException("Unsafe or duplicate backup ZIP entry")
                }
                val section = BackupSection.entries.firstOrNull { it.entryName == lower }
                if (section == null) unknown++ else recognized.add(section)
                val entryLimit = if (section == BackupSection.INDEX) MAX_INDEX_BYTES
                    else MAX_ENTRY_BYTES
                if (entry.size > entryLimit || entry.size < -1 ||
                    entry.compressedSize > maxArchiveBytes) {
                    throw IOException("Backup ZIP entry exceeds its size limit")
                }
                val checksum = CRC32()
                var actual = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        if (n == 0) continue
                        actual += n
                        expanded += n
                        if (actual > entryLimit || expanded > MAX_EXPANDED_BYTES) {
                            throw IOException("Expanded backup ZIP exceeds its size limit")
                        }
                        checksum.update(bytes, 0, n)
                    }
                }
                if (entry.size >= 0L && actual != entry.size) {
                    throw IOException("Backup ZIP entry length mismatch")
                }
                if (entry.crc >= 0L && checksum.value != entry.crc) {
                    throw IOException("Backup ZIP entry checksum mismatch")
                }
            }
        }
        if (recognized.isEmpty() ||
            (requireIndex && BackupSection.INDEX !in recognized)) {
            throw IOException("Not a recognized Monogatari/Kotatsu backup ZIP")
        }
        return Report(count, recognized, unknown, file.length(), expanded)
    }

    /** Copy a SAF stream to a temporary file, aborting before unbounded storage use. */
    fun copyBounded(source: InputStream, target: File, maxBytes: Long = MAX_ZIP_BYTES): Long {
        var total = 0L
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    total += n
                    if (total > maxBytes) throw IOException("Backup exceeds size limit")
                    output.write(buffer, 0, n)
                }
            }
            return total
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }
}
