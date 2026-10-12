# Monogatari: master build guide for ChatGPT (one step at a time)

Version 1, 2026-10-12. Written for branch **`claude/mangayomi-kotatsu-features-fb46y2`** at commit `cb5e722`
(your real app: Kotatsu-Redo base + Mangayomi JS extensions + books + video).

This single document replaces having to juggle `MONOGATARI_AUDIT.md` and `MONOGATARI_SOURCES_PLAN.md`. Those stay in
`docs/` as background reading; **everything ChatGPT needs is here.** Steps are small on purpose, so ChatGPT doesn't
time out or lose track.

---

## PART 0: How to use this document

### 0.1 The routine for every step
1. Open a **new ChatGPT chat** for each step, or at least every few steps. Long chats get confused.
2. Paste **the Project Context block (section 0.3)**, then **your progress log (0.4)**, then **the step's prompt**.
3. Paste the **files the step lists**, in full. On GitHub, open the file → "Raw" → copy all.
   If a file is very long, paste it in its own message with the line "File: `<path>` (part 1 of 2)".
4. ChatGPT replies with **complete replacement files**, one per message. Type `NEXT` to get the next one.
5. Put the files into your repo on a branch named after the step (e.g. `step-07-search-fixes`), commit and push.
   GitHub's **Build check** runs automatically.
6. Build green ✅ → merge the branch. Build red ❌ → open the failed run, copy the error (the last ~60 lines), paste it
   back to ChatGPT: "The build failed with this error, fix it." Repeat until green.
7. Do the step's **"Done when"** check on your phone if it says so.
8. Add one line to your progress log (0.4), then move to the next step.

### 0.2 If ChatGPT times out or cuts off
- Say: "Continue exactly where you stopped, starting with the file `<name>`."
- If a step is still too big, ask: "Split this step into parts a, b, c. Do only part a now."
- Never paste the whole repo. Paste only the files the step names, plus any file an error message mentions.
- If ChatGPT says it needs another file, find it on GitHub with the search box (press `t` on the repo page to search
  file names) and paste it.

### 0.3 Project Context block (paste at the start of every chat)
```
PROJECT CONTEXT - Monogatari (Android app, Kotlin)
- Fork of Kotatsu-Redo (a fork of Kotatsu manga reader). Kotlin package: org.koitharu.kotatsu (do NOT rename).
- Source paths: app/src/main/kotlin/org/koitharu/kotatsu/... ; resources in app/src/main/res/.
- Build: Gradle, Java 17, minSdk 23, targetSdk 37, Hilt (DI), Room (DB, current DATABASE_VERSION in
  core/db/MangaDatabase.kt), Coil 3 (images), OkHttp, kotlinx.serialization, Media3 (video), quickjs-kt (JS engine).
- Manga sources: Kotatsu-Redo parser library (com.github.clquwu:kotatsu-parsers-redo).
- Mangayomi JavaScript extensions run unmodified via QuickJS: package jsext/ (JsMangaSource "js:<id>",
  JsMangaRepository, JsExtensionProvider, JsSourceRegistry), repos in sourcerepo/.
- Media types: core/media/MediaType.kt (MANGA, BOOK, VIDEO). Novel/video reader: reader/ui/MediaReaderActivity.kt.
- Every source is a MangaSource with a MangaRepository (core/parser/MangaRepository.kt, Factory.createRepository).
- Style: tabs for indentation, match surrounding code, user-visible text in res/values/strings.xml.
RULES FOR YOU (ChatGPT):
1. Return COMPLETE files, never snippets or "...rest unchanged". One file per message; wait for NEXT.
2. Only change what the step asks. Don't rename, reformat or "improve" unrelated code.
3. Never change existing Manga/source IDs, database column names, or preference keys unless the step says so.
4. Any Room schema change = bump DATABASE_VERSION + add a Migration class + register it. Never destructive migration.
5. Don't add libraries unless the step says so. If you must, say exactly which line goes in gradle/libs.versions.toml
   and app/build.gradle.
6. If you are unsure how an existing class works, ASK me to paste it instead of guessing.
7. Add or update a unit test when the step says "test".
```

### 0.4 Progress log (keep this in `docs/PROGRESS.md`, paste it each time)
```
PROGRESS
- Step 1: done (merged PR #1, tagged v0.9-base)
- Step 2: done
- Step 3: in progress - build error about X
```

### 0.5 Ground rules for you
- **One step = one branch = one merge.** If a step goes badly, delete the branch; nothing else breaks.
- **Never skip Stage A and B.** They protect your data and make releases possible.
- Steps marked 📱 need a check on your phone. Install the APK from the build's "Artifacts" section.
- Steps marked ⚠️ touch the database or app identity. Back up first (Settings → Backup).

---

## STAGE A: Safety net (do first)

### Step 1: Make your real app the main branch
**Why:** your app lives only on `claude/mangayomi-kotatsu-features-fb46y2`; `devel` is old plain Kotatsu. New
sessions, and release workflows, start from the wrong code.
**No ChatGPT needed.** On GitHub:
1. Branches → create `archive/kotatsu-original` from `devel` (keeps the old history).
2. Open **PR #1** → **Merge pull request**. If GitHub reports conflicts, instead go to Settings → General → Default
   branch and set it to `claude/mangayomi-kotatsu-features-fb46y2`, or create a `main` branch from it and make that
   the default.
3. Releases → "Draft a new release" → tag `v0.9-base` on commit `cb5e722` → "Save draft" is enough. This is your
   restore point.
4. Also merge branch `ccr-7d1c1ef4-qm7uux` (it only adds the docs in `docs/`).
**Done when:** the repo's default branch shows the Monogatari code and `docs/` contains this file.

### Step 2: Start the progress log and the AI context file
**Prompt:**
> Create two files. (1) `docs/PROGRESS.md` with a heading "Monogatari progress" and the line "- Step 1: done".
> (2) `AGENTS.md` at the repo root containing the PROJECT CONTEXT block I pasted above, word for word, under a
> heading "Instructions for AI assistants".
**Done when:** both files are in the repo. (`AGENTS.md` is read automatically by some AI coding tools.)

