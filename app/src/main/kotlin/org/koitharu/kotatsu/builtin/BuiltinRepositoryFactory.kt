package org.koitharu.kotatsu.builtin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.builtin.ao3.Ao3Repository
import org.koitharu.kotatsu.core.prefs.AppSettings

class BuiltinRepositoryFactory @Inject constructor(
    private val cache: MemoryContentCache,
    @MangaHttpClient private val mangaClient: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
) {
    fun create(source: BuiltinSource): MangaRepository {
        val client = mangaClient.newBuilder()
            .addInterceptor(PoliteHostInterceptor(PoliteHostInterceptor.intervalFor(source.name.substringAfter(':'))))
            .build()
        return when (source) {
            BuiltinSource.GUTENBERG -> GutenbergRepository(cache, client, context)
            BuiltinSource.OPEN_LIBRARY -> OpenLibraryRepository(cache, client, context, settings)
            BuiltinSource.AO3 -> Ao3Repository(cache, client, settings)
            BuiltinSource.IA_BOOKS, BuiltinSource.IA_VIDEO, BuiltinSource.IA_AUDIO, BuiltinSource.IA_COMICS ->
                ArchiveRepository(source, cache, client, context)
        }
    }
}
