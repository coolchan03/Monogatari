package org.koitharu.kotatsu.builtin

import javax.inject.Inject
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.MangaRepository

class BuiltinRepositoryFactory @Inject constructor(private val cache: MemoryContentCache) {
    fun create(source: BuiltinSource): MangaRepository = when (source) {
        BuiltinSource.GUTENBERG -> GutenbergRepository(cache)
    }
}
