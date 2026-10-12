# Monogatari sources plan: use every source well, fix what's broken, add AO3 and more

Written 2026-10-12 against branch **`claude/mangayomi-kotatsu-features-fb46y2`** at commit `cb5e722` (your real app).
This is the companion to `docs/MONOGATARI_AUDIT.md`. That file is the full audit; **this file is the action plan
for sources**, plus the current-issue fixes they depend on. Where a task matches an audit task, the audit ID is
given in brackets, e.g. [audit K1].

Paths are relative to `app/src/main/kotlin/org/koitharu/kotatsu/` unless they start with `app/`, `docs/` or `.github/`.
Line numbers are from `cb5e722`.

> **For ChatGPT, use `docs/MONOGATARI_MASTER_STEPS.md`.** It turns this document into numbered steps with ready-to-paste prompts. This file is background detail.

### How to use this with ChatGPT (or Claude)
1. **First merge PR #1 into your default branch** and tag `cb5e722` (`git tag v0.9-base cb5e722`). Otherwise new
   sessions start from plain Kotatsu, which happened during the audit.
2. Give the AI this whole file once, then **one task ID at a time** ("Do task S2"). Paste every file the task names,
   in full, and ask for full replacement files.
3. One git branch per task. Merge it when the GitHub build check is green.
4. The Kotlin in this file is a **skeleton written against your branch's real interfaces**, but it has not been
   compiled. Expect one or two rounds of fixing build errors.

---

## Part 1: The sources you have today, and how to use each one best

| Layer | Where in code | Best at | Weak at | How to use it |
|---|---|---|---|---|
| **Kotatsu-Redo parsers** (built-in Kotlin) | dependency `com.github.clquwu:kotatsu-parsers-redo:b3c0000245`; `core/parser/ParserMangaRepository.kt` | **Manga.** Fast, filters, tags, sort by date and popularity, many sites | Only updates when you bump the library; one maintainer; few novels; no video | Your **default for manga**. Bump the parser hash monthly. Turn mirror switching on (F10) |
| **Mangayomi JS sources** | `jsext/`, `sourcerepo/`, your repo `coolchan03/Best-BL-Mangayomi-Extensions` | **Novels and anime/video**, plus manga sites the parsers lack, including BL sites | Some extensions need host functions you haven't implemented; some video hosts unsupported | Default for **novels and video**; manga only when no healthy parser exists for that site. Make them automatic (Part 5) |
| **Public book catalogs** (Gutenberg, Open Library, Internet Archive) | `core/media/PublicBookCatalog.kt`, shown in Books search | Classic and public-domain books; Open Library is the best book *catalog* (metadata, covers) | **Results open in the web browser** (`search/ui/multi/SearchActivity.kt` line 102, `core/media/BookLibraryActivity.kt` ~line 190). No library, history or in-app reading. The Gutenberg EPUB link is fetched (`downloadUrl`) and then ignored. Internet Archive has no language or "borrow-only" filter | Turn into real sources (Part 4: N4, N5, N11) |
| **OPDS catalogs** | `core/media/OpdsCatalog.kt`, saved catalogs in Books | Your own or community ebook servers (Calibre, Kavita, Komga, Standard Ebooks) | Acquisitions open in the browser too | Make each saved catalog a real source (N7) |
| **Local files** | `core/media/BookShelf.kt`, `LocalEpubReaderActivity`, `LocalPdfReaderActivity`, `LocalTextReaderActivity`; Kotatsu's `local/` for CBZ | Offline reading | Books shelf is separate from the main library | Unify with the library (F14) |
| **Community scores** (from Kotatsu-Redo) | `sourcescore/`, server `community.kotatsuredo.app` | Ranks sources by how well they work for everyone | Depends on a third-party server | Keep, and combine with your own local health (Part 5) |

### The rules that make all of these work together
1. **Everything is a `MangaSource`.** Only then do library, history, favourites, downloads, update tracking,
   stats, backups and global search work. JS sources already follow this rule (`jsext/source/JsMangaSource.kt`,
   persisted as `js:<id>`). New built-in sources follow the same pattern as `builtin:<key>` (S2).
2. **Readers are chosen by what the source can do, not by what kind of source it is.** Today the novel/video reader
   only accepts JS sources (`reader/ui/MediaReaderActivity.kt` line 200: `manga.source.asJsSource() ?: return showError`,
   and `core/nav/ReaderIntent.kt` line 33 uses `isJsContinuousMedia()`). S1 fixes this.
3. **One site appears once.** If the same website exists as a parser and as a JS source, show one entry and use the
   healthier implementation (P3). Your branch already prefers the JS version when a Dart and a JS source share an ID
   (commit `fa3e41e`); extend that idea to parsers vs JS.
4. **Health decides order.** Community scores plus your own phone's success/failure history decide search order,
   hide broken sources, and trigger automatic repair (Part 5).
5. **Presets for moods.** Your branch has source presets (`explore/ui/preset/`, `settings.activeSourcePresetId`).
   Create sensible ones on first run (P5): "BL & Yaoi", "Popular manga", "Books & fanfic", "Anime & video".
6. **Be polite to websites.** Space out requests per host and honour `429 Retry-After` (S3). Fan-fiction and novel
   sites ban scrapers quickly.

---

## Part 2: Fix current issues first (things that break or put you at risk)

Ordered by importance. Do F1–F4 before sharing the app with anyone.

### F1. Release builds have never been made, and may crash [audit K1]
- CI (`.github/workflows/build-check.yml` lines 25–27) runs only `testDebugUnitTest` and `assembleDebug`.
- Release builds shrink code (`minifyEnabled true`, R8 full mode). `app/proguard-rules.pro` has **no rules** for
  `quickjs-kt` (its native code calls back into Kotlin by name) or for your `jsext` bridges and the Mangayomi runtime
  resources.

Fix:
1. Append to `app/proguard-rules.pro`:
   ```proguard
   # QuickJS engine: native code calls these classes by name
   -keep class com.dokar.quickjs.** { *; }
   -keepclasseswithmembernames class * { native <methods>; }
   # JS host bridges are invoked from JavaScript by function name
   -keep class org.koitharu.kotatsu.jsext.** { *; }
   # kotlinx.serialization models used by JS bridges
   -keepattributes *Annotation*, InnerClasses, Signature
   -keepclassmembers class org.koitharu.kotatsu.jsext.** { *** Companion; }
   ```
