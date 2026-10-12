package org.koitharu.kotatsu.builtin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.MangaRepository

class BuiltinRepositoryFactory @Inject constructor(
    private val cache: MemoryContentCache,
    @MangaHttpClient private val mangaClient: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    fun create(source: BuiltinSource): MangaRepository {
        val client = mangaClient.newBuilder()
            .addInterceptor(PoliteHostInterceptor(PoliteHostInterceptor.intervalFor(source.name.substringAfter(':'))))
            .build()
        return when (source) {
            BuiltinSource.GUTENBERG -> GutenbergRepository(cache, client, context)
        }
    }
}
