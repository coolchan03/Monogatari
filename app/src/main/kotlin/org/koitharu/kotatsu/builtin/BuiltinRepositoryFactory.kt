package org.koitharu.kotatsu.builtin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.media.BookMetadataProvider
import org.koitharu.kotatsu.builtin.ao3.Ao3Repository
import org.koitharu.kotatsu.builtin.royalroad.RoyalRoadRepository
import org.koitharu.kotatsu.builtin.scribblehub.ScribbleHubRepository
import org.koitharu.kotatsu.builtin.librivox.LibriVoxRepository
import org.koitharu.kotatsu.builtin.dailymotion.DailymotionRepository
import org.koitharu.kotatsu.builtin.newpipe.NewPipeVideoRepository
import org.koitharu.kotatsu.core.prefs.AppSettings

class BuiltinRepositoryFactory @Inject constructor(
    private val cache: MemoryContentCache,
    @MangaHttpClient private val mangaClient: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
    private val bookMetadata: BookMetadataProvider,
) {
    fun create(source: BuiltinSource): MangaRepository {
        val client = mangaClient.newBuilder()
            .addInterceptor(PoliteHostInterceptor(PoliteHostInterceptor.intervalFor(source.name.substringAfter(':'))))
            .build()
        return when (source) {
            BuiltinSource.GUTENBERG -> GutenbergRepository(cache, client, context, bookMetadata)
            BuiltinSource.OPEN_LIBRARY -> OpenLibraryRepository(cache, client, context, settings, bookMetadata)
            BuiltinSource.AO3 -> Ao3Repository(cache, client, settings)
            BuiltinSource.ROYAL_ROAD -> RoyalRoadRepository(cache, client)
            BuiltinSource.SCRIBBLE_HUB -> ScribbleHubRepository(cache, client)
            BuiltinSource.LIBRIVOX -> LibriVoxRepository(cache, client)
            BuiltinSource.DAILYMOTION -> DailymotionRepository(cache, client)
            BuiltinSource.YOUTUBE, BuiltinSource.PEERTUBE, BuiltinSource.SOUNDCLOUD -> NewPipeVideoRepository(cache, source, client)
            BuiltinSource.IA_BOOKS, BuiltinSource.IA_VIDEO, BuiltinSource.IA_AUDIO, BuiltinSource.IA_COMICS ->
                ArchiveRepository(source, cache, client, context)
        }
    }
}
