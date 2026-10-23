package org.koitharu.kotatsu.core.media

import android.content.Context
import java.security.MessageDigest

/** Local-only resume data; stores a keyed hash rather than recording media URLs. */
internal object AudioResumeStore {
    private const val PREFS = "monogatari_audio_resume"
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    fun read(context: Context, uri: String): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = digest(uri)
        val updated = prefs.getLong("$key:updated", 0L)
        if (System.currentTimeMillis() - updated > MAX_AGE_MS) return 0L
        return prefs.getLong("$key:position", 0L).coerceAtLeast(0L)
    }

    /** When the position was last saved, or 0 when there is none (or it has expired). */
    fun updatedAt(context: Context, uri: String): Long {
        val updated = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("${digest(uri)}:updated", 0L)
        return if (System.currentTimeMillis() - updated > MAX_AGE_MS) 0L else updated
    }

    fun write(context: Context, uri: String, positionMs: Long) {
        if (uri.isBlank() || positionMs < 0L) return
        val key = digest(uri)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("$key:position", positionMs)
            .putLong("$key:updated", System.currentTimeMillis())
            .apply()
    }

    private fun digest(uri: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
}