### Step 3: Build a release (shrunk) APK in CI so release-only crashes show up
**Why:** CI only builds the debug APK (`.github/workflows/build-check.yml` lines 25–27). Release builds shrink code
with R8. No release has ever been built, and `app/proguard-rules.pro` has no rules for the JS engine.
**Paste:** `.github/workflows/build-check.yml`, `app/proguard-rules.pro`
**Prompt:**
> In `build-check.yml`, after the assembleDebug step, add a step "Build release (unsigned, R8)" that runs
> `./gradlew assembleRelease --stacktrace` and uploads `app/build/outputs/apk/release/*.apk` as an artifact named
> `monogatari-release-unsigned`. If assembleRelease requires signing, configure the release buildType to fall back
> to the debug signing config when the env var `RELEASE_KEYSTORE` is empty. Append to `proguard-rules.pro` the
> rules from Appendix A1 of my guide (pasted below).
(Paste Appendix A1.)
**Done when:** the build check is green and the release artifact appears. 📱 Install it, open one JS manga source,
one JS novel source and one JS video source. If one crashes, paste the crash text to ChatGPT ("add keep rules for
this").

---

## STAGE B: Identity and releases ⚠️

### Step 4: Give the app its own identity
**Why:** `app/build.gradle` line 50 is `applicationId 'io.github.kotatsuredo.kotatsu'`, Kotatsu-Redo's ID. It can't
coexist with their app, and changing it later loses users' data.
⚠️ **Before this step:** in your current app, Settings → Backup → create a backup and save it. After installing the
new-ID app, restore it.
**Paste:** `app/build.gradle`, `app/src/main/res/values/constants.xml`, `app/src/debug/res/values/constants.xml`,
`app/src/nightly/res/values/constants.xml` (if present)
**Prompt:**
> Change: applicationId → `io.github.coolchan03.monogatari`; versionCode → 1; versionName → "0.9.0".
> In constants.xml: `sync_authority_history` → `io.github.coolchan03.monogatari.history`,
> `sync_authority_favourites` → `io.github.coolchan03.monogatari.favourites`, `account_type_sync` →
> `io.github.coolchan03.monogatari.sync`. In debug and nightly constants.xml, the same keys with `.debug` / `.nightly`
> inserted before the last part. `github_updates_repo` → `coolchan03/Monogatari`. `url_weblate`, `url_user_manual`:
> point them to `https://github.com/coolchan03/Monogatari`. `app_icon_url` → `https://raw.githubusercontent.com/coolchan03/Monogatari/HEAD/metadata/en-US/images/icon.png`.
> Remove `discord_app_id` usage only if I say so; leave it for now.
**Done when:** the build is green. 📱 The new app installs **next to** the old one, and your backup restores into it.

### Step 5: Fix remaining "Kotatsu" text
**Paste:** run this search on GitHub (repo search box): `Kotatsu path:app/src/main/res/values` and paste the matching
lines (not whole files) of `strings.xml`.
**Prompt:**
> For each user-visible string that says Kotatsu or Kotatsu-Redo, replace it with Monogatari, except credits and
> "based on Kotatsu" attributions. Give me the edited lines in a form I can apply, then the full file.
For the 30+ translated `values-*/strings.xml` files, run on a computer (or GitHub Codespaces, free tier):
`sed -i 's/Kotatsu-Redo/Monogatari/g; s/Kotatsu/Monogatari/g' app/src/main/res/values-*/strings.xml`, then check
`git diff` for credit lines you want to keep.

### Step 6: Signed releases
**Why:** `publish-release.yml` exists but needs secrets, and it only runs from the default branch (done in Step 1).
**No ChatGPT needed, except to read errors:**
1. On a computer: `keytool -genkey -v -keystore monogatari.jks -keyalg RSA -keysize 4096 -validity 36500 -alias monogatari`.
   **Back this file and its passwords up in two places.** If you lose them, you can never update the app.
2. `base64 -w0 monogatari.jks > keystore.txt`. Then GitHub → Settings → Secrets and variables → Actions → add the
   secrets that `publish-release.yml` expects (open the file; the names are in `${{ secrets.NAME }}`).
3. Actions → "publish release" → Run workflow.
**Done when:** a GitHub Release with a signed APK exists. 📱 Install it. From now on, the in-app updater finds new
releases.

### Step 7: Google developer verification (outside the app)
Google requires apps on certified Android phones to be registered to a verified developer: enforced in some
countries since Sept 2026, worldwide in 2027. For you and friends, create a **free limited-distribution
(hobbyist/student) account** (no ID needed, up to 20 devices) and register `io.github.coolchan03.monogatari` with
your keystore's certificate. Do it after Steps 4 and 6. Also add the repo to **Obtainium** on your phone for automatic
updates from GitHub Releases.

---

## STAGE C: Security and privacy fixes

### Step 8: Keep extension repo URLs out of public code
**Why:** `sourcerepo/data/SourceRepoStore.kt` line 38 hard-codes 9 extension repos. Kotatsu was shut down after legal
threats. A public app that ships scraper repos is an easy target.
**Paste:** `sourcerepo/data/SourceRepoStore.kt`, `app/build.gradle`, `.github/workflows/build-check.yml`,
`.github/workflows/publish-release.yml`
**Prompt:**
> Move DEFAULT_REPOS out of the code. In app/build.gradle read a property `monogatari.repos` from local.properties
> (the same way `tg_backup_bot_token` is read) and expose `buildConfigField "String", "DEFAULT_REPOS", "\"...\""`
> (comma-separated, empty if missing). In SourceRepoStore use
> `BuildConfig.DEFAULT_REPOS.split(',').map { it.trim() }.filter { it.startsWith("https://") }.toSet()`. In both
> workflows add a step before the build that writes `monogatari.repos=${{ secrets.MONOGATARI_REPOS }}` into
> local.properties. Also add support for a link `monogatari://setup?repos=<url-encoded comma list>` that adds those
> repos (add the intent-filter to AndroidManifest.xml for the activity that already handles deep links; ask me to
> paste AndroidManifest.xml).
Then add the secret `MONOGATARI_REPOS` on GitHub with your repo list (copy it from the old `DEFAULT_REPOS`), and put
the same line in your own `local.properties`.

### Step 9: Stop JS sources from using cookies for other sites
**Why:** JS sources share the app's cookie jar (`jsext/source/JsOkHttpTransport.kt`), so any JS source can send your
login cookies for other websites.
**Paste:** `jsext/source/JsOkHttpTransport.kt`, `jsext/repo/JsSourceEntry.kt`, `jsext/JsModels.kt`
**Prompt:**
> Wrap the CookieJar used by JsOkHttpTransport in a `ScopedCookieJar(delegate, allowedHosts)` that only loads and
> saves cookies when the request host equals, or is a subdomain of, one of the source's allowed hosts. Allowed hosts
> are the hosts of the source's baseUrl and apiUrl (from its entry). All other hosts get an empty list, and their
> responses' cookies are dropped. Add a unit test for the host matching (`example.com` allows `www.example.com` and
> `cdn.example.com`, not `badexample.com`).

### Step 10: Always verify extension downloads
**Why:** `sourcerepo/domain/SourceRepoRepository.kt` line 147 accepts a file without checking it when the index has
no `sha256`.
**Paste:** `sourcerepo/domain/SourceRepoRepository.kt`, `jsext/repo/JsSourceStore.kt`
**Prompt:**
> When the index has no sha256: compute the downloaded file's SHA-256. On first install, store it with the source
> version (trust on first use). On an update, accept a different hash only if the version number increased; if the
> version is the same and the hash differs, refuse the update and report "Source file changed without a version
> bump". Keep current behaviour when sha256 is provided. Unit test the decision function.
Also in your extension repo `Best-BL-Mangayomi-Extensions`, ask ChatGPT for a GitHub Action that fills in `sha256`
for every source in the index on each push.

### Step 11: Make "ignore SSL errors" apply to one source only
**Why:** accepting the SSL dialog once disables certificate checks for the **whole** app
(`core/network/NetworkModule.kt` line 76): sync, logins, update downloads.
**Paste:** `core/network/NetworkModule.kt`, `core/network/SSLUtils.kt`, `core/exceptions/resolve/ExceptionResolver.kt`,
`core/prefs/AppSettings.kt`
**Prompt:**
> Replace the global `isSSLBypassEnabled` with `sslBypassHosts: Set<String>` in AppSettings (new key; migrate: if the
> old key is true, clear it). Remove disableCertificateVerification from the base client. In the manga/scraping
> client, add an interceptor or a second client so certificate verification is skipped only for requests whose host
> is in sslBypassHosts. The SSL dialog in ExceptionResolver should name the host and add only that host.

### Step 12: Secure the app-lock PIN
**Why:** `settings/protect/ProtectSetupViewModel.kt` line 42 stores `password.md5()`, which is crackable instantly.
**Paste:** `settings/protect/ProtectSetupViewModel.kt`, `main/ui/protect/ProtectViewModel.kt`, `core/prefs/AppSettings.kt`
**Prompt:**
> Store the PIN as PBKDF2WithHmacSHA256 (120000 iterations, 16-byte random salt), saved as "pbkdf2$<salt b64>$<hash b64>".
> When checking: if the stored value has no "pbkdf2$" prefix, compare MD5 as before and, on success, re-save in the
> new format. Use a constant-time comparison. Unit test hash/verify and the MD5 migration.