2. In `build-check.yml`, add a step `run: ./gradlew assembleRelease -Pandroid.injected.signing.store.file=` or
   simply `./gradlew minifyReleaseWithR8` so shrinking runs on every push (it doesn't need a real signing key).
3. Install a release APK on your phone and run one JS manga, one JS novel and one JS video source. A crash on any
   means more keep rules: copy the class name from the crash into a `-keep` line.

### F2. The app still uses Kotatsu-Redo's identity [audit A2, your own audit's P0]
`app/build.gradle` line 50: `applicationId 'io.github.kotatsuredo.kotatsu'`; `res/values/constants.xml`:
`sync_authority_*` = `io.github.kotatsuredo.kotatsu.*`, `account_type_sync` = `org.kotatsu.sync`.
It can't be installed next to Kotatsu-Redo, and changing it later means users lose their data. Change it **now**:
- `applicationId 'io.github.coolchan03.monogatari'`, `versionCode 1`, `versionName '0.9.0'`
- `sync_authority_history` / `_favourites` → `io.github.coolchan03.monogatari.history` / `.favourites`
- `account_type_sync` → `io.github.coolchan03.monogatari.sync`
- the same three keys in `app/src/debug/res/values/constants.xml` and `app/src/nightly/...` with `.debug`/`.nightly`
- `github_updates_repo` is `coolchan03/Manga-Reader`. Point it at the repo you'll actually publish releases from.
- Also replace `url_weblate`, `url_user_manual`, `discord_app_id` and `app_icon_url` (still Kotatsu's).
Do this **before** F4, and before registering for Google's developer verification [audit K3].

### F3. Signed releases [audit I2]
`publish-release.yml` can only run once it's on the default branch (merge PR #1). Create a keystore once
(`keytool -genkey -v -keystore monogatari.jks -keyalg RSA -keysize 4096 -validity 36500 -alias monogatari`). Add it
as a base64 repository secret with its passwords, and back it up somewhere safe. If you lose it, nobody can update
the app.

### F4. Repo URLs are hard-coded in public code [audit Q1]
`sourcerepo/data/SourceRepoStore.kt` line 38 (`DEFAULT_REPOS`) lists 9 extension repos. Kotatsu was shut down after
a publisher's legal threats; a public app that ships and auto-installs scraper repos is an easy target.
Fix: move the list to `local.properties` (`monogatari.repos=url1,url2,...`), read it in `app/build.gradle` the same way
`tg_backup_bot_token` is read, and expose it as `BuildConfig.DEFAULT_REPOS`. `DEFAULT_REPOS` becomes
`BuildConfig.DEFAULT_REPOS.split(',').filter { it.isNotBlank() }.toSet()`. For GitHub Actions builds, store the
value as a secret and write it into `local.properties` in a workflow step. Your builds are fully pre-configured;
the public code contains no URLs.
Also support a setup link `monogatari://setup?repos=<urlencoded>` so a new phone can be configured with one tap.

### F5. JS sources can use cookies from every site [audit Q2]
JS sources share the app's scraping client and cookie jar (`jsext/source/JsOkHttpTransport.kt`). A JS source can
therefore send requests carrying your login cookies for any other site.
Fix: in `JsOkHttpTransport`, build the client with a wrapping `CookieJar` that only loads and saves cookies when
the request host matches the source's own base-URL host (or a subdomain of it), plus hosts listed in the
extension's own `baseUrl` and `apiUrl` fields. Everything else gets an empty cookie list. Cloudflare clearance
cookies for the source's own host keep working.

### F6. Unverified downloads when the index has no hash [audit Q3]
`sourcerepo/domain/SourceRepoRepository.kt` line 147: `val expected = plugin.sha256 ?: return@withContext uri`.
That accepts any file when no hash is listed.
Fix:
- Your own repo (`Best-BL-Mangayomi-Extensions`) should always publish `sha256`. Add a GitHub Action there that
  computes it for every `.js` file on push.
- For other repos: on first install, store the file's SHA-256 next to its version (trust on first use). On update,
  accept a different hash only if the version number also increased; otherwise refuse and log it.

### F7. "Ignore SSL errors" weakens security for the whole app [audit K2]
`core/network/NetworkModule.kt` line 76 disables certificate checks on the **base** client when the user accepts the
SSL dialog once. That covers sync, tracker logins and update downloads too. Make it per-source (a set of source
names in `AppSettings`), applied only to requests for that source's host. Details in the audit, K2.

### F8. App-lock PIN stored as unsalted MD5 [audit A3]
`settings/protect/ProtectSetupViewModel.kt` line 42: `settings.appPassword = password.md5()`. Use PBKDF2-SHA256 with
a random salt, and migrate on the next successful unlock (`main/ui/protect/ProtectViewModel.kt`).

### F9. Privacy defaults [audit K7]
`core/prefs/AppSettings.kt`:
- line 599 `KEY_DISCORD_RPC_SKIP_NSFW` default `false` → `true` (stops adult titles appearing on your Discord status)
- line 512 `KEY_DOH` default `DoHProvider.NONE` → Cloudflare, or ask during first run (also gets around ISP blocks of
  manga sites)

### F10. Mirror switching is off [audit B1]
`AppSettings.kt` line 325: `KEY_MIRROR_SWITCHING` default `false` → `true`. Parsers then try their other known
domains automatically when a site moves.

### F11. Search leftovers [audit D1]
- `search/ui/multi/SearchMenuProvider.kt`: the "Pinned sources only" checkbox isn't restored after rotation (hide-empty
  is). Add `menu.findItem(R.id.action_filter_pinned_only)?.isChecked = viewModel.isPinnedOnly` next to line 38, and
  expose `val isPinnedOnly get() = pinnedOnly.value` in the ViewModel.
- `search/ui/multi/SearchViewModel.kt` ~line 171 `retry()`: `results.value = emptyList()` runs before the old search
  has stopped, so late results from the previous search can appear. Move the clearing into `doSearch()` right after
  `prevJob?.cancelAndJoin()`.
- Line 64 `MAX_PARALLELISM = 4`: with hundreds of sources, global search is slow. Raise it to 8 and keep per-host
  politeness in S3.

### F12. Novel/video reader only accepts JS sources
`reader/ui/MediaReaderActivity.kt` line 200 and `core/nav/ReaderIntent.kt` line 33. Fixed by S1. It's listed here
because nothing in Part 4 can be read in-app until it's done.

### F13. Book catalog results open in the browser
Fixed by turning catalogs into real sources (N4–N7). Until then, at least use the Gutenberg `downloadUrl` that
`PublicBookCatalog.searchGutenberg` already returns: download the EPUB into the book shelf
(`core/media/BookShelf.kt`) and open it with `LocalEpubReaderActivity`, instead of opening `book.url` in the browser.

### F14. Books UI and library split [your own audit]
`core/media/BookLibraryFragment.kt` / `BookLibraryActivity.kt` build screens from raw views with hard-coded English
strings, and duplicate each other. After Part 4, books from sources live in the normal library. Make the Books tab a
filtered view of the library (`MediaType.BOOK`), move strings into `res/values/strings.xml`, and merge the activity
and fragment into one fragment backed by a ViewModel.

### F15. Backups don't include your sources [audit F1/F2]
- Add a backup section for **installed JS sources**: repo URL, source id, version and preferences
  (`jsext/repo/JsSourceStore.kt` and their preference store), plus the **repo list** and saved **OPDS catalogs**. On
  restore, reinstall automatically (Part 5).
- Restore must be **newest-wins**: `backups/data/BackupRepository.kt` ~line 183 upserts history without comparing
  `updated_at`, so restoring an older backup overwrites newer reading progress.

### F16. Stats bugs [audit E]
`AppSettings.kt` line 638 stats default `false` → `true`; `stats/data/StatsEntity.kt` line 16 `CASCADE` deletes all
stats when history is cleared; idle time counts as reading. Details and fixes are in audit Part E. They matter here
because time-left estimates for long AO3 and Royal Road works need real reading speeds.

### F17. Move diagnostics out of the main UI [audit Q5]
The JS source diagnostics, health sweep and guided QA screens are great tools. Put them under
Settings → Advanced → "Source diagnostics" rather than in the normal flow.

---

## Part 3: Source foundation (do before adding new sources)

### S1. One interface for text and video sources
New file `core/media/ContinuousMediaProvider.kt`:
```kotlin
package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter

/** A playable or readable stream for VIDEO/AUDIO chapters. */
data class MediaStream(
	val url: String,
	val quality: String,
	val headers: Map<String, String> = emptyMap(),
	val subtitles: List<MediaTrack> = emptyList(),
	val audios: List<MediaTrack> = emptyList(),
)

data class MediaTrack(val url: String, val label: String)

/**
 * Implemented by any repository whose chapters are text (BOOK) or streams (VIDEO/AUDIO) rather than images.
 * The media reader talks only to this interface, so JS sources and built-in sources work the same way.
 */
interface ContinuousMediaProvider {

	/** BOOK chapters: the chapter body as HTML (the novel reader cleans and styles it). */
	suspend fun getChapterHtml(manga: Manga, chapter: MangaChapter): String =
		throw UnsupportedOperationException("This source has no text chapters")

	/** VIDEO/AUDIO chapters: candidate streams, best first. */
	suspend fun getStreams(manga: Manga, chapter: MangaChapter): List<MediaStream> =
		throw UnsupportedOperationException("This source has no media streams")
}
```
Then:
1. `jsext/source/JsMangaRepository.kt`: implement `ContinuousMediaProvider`. `getChapterHtml` calls
   `extensions.get(source.id).getHtmlContent(manga.title, chapter.url)`. `getStreams` calls
   `extensions.get(source.id).getVideoList(chapter.url)` and maps each `JsVideo` (`jsext/JsModels.kt` line 93) to
   `MediaStream` (`JsTrack(file, label)` → `MediaTrack(file ?: skip, label ?: "")`). This moves the code that's
   currently inside `MediaReaderActivity.loadNovel` / `loadVideo` (lines ~196–260) into the repository.
2. `reader/ui/MediaReaderActivity.kt`: replace `manga.source.asJsSource() ?: return showError(...)` with
   `val provider = mangaRepositoryFactory.create(manga.source) as? ContinuousMediaProvider ?: return showError(...)`
   (inject `MangaRepository.Factory`). Switch `chooseVideo(videos: List<JsVideo>)` to `List<MediaStream>`.
3. `core/media/MediaTypeFilter.kt` line 21: rename `isJsContinuousMedia()` to `isContinuousMedia()`, returning
   `mediaType() != MediaType.MANGA` for JS **and built-in** sources. Update the 4 callers
   (`core/nav/ReaderIntent.kt` line 33, `reader/ui/ReaderViewModel.kt` lines 268, 483 and 565).
4. Also make Kotatsu-Redo parser **novel** sources (`ContentType.NOVEL`) work: wrap `ParserMangaRepository` so
   `getChapterHtml` returns the page content for novel parsers (check how the redo library exposes novel text; if
   it returns pages with HTML URLs, fetch the URL and return the body).
5. Add `AUDIO` to `core/media/MediaType.kt` (LibriVox, SoundCloud, Internet Archive audio), treated like VIDEO by
   the player. Add background playback in C3 of the audit.

**Check:** existing JS novel and anime sources still open and play exactly as before.

### S2. Built-in sources: same pattern as JS sources
New package `builtin/`. File `builtin/BuiltinSource.kt`:
```kotlin
package org.koitharu.kotatsu.builtin

import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.parsers.model.MangaSource

/** Sources implemented inside Monogatari (no parser library, no extension). Persisted as `builtin:<key>`. */
sealed class BuiltinSource(
	val key: String,
	val title: String,
	val mediaType: MediaType,
	val locale: String,
	val baseUrl: String,
	/** True only for sources that are adult-only as a whole. AO3 etc. rate per work instead. */
	val isAdultOnly: Boolean = false,
) : MangaSource {

	override val name: String
		get() = PREFIX + key

	data object Ao3 : BuiltinSource("ao3", "Archive of Our Own", MediaType.BOOK, "en", "https://archiveofourown.org")
	data object Gutenberg : BuiltinSource("gutenberg", "Project Gutenberg", MediaType.BOOK, "en", "https://www.gutenberg.org")
	data object InternetArchiveBooks : BuiltinSource("ia_books", "Internet Archive Books", MediaType.BOOK, "en", "https://archive.org")
	data object InternetArchiveComics : BuiltinSource("ia_comics", "Internet Archive Comics", MediaType.MANGA, "en", "https://archive.org")
	data object InternetArchiveVideo : BuiltinSource("ia_video", "Internet Archive Video", MediaType.VIDEO, "en", "https://archive.org")
	data object RoyalRoad : BuiltinSource("royalroad", "Royal Road", MediaType.BOOK, "en", "https://www.royalroad.com")
	data object ScribbleHub : BuiltinSource("scribblehub", "Scribble Hub", MediaType.BOOK, "en", "https://www.scribblehub.com")
	data object Wattpad : BuiltinSource("wattpad", "Wattpad", MediaType.BOOK, "en", "https://www.wattpad.com")
	data object LibriVox : BuiltinSource("librivox", "LibriVox", MediaType.AUDIO, "en", "https://librivox.org")
	data object YouTube : BuiltinSource("youtube", "YouTube", MediaType.VIDEO, "en", "https://www.youtube.com")

	companion object {
		const val PREFIX = "builtin:"

		val all: List<BuiltinSource> by lazy {
			listOf(Ao3, Gutenberg, InternetArchiveBooks, InternetArchiveComics, InternetArchiveVideo,
				RoyalRoad, ScribbleHub, Wattpad, LibriVox, YouTube)
		}

		fun fromName(name: String): BuiltinSource? =
			name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.let { k -> all.find { it.key == k } }
	}
}
```
Register it **everywhere `JsMangaSource` is registered**. These are the exact spots on your branch:
- `core/model/MangaSource.kt` line 48: next to `JsMangaSource.fromName(name)?.let { return it }` add
  `BuiltinSource.fromName(name)?.let { return it }`. Lines 60 (NSFW: `is BuiltinSource -> isAdultOnly`), 89 (locale),
  104 and 125 (title: `is BuiltinSource -> title`).
- `core/parser/MangaRepository.kt` line 98 (`Factory.createRepository`): `is BuiltinSource -> builtinRepositories.create(source)`
  (a small `BuiltinRepositoryFactory` injected with OkHttp, the content cache and the EPUB helper).
- `core/media/MediaTypeFilter.kt` `mediaType()`: `if (source is BuiltinSource) return source.mediaType`.
- `explore/data/MangaSourcesRepository.kt` lines 384–412 (`getJsSources` and the enabled-names code): add the built-in
  sources the same way. They're enabled by default, and the user can switch them off.
Each repository extends `CachingMangaRepository(cache)` exactly like `JsMangaRepository` (implement `getList`,
`getDetailsImpl`, `getPagesImpl`, `getPageUrl`, `getFilterOptions`, `getRelatedMangaImpl`, `sortOrders`,
`defaultSortOrder`, `filterCapabilities`), and also `ContinuousMediaProvider` (S1) for BOOK/VIDEO/AUDIO.
`getPagesImpl` for BOOK/VIDEO/AUDIO returns one synthetic page, exactly like `JsMangaRepository` lines 84–92.

**IDs:** create `Manga.id` and `MangaChapter.id` from a stable hash of `source.name + url` (copy `uid()` from
`JsMangaRepository` line 162). **Never change this later**, or library entries break.

### S3. Polite networking for scraped sites
Your app's `core/network/RateLimitInterceptor.kt` turns HTTP 429 into `TooManyRequestExceptions` with `retryAfter`,
but it doesn't *space out* requests. Add `builtin/PoliteHostInterceptor.kt`, used only by built-in scrapers:
```kotlin
package org.koitharu.kotatsu.builtin

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/** Keeps at least [minIntervalMs] between requests to the same host. Blocking; runs on OkHttp's thread. */
class PoliteHostInterceptor(private val minIntervalMs: Long) : Interceptor {

	private val locks = ConcurrentHashMap<String, Any>()
	private val lastRequestAt = ConcurrentHashMap<String, Long>()

	override fun intercept(chain: Interceptor.Chain): Response {
		val host = chain.request().url.host
		val lock = locks.getOrPut(host) { Any() }
		synchronized(lock) {
			val wait = (lastRequestAt[host] ?: 0L) + minIntervalMs - System.currentTimeMillis()
			if (wait > 0) Thread.sleep(wait)
			lastRequestAt[host] = System.currentTimeMillis()
		}
		return chain.proceed(chain.request())
	}
}
```
Intervals: AO3 3000 ms, Wattpad 1000 ms, Royal Road and Scribble Hub 1500 ms, APIs (Gutenberg, Internet Archive,
LibriVox) 300 ms. Build each scraper's client as `baseClient.newBuilder().addInterceptor(PoliteHostInterceptor(ms))`.
Use the **scraping client** (`@MangaHttpClient`), so Cloudflare handling and the existing 429 handling still apply.
Send a clear User-Agent such as `"Monogatari/0.9 (Android; +https://github.com/coolchan03/Monogatari)"` for API
sources. Keep the default browser UA for scraped sites.

### S4. One shared EPUB helper
Your branch already parses EPUB natively in `jsext/JsEpubBridge.kt` (EPUB 2/3, table of contents, per-source book
cache) and in the local EPUB reader. Move the parsing into `core/media/epub/EpubBook.kt` with:
`fun open(file: File): EpubBook`, `val chapters: List<EpubChapter(title, href)>`, `fun chapterHtml(href): String`.
Use it from `JsEpubBridge`, `LocalEpubReaderActivity` and every built-in EPUB source (Gutenberg, Internet Archive,
AO3 offline, Standard Ebooks). One parser to fix, three features that benefit.
Download EPUBs into `cacheDir/books/<source>/<id>.epub` (reuse the per-source book cache).

### S5. Covers for things without covers [audit L]
AO3, Royal Road, Scribble Hub, Gutenberg and Internet Archive items often have no cover, or a poor one. Use the
generated-cover drawable from audit Part L (complete code there). Lookup order: source cover → Open Library cover by
title and author (books) → generated cover.

---

## Part 4: New sources (each one is a task)

All of these use S1–S4. Do them in this order: N1 AO3 → N4 Gutenberg → N5 Internet Archive → N11 Open Library →
N2 Royal Road → N3 Scribble Hub → N8 YouTube → the rest. Part 4B (after N11) rates every other source.

### N1. Archive of Our Own (AO3): BL/M/M fan fiction
File `builtin/ao3/Ao3Repository.kt`. AO3 has no API, so this is HTML scraping. It's stable, plain HTML. Selectors
below are AO3's long-standing markup, but **verify each one in your phone's browser** (long-press → inspect isn't
available on phones, so use a desktop browser's developer tools once) before relying on it.

**Listing and search** (`getList`)
- URL: `https://archiveofourown.org/works/search?page=<n>&work_search[query]=<q>&work_search[language_id]=en&work_search[sort_column]=<col>&work_search[sort_direction]=desc`
  - `page` is 1-based, 20 works per page. Convert from Kotatsu's offset: `page = offset / 20 + 1`.
  - Sort columns: `revised_at` (updated), `kudos_count` (popular), `hits`, `created_at` (newest), `word_count`.
    Map `SortOrder.UPDATED → revised_at`, `POPULARITY → kudos_count`, `NEWEST → created_at`.
  - **Category filter**: `work_search[category_ids][]=23` is M/M. The other IDs are Gen 21, F/M 22, Other 24,
    F/F 116 and Multi 2246. **Verify these IDs** by checking AO3's search form (each checkbox's `value`).
  - Ratings: `work_search[rating_ids]=` with 10 General, 11 Teen, 12 Mature, 13 Explicit, 9 Not Rated (verify the
    same way). Apply your content level setting [audit D4] here.
  - Empty query plus M/M category plus `revised_at` gives a "latest M/M" feed, which makes a good default "Popular" tab.
- Each result is `li.work.blurb`:
  - id: the number in `h4.heading a[href^="/works/"]`
  - title: that link's text; authors: `a[rel=author]` (or "Anonymous")
  - rating: `span.rating` `title` attribute (e.g. "Explicit") → `ContentRating.ADULT` for Explicit and Mature,
    `SUGGESTIVE` for Teen, `SAFE` for General
  - category: `span.category` `title` (e.g. "M/M")
  - fandom: `h5.fandoms a.tag`
  - tags: `li.relationships a.tag`, `li.characters a.tag`, `li.freeforms a.tag`, plus the category itself as a tag
    "M/M", so BL synonyms [audit D2] match
  - summary: `blockquote.userstuff.summary`
  - stats: `dd.words`, `dd.chapters` (e.g. "12/20"; second number "?" means unfinished), `dd.kudos`, `dd.hits`
  - complete: chapters "x/x" → `MangaState.FINISHED`, else `ONGOING`
- Manga `url` = `/works/<id>`, `publicUrl` = full URL.

**Details** (`getDetailsImpl`)
- `GET /works/<id>/navigate?view_adult=true` → chapters are `ol.chapter.index li`, each `a` (href
  `/works/<id>/chapters/<cid>`) followed by a `span.datetime` like `(2024-05-01)`. Parse it as the upload date.
- A single-chapter work has no list: create one chapter with url `/works/<id>`.
- `view_adult=true` skips AO3's "this work could have adult content" interstitial. Only add it when the user's
  content level allows adult content.

**Chapter text** (`getChapterHtml`)
- `GET <chapter url>?view_adult=true`
- Body: `div#chapters div.userstuff` (for multi-chapter works: `div#chapters div.chapter div.userstuff[role=article]`).
  Remove `h3.landmark` ("Chapter Text").
- Optional: prepend chapter notes `div.notes .userstuff` in a collapsible block ("Show author's notes" setting).

**Offline / download**
- The work page has `li.download a[href$=".epub"]`. Download it and use S4 to read offline, which is a single
  request instead of one per chapter. Use it for "Download all".

**Login-only works**
- Some works redirect to `/users/login` (archive-locked). Detect it, show "Log in to AO3 to read this work", and
  open the site in the in-app browser. Your branch already lets JS sources "open their website for login"
  (commit `f19b194`); reuse that flow. The cookie jar keeps the session.

**Updates and notifications**
- The tracker works automatically: a new chapter appears in `/navigate`. Use AO3's own "Subscribe" only if logged in.
  Not needed.

**Politeness:** 3 s per request (S3). On 429, wait for `Retry-After`, otherwise 60 s. Cache listing pages for 10
minutes.

**Tests:** save one search page, one navigate page and one chapter page as HTML fixtures in
`app/src/test/resources/builtin/ao3/`. Write `Ao3ParserTest` like your branch's `jsext` tests (canned HTML in,
parsed models out).

