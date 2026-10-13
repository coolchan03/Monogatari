package org.koitharu.kotatsu.backups.data

import androidx.collection.ArrayMap
import androidx.room.withTransaction
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.backups.data.model.BackupIndex
import org.koitharu.kotatsu.backups.data.model.BookmarkBackup
import org.koitharu.kotatsu.backups.data.model.CategoryBackup
import org.koitharu.kotatsu.backups.data.model.FavouriteBackup
import org.koitharu.kotatsu.backups.data.model.HistoryBackup
import org.koitharu.kotatsu.backups.data.model.JsSourceBackup
import org.koitharu.kotatsu.backups.data.model.MangaBackup
import org.koitharu.kotatsu.backups.data.model.ScrobblingBackup
import org.koitharu.kotatsu.backups.data.model.SourceBackup
import org.koitharu.kotatsu.backups.data.model.StatisticBackup
import org.koitharu.kotatsu.backups.data.model.ChapterMetaBackup
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.util.CompositeResult
import org.koitharu.kotatsu.core.util.progress.Progress
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.filter.data.PersistableFilter
import org.koitharu.kotatsu.filter.data.SavedFiltersRepository
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.reader.data.TapGridSettings
import org.koitharu.kotatsu.sourcescore.data.CommunitySettings
import org.koitharu.kotatsu.sourcerepo.data.SourceRepoStore
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.repo.compareVersions
import org.koitharu.kotatsu.builtin.opds.OpdsSourceRegistry
import org.koitharu.kotatsu.core.prefs.SourceSettings
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@Reusable
class BackupRepository @Inject constructor(
    private val database: MangaDatabase,
    private val settings: AppSettings,
    private val tapGridSettings: TapGridSettings,
    private val mangaSourcesRepository: MangaSourcesRepository,
    private val savedFiltersRepository: SavedFiltersRepository,
    @ApplicationContext private val context: Context,
    private val repoStore: SourceRepoStore,
    /**
     * Nullable so [org.koitharu.kotatsu.backups.domain.AppBackupAgent] can keep constructing this by
     * hand: a backup agent runs in its own process with no Hilt graph. Dagger ignores the default and
     * injects the real instance; the agent simply omits the identity section.
     */
    private val communitySettings: CommunitySettings? = null,
    private val jsSourceStore: JsSourceStore? = null,
    private val sourceRepoRepository: SourceRepoRepository? = null,
) {

    private companion object {
        const val KEY_IDENTITY_SECRET = "secret"
        const val KEY_IDENTITY_NICKNAME = "nickname"
    }

    private val json = Json {
        allowSpecialFloatingPointValues = true
        coerceInputValues = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        useAlternativeNames = false
    }

    suspend fun createBackup(
        output: ZipOutputStream,
        progress: FlowCollector<Progress>?,
        sections: Set<BackupSection> = BackupSection.entries.toSet(),
    ) {
        progress?.emit(Progress.INDETERMINATE)
        var commonProgress = Progress(0, sections.size)
        for (section in BackupSection.entries.filter { it in sections }) {
            when (section) {
                BackupSection.INDEX -> output.writeJsonArray(
                    section = BackupSection.INDEX,
                    data = flowOf(BackupIndex(settings.backupDeviceId, settings.backupDeviceName)),
                    serializer = serializer(),
                )

                BackupSection.HISTORY -> output.writeJsonArray(
                    section = BackupSection.HISTORY,
                    data = database.getHistoryDao().dump().map { HistoryBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.CATEGORIES -> output.writeJsonArray(
                    section = BackupSection.CATEGORIES,
                    data = database.getFavouriteCategoriesDao().findAllForBackup().asFlow().map { CategoryBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.FAVOURITES -> output.writeJsonArray(
                    section = BackupSection.FAVOURITES,
                    data = database.getFavouritesDao().dump().map { FavouriteBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.SETTINGS -> output.writeString(
                    section = BackupSection.SETTINGS,
                    data = dumpSettings(),
                )

                BackupSection.IDENTITY -> output.writeString(
                    section = BackupSection.IDENTITY,
                    data = dumpIdentity(),
                )

                BackupSection.SETTINGS_READER_GRID -> output.writeString(
                    section = BackupSection.SETTINGS_READER_GRID,
                    data = dumpReaderGridSettings(),
                )

                BackupSection.BOOKMARKS -> output.writeJsonArray(
                    section = BackupSection.BOOKMARKS,
                    data = database.getBookmarksDao().dump().map { BookmarkBackup(it.first, it.second) },
                    serializer = serializer(),
                )

                BackupSection.SOURCES -> output.writeJsonArray(
                    section = BackupSection.SOURCES,
                    data = database.getSourcesDao().dumpEnabled().map { SourceBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.REPOS -> output.writeString(
                    section = BackupSection.REPOS,
                    data = encodeUrlList(repoStore.getAll(), httpsOnly = true),
                )

                BackupSection.JS_SOURCES -> output.writeJsonArray(
                    section = BackupSection.JS_SOURCES,
                    data = jsSourceStore?.list().orEmpty()
                        .filter { BackupCatalogPolicy.safeUrl(it.repoUrl, httpsOnly = true) != null }
                        .map { JsSourceBackup(it.id, it.repoUrl, it.version) }.asFlow(),
                    serializer = serializer(),
                )

                BackupSection.OPDS_CATALOGS -> output.writeString(
                    section = BackupSection.OPDS_CATALOGS,
                    data = encodeUrlList(
                        context.getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
                            .getStringSet("urls", emptySet()).orEmpty(), httpsOnly = false,
                    ),
                )

                BackupSection.SOURCE_SETTINGS -> {
                    val options = JSONObject()
                    mangaSourcesRepository.getEnabledSources().forEach { source ->
                        val values = SourceSettings(context, source).dumpBackupValues()
                        if (values.isNotEmpty()) options.put(source.name, JSONObject(values))
                    }
                    output.writeString(BackupSection.SOURCE_SETTINGS, options.toString())
                }

                BackupSection.SCROBBLING -> output.writeJsonArray(
                    section = BackupSection.SCROBBLING,
                    data = database.getScrobblingDao().dumpEnabled().map { ScrobblingBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.STATS -> output.writeJsonArray(
                    section = BackupSection.STATS,
                    data = database.getStatsDao().dumpEnabled().map { StatisticBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.CHAPTER_META -> output.writeJsonArray(
                    section = BackupSection.CHAPTER_META,
                    data = database.getStatsDao().dumpChapterMeta().asFlow().map { ChapterMetaBackup(it) },
                    serializer = serializer(),
                )

                BackupSection.SAVED_FILTERS -> {
                    val sources = mangaSourcesRepository.getEnabledSources()
                    val filters = sources.flatMap { source ->
                        savedFiltersRepository.getAll(source)
                    }
                    output.writeJsonArray(
                        section = BackupSection.SAVED_FILTERS,
                        data = filters.asFlow(),
                        serializer = serializer(),
                    )
                }
            }
            progress?.emit(commonProgress)
            commonProgress++
        }
        progress?.emit(commonProgress)
    }

    suspend fun restoreBackup(
        input: ZipInputStream,
        sections: Set<BackupSection>,
        progress: FlowCollector<Progress>?,
    ): CompositeResult {
        progress?.emit(Progress.INDETERMINATE)
        var commonProgress = Progress(0, sections.size)
        var entry = input.nextEntry
        var result = CompositeResult.EMPTY
        while (entry != null) {
            val section = BackupSection.of(entry)
            if (section in sections) {
                result += when (section) {
                    BackupSection.INDEX -> CompositeResult.EMPTY // useless in our case
                    BackupSection.HISTORY -> input.readJsonArray<HistoryBackup>(serializer()).restoreToDb {
                        val incoming = it.toEntity()
                        val existing = getHistoryDao().findIncludingDeleted(incoming.mangaId)
                        if (BackupMergePolicy.shouldReplace(
                                existing?.updatedAt, existing?.deletedAt ?: 0L,
                                incoming.updatedAt, incoming.deletedAt)) {
                            upsertManga(it.manga)
                            getHistoryDao().upsertRestored(incoming)
                        }
                    }

                    BackupSection.CATEGORIES -> input.readJsonArray<CategoryBackup>(serializer()).restoreToDb {
                        val incoming = it.toEntity()
                        val existing = getFavouriteCategoriesDao().findIncludingDeleted(incoming.categoryId)
                        if (BackupMergePolicy.shouldReplace(
                                existing?.createdAt, existing?.deletedAt ?: 0L,
                                incoming.createdAt, incoming.deletedAt)) {
                            getFavouriteCategoriesDao().upsert(incoming)
                        }
                    }

                    BackupSection.FAVOURITES -> input.readJsonArray<FavouriteBackup>(serializer()).restoreToDb {
                        val incoming = it.toEntity()
                        val existing = getFavouritesDao().findIncludingDeleted(incoming.mangaId, incoming.categoryId)
                        if (BackupMergePolicy.shouldReplace(
                                existing?.createdAt, existing?.deletedAt ?: 0L,
                                incoming.createdAt, incoming.deletedAt)) {
                            upsertManga(it.manga)
                            getFavouritesDao().upsert(incoming)
                        }
                    }

                    BackupSection.SETTINGS -> input.readMap().let {
                        settings.mergeBackupValues(it.filterKeys(BackupCatalogPolicy::safePreferenceKey))
                        CompositeResult.success()
                    }

                    BackupSection.IDENTITY -> input.readMap().let {
                        restoreIdentity(it)
                        CompositeResult.success()
                    }

                    BackupSection.SETTINGS_READER_GRID -> input.readMap().let {
                        tapGridSettings.upsertAll(it)
                        CompositeResult.success()
                    }

                    BackupSection.BOOKMARKS -> input.readJsonArray<BookmarkBackup>(serializer()).restoreToDb {
                        upsertManga(it.manga)
                        getBookmarksDao().insertMissing(it.bookmarks.map { b -> b.toEntity() })
                    }

                    BackupSection.SOURCES -> input.readJsonArray<SourceBackup>(serializer()).restoreToDb {
                        getSourcesDao().upsert(it.toEntity())
                    }

                    BackupSection.REPOS -> input.readMap().let { values ->
                        decodeUrlList(values, httpsOnly = true).forEach { url -> repoStore.add(url) }
                        CompositeResult.success()
                    }

                    BackupSection.JS_SOURCES -> {
                        val wanted = input.readJsonArray<JsSourceBackup>(serializer()).toList()
                        if (wanted.isEmpty()) CompositeResult.EMPTY else {
                            val repo = sourceRepoRepository
                            if (repo == null) CompositeResult.failure(IOException("Source installer unavailable"))
                            else {
                                val available = repo.fetchAll().jsSources.map { it.entry }
                                wanted.asSequence().restoreWithoutTransaction { record ->
                                    val safeUrl = BackupCatalogPolicy.safeUrl(record.repoUrl, httpsOnly = true)
                                        ?: throw IOException("Invalid source repository in backup")
                                    val entry = available.firstOrNull { it.id == record.id && it.repoUrl == safeUrl }
                                        ?: throw IOException("Source ${record.id} is not available in its repository")
                                    if (compareVersions(entry.version, record.version) < 0) {
                                        throw IOException("Source ${record.id} is older than backup version")
                                    }
                                    val existing = jsSourceStore?.get(record.id)?.entry
                                    if (existing != null && existing.repoUrl == safeUrl &&
                                        compareVersions(existing.version, entry.version) >= 0) {
                                        return@restoreWithoutTransaction
                                    }
                                    repo.installJsSource(entry)
                                }
                            }
                        }
                    }

                    BackupSection.OPDS_CATALOGS -> input.readMap().let { values ->
                        val urls = decodeUrlList(values, httpsOnly = false)
                        val prefs = context.getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
                        val old = prefs.getStringSet("urls", emptySet()).orEmpty()
                        prefs.edit().putStringSet("urls", old + urls).apply()
                        urls.forEach { url -> OpdsSourceRegistry.register(url) }
                        CompositeResult.success()
                    }

                    BackupSection.SOURCE_SETTINGS -> input.readMap().let { values ->
                        val known = mangaSourcesRepository.getEnabledSources().associateBy { it.name }
                        values.forEach { (name, raw) ->
                            val source = known[name] ?: return@forEach
                            val valuesObj = raw as? JSONObject ?: return@forEach
                            val prefValues = mutableMapOf<String, Any>()
                            val keys = valuesObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                prefValues[key] = valuesObj.get(key)
                            }
                            SourceSettings(context, source).restoreBackupValues(prefValues)
                        }
                        CompositeResult.success()
                    }

                    BackupSection.SCROBBLING -> input.readJsonArray<ScrobblingBackup>(serializer()).restoreToDb {
                        getScrobblingDao().upsert(it.toEntity())
                    }

                    BackupSection.STATS -> input.readJsonArray<StatisticBackup>(serializer()).restoreToDb {
                        getStatsDao().insertMissing(it.toEntity())
                    }

                    BackupSection.CHAPTER_META -> input.readJsonArray<ChapterMetaBackup>(serializer()).restoreToDb {
                        val incoming = it.toEntity()
                        require(incoming.mangaId > 0L && incoming.chapterId > 0L)
                        val dao = getStatsDao()
                        val existing = dao.getChapterMeta(incoming.mangaId, incoming.chapterId)
                        dao.upsertChapterMeta(incoming.copy(
                            pages = maxOf(existing?.pages ?: 0, incoming.pages.coerceIn(0, 20000)),
                            words = maxOf(existing?.words ?: 0, incoming.words.coerceIn(0, 2_000_000)),
                            durationMs = maxOf(existing?.durationMs ?: 0L, incoming.durationMs.coerceIn(0L, 86400000L)),
                            updatedAt = maxOf(existing?.updatedAt ?: 0L, incoming.updatedAt),
                        ))
                    }

                    BackupSection.SAVED_FILTERS -> input.readJsonArray<PersistableFilter>(serializer())
                        .restoreWithoutTransaction {
                            savedFiltersRepository.save(it)
                        }

                    null -> CompositeResult.EMPTY // skip unknown entries
                }
                progress?.emit(commonProgress)
                commonProgress++
            }
            input.closeEntry()
            entry = input.nextEntry
        }
        progress?.emit(commonProgress)
        return result
    }

    private suspend fun <T> ZipOutputStream.writeJsonArray(
        section: BackupSection,
        data: Flow<T>,
        serializer: SerializationStrategy<T>,
    ) {
        data.onStart {
            putNextEntry(ZipEntry(section.entryName))
            write("[")
        }.onCompletion { error ->
            if (error == null) {
                write("]")
            }
            closeEntry()
            flush()
        }.collectIndexed { index, value ->
            if (index > 0) {
                write(",")
            }
            json.encodeToStream(serializer, value, this)
        }
    }

    private fun <T> InputStream.readJsonArray(
        serializer: DeserializationStrategy<T>,
    ): Sequence<T> = json.decodeToSequence(this, serializer, DecodeSequenceMode.ARRAY_WRAPPED)

    private fun InputStream.readMap(): Map<String, Any?> {
        val jo = JSONArray(readString()).getJSONObject(0)
        val map = ArrayMap<String, Any?>(jo.length())
        val keys = jo.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jo.get(key)
        }
        return map
    }

    private fun ZipOutputStream.writeString(
        section: BackupSection,
        data: String,
    ) {
        putNextEntry(ZipEntry(section.entryName))
        try {
            write("[")
            write(data)
            write("]")
        } finally {
            closeEntry()
            flush()
        }
    }

    private fun OutputStream.write(str: String) = write(str.toByteArray())

    private fun InputStream.readString(): String = readBytes().decodeToString()

    /**
     * Only the key and the nickname. Deliberately not `registered` or `nickname_synced`: those
     * describe this install's conversation with the server, and a restored device has to introduce
     * itself again.
     */
    private fun dumpIdentity(): String {
        val json = JSONObject()
        communitySettings?.peekSecret()?.let { json.put(KEY_IDENTITY_SECRET, it) }
        communitySettings?.nickname?.let { json.put(KEY_IDENTITY_NICKNAME, it) }
        return json.toString()
    }

    private fun restoreIdentity(values: Map<String, Any?>) {
        val secret = values[KEY_IDENTITY_SECRET] as? String
        if (secret.isNullOrEmpty()) return
        val community = communitySettings ?: return
        if (!community.adoptSecret(secret)) return
        (values[KEY_IDENTITY_NICKNAME] as? String)?.let { community.nickname = it }

        // A key in the backup is proof this person opted in already and has had a name for months.
        // Asking both questions again is the restore failing to restore: the account comes back but
        // the app behaves as though it had never met them. If the backup predates the nickname being
        // stored, the first sync takes it from the server rather than asking.
        community.isEnabled = true
        community.hasSeenOnboarding = true
    }

    private fun encodeUrlList(urls: Collection<String>, httpsOnly: Boolean): String =
        JSONObject().put("urls", JSONArray(urls.mapNotNull {
            BackupCatalogPolicy.safeUrl(it, httpsOnly)
        }.distinct().sorted())).toString()

    private fun decodeUrlList(values: Map<String, Any?>, httpsOnly: Boolean): List<String> {
        val data = values["urls"] as? JSONArray ?: return emptyList()
        return (0 until data.length()).mapNotNull { index ->
            BackupCatalogPolicy.safeUrl(data.optString(index), httpsOnly)
        }.distinct()
    }

    private fun dumpSettings(): String {
        val map = settings.getAllValues().filterKeys(BackupCatalogPolicy::safePreferenceKey).toMutableMap()
        map.remove(AppSettings.KEY_APP_PASSWORD)
        map.remove(AppSettings.KEY_PROXY_PASSWORD)
        map.remove(AppSettings.KEY_PROXY_LOGIN)
        map.remove(AppSettings.KEY_INCOGNITO_MODE)
        return JSONObject(map).toString()
    }

    private fun dumpReaderGridSettings(): String {
        return JSONObject(tapGridSettings.getAllValues()).toString()
    }

    private suspend fun MangaDatabase.upsertManga(manga: MangaBackup) {
        val tags = manga.tags.map { it.toEntity() }
        getTagsDao().upsert(tags)
        getMangaDao().upsert(manga.toEntity(), tags)
    }

    private suspend inline fun <T> Sequence<T>.restoreToDb(crossinline block: suspend MangaDatabase.(T) -> Unit): CompositeResult {
        return fold(CompositeResult.EMPTY) { result, item ->
            result + runCatchingCancellable {
                database.withTransaction {
                    database.block(item)
                }
            }
        }
    }

    private suspend inline fun <T> Sequence<T>.restoreWithoutTransaction(crossinline block: suspend (T) -> Unit): CompositeResult {
        return fold(CompositeResult.EMPTY) { result, item ->
            result + runCatchingCancellable {
                block(item)
            }
        }
    }
}