### Step 13: Privacy defaults and an 18+ question
**Paste:** `core/prefs/AppSettings.kt`, the welcome screen files in `main/ui/welcome/` (paste the list of file names
first; ChatGPT will ask for the ones it needs)
**Prompt:**
> Change defaults: KEY_DISCORD_RPC_SKIP_NSFW → true; KEY_DOH → DoHProvider.CLOUDFLARE; KEY_MIRROR_SWITCHING → true;
> KEY_STATS_ENABLED → true. Only change the default values in the getters, not the stored values of existing users.
> In the welcome flow add one question: "Show adult (18+) content?" (No / Yes, I'm 18+), which sets the existing NSFW
> setting.

### Step 14: Move diagnostics into Advanced settings
**Why:** your own audit says the JS diagnostics, health sweep and guided QA screens shouldn't be in the normal flow.
**Paste:** the files that open them (search the repo for `JsSourceDiagnostics`, `FirstRunHealthChecks`, and the
guided QA screen from commit `cb5e722`), plus the settings XML that lists them.
**Prompt:**
> Keep all diagnostics features, but reach them only from Settings → Advanced → "Source diagnostics". The first-run
> health check should run silently and only show something if a problem is found.

---

## STAGE D: Quick wins

### Step 15: Search fixes
**Paste:** `search/ui/multi/SearchMenuProvider.kt`, `search/ui/multi/SearchViewModel.kt`
**Prompt:**
> (1) In onPrepareMenu also restore the "pinned sources only" checkbox from the ViewModel (add
> `val isPinnedOnly: Boolean get() = pinnedOnly.value`). (2) In `retry()`, don't clear `results` before the old job
> has stopped. Move `results.value = emptyList()` into `doSearch()` right after `prevJob?.cancelAndJoin()`.
> (3) Change MAX_PARALLELISM from 4 to 8.

### Step 16: BL genre synonyms
**Why:** genre search treats "yaoi", "Boys' Love", "BL", "Shounen Ai", "Danmei" and "M/M" as different tags.
**Paste:** `search/domain/SearchV2Helper.kt` plus Appendix A2
**Prompt:**
> Add `search/domain/TagSynonyms.kt` from Appendix A2. In SearchV2Helper, wherever a tag title is compared with the
> query (including `normalizedTag()` comparisons), treat them as equal if
> `TagSynonyms.expand(query).map { it.normalizedTag() }` contains the tag's normalizedTag. Unit test: "yaoi" matches a
> tag "Boys' Love", "gl" matches "Girls' Love", "action" matches only "action".

### Step 17: Global hidden tags
**Paste:** `core/prefs/AppSettings.kt`, `list/ui/MangaListViewModel.kt`, `search/domain/SearchV2Helper.kt`, the
settings XML for content/suggestions (ask ChatGPT which)
**Prompt:**
> Add a setting "Hidden tags" (Set<String>, editable list). Hide any manga with a tag in the set (compared via
> TagSynonyms and normalizedTag) in all lists (next to `skipNsfwIfNeeded()` in MangaListViewModel, line ~53) and in
> search results (SearchV2Helper post-filter). Show a small "N hidden" note at the bottom of lists when something was
> hidden.

### Step 18: Content level per title (don't hide whole BL sources)
**Why:** today NSFW is all-or-nothing, and whole sources marked adult disappear.
**Paste:** `core/prefs/AppSettings.kt`, `core/model/MangaSource.kt`, `list/ui/MangaListViewModel.kt`, `core/model/Manga.kt`
**Prompt:**
> Add "Content level": SAFE / SUGGESTIVE / EVERYTHING (default derived from the existing NSFW setting: disabled →
> SUGGESTIVE, enabled → EVERYTHING). Filter individual titles by `Manga.contentRating` (SAFE, SUGGESTIVE, ADULT; null
> counts as SAFE). Keep "hide adult-only sources" as a separate switch, default off. Replace uses of
> isNsfwContentDisabled for list filtering with the new level; leave source-catalog filtering on the separate switch.

---

## STAGE E: Source foundation (needed before new sources)

### Step 19: One reader interface for text and video sources
**Why:** the novel/video reader only accepts JS sources (`reader/ui/MediaReaderActivity.kt` line 200
`asJsSource() ?: return showError`; `core/nav/ReaderIntent.kt` line 33 `isJsContinuousMedia()`). Built-in sources like
AO3 couldn't open.
**Paste:** Appendix A3, `reader/ui/MediaReaderActivity.kt`, `jsext/source/JsMangaRepository.kt`, `jsext/JsModels.kt`,
`core/media/MediaTypeFilter.kt`, `core/nav/ReaderIntent.kt`
**Prompt:**
> Add `core/media/ContinuousMediaProvider.kt` from Appendix A3. Make JsMangaRepository implement it: getChapterHtml
> calls the extension's getHtmlContent(manga.title, chapter.url); getStreams calls getVideoList(chapter.url) and maps
> JsVideo → MediaStream (skip tracks whose file is null). Move that logic out of MediaReaderActivity. The activity
> gets the repository through an injected MangaRepository.Factory and casts it to ContinuousMediaProvider, showing
> the existing "unsupported" error if the cast fails. Rename `isJsContinuousMedia()` to `isContinuousMedia()`
> (true when mediaType() != MANGA and the repository implements ContinuousMediaProvider; for now: JS sources). Update
> callers (ReaderIntent line 33, ReaderViewModel lines ~268, ~483, ~565; ask me to paste ReaderViewModel).
**Done when:** 📱 JS novels and JS anime open and play exactly as before.

### Step 20: Add AUDIO as a media type
**Paste:** `core/media/MediaType.kt`, `core/media/MediaTypeFilter.kt`, `reader/ui/MediaReaderActivity.kt`, files that
show media-type filter chips (search the repo for `MediaType.VIDEO`)
**Prompt:**
> Add AUDIO to MediaType. Treat it like VIDEO in MediaReaderActivity (same player, but hide the video surface and
> show the cover image with play controls). Add an "Audio" chip wherever Manga/Books/Video chips exist.

### Step 21: Built-in source type
**Paste:** Appendix A4, `core/model/MangaSource.kt`, `core/parser/MangaRepository.kt`, `core/media/MediaTypeFilter.kt`,
`explore/data/MangaSourcesRepository.kt`, `jsext/source/JsMangaSource.kt` (as the example)
**Prompt:**
> Add `builtin/BuiltinSource.kt` from Appendix A4 (start with only `Gutenberg` in the list; other entries will be added
> by later steps). Register built-in sources exactly where JsMangaSource is registered: MangaSource(name) parsing
> (MangaSource.kt ~line 48), NSFW (~60: isAdultOnly), locale (~89), title (~104/125), mediaType() in
> MediaTypeFilter, Factory.createRepository in MangaRepository.kt (~98: delegate to a new
> `builtin/BuiltinRepositoryFactory` injected with @MangaHttpClient OkHttpClient and MemoryContentCache), and the
> enabled-sources list in MangaSourcesRepository (~384–412), enabled by default. Create a stub
> GutenbergRepository extending CachingMangaRepository that returns empty lists for now.
**Done when:** "Project Gutenberg" appears in Explore and opens (empty).

### Step 22: Polite networking for scraped sites
**Paste:** Appendix A5, `builtin/BuiltinRepositoryFactory.kt`
**Prompt:**
> Add `builtin/PoliteHostInterceptor.kt` from Appendix A5. In BuiltinRepositoryFactory build a client per source:
> `mangaClient.newBuilder().addInterceptor(PoliteHostInterceptor(ms)).build()`, where ms comes from a map: ao3 3000,
> wattpad 1000, royalroad 1500, scribblehub 1500, everything else 300.

### Step 23: One shared EPUB reader engine
**Why:** EPUB parsing exists in `jsext/JsEpubBridge.kt` and in `core/media/LocalEpubReaderActivity.kt`. Built-in
book sources need it too.
**Paste:** `jsext/JsEpubBridge.kt`, `core/media/LocalEpubReaderActivity.kt`
**Prompt:**
> Extract the EPUB parsing into `core/media/epub/EpubBook.kt`: `class EpubBook private constructor(...)` with
> `companion fun open(file: File): EpubBook`, `val title`, `val author`, `val coverBytes: ByteArray?`,
> `val chapters: List<EpubChapter>` (title, href, in reading order using the table of contents when present, else
> the spine), `fun chapterHtml(href: String): String`. Make JsEpubBridge and LocalEpubReaderActivity use it, with
> behaviour unchanged. Unit test with a tiny EPUB built in the test (zip with container.xml, an OPF and two XHTML
> files).

---

## STAGE F: New sources (books, fan fiction, audio, video)
Each source is split into **(a) parser + tests** and **(b) repository + wiring**, so each chat stays small.
For scraped sites, first save real pages: open the URL in a desktop browser → right-click → "Save page as… (HTML only)"
→ put them in `app/src/test/resources/builtin/<source>/`. Commit them before the step and paste them to ChatGPT
(trim to the relevant part if huge).

### Step 24: Project Gutenberg
**Paste:** `builtin/BuiltinSource.kt`, stub `GutenbergRepository.kt`, `core/media/PublicBookCatalog.kt`,
`core/media/epub/EpubBook.kt`, `jsext/source/JsMangaRepository.kt` (as an example), `core/media/ContinuousMediaProvider.kt`
**Prompt:**
> Implement GutenbergRepository (MediaType BOOK) with gutendex.com:
> - getList: query → `https://gutendex.com/books/?search=<q>&languages=en&page=<n>`; no query →
>   `?languages=en&sort=popular&page=<n>`. 32 results per page; page = offset / 32 + 1.
> - Map: title, authors[].name, subjects → tags, cover formats["image/jpeg"], url "/ebooks/<id>",
>   contentRating SAFE. Ids: stable hash of source name + url (copy uid() from JsMangaRepository).
> - getDetails: download formats["application/epub+zip"] to cacheDir/books/gutenberg/<id>.epub (reuse if present),
>   open with EpubBook, one MangaChapter per EpubChapter (url = "<id>#<href>").
> - getPages: one synthetic page (like JsMangaRepository NOVEL). Implement ContinuousMediaProvider.getChapterHtml
>   via EpubBook.chapterHtml.
> - Remove Gutenberg from PublicBookCatalog and from the Books catalog search in SearchViewModel (now it's a normal source).
> Unit test the JSON mapping with a saved gutendex response.
**Done when:** 📱 search "Pride and Prejudice" → open → read chapter 1 in-app → it appears in History.

### Step 25: Internet Archive (books, comics, video, audio)
**Paste:** `builtin/BuiltinSource.kt`, `builtin/BuiltinRepositoryFactory.kt`, `builtin/gutenberg/GutenbergRepository.kt`
(as the example), `core/media/PublicBookCatalog.kt`, `core/media/ContinuousMediaProvider.kt`
**Prompt (25a, books):**
> Add BuiltinSource entries ia_books (BOOK), ia_video (VIDEO), ia_audio (AUDIO), ia_comics (MANGA) and an
> `ArchiveRepository(kind)`. Search URL:
> `https://archive.org/advancedsearch.php?q=<expr>&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=subject&rows=30&page=<n>&output=json&sort[]=<sort>`.
> Books expression: `(<q>) AND mediatype:texts AND language:(English OR eng) AND -collection:inlibrary AND -collection:printdisabled`
> (no query → omit the first part). Sort: popularity "downloads desc", updated "publicdate desc".
> Cover `https://archive.org/services/img/<id>`. Details: GET `https://archive.org/metadata/<id>`, choose the first
> file ending .epub (else "Text PDF" .pdf). EPUB → EpubBook chapters like Gutenberg. PDF → one chapter that opens the
> existing LocalPdfReaderActivity after download (ask me to paste it). Remove Internet Archive from PublicBookCatalog
> and SearchViewModel's catalog search. Unit test parsing of a saved search and metadata response.
**Prompt (25b, video and audio):**
> Video expression: `(<q>) AND mediatype:movies`; audio: `(<q>) AND mediatype:audio`. Details: one chapter per playable
> file (.mp4/.ogv for video; .mp3 for audio, sorted by "track" or name). getStreams returns
> `https://archive.org/download/<id>/<file>` as a MediaStream.
**Prompt (25c, comics):**
> Comics expression: `(<q>) AND collection:(comics)`. Choose a .cbz (else .cbr is unsupported → skip). Download, unzip
> image entries in name order into cacheDir, and return them as image pages (MANGA type, normal reader).
**Done when:** 📱 one book, one video, one audio track and one comic work in-app.

### Step 26: Open Library (catalog, "readable now", covers for everything)
**Paste:** `builtin/BuiltinSource.kt`, `builtin/BuiltinRepositoryFactory.kt`, `builtin/archive/ArchiveRepository.kt`,
`core/media/PublicBookCatalog.kt`
**Prompt (26a, source):**
> Add BuiltinSource openlibrary (BOOK). Search:
> `https://openlibrary.org/search.json?q=<q>&fields=key,title,author_name,first_publish_year,cover_i,ebook_access,ia,subject,ratings_average,number_of_pages_median&limit=30&page=<n>`.
> No query: `https://openlibrary.org/trending/daily.json`. Add a filter option "Readable now" (default on) that keeps
> only ebook_access == "public". Cover `https://covers.openlibrary.org/b/id/<cover_i>-L.jpg`. Details: for "public"
> works, delegate chapters and text to ArchiveRepository using ia[0]. For "borrowable"/"printdisabled", show a
> single chapter titled "Borrow on Open Library" that opens `https://openlibrary.org<key>` in the in-app browser. For
> "no_ebook", show no chapters and a description line "Not available to read". Send User-Agent
> "Monogatari/0.9 (+https://github.com/coolchan03/Monogatari)". Remove Open Library from PublicBookCatalog.
**Prompt (26b, metadata helper):**
> Add `core/media/BookMetadataProvider` that, given title and author, calls
> `search.json?title=<t>&author=<a>&limit=1&fields=cover_i,first_sentence,subject,number_of_pages_median` and caches
> the result per title in a small Room table (new migration). Use it to fill missing cover, description and page
> count for BOOK titles from any source.
**Prompt (26c, want-to-read import, optional):**
> Settings → Import → "Open Library reading list": ask for a username, fetch
> `https://openlibrary.org/people/<u>/books/want-to-read.json` (also currently-reading, already-read), create a
> favourites category per list, and for each book search readable sources for a copy.

### Step 27: Archive of Our Own (AO3)
**Before:** save these pages from a desktop browser into `app/src/test/resources/builtin/ao3/`:
a search result page (`https://archiveofourown.org/works/search?work_search[query]=love&work_search[language_id]=en`),
a multi-chapter work's `/works/<id>/navigate`, one chapter page, and one single-chapter work page.
Also check AO3's search form: the checkbox **values** for categories (M/M is believed to be 23) and ratings
(General 10, Teen 11, Mature 12, Explicit 13). Write down what you see.
**Prompt (27a, parser + tests):**
> Create `builtin/ao3/Ao3Parser.kt` (pure functions, no network) and tests using the saved HTML:
> - parseSearch(html): each `li.work.blurb` → id (digits from `h4.heading a[href^="/works/"]`), title (that link
>   text), authors (`a[rel=author]`, else "Anonymous"), rating (`span.rating` title), category (`span.category` title),
>   fandoms (`h5.fandoms a.tag`), tags (`li.relationships a.tag`, `li.characters a.tag`, `li.freeforms a.tag`),
>   warnings (`li.warnings a.tag`), summary (`blockquote.userstuff.summary` html), words (`dd.words`), chapters
>   (`dd.chapters`, e.g. "12/20" or "3/?"), kudos (`dd.kudos`), updated date (`p.datetime`). Also hasNextPage
>   (`li.next a` exists).
> - parseNavigate(html): `ol.chapter.index li` → (title, url `/works/<id>/chapters/<cid>`, date from `span.datetime`
>   "(yyyy-MM-dd)").
> - parseChapterText(html): the chapter body `div#chapters div.userstuff` with `h3.landmark` removed; also the work
>   notes `div.notes .userstuff` (optional).
> - isLoginRequired(html, finalUrl): true if redirected to /users/login.
**Prompt (27b, repository):**
> Add BuiltinSource ao3 (BOOK, en, baseUrl https://archiveofourown.org, isAdultOnly false). Ao3Repository using
> Ao3Parser:
> - getList: URL `/works/search?page=<n>&work_search[query]=<q>&work_search[language_id]=en&work_search[sort_column]=<col>&work_search[sort_direction]=desc`
>   plus `work_search[category_ids][]=<ids>` from a filter (default M/M = <id I verified>) and rating ids allowed
>   by the content level. 20 per page; page = offset/20+1. Sort: POPULARITY→kudos_count, UPDATED→revised_at,
>   NEWEST→created_at. Filter capabilities: search, tags (category chips: M/M, F/M, F/F, Gen, Multi, Other).
> - Map rating: Explicit/Mature → ADULT, Teen → SUGGESTIVE, General → SAFE, Not Rated → SUGGESTIVE. Category
>   becomes a tag ("M/M"), and warnings become tags prefixed "Warning: ". State: "x/x" → FINISHED, else ONGOING.
> - getDetails: GET `/works/<id>/navigate?view_adult=true` (add view_adult only if the content level is EVERYTHING);
>   if there's no chapter list, one chapter with url `/works/<id>`.
> - getChapterHtml: GET chapter url + `?view_adult=true`; if isLoginRequired, throw an error whose message says "Log in
>   to AO3 to read this work" and that offers opening the site in the in-app browser (reuse how JS sources open
>   their website for login; ask me for the file).
> - Option "Download as EPUB" (download action): use the work page's `li.download a[href$=".epub"]` and EpubBook.
**Done when:** 📱 browse AO3 M/M sorted by updated, open a multi-chapter work, read chapter 2, close the app, reopen,
and it resumes. Updates appear in the Feed when a new chapter is posted.

### Step 28: Royal Road
**Before:** save a search page (`/fictions/search?title=dragon`), a fiction page, a chapter page.
**Prompt (28a, parser + tests):**
> `builtin/royalroad/RoyalRoadParser.kt`: search → `div.fiction-list-item` (title `h2.fiction-title a`, href
> `/fiction/<id>/<slug>`, cover img src, tags `.tags a`); fiction page → description `.description`, chapters
> `table#chapters tbody tr` (link `td a`, date from `time[unixtime]`); chapter → `.chapter-content`, **removing
> anti-scraping text**: parse every `<style>` block for rules like `.abc123{display:none}` (any spacing) and delete
> elements with those classes. Test that hidden sentences are removed.
**Prompt (28b, repository):**
> BuiltinSource royalroad (BOOK). Lists: popular `/fictions/best-rated?page=<n>`, updated `/fictions/latest-updates?page=<n>`,
> search `/fictions/search?title=<q>&page=<n>`. Wire like Ao3Repository.

### Step 29: Scribble Hub (Yaoi and Shounen Ai genres)
**Before:** save a search page (`/?s=love&post_type=fictionposts`), the genre page `/genre/yaoi/`, a series page with
`?toc=1`, a chapter page. Check that the selectors below match.
**Prompt (29a + 29b):**
> Parser + tests, then repository (BuiltinSource scribblehub, BOOK). Series: `/series/<id>/<slug>/`; table of contents
> paginated with `?toc=<n>`, chapters `li.toc_w a` (newest first, so reverse); chapter text `#chp_raw`. Lists: search,
> genre pages (`/genre/yaoi/`, `/genre/shounen-ai/`) exposed as tag filters.

### Step 30: Saved OPDS catalogs as real sources
**Paste:** `core/media/OpdsCatalog.kt`, the code that stores saved catalogs (search for "Saved OPDS"),
`builtin/BuiltinRepositoryFactory.kt`, `core/model/MangaSource.kt`
**Prompt:**
> Each saved OPDS catalog becomes a source named `opds:<sha1 of url, first 12 chars>` (an `OpdsSource` data class like
> JsMangaSource, registered the same way). Navigation entries become browse pages (filter "section"). Acquisition
> entries become books: download EPUB/PDF/CBZ to cacheDir/books/opds/ and open in-app (EpubBook, PDF reader, or
> image pages). Remove "Saved OPDS" from SearchViewModel's catalog branch. Suggest
> `https://standardebooks.org/feeds/opds` when adding a catalog.

### Step 31: LibriVox audiobooks
**Prompt:**
> BuiltinSource librivox (AUDIO). Search `https://librivox.org/api/feed/audiobooks/?title=^<q>&format=json&extended=1&limit=30&offset=<offset>`;
> no query → same without title, sorted by newest (`&since=` not needed). Each `sections[]` item → chapter
> (title, listen_url as MediaStream). Cover: `https://archive.org/services/img/<identifier from url_iarchive>`.
> Plus background playback: add a Media3 `MediaSessionService` so audio continues with the screen off, with a
> notification and lock-screen controls (add `androidx.media3:media3-session`, same version as media3).

### Step 32: YouTube, PeerTube and SoundCloud (NewPipeExtractor)
Split into 32a–32d; each is one chat.
**32a, dependency + downloader.** Paste `gradle/libs.versions.toml`, `app/build.gradle`, `core/BaseApp.kt`, and the
text of NewPipe's `DownloaderImpl.java` (from `github.com/TeamNewPipe/NewPipe`, file
`app/src/main/java/org/schabi/newpipe/DownloaderImpl.java`; both GPL-3.0, same as your app).
> Add `com.github.TeamNewPipe:NewPipeExtractor:<latest tag, e.g. v0.26.5>`. If java.nio errors appear, switch
> coreLibraryDesugaring to desugar_jdk_libs_nio. Convert DownloaderImpl to Kotlin as
> `builtin/youtube/NewPipeDownloader.kt` using our injected OkHttpClient; drop the reCAPTCHA activity (throw an
> error instead). Initialise in BaseApp.onCreate: `NewPipe.init(downloader, Localization("en","US"), ContentCountry("US"))`.
> Add keep rules: `-keep class org.schabi.newpipe.extractor.** { *; }`, `-keep class org.mozilla.javascript.** { *; }`,
> `-dontwarn org.mozilla.javascript.**`, `-dontwarn java.beans.**`, `-dontwarn javax.script.**`.

**32b, search and channels.**
> BuiltinSource youtube (VIDEO). getList with query: `SearchInfo.getInfo(ServiceList.YouTube,
> ServiceList.YouTube.searchQHFactory.fromQuery(q, listOf("videos"), ""))`; next pages via `SearchInfo.getMoreItems`
> (keep the Page object per query like JsMangaRepository's pageStates). A filter switches between videos, channels
> and playlists. Each video item → Manga with one chapter (itself). Channel → Manga whose chapters are its videos
> (ChannelInfo → videos tab → ChannelTabInfo). Playlist → chapters from PlaylistInfo.relatedItems.
> No query: trending is unreliable, so show search results for "BL drama" by default.

**32c, playback.**
> getStreams: `StreamInfo.getInfo(ServiceList.YouTube, url)`. Prefer hlsUrl, then dashMpdUrl, then the best muxed
> videoStreams entry. If only video-only and audio streams exist, return both, and in MediaReaderActivity merge
> them with Media3 MergingMediaSource. Subtitles from info.subtitles.

**32d, PeerTube and SoundCloud.**
> Add peertube (VIDEO) and soundcloud (AUDIO) entries reusing the same repository with a different ServiceList
> service. Plus new-video checks for followed channels via the free RSS feed
> `https://www.youtube.com/feeds/videos.xml?channel_id=<UC...>` in the tracker.
Note: YouTube breaks NewPipeExtractor every few weeks. When videos stop playing, bump the version (Step 3's CI tells
you if it builds) and release.

### Step 33: Wattpad (last, uses unofficial endpoints)
On a desktop browser, open wattpad.com → search something → open the developer tools Network tab → find the JSON
requests for search, story details and chapter text. Copy them, with redacted cookies, into the prompt.
> BuiltinSource wattpad (BOOK) using these endpoints: <paste>. Map mature → ADULT. 1 s between requests.

### Step 34: Other official and public sources
- **MangaDex, MANGA Plus, Webtoon, Tapas**: check Explore → Sources whether the redo parsers include them. Enable and
  pin MangaDex and MANGA Plus. No code.
- **"Read officially" buttons** (Kodansha K MANGA for *Sailor Moon*, VIZ for *Naruto*, Lezhin/Tappytoon for BL,
  Libby for library loans):
  > On the details screen add a "Read/Watch officially" section. Use the linked AniList entry's `externalLinks`
  > (GraphQL field on Media) to show buttons that open those sites or apps. Paste `details/ui/DetailsActivity.kt`
  > and the AniList API file from `scrobbling/anilist/`.
- **Podcasts** (BL audio dramas): Podcast Index API (free key; store it like Step 8 secrets). Podcast = manga,
  episodes = chapters (AUDIO). Do it after Step 31.
- **Comic Book Plus / Digital Comic Museum**: public-domain comics, simple HTML scraping, same pattern as Royal Road.
  Optional.

---

## STAGE G: Sources that look after themselves

### Step 35: Local source health
**Paste:** `core/db/entity/MangaSourceEntity.kt`, `core/db/MangaDatabase.kt`, `core/db/migrations/Migration27To28.kt`
(as the example), `search/ui/multi/SearchViewModel.kt`, `remotelist/ui/RemoteListViewModel.kt`
> Add columns `fail_count INTEGER NOT NULL DEFAULT 0` and `last_success_at INTEGER NOT NULL DEFAULT 0` to the sources
> table (DATABASE_VERSION 28 → 29, Migration28To29). After every list/search request: success → fail_count = 0,
> last_success_at = now; failure → fail_count + 1, except CloudFlareProtectedException and no-network errors. A
> source is "unhealthy" when fail_count ≥ 5 and last_success_at is older than 7 days. Unhealthy sources are skipped in
> global search and shown last in Explore with a warning icon.

### Step 36: Automatic install on first run
**Paste:** `sourcerepo/domain/SourceRepoRepository.kt`, `jsext/repo/JsSourceStore.kt`, `jsext/repo/MangayomiIndexParser.kt`,
`sourcerepo/data/SourceRepoStore.kt`, the welcome screen ViewModel
> After the welcome questions, fetch all repo indexes and install, in the background (4 at a time), every JS source
> whose lang is "en" or "all", whose NSFW flag fits the user's choice, and that isn't a site already covered by an
> enabled Kotatsu parser with the same host. Show a small progress card in Explore ("Setting up sources… 37/112").
> Built-in sources are available immediately.

### Step 37: Automatic updates, repair and rollback
> New `sourcerepo/work/SourceUpdateWorker.kt` (WorkManager periodic, 12 h, network required; also enqueue once on app
> start if the last run was over 6 h ago). For each repo, compare versions; download newer sources, verify (Step 10),
> write `<file>.new`, keep the old as `<file>.prev`, rename, and reload that source in JsSourceRegistry. Newly listed
> English sources install automatically. Repair: when a JS source reaches 3 consecutive failures, run the update
> check for that one source immediately and retry once. Rollback: if a just-updated source fails its first 3
> requests and was healthy before, restore `.prev` and pause updates for it for 48 h. Sources removed from their repo
> are hidden after 30 days. Show "N sources updated today" on the Sources screen; no notifications.

### Step 38: One entry per website + auto-move dead titles
> (a) In Explore and global search, group sources by normalized host of their base URL (strip www. and m.). Show one
> entry. Requests go to the healthiest implementation and fail over to the next. (b) A weekly worker: for library
> titles whose source has been unhealthy for 7+ days, find the same title on healthy sources using the existing
> `alternatives/` matcher (paste its domain files), and move it automatically, keeping categories, read chapters
> (matched by number) and stats. Show one summary with Undo. Unclear matches go to a "Needs your choice" list.

### Step 39: Default presets
**Paste:** files in `explore/ui/preset/` and the preset repository
> On first run, create presets: "BL & Yaoi" (MangaDex, BL JS sources from my BL repo, AO3, Scribble Hub), "Popular
> manga" (top 15 English manga sources by sourcescore rank), "Books & fanfic" (AO3, Royal Road, Scribble Hub,
> Gutenberg, Internet Archive Books, Open Library, OPDS sources, JS novel sources), "Anime & video" (JS anime sources,
> YouTube, Internet Archive Video), "Audio" (LibriVox, Internet Archive Audio, SoundCloud).

### Step 40: Nightly health check in your extension repo (no app code)
In `coolchan03/Best-BL-Mangayomi-Extensions`, ask ChatGPT:
> Write a GitHub Action that runs nightly, uses Node to load each JS source (like Monogatari's
> `scripts/audit-js-compatibility.mjs`, which I'll paste), runs search("love") and getDetail on the first result,
> and writes `"status": "ok"` or `"broken"` plus `"checkedAt"` into each entry of index.json, novel_index.json and
> anime_index.json.
Then in the app (one small step): hide sources whose index status is "broken".

---

## STAGE H: Backups and sync

### Step 41: Newest-wins restore
**Paste:** `backups/data/BackupRepository.kt`, the history, favourites and categories DAOs
> When restoring: history rows only overwrite if incoming updated_at > existing; favourites and categories compare
> created_at/deleted_at (a newer deleted_at wins); bookmarks and stats are merged (union). Unit test with an older
> and a newer backup.

### Step 42: Back up sources, their settings and stats extras
**Paste:** `backups/domain/BackupSection.kt`, `backups/data/BackupRepository.kt`, `backups/data/model/BackupIndex.kt`,
`jsext/repo/JsSourceStore.kt`, `core/prefs/SourceSettings.kt`, `sourcerepo/data/SourceRepoStore.kt`
> Add sections: JS_SOURCES (repo url, id, version, preferences), REPOS, OPDS_CATALOGS, SOURCE_SETTINGS (per-parser
> SharedPreferences, excluding cookies and auth keys). After restore, reinstall JS sources automatically (Step 36
> logic). Add `deviceId` (UUID created once) and `deviceName` to BackupIndex. Optional: "Encrypt backups with a
> password" (AES-256-GCM, PBKDF2 key, `.mbak` extension).

### Step 43: Sync through a folder (Google Drive, Syncthing; no server)
**Paste:** `backups/domain/ExternalBackupStorage.kt`, the periodic backup service/settings files
> Setting "Sync my devices through the backup folder". A worker every 6 h and on app start: list backups in the
> folder, take the newest from each *other* deviceId that's newer than the last merged one, restore it with
> newest-wins rules, then write a fresh backup. Keep the 3 newest per device.
**Done when:** 📱 read on your phone, open the tablet, and progress matches after a sync.

### Step 44: WebDAV (optional, better sync)
> Add WebDAV as a backup target (URL, user, app password; HTTPS only): PUT the backup zip to
> `<url>/Monogatari/<deviceId>.zip`, PROPFIND Depth 1 to list. Reuse Step 43's merge logic. Works with Nextcloud and
> Koofr.

### Step 45: Import from Mihon/Tachiyomi, Mangayomi and Kotatsu
> (a) Kotatsu/Kotatsu-Redo backups: confirm they restore (same format). (b) Mihon `.tachibk`: gzip + protobuf. Copy
> the backup model classes from Mihon (Apache-2.0: BackupManga, BackupChapter, BackupCategory, BackupHistory,
> BackupSource), map titles by source name or domain to our sources, and put unmatched titles in a "Needs migration"
> category. (c) Mangayomi JSON backup: same mapping.
Split into three chats.

---

## STAGE I: Reading time and statistics

### Step 46: Fix stats collection
**Paste:** `stats/domain/StatsCollector.kt`, `stats/data/StatsEntity.kt`, `stats/data/StatsDao.kt`, `core/db/MangaDatabase.kt`
> (1) Idle time: add each gap between page events capped at 2 minutes, instead of `now - startedAt`. (2) Change the
> stats foreign key from the history table to the manga table, so clearing history no longer deletes stats
> (migration). (3) Add columns chapter_id, words, media_ms. (4) New table chapter_meta(chapter_id PK, manga_id,
> pages, words, duration_ms, updated_at), filled when a chapter is opened (pages from the image reader, words from
> the novel reader, duration from the player; ask me for those files).

### Step 47: Time-left estimates
**Paste:** `details/domain/ReadingTimeUseCase.kt`, `stats/data/StatsDao.kt`
> New `stats/domain/TimeEstimator.kt`. Speed: manga ms/page for this title → this media type → global → 8 s; novels
> words per minute (start 230); video remaining duration. Weight the last 30 days double. Rest of chapter = remaining
> pages × ms/page (or words ÷ wpm). Rest of title = sum over unread chapters using chapter_meta sizes (average of known
> chapters for unknown ones; AO3 word counts when available). Replace ReadingTimeUseCase's 20-pages × 10-seconds
> guess. Show "~3 min left in chapter" in the reader top bar and "~4 h 10 m to finish" on details.

### Step 48: Stats screen upgrades and backup
> Totals per day/week/month by media type; per-tag share ("37% Boys' Love"); streaks and a daily goal; time-of-day
> heatmap; "backlog: 63 h". Add CHAPTER_META to backups. Optional: a year-in-review "Wrapped" screen in December.

---

## STAGE J: Look and feel (Mangayomi-style)

### Step 49: Navigation like Mangayomi
**Paste:** `core/prefs/AppSettings.kt` (mainNavItems ~line 75), `core/prefs/NavItem.kt`, strings for nav labels
> Default nav for **new** installs: Library (favourites), Updates, History, Browse (explore), More. Add a MORE item
> opening a list: Downloads, Stats, Bookmarks, Sources, Backup & sync, Settings, About. Existing users keep their
> current order.

### Step 50: Library by media type + Books shelf merged in
**Paste:** `favourites/ui/container/FavouritesContainerFragment.kt`, `core/media/BookLibraryFragment.kt`,
`core/media/BookLibraryActivity.kt`, `core/media/BookShelf.kt`
> Add a top switch "Manga · Books · Video · Audio" (hide empty kinds) in the library. Local imported books
> (BookShelf) appear under Books as normal entries. Replace the raw-view BookLibraryActivity/Fragment UI with a
> RecyclerView using the normal list item layouts, move hard-coded English text into strings.xml, and remove the
> duplicate code between the Activity and Fragment.

### Step 51: Generated covers for items without covers
**Paste:** Appendix A6 (complete code), `image/ui/CoverImageView.kt`, `widget/shelf/ShelfListFactory.kt`,
`widget/recent/RecentListFactory.kt`
> Add GeneratedCoverDrawable from Appendix A6. In CoverImageView.setImageAsync(coverUrl, manga) (~line 133) treat a
> blank url as null and set the generated drawable as both fallback and error; skip the error overlay text when it's
> used. In both widget factories (ShelfListFactory line 85, RecentListFactory line 74) use
> `GeneratedCoverDrawable(title, author).toBitmap(300, 450)` instead of ic_placeholder.
Second chat: same for `tracker/work/TrackerNotificationHelper.kt` and `core/os/AppShortcutManager.kt`.

### Step 52: Compact grid and details header
> Add a "Compact" list mode: cover with the title on a bottom dark gradient, an unread-count badge top-left, a
> downloaded badge top-right, 8 dp corners. Details screen: blurred cover behind the header, genre chips, an
> expandable description, and a big "Resume Ch. N" button. Paste `core/prefs/ListMode.kt`,
> `res/layout/item_manga_grid.xml` and the details layout when asked.

### Step 53: Default theme
> New installs: dark theme with pure black (KEY_THEME_AMOLED default true, AppSettings ~line 69) and the Monogatari
> palette as the default colour scheme when dynamic colour is off.

---

## STAGE K: Translation (port from Kototoro)
Kototoro (`github.com/Kototoro-app/Kototoro`, Apache-2.0) has in-reader manga translation: it detects speech bubbles,
reads the text (OCR) and translates it offline or with Gemini, then draws the translation into the bubbles. Code:
`app/src/main/kotlin/org/skepsun/kototoro/reader/translate/` (`domain/` and `data/` folders). Download Kototoro as
a ZIP to get the files. Keep their licence header and add "Ported from Kototoro" (and see Step 66).

### Step 54: Port the translation engine (several chats)
1. Paste the file list of `reader/translate/domain` and `data`, and Kototoro's `gradle/libs.versions.toml`, and ask:
   "Which files and dependencies are needed for: ML Kit OCR + ONNX bubble detection + offline translation +
   rendering, without Gemini, Paddle or Anime4K? Give me a porting plan in 5–8 chats."
2. Follow that plan. Rename the package to `org.koitharu.kotatsu.reader.translate` and adapt to our reader
   (`reader/ui/pager/`).
3. Then add Gemini/other providers as an option (key entered by the user, stored with
   androidx.security EncryptedSharedPreferences, never backed up).

### Step 55: Translation features
> (a) Per-series "Always translate" (stored per manga, backed up). Default to on for sources whose language isn't
> English. (b) Translate the next chapter in the background on Wi-Fi. (c) A glossary per series (names → translation),
> fed to providers. (d) If a cloud provider refuses or returns empty (common with explicit pages), fall back to
> offline for that page.

### Step 56: Translate titles, descriptions, tags and search
> Add ML Kit on-device translation (`com.google.mlkit:translate` + `com.google.mlkit:language-id`). Prefer real English
> titles (altTitles in Latin script, AniList english title) before translating. Cache translations in a Room table
> keyed by sha1(source lang + target lang + text). Never overwrite manga.title; show translated text with a small 🌐
> that toggles the original. In global search, also translate the query into each non-English source's language and
> merge the results.

### Step 57: Translate novel chapters and subtitles
> Novel reader: "Translate chapter" in batches of ~1500 characters with the series glossary, cached per paragraph.
> Video: "Translate subtitles to English" generates a VTT track from the source subtitles.

---

## STAGE L: More from Mangayomi (more extensions working)

### Step 58: Count what's missing
**Paste:** `sourcerepo/ui/JsSourceDiagnostics.kt`, `jsext/JsExtension.kt`
> Add a failure category "missing host function: <name>" whenever an extension calls an unimplemented host function
> (see docs/JS_EXTENSIONS.md "Not implemented yet"). Show counts per function in the diagnostics report.
Run the full sweep and note the top functions.

### Step 59: Implement the missing JS host functions (one per chat, most-needed first)
For each, find the Dart original in `github.com/kodjodevf/mangayomi` (use GitHub search with the function name) and
paste it as the reference:
- `decryptAES*`: CryptoJS-compatible AES ("Salted__" header, key/IV via EVP_BytesToKey with MD5), in a new
  `jsext/JsCryptoBridge.kt`.
- `unpackJs*`: Dean Edwards "p,a,c,k,e,d" unpacker.
- `parseDates`: relative ("3 days ago") and pattern dates.
- `evaluateJavascriptViaWebview`: run the script in a hidden WebView, 30 s timeout.
- XPath attributes: Jsoup `selectXpath`, returning attribute values for `/@attr`.
Each with a unit test using a snippet from a real extension.

### Step 60: More video extractors
> Log the host name in `jsext/JsVideoExtractorBridge.kt` whenever an extractor is missing. After a week of use, port
> the top five from Mangayomi's Dart extractors (paste the Dart file and our Sendvid port from commit ab56a47 as
> the example).

### Step 61: Rewrite favourite Dart-only sources as JS
> Paste a Dart source from mangayomi-extensions and a working JS source as the example: "Rewrite this as a Mangayomi
> JS source with the same features (MProvider contract)." Publish it in Best-BL-Mangayomi-Extensions. It works in
> Mangayomi too.

---

## STAGE M: Extras (pick what you like)
Each is one or two chats. Paste the files ChatGPT asks for.
- **Step 62, Discover tab (AniList):** trending manga and anime by tag ("Boys' Love"), "because you read X"
  recommendations; tapping runs "Find a copy" (global search for that title).
- **Step 63, Follow a tag:** a worker that checks followed tags (AO3 M/M + a fandom, Scribble Hub Yaoi, MangaDex BL)
  for new titles and posts "New in your tags".
- **Step 64, News feed:** RSS reader in the Feed tab (Anime News Network `https://www.animenewsnetwork.com/all/rss.xml`,
  publisher news, YouTube channel RSS, subreddit `.rss`), filterable by your followed tags.
- **Step 65, Private categories:** a category can be private, hidden until unlocked with biometrics, excluded from
  widgets, Discord status, cloud translation and screenshots.
- **Step 66, Licence notices:** add `NOTICE` crediting Kotatsu/Kotatsu-Redo (GPL-3.0), Mangayomi and Kototoro
  (Apache-2.0), NewPipe (GPL-3.0), Mihon (Apache-2.0, if used), and an "Open-source licences" screen under About.
- **Step 67, Novel text-to-speech:** Android TextToSpeech in the novel reader, paragraph by paragraph, highlighting
  the current one, with a sleep timer and background play.
- **Step 68, Video player extras:** picture-in-picture, "skip intro +85 s", per-show playback speed, resume
  position, auto-next episode.
- **Step 69, Release calendar:** estimate each ongoing title's update rhythm from chapter dates and show "Expected
  this week".
- **Step 70, Cross-media links:** from the AniList relations of a title, show "Watch the anime" / "Read the novel" /
  "Sequel" buttons that search your sources.
- **Step 71, Crash report button:** the crash dialog's "Report" opens a prefilled GitHub issue
  (`https://github.com/coolchan03/Monogatari/issues/new?title=…&body=…`) with stack trace, app version and Android
  version (truncate to 6000 characters, and strip titles of NSFW sources).
- **Step 72, Exports:** CSV of library and stats; Goodreads/StoryGraph CSV for books.
- **Step 73, Optional extra source pools:** Mihon extensions (copy Kototoro's `eu/kanade/tachiyomi/` + `mihon/`
  folders; needs keep rules `-keep class eu.kanade.tachiyomi.** { *; }` etc.), Aniyomi or CloudStream for more video,
  LNReader plugins for more novels. Only if Stage F + G still leave gaps.

---

## STAGE N: Before every release
### Step 74: Release checklist (no ChatGPT)
1. Build check green, including the release (R8) build from Step 3.
2. Bump `versionCode` (+1) and `versionName` in `app/build.gradle`. Add notes to `CHANGELOG.md`.
3. 📱 Smoke test on a **release** APK:
   - Fresh install → welcome → sources set up → search "Given" → results from several sources.
   - Read 3 manga chapters → close → reopen → resumes; stats recorded.
   - AO3 chapter, Gutenberg book, Internet Archive video, one JS anime episode, one LibriVox chapter.
   - Backup → uninstall → install → restore → library, progress, sources, settings back.
   - Two devices with folder sync → progress matches.
   - Airplane mode → downloads open.
   - Rotate during search, reading and video.
4. Run the "publish release" workflow. Obtainium on your phone picks it up.

---

## APPENDIX: Code and config referenced by the steps

### A1. R8 / ProGuard keep rules (Step 3)
```proguard
# QuickJS engine: native code calls these classes by name
-keep class com.dokar.quickjs.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
# JS host bridges are invoked from JavaScript by function name
-keep class org.koitharu.kotatsu.jsext.** { *; }
# kotlinx.serialization models and companions
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class org.koitharu.kotatsu.jsext.** { *** Companion; }
-keepclassmembers class **$$serializer { *; }
```

### A2. Tag synonyms (Step 16)
```kotlin
package org.koitharu.kotatsu.search.domain

object TagSynonyms {
    private val groups: List<Set<String>> = listOf(
        setOf("yaoi", "boys' love", "boys love", "boy's love", "bl", "shounen ai", "shonen ai", "shounen-ai", "shonen-ai", "danmei", "m/m", "gay"),
        setOf("yuri", "girls' love", "girls love", "gl", "shoujo ai", "shojo ai", "shoujo-ai", "f/f"),
        setOf("smut", "explicit", "mature"),
        setOf("isekai", "another world", "reincarnation"),
        setOf("magical girl", "magical girls", "mahou shoujo", "mahou shojo"),
        setOf("slice of life", "slice-of-life", "sol"),
        setOf("omegaverse", "abo", "alpha/beta/omega"),
    )
    fun expand(query: String): Set<String> {
        val q = query.trim().lowercase()
        return groups.firstOrNull { q in it } ?: setOf(q)
    }
}
```

### A3. ContinuousMediaProvider (Step 19)
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

### A4. BuiltinSource (Step 21; later steps add entries)
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

	data object Gutenberg : BuiltinSource("gutenberg", "Project Gutenberg", MediaType.BOOK, "en", "https://www.gutenberg.org")
	// Added by later steps, for example:
	// data object Ao3 : BuiltinSource("ao3", "Archive of Our Own", MediaType.BOOK, "en", "https://archiveofourown.org")

	companion object {
		const val PREFIX = "builtin:"

		val all: List<BuiltinSource> by lazy {
			listOf(Gutenberg)
		}

		fun fromName(name: String): BuiltinSource? =
			name.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.let { k -> all.find { it.key == k } }
	}
}
```
**Never change a `key` after release.** It's part of every saved title's ID.

### A5. PoliteHostInterceptor (Step 22)
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

### A6. GeneratedCoverDrawable (Step 51)
```kotlin
package org.koitharu.kotatsu.image.ui.generated

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A procedurally drawn book-style cover for items that have no thumbnail.
 * The style and palette are chosen deterministically from the title, so a given title always looks the same.
 */