### N2. Royal Road: English web novels
- Search: `https://www.royalroad.com/fictions/search?title=<q>&page=<n>`. Results are `div.fiction-list-item`, with
  title `h2.fiction-title a` (href `/fiction/<id>/<slug>`), cover `img`, and tags `.tags a`.
- Lists for the "Popular" and "Latest" tabs: `/fictions/best-rated`, `/fictions/latest-updates`, `/fictions/trending`.
- Details: `/fiction/<id>/<slug>`, with chapters in `table#chapters tbody tr` (`td a` for link and title, `time`
  `unixtime` attribute for the date) and description `.description`.
- Chapter text: `.chapter-content`.
  **Anti-scraping:** Royal Road inserts hidden "stolen content" sentences. A `<style>` block in the page hides
  elements with randomly named classes (`display: none`). Parse the page's `<style>` rules for
  `.<class>{display:none}` and remove those elements before returning the HTML.
- 1.5 s per request.

### N3. Scribble Hub: has Yaoi and Shounen Ai genres
- Search: `https://www.scribblehub.com/?s=<q>&post_type=fictionposts`. Genre listings like
  `https://www.scribblehub.com/genre/yaoi/` and `/genre/shounen-ai/` make good "Popular" tabs for BL.
- Series page: `/series/<id>/<slug>/`. Its table of contents is paginated: `?toc=<n>`, with chapters in
  `li.toc_w a` (newest first, so reverse them).
