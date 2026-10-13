package org.koitharu.kotatsu.core.qa

/** Standards-compatible CSV for future local library and reading-statistics exports. */
internal object CsvExportFormatter {
    fun encode(headers: List<String>, rows: List<List<String>>): String {
        require(headers.isNotEmpty() && headers.size <= 50)
        require(rows.size <= 100_000 && rows.all { it.size == headers.size })
        return (listOf(headers) + rows).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { value ->
                val safe = if (value.trimStart().firstOrNull() in setOf('=', '+', '-', '@', '\t')) "'" + value else value
                "\"" + safe.replace("\"", "\"\"") + "\""
            }
        }
    }
}
