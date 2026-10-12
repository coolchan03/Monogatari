package org.koitharu.kotatsu.builtin.opds

import android.content.Context
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.security.MessageDigest

data class OpdsSource(override val name: String, val url: String) : MangaSource {
    companion object {
        fun fromUrl(url: String): OpdsSource {
            val normalized = java.net.URI(url.trim()).normalize().toString()
            require(normalized.startsWith("https://") || normalized.startsWith("http://"))
            val hex = MessageDigest.getInstance("SHA-1")
                .digest(normalized.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return OpdsSource("opds:${hex.take(12)}", normalized)
        }
    }
}

/** Stable per-catalog sources restored from the existing book-library preference store. */
object OpdsSourceRegistry {
    @Volatile private var sources: Map<String, OpdsSource> = emptyMap()
    fun initialize(context: Context) {
        val urls = context.getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
            .getStringSet("urls", emptySet()).orEmpty()
        sources = urls.mapNotNull { runCatching { OpdsSource.fromUrl(it) }.getOrNull() }
            .associateBy { it.name }
    }
    fun list(): List<OpdsSource> = sources.values.sortedBy { it.name }
    fun fromName(name: String): OpdsSource? = sources[name]
    fun register(url: String): OpdsSource {
        val entry = OpdsSource.fromUrl(url)
        sources = sources + (entry.name to entry)
        return entry
    }
    fun unregister(url: String) {
        val id = OpdsSource.fromUrl(url).name
        sources = sources - id
    }
}