- Chapter text: `#chp_raw`.
- Verify the selectors in a browser first; 1.5 s per request.

### N4. Project Gutenberg (move from catalog to a real source)
- Move `searchGutenberg` from `core/media/PublicBookCatalog.kt` into `builtin/gutenberg/GutenbergRepository.kt` and
  add `&languages=en`.
- Popular tab: `https://gutendex.com/books/?languages=en&sort=popular&page=<n>`.
- Cover: `formats["image/jpeg"]`. Subjects become tags.
- Details: download `formats["application/epub+zip"]` (the `downloadUrl` your branch already extracts), open it with
  S4, and make each EPUB chapter a `MangaChapter`. `getChapterHtml` returns `EpubBook.chapterHtml(href)`.

### N5. Internet Archive (books, comics, video, audio)
Move `searchInternetArchive` into `builtin/archive/` with these fixes:
- Add language and borrow-only filters to the query:
  `(<q>) AND mediatype:texts AND language:(English OR eng) AND -collection:inlibrary AND -collection:printdisabled`.
  The two excluded collections are borrow-only books, which can't be downloaded without an account.
- Popular tab: same query without `<q>`, `sort[]=downloads desc`. Newest: `sort[]=publicdate desc`.
- Details: `https://archive.org/metadata/<identifier>` → `files[]`. Choose by format:
  - Books: `.epub` (preferred, use S4), else `Text PDF` `.pdf`. PDF goes to your existing `LocalPdfReaderActivity`
    after download.
  - Comics (`ia_comics`, query adds `AND collection:(comics)`): `.cbz`/`.cbr`. Download, then hand to Kotatsu's
    local CBZ reader (`local/`), or unzip pages and serve them as image pages.
  - Video (`ia_video`, `mediatype:movies`): `.mp4`/`.ogv` files become `MediaStream`s (S1).
  - Audio (`mediatype:audio`): `.mp3` files become chapters (MediaType AUDIO).
