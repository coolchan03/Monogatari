package org.koitharu.kotatsu.reader.translate

/** Read-only WebVTT cue parsing for future opt-in subtitle translation. */
internal object VttCueParser {
    data class Cue(val start: String, val end: String, val text: String)
    private val times = Regex("^(\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3}\\s+-->\\s+(\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3}(?:\\s+.*)?$")

    fun parse(vtt: String): List<Cue> {
        if (!vtt.trimStart().startsWith("WEBVTT")) return emptyList()
        return vtt.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .drop(1).mapNotNull { block ->
                val lines = block.lines().map { it.trimEnd() }
                val index = lines.indexOfFirst { times.matches(it) }
                if (index < 0 || index + 1 >= lines.size) return@mapNotNull null
                val rawTimes = lines[index].split("-->", limit = 2)
                val body = lines.drop(index + 1).joinToString("\n").trim()
                if (body.isBlank()) null else Cue(rawTimes[0].trim(), rawTimes[1].trim().substringBefore(' '), body)
            }
    }
}