class GeneratedCoverDrawable(
	title: String,
	private val subtitle: String? = null,
	private val badge: String? = null,
) : Drawable() {

	private val title: String = title.trim().ifEmpty { "?" }
	private val seed: Int = seedOf(this.title)
	private val coverStyle: Style = Style.entries[seed % Style.entries.size]
	private val palette: Palette = PALETTES[(seed / Style.entries.size) % PALETTES.size]
	private val isLight = ColorUtils.calculateLuminance(palette.bg) > 0.5
	private val textColor = if (isLight) 0xFF1A1A1A.toInt() else 0xFFFAFAFA.toInt()

	private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
	private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
		strokeJoin = Paint.Join.ROUND
	}
	private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = textColor
		typeface = Typeface.create(if (seed % 2 == 0) Typeface.SERIF else Typeface.SANS_SERIF, Typeface.BOLD)
	}
	private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
		color = ColorUtils.setAlphaComponent(textColor, 210)
		typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
	}
	private val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
	private val path = Path()

	private var titleLayout: StaticLayout? = null
	private var subtitleLayout: StaticLayout? = null
	private var drawAlpha = 255

	override fun onBoundsChange(bounds: Rect) {
		super.onBoundsChange(bounds)
		val w = bounds.width()
		val h = bounds.height()
		if (w <= 0 || h <= 0) {
			titleLayout = null
			subtitleLayout = null
			return
		}
		titleLayout = buildTitleLayout(w, h)
		subtitleLayout = subtitle?.takeIf { it.isNotBlank() }?.let {
			subtitlePaint.textSize = w * 0.055f
			staticLayout(it.trim(), subtitlePaint, (w * 0.8f).toInt(), maxLines = 2)
		}
		badgePaint.textSize = w * 0.09f
	}

	override fun draw(canvas: Canvas) {
		val b = bounds
		if (b.isEmpty) return
		val w = b.width().toFloat()
		val h = b.height().toFloat()
		val saveCount = if (drawAlpha < 255) {
			canvas.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), drawAlpha)
		} else {
			canvas.save()
		}
		canvas.translate(b.left.toFloat(), b.top.toFloat())
		canvas.clipRect(0f, 0f, w, h)
		canvas.drawColor(palette.bg)
		drawPattern(canvas, w, h)
		drawText(canvas, w, h)
		canvas.restoreToCount(saveCount)
	}

	private fun drawPattern(canvas: Canvas, w: Float, h: Float) {
		val accent = palette.accent
		when (coverStyle) {
			Style.SPINE -> {
				fillPaint.color = accent
				canvas.drawRect(0f, 0f, w * 0.08f, h, fillPaint)
				strokePaint.color = accent
				strokePaint.strokeWidth = w * 0.008f
				canvas.drawLine(w * 0.15f, h * 0.08f, w * 0.92f, h * 0.08f, strokePaint)
				canvas.drawLine(w * 0.15f, h * 0.92f, w * 0.92f, h * 0.92f, strokePaint)
			}

			Style.STRIPES -> {
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 70)
				val diag = sqrt(w * w + h * h)
				canvas.save()
				canvas.rotate(-35f, w / 2f, h / 2f)
				var x = w / 2f - diag
				while (x < w / 2f + diag) {
					canvas.drawRect(x, h / 2f - diag, x + w * 0.05f, h / 2f + diag, fillPaint)
					x += w * 0.13f
				}
				canvas.restore()
			}

			Style.DOTS -> {
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 90)
				val step = w * 0.14f
				val r = w * 0.025f
				var row = 0
				var y = step / 2f
				while (y < h + step) {
					var x = if (row % 2 == 0) step / 2f else step
					while (x < w + step) {
						canvas.drawCircle(x, y, r, fillPaint)
						x += step
					}
					y += step * 0.87f
					row++
				}
			}

			Style.SPLIT -> {
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 200)
				path.reset()
				path.moveTo(0f, h)
				path.lineTo(w, h * 0.38f)
				path.lineTo(w, h)
				path.close()
				canvas.drawPath(path, fillPaint)
			}

			Style.RINGS -> {
				strokePaint.color = ColorUtils.setAlphaComponent(accent, 100)
				strokePaint.strokeWidth = w * 0.03f
				var r = w * 0.12f
				while (r < max(w, h) * 1.5f) {
					canvas.drawCircle(w, 0f, r, strokePaint)
					r += w * 0.12f
				}
			}

			Style.MOON -> {
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 140)
				canvas.drawCircle(w * 0.74f, h * 0.2f, w * 0.3f, fillPaint)
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 70)
				canvas.drawCircle(w * 0.2f, h * 0.86f, w * 0.14f, fillPaint)
			}

			Style.CHECKER -> {
				fillPaint.color = ColorUtils.setAlphaComponent(accent, 45)
				val size = w / 6f
				var row = 0
				var y = 0f
				while (y < h) {
					var col = 0
					var x = 0f
					while (x < w) {
						if ((row + col) % 2 == 0) canvas.drawRect(x, y, x + size, y + size, fillPaint)
						x += size
						col++
					}
					y += size
					row++
				}
			}

			Style.WAVES -> {
				strokePaint.color = ColorUtils.setAlphaComponent(accent, 120)
				strokePaint.strokeWidth = w * 0.02f
				for (i in 0 until 6) {
					val baseY = h * (0.62f + i * 0.07f)
					path.reset()
					var x = 0f
					path.moveTo(0f, baseY)
					while (x <= w) {
						path.lineTo(x, baseY + sin((x / w) * 2f * PI.toFloat() * 1.5f + i) * h * 0.02f)
						x += w / 40f
					}
					canvas.drawPath(path, strokePaint)
				}
			}

			Style.FRAME -> {
				strokePaint.color = accent
				strokePaint.strokeWidth = w * 0.012f
				canvas.drawRect(w * 0.05f, w * 0.05f, w * 0.95f, h - w * 0.05f, strokePaint)
				strokePaint.strokeWidth = w * 0.005f
				canvas.drawRect(w * 0.08f, w * 0.08f, w * 0.92f, h - w * 0.08f, strokePaint)
				fillPaint.color = accent
				val d = w * 0.03f
				for ((cx, cy) in listOf(w * 0.05f to w * 0.05f, w * 0.95f to w * 0.05f, w * 0.05f to h - w * 0.05f, w * 0.95f to h - w * 0.05f)) {
					path.reset()
					path.moveTo(cx, cy - d)
					path.lineTo(cx + d, cy)
					path.lineTo(cx, cy + d)
					path.lineTo(cx - d, cy)
					path.close()
					canvas.drawPath(path, fillPaint)
				}
			}

			Style.CHEVRON -> {
				strokePaint.color = ColorUtils.setAlphaComponent(accent, 90)
				strokePaint.strokeWidth = w * 0.025f
				val stepX = w / 6f
				var y = 0f
				while (y < h + h * 0.08f) {
					path.reset()
					path.moveTo(-stepX, y)
					var x = -stepX
					var up = true
					while (x <= w + stepX) {
						x += stepX / 2f
						path.lineTo(x, if (up) y - h * 0.03f else y)
						up = !up
					}
					canvas.drawPath(path, strokePaint)
					y += h * 0.08f
				}
			}
		}
	}

	private fun drawText(canvas: Canvas, w: Float, h: Float) {
		val tl = titleLayout ?: return
		val sl = subtitleLayout
		val gap = if (sl != null) w * 0.04f else 0f
		val blockHeight = tl.height + gap + (sl?.height ?: 0)
		val top = (h - blockHeight) / 2f - h * 0.04f
		val pad = w * 0.05f
		// Semi-transparent band behind the text keeps it readable over any pattern
		fillPaint.color = ColorUtils.setAlphaComponent(palette.bg, 215)
		canvas.drawRect(0f, top - pad, w, top + blockHeight + pad, fillPaint)
		canvas.save()
		canvas.translate((w - tl.width) / 2f, top)
		tl.draw(canvas)
		canvas.restore()
		if (sl != null) {
			canvas.save()
			canvas.translate((w - sl.width) / 2f, top + tl.height + gap)
			sl.draw(canvas)
			canvas.restore()
		}
		if (!badge.isNullOrEmpty()) {
			canvas.drawText(badge, w * 0.06f, w * 0.06f - badgePaint.ascent(), badgePaint)
		}
	}

	private fun buildTitleLayout(w: Int, h: Int): StaticLayout {
		val maxWidth = (w * 0.8f).toInt().coerceAtLeast(1)
		val minSize = w * 0.06f
		var size = w * 0.13f
		while (true) {
			titlePaint.textSize = size
			val layout = staticLayout(title, titlePaint, maxWidth, maxLines = 6)
			if (layout.height <= h * 0.5f || size <= minSize) return layout
			size *= 0.9f
		}
	}

	private fun staticLayout(text: CharSequence, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
		StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setMaxLines(maxLines)
			.setEllipsize(TextUtils.TruncateAt.END)
			.setLineSpacing(0f, 1.05f)
			.setIncludePad(false)
			.build()

	override fun setAlpha(alpha: Int) {
		drawAlpha = alpha
		invalidateSelf()
	}

	override fun setColorFilter(colorFilter: ColorFilter?) {
		fillPaint.colorFilter = colorFilter
		strokePaint.colorFilter = colorFilter
		titlePaint.colorFilter = colorFilter
		subtitlePaint.colorFilter = colorFilter
		invalidateSelf()
	}

	@Deprecated("Deprecated in Java")
	override fun getOpacity(): Int = if (drawAlpha == 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

	private enum class Style { SPINE, STRIPES, DOTS, SPLIT, RINGS, MOON, CHECKER, WAVES, FRAME, CHEVRON }

	private class Palette(val bg: Int, val accent: Int)

	companion object {

		private val PALETTES = listOf(
			Palette(Color.parseColor("#1E2A4A"), Color.parseColor("#E8C468")), // midnight / gold
			Palette(Color.parseColor("#6B1E2E"), Color.parseColor("#F2B5C4")), // burgundy / blush
			Palette(Color.parseColor("#1F4D3A"), Color.parseColor("#A8E6CF")), // forest / mint
			Palette(Color.parseColor("#4A2545"), Color.parseColor("#D6A2E8")), // plum / lilac
			Palette(Color.parseColor("#0F4C5C"), Color.parseColor("#FFB997")), // teal / peach
			Palette(Color.parseColor("#2B2B2B"), Color.parseColor("#FF6F61")), // charcoal / coral
			Palette(Color.parseColor("#F7C6D0"), Color.parseColor("#B0305A")), // sakura / rose
			Palette(Color.parseColor("#EAD7B7"), Color.parseColor("#7A4E2D")), // sand / brown
			Palette(Color.parseColor("#BFE3F2"), Color.parseColor("#23476B")), // sky / navy
			Palette(Color.parseColor("#C9D8B6"), Color.parseColor("#4E6B3A")), // sage / olive
			Palette(Color.parseColor("#DCD3F2"), Color.parseColor("#4B3B8F")), // lavender / indigo
			Palette(Color.parseColor("#E3B23C"), Color.parseColor("#2D2A32")), // mustard / ink
		)

		/** Same normalised title -> same cover, regardless of source or device. */
		fun seedOf(title: String): Int {
			val normalized = title.lowercase().filter { it.isLetterOrDigit() }
			return normalized.hashCode() and 0x7FFFFFFF
		}
	}
}
```
Notes: don't change `seedOf` after release (every generated cover would change). The badge is optional ("📖", "🎧", "🎬").