- Cover: `https://archive.org/services/img/<identifier>`.
- Open Library gets its own task (N11): its readable-now books go through this source via their `ia` identifier.

### N6. LibriVox (free audiobooks)
- `https://librivox.org/api/feed/audiobooks/?title=^<q>&format=json&extended=1&limit=30&offset=<n>`
- Each book: `sections[]` (one chapter each), with `listen_url` → `MediaStream`. Cover via the Internet Archive item
  (`url_iarchive`).
- Needs MediaType AUDIO (S1.5) and background playback.

### N7. OPDS catalogs as real sources
Each saved OPDS catalog becomes a source `opds:<hash of url>`, registered like S2 but dynamic. Navigation entries
become "filters" or browse pages; acquisition entries become books. Download the EPUB/PDF/CBZ into the shelf and open
it in-app (S4). Add **Standard Ebooks** (`https://standardebooks.org/feeds/opds`) as a suggested catalog. Check
whether it requires a Patron account, and if so leave it out of the defaults.

### N8. YouTube, PeerTube, SoundCloud (NewPipeExtractor) [audit B7]
Full steps are in audit B7. In this plan's terms:
- `builtin/youtube/YouTubeRepository.kt` implements `ContinuousMediaProvider.getStreams` with
  `StreamInfo.getInfo(...)` (HLS/DASH first, else best video-only plus best audio stream, merged by Media3).
- Channels and playlists are "manga" and videos are "chapters", so favouriting a channel gives new-video
  notifications through the existing tracker.
- NewPipeExtractor is GPL-3.0 (same as your app). Copy `DownloaderImpl` from the NewPipe app.
- **Ship app updates when YouTube breaks the extractor** (every few weeks). It's not updatable like JS sources.

### N9. Wattpad (unofficial endpoints, so build it last)
Big BL community, but no public API. The JSON endpoints the website uses change without notice. Find the current
ones with desktop browser dev tools (search page → Network tab) and implement them defensively. Health tracking
(Part 5) hides it automatically when it breaks. Map `mature: true` → ADULT.

### N10. More legal and official options to check
- **MangaDex** (Kotatsu-Redo parser, official API): proper "Boys' Love" tag. Pin it, and put it in the BL preset.
- **Webtoon (official)** and **Tapas**: check whether the redo parsers or your JS repos already have them; official
  English BL webtoons live there.
- **Comic Book Plus / Digital Comic Museum**: public-domain comics, simple HTML. Low priority.
- **Mihon extensions** [audit B2]: an optional extra pool of manga sources (APK-based, copy the loader from
  Kototoro). Only if Parts 4–5 still leave gaps.
- **LNReader plugins** [audit B6]: MIT-licensed JS novel plugins. Your QuickJS engine makes this more realistic than
  before, but they expect `cheerio` and `fetch` shims. Try it only if Mangayomi novel sources plus N1–N3 aren't
  enough.

### N11. Open Library (used properly, not just as a search box)
Open Library (`openlibrary.org`, run by the Internet Archive) is the best **book catalog** there is: tens of millions
of works, covers, subjects, ratings and series info. But most of its books are **borrow-only** (DRM-protected loans),
so it's a *discovery and metadata* source more than a reading source. Today your branch only searches it and opens
the result in the browser. Do this instead (`builtin/openlibrary/OpenLibraryRepository.kt`, MediaType BOOK):

**What each book can be:** the search API returns `ebook_access` per work:
- `public`: free full text on the Internet Archive. **Readable in-app**: take the work's `ia[0]` identifier and
  hand it to the Internet Archive source (N5) for files (EPUB/PDF).
- `borrowable` / `printdisabled`: a loan with DRM. Can't be read in-app. Show a "Borrow on Open Library" button that
  opens the book's page in the **in-app browser**; once the user logs in with a free Internet Archive account, they
  can read it in Internet Archive's web reader there. Reading progress isn't tracked for these; say so on the button.
- `no_ebook`: metadata only. Show it as "Not available to read". It's still useful for "find this book in my other
  sources" (search AO3/Royal Road/Gutenberg/JS novel sources by title) and for lists.

**Endpoints** (free, no key, please send a User-Agent with contact info and keep under ~1 request/second):
- Search: `https://openlibrary.org/search.json?q=<q>&fields=key,title,author_name,first_publish_year,cover_i,ebook_access,ia,subject,ratings_average,number_of_pages_median&limit=30&page=<n>`
  - A "Readable now" toggle keeps only `ebook_access == "public"`, filtered on your side. Default it **on**, so
    search results are things you can actually read.
- Popular/trending tab: `https://openlibrary.org/trending/daily.json` (also `weekly`, `monthly`)
- Subject browse: `https://openlibrary.org/subjects/<subject>.json?limit=30&offset=<n>&ebooks=true`. Good subjects
  for you: `gay_men`, `lgbtq_fiction`, `romance`, `fantasy`, `manga`, `graphic_novels`. **Check each subject's
  page exists and has results** before hard-coding it.
- Work details: `https://openlibrary.org/works/<OLID>.json` (description, subjects, series links),
  editions `.../editions.json`, ratings `.../ratings.json`
- Cover: `https://covers.openlibrary.org/b/id/<cover_i>-L.jpg`. Append `?default=false` to get a 404 instead of a
  blank image, then fall back to the generated cover (S5).

**Extra uses beyond being a source:**
1. **Cover and metadata provider for every book source.** When Gutenberg, Internet Archive, Royal Road or a local
   EPUB has no or poor cover/description, look up `search.json?title=<t>&author=<a>&limit=1` and use its cover,
   description, subjects and page count. Cache the result per title. Page count also improves time-left estimates
   (U6).
2. **"Want to read" import:** a user's public reading log is at
   `https://openlibrary.org/people/<username>/books/want-to-read.json` (also `currently-reading`, `already-read`).
   Import it as a library category. For each book, search your readable sources for a copy, and show "Found on:
   Gutenberg, Internet Archive" or "Not found yet".
3. **Series order:** Open Library links works to series (when the data exists), so the details screen can show
   "Book 2 of 5" with links to the others.

**Tests:** fixtures for `search.json`, a `works/<id>.json` and `trending/daily.json` in
`app/src/test/resources/builtin/openlibrary/`.

---

## Part 4B: Every other source considered (and what to do with each)

Verdicts:
- **Add**: build it as a source.
- **Metadata**: use it for covers, descriptions, ratings and discovery, not for reading.
- **Link out**: show a "Read officially" / "Open in app" button (in-app browser or the publisher's own app).
- **Skip**: not worth it, or not possible. The reason is given.

### Books and text
| Source | Verdict | How / why |
|---|---|---|
| **Open Library** | Add (as N11) + Metadata | See N11: readable-now books via the Internet Archive, borrow link-outs, covers and metadata for everything |
| **Project Gutenberg** | Add (N4) | Free EPUBs, clean |
| **Internet Archive** | Add (N5) | Books, comics, video, audio. Skip borrow-only collections |
| **Standard Ebooks** | Add via OPDS (N7) or scrape | Beautiful public-domain EPUBs. Check whether their OPDS feed needs a Patron account; if so, scrape `standardebooks.org/ebooks?query=` and its `.epub` links |
| **Feedbooks public domain** | Add via OPDS (N7) | Public-domain OPDS catalog. Add its catalog URL as a suggested OPDS feed if it still responds |
| **Archive of Our Own** | Add (N1) | Main BL fan-fiction source |
| **Royal Road** | Add (N2) | English web novels |
| **Scribble Hub** | Add (N3) | Has Yaoi and Shounen Ai genres |
| **Wattpad** | Add last (N9) | Big BL community, unofficial endpoints |
| **FanFiction.net** | Skip for now | Heavy Cloudflare protection, so scraping constantly breaks. If you want it, a Mangayomi JS source using your existing Cloudflare flow is the better route than a built-in |
| **NovelUpdates** | Metadata | An index of translated Asian web novels with a big "Yaoi"/"Shounen Ai" tag set, ratings and recommendations, but it doesn't host chapters. Use it for discovery ("Popular BL novels"), then search your novel sources for the title |
| **Google Books API** | Metadata | `https://www.googleapis.com/books/v1/volumes?q=<q>` (no key needed for light use). Fallback covers and descriptions when Open Library has none. Its free full-view books are mostly old public domain already covered by Gutenberg and the Internet Archive |
| **Libby / OverDrive, Hoopla** (public-library loans) | Link out | No public API for third-party apps, and DRM. A "Borrow from your library" button can open the Libby app (`https://libbyapp.com/search/…`) with the title prefilled |
| **Anna's Archive / LibGen / Z-Library** (shadow libraries) | Your call | Your branch already supports Anna's Archive's JS source EPUB contract (see `docs/JS_EXTENSIONS.md`). These host pirated books, so the legal risk is real. If you keep it, keep it out of the public defaults (F4) and off by default |
| **Kindle / Kobo / Apple Books** | Skip | DRM, no API |

### Manga, manhwa and comics
| Source | Verdict | How / why |
|---|---|---|
| **MangaDex** | Add (parser exists) | Official API, proper "Boys' Love" tag. Pin it, and put it in the BL preset |
| **MANGA Plus (Shueisha)** | Add if the redo parsers have it | Official, free, legal. Has *Boruto* (the Naruto sequel) and other Shueisha titles. Check Sources for "MANGA Plus" |
| **Webtoon (official)**, **Tapas** | Add if available | Official English webtoons, including BL. Check the redo parsers or your JS repos; otherwise Link out to their apps |
| **Lezhin, Tappytoon, Toomics, BomToon** (paid BL platforms) | Link out | Official and mostly paid, behind accounts with no API. A "Read officially" button on matching titles supports the creators |
| **Kodansha K MANGA, VIZ Manga, Azuki** (official publishers) | Link out | *Sailor Moon* is Kodansha and *Naruto* is VIZ/Shueisha. These are subscription apps with no API, so link out by title |
| **Comic Book Plus, Digital Comic Museum** | Add (low priority) | Public-domain golden-age comics, simple HTML |
| **Internet Archive comics** | Add (N5) | CBZ/CBR comics |
| **GlobalComix** | Check | Large free and indie comics platform. Investigate whether it has a stable JSON API before building anything |
| **Mihon/Tachiyomi extensions** | Optional (audit B2) | A huge extra manga pool, but they are APKs and need the Kototoro loader. Only if Parts 4–5 still leave gaps |
| **Raw sites (Korean/Chinese/Japanese)** | Via parsers/JS + translation | Pair with per-series auto-translate (audit Part N, U4) for early chapters |

### Video
| Source | Verdict | How / why |
|---|---|---|
| **YouTube** | Add (N8, NewPipe) | Official BL drama channels, anime clips, reviews. Channels and playlists are followable |
| **PeerTube, media.ccc.de** | Add (free with NewPipe) | Same extractor, just switch the service |
| **Internet Archive video** | Add (N5) | Public-domain films, classic cartoons, old anime |
| **Mangayomi JS anime sources** | Keep (Part 5) | Your existing anime pool. Improve with more extractors (audit O1/O2) |
| **Aniyomi / CloudStream extensions** | Optional (audit B8) | Bigger video pool, APK-based. Later, only if needed |
| **Crunchyroll, Netflix, Gagaoolala, iQIYI, Viki** (official streaming, incl. BL dramas) | Link out | DRM-protected. Use AniList/TMDB matches to show "Watch on <service>" buttons |
| **Tubi, Pluto TV, RetroCrush** (free with ads) | Link out | No public API, DRM on many titles |
| **Bilibili** | Check | Has anime and many Chinese BL drama clips. Region-locked and API-signed, so only via a JS source if one exists |

### Audio
| Source | Verdict | How / why |
|---|---|---|
| **LibriVox** | Add (N6) | Free public-domain audiobooks |
| **Internet Archive audio** | Add (N5) | Old-time radio, audiobooks, music |
| **SoundCloud, Bandcamp** | Add (free with NewPipe) | Audio dramas and fan audio |
| **Podcasts** (incl. BL audio dramas) | Add (later) | The **Podcast Index API** (`api.podcastindex.org`, free key) searches podcasts. Episodes are plain MP3s in RSS feeds, so a podcast is "manga", episodes are "chapters", and the tracker gives new-episode alerts |
| **Audible, Spotify audiobooks** | Skip | DRM, no API |

### Discovery and metadata (feeds into all of the above)
| Service | Use |
|---|---|
| **AniList** (already used for tracking) | Trending and "Boys' Love" tag lists, recommendations, English titles, cross-media relations (audit K10) |
| **MangaUpdates** | Release feeds and BL genres ("Yaoi", "Shounen Ai"), to find where a title is being released |
| **Open Library, Google Books** | Book covers, descriptions, series, page counts (N11) |
| **NovelUpdates** | BL web-novel discovery |
| **TMDB** (free key) | Metadata for live-action BL dramas and movies, plus "Where to watch" (link-outs) |
| **RSS** (Anime News Network, publishers like SuBLime and Seven Seas) | News feed (audit J) |

**How these combine in the app:** a title found in *any* discovery service gets a **"Find a copy"** button. It runs a
global search for the title across all your readable and watchable sources, using English and translated titles
(audit N3). It shows what it found, or the official link-outs if nothing free is available.

---

## Part 5: Make sources look after themselves [audit Part M, adapted to your JS sources]

JS sources are just `.js` files: no Android install prompts and no APK rules, so everything here is simpler than
the audit's APK version.

### P1. Auto-install on first run
After the welcome questions (language: English; content level; hidden tags), fetch every repo index, then install
automatically:
- `lang` `en` or `all` (if the source supports English), and NSFW only if the content level allows it
- **skip** JS sources for sites where a healthy Kotatsu-Redo parser exists (same host), since parsers are faster
  and better for manga
- 4 at a time in the background, with a small "Setting up sources… 37/112" card. Built-in sources (Part 4) work
  immediately.
Use the existing installer in `sourcerepo/domain/SourceRepoRepository.kt` and `jsext/repo/JsSourceStore.kt`. Only
the automation is new.

### P2. Automatic updates
New `sourcerepo/work/SourceUpdateWorker.kt` (WorkManager, every 12 h, network required, plus a run on app start if
the last one was more than 6 h ago):
- Fetch each repo index and compare each installed source's version.
- Download newer versions, verify them (F6), and swap them in atomically: write `<id>.js.new`, then rename. Keep
  `<id>.js.prev` for rollback.
- Reload that source in `JsSourceRegistry` / `JsExtensionProvider`.
- No notification; just a line on the Sources screen ("4 sources updated today").
- New English sources in an index get installed automatically (same filters as P1) and get the "new" badge.

### P3. One entry per site
Group all implementations (parser, JS, built-in) by normalized base-URL host (strip `www.` and `m.`). Show one row in
Browse and search. Send requests to the healthiest implementation and **fail over** to the next one on error.
Library entries stay on the implementation they were added from (P5 handles moving them).

### P4. Health and self-repair
Combine **community scores** (`sourcescore/`) with **local health** (your phone's own success/failure counts, added
to the sources table [audit B4]):
- 3 failures in a row → immediately re-check that source's repo for an update (bypassing the 12 h timer), update,
  and retry once. Most "site moved" breakages get fixed this way once the extension maintainer pushes a fix.
- Still failing after an update, but working before it → roll back to `.prev` and pause updates for 48 h.
- Failing for 7 days, or removed from its repo → hidden from Browse and search, and P5 runs.
- Cloudflare challenges don't count as failures. They need the user, not a repair.
- Your extension repo can help: add a nightly GitHub Action in `Best-BL-Mangayomi-Extensions` that runs each source's
  search and first chapter (your branch has `scripts/audit-js-compatibility.mjs` as a start) and writes
  `"status": "ok" | "broken"` into the index. The app then hides known-broken sources before you ever hit them.

### P5. Auto-move library titles off dead sources
For each library title on a dead source, find the same title on healthy sources (your branch's `alternatives/`
matcher; match chapters by number) and **move it automatically**. Keep categories, read marks and stats, and show
one summary with Undo ("Moved 6 titles from SiteX to SiteY"). Unclear matches go to a "Needs your choice" list.
Also create the default **presets** on first run (`explore/ui/preset/`):
- **BL & Yaoi:** MangaDex, your Best-BL JS sources, AO3 (M/M), Scribble Hub, plus BL-heavy parsers
- **Popular manga:** top-ranked English manga sources by score (`sourcescore/`)
- **Books & fanfic:** AO3, Royal Road, Scribble Hub, Gutenberg, Internet Archive Books, OPDS catalogs, JS novel sources
- **Anime & video:** JS anime sources, YouTube, Internet Archive Video
- **Audio:** LibriVox, Internet Archive audio, SoundCloud

### P6. What you see
The **Sources** tab shows one list grouped by language, each row with a status dot (green / yellow / red) and a kind
icon (📚 manga, 📖 book, 🎬 video, 🎧 audio). You can toggle and pin. There are no install, update or uninstall
buttons. Repo URLs, diagnostics and "re-run setup" live under Settings → Advanced.

---

## Part 6: Use the sources smartly (search and discovery)

### U1. BL tag synonyms [audit D2]
Your branch's `normalizedTag()` (`search/domain/SearchV2Helper.kt` line 163) ignores punctuation, but "yaoi" still
won't match "Boys' Love". Add the `TagSynonyms` object from audit D2 and use it in genre search, the filter sheet and
AO3 tags ("M/M"). Groups to include: yaoi / boys' love / BL / shounen ai / danmei / M/M / slash; yuri / girls' love /
GL / shoujo ai / F/F; omegaverse / ABO / alpha-beta-omega.

### U2. Global hidden tags and content level [audit D3, D4]
One "Hidden tags" list (e.g. non-con, NTR) applied to every source, including AO3 freeform tags and warnings. AO3's
`span.warnings` title gives the "Archive Warnings". Content level (Safe / Suggestive / Everything) is applied **per
work** using `contentRating`, so AO3 and Wattpad don't have to be hidden wholesale.

### U3. Global search across media types
Today Books search only includes the catalogs when the media type is set to BOOK (`SearchViewModel.doSearch` line ~236).
Once catalogs are real sources (Part 4), the special catalog branch can go. Global search then naturally includes
AO3, Royal Road, Gutenberg and the rest, filtered by the media-type chips your branch already has. Add an
"English only" toggle and a "Group same title" toggle [audit D5].

### U4. Translation for raw sources [audit Part N]
With per-series "Always translate" for manga pages (port Kototoro's `reader/translate/`) and title, description and
search-query translation, Korean, Chinese and Japanese raw sites become usable. They're often where new BL chapters
appear first. AO3 and novel sources benefit from N4 (novel chapter translation) for non-English works.

### U5. Discovery and feeds [audit J, K10, K11]
- AniList Discover tab: trending by tag `Boys' Love`, plus "because you read X", which searches your sources for the
  result.
- Follow a tag across sources (AO3 M/M plus a fandom, Scribble Hub Yaoi, MangaDex BL), giving a "New in your tags"
  feed.
- Release calendar from chapter dates; cross-media links ("Read the novel", "Watch the anime").

### U6. Time left [audit E]
Fix the stats bugs (F16), then show "~3 min left in chapter" and "~6 h to finish". Novels use words per minute (AO3
gives word counts per work, `dd.words`), video uses remaining duration. Long fan fics finally get an honest estimate.

---

## Part 7: Testing every source
- **Fixture tests per built-in source:** save real HTML/JSON responses under `app/src/test/resources/builtin/<source>/`,
  and test parsing offline in CI (same idea as your branch's `wordrain69.js` test and `MangayomiIndexTest`).
- **Live health sweep:** include built-in sources in your existing sweep (`sourcerepo/ui/SourceScanManager.kt`,
  `JsSourceDiagnostics.kt`): search "love", open the first result, load the first chapter or stream. Record the
  results into local health (P4).
- **Release build** (F1) on a real phone before every release, plus the audit's K14 smoke test. Your guided QA screen
  (`cb5e722`) can host it.

---

## Order of work (one line ≈ one or two sessions)
1. Merge PR #1, tag `cb5e722`.
2. **F2** (identity) → **F1** (release build and R8 rules) → **F3** (signed release). Install it on your phone.
3. **F4, F5, F6, F7** (repo URLs private, cookie isolation, hash pinning, SSL bypass) → **F8, F9, F10, F11**.
4. **S1** (one interface for text and video) → **S2** (built-in source type) → **S3** (polite networking) → **S4** (shared EPUB).
5. **N1 AO3** → **N4 Gutenberg** → **N5 Internet Archive** → **N11 Open Library** (fixes F12 and F13 for books; Open Library also becomes the cover and metadata provider).
6. **P1 → P2 → P4** (automatic install, updates, repair) → **P3, P5, P6** (one list, auto-move, presets, simple UI).
7. **F15** (back up sources, newest-wins restore) → **F16** (stats) → **U6** (time left).
8. **U1, U2, U3** (BL synonyms, hidden tags, unified search).
9. **N2 Royal Road**, **N3 Scribble Hub**, **N7 OPDS** → **F14** (books in the main library) → **S5** (covers).
10. **N8 YouTube** → **N6 LibriVox** (with AUDIO and background playback).
11. **U4** (translation) → **U5** (discovery, "Find a copy" button, Part 4B metadata services) → **N9 Wattpad**, **N10** and Part 4B "Add"/"Link out" items as needed (podcasts, MANGA Plus, official link-outs).
