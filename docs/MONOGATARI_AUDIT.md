# Monogatari: full audit and build plan (v4)

Audit date: 2026-10-12. **Your app's code is on branch `claude/mangayomi-kotatsu-features-fb46y2` (commit `cb5e722`). Part 0 maps every task to it.** Parts A–N were first written against `devel` (plain Kotatsu, `34f6e52`); their paths still apply, but line numbers differ on your branch.

This is written to be handed to another AI (e.g. ChatGPT) **one task at a time**. Each task says which files to
change or which files to copy from another project, what to do, and how to check it.

Paths are relative to `app/src/main/kotlin/org/koitharu/kotatsu/` unless they start with `app/`, `gradle/` or `.github/`.

**Companion:** `docs/MONOGATARI_SOURCES_PLAN.md` is the focused action plan for sources (AO3, Open Library, Gutenberg, Internet Archive, YouTube, every other source rated) and the current-issue fixes they need. Start there.

### Contents
- **Part 0**: Where your branch stands, how not to lose work, borrowing from Kototoro/Mangayomi, task status table
- **A**: Fork hygiene: parser library, app identity, dead and unsafe servers
- **B**: Sources: mirrors, Mihon extensions, domain overrides, health, built-in legal sources, YouTube/NewPipe
- **C**: Novels, video, audio (content kinds and readers)
- **D**: Search fixes, BL tag synonyms, hidden tags, content levels
- **E**: Reading time, statistics and "time left" estimates
- **F**: Backup, restore, cross-device sync
- **G**: Mangayomi look
- **H**: Other code bugs
- **I**: Automatic builds and releases
- **J**: Extra features
- **K**: Second sweep (release-build crash, SSL bypass, developer verification, imports, privacy, more)
- **L**: Generated covers for items without thumbnails
- **M**: Self-managing sources (pre-installed, auto-updating, auto-repairing; no extension screens)
- **N**: AI translation (manga pages, titles, descriptions, search, novels, subtitles)
- **O**: What else to take from Mangayomi
- **Suggested order** (for your branch)

### How to work with ChatGPT on this
1. Give it this whole document once so it has context. Then ask it to do **one task ID** (e.g. "Do task A2").
2. Paste the **full current contents** of every file the task names. ChatGPT can't see your repo.
   For "copy from Kototoro" tasks, download Kototoro as a ZIP from GitHub (green "Code" button → "Download ZIP")
   and paste the named files.
3. Ask for **complete replacement files**, not snippets.
4. Build after every task: `./gradlew assembleDebug`. If the build fails, paste the error back. Don't start the next
   task until it's green. Task I1 sets up GitHub to build automatically, so you can do it from a phone.

### What I could and couldn't verify
- Everything about **this repo** was read directly from the code, and line numbers are accurate for commit `34f6e52`.
- I could **not compile** it: my sandbox blocks JitPack, where the parser library is downloaded from.
- Facts about **other projects** (Kototoro, kotatsu-parsers-redo, NewPipe, LNReader) come from their public GitHub
  pages as of today. I could see their folder names but not every file's contents, so treat "copy folder X" as
  "start from folder X".

---

## Part 0: Where things actually stand (read this first)

### Correction
The first versions of this document audited the `devel` branch, which is plain Kotatsu. **Your real work is on
branch `claude/mangayomi-kotatsu-features-fb46y2`** (open PR #1, 79 commits, ~600 files, latest build check green on
`cb5e722`). Earlier I recommended forking Kototoro. **I'm withdrawing that.** Switching bases would throw away work
that already does much of what you want. **Keep your branch as the base and borrow from Kototoro and Mangayomi.**

### What your branch already has (keep all of it)
- **Base:** Kotatsu-Redo (commit `8ae9feb`), with the maintained parser library `com.github.clquwu:kotatsu-parsers-redo`
  pinned at `b3c0000245`, Java 17 and target SDK 37. **Task A1 is done.**
- **Mangayomi extensions running unmodified** (`jsext/`): QuickJS engine (`quickjs-kt`), Mangayomi's own JS runtime
  files copied into `src/main/resources/jsext/*.js`, and Kotlin bridges for HTTP (`JsHttpBridge`, `JsOkHttpTransport`),
  HTML (`JsDomBridge`, Jsoup), EPUB (`JsEpubBridge`), utilities and video extractors (`JsVideoExtractorBridge`, with a
  Sendvid HLS extractor ported).
- **Source repositories** (`sourcerepo/`): add, list and install JS sources; per-source settings; sha256 checking when
  the index provides it; health sweeps and diagnostics; Dart→JS/Kotatsu alternative matching; first-run health checks.
  Plus your curated repo `coolchan03/Best-BL-Mangayomi-Extensions`.
- **Novels and books:** JS novel sources in a text reader; local EPUB, PDF, TXT and HTML readers with resume; OPDS
  catalogs; public book catalogs; a Books tab/shelf (`core/media/`).
- **Video:** JS anime sources played in Media3 with headers and subtitles.
- **Media types:** `core/media/MediaType.kt` (`MANGA`, `BOOK`, `VIDEO`) with filters in Explore, search, history and
  favourites. This covers most of task C0.
- **Search:** year/date filter, media-type filter, tolerant genre matching (`normalizedTag()` in `SearchV2Helper`), and
  the hide-empty checkbox state restored.
- **Source health and ranking** from Kotatsu-Redo (`sourcescore/`): community source scores, trending sources,
  probe recording. This covers much of B4.
- **Branding:** Monogatari name, storybook identity, splash, palette, redesigned library and source directory, and
  configurable navigation.
- **CI:** `build-check.yml` (unit tests plus a debug APK on `claude/**` pushes), a guarded `publish-release.yml`, and
  Discord webhooks.
- **Your own earlier audit:** `docs/MONOGATARI_AUDIT_2026-10-11.md`. Its P0 items are still valid and are folded in
  below.

### How to avoid losing anything (do this now)
1. **Merge PR #1 into your default branch.** Today `devel` is plain Kotatsu, and your real app only lives on a
   `claude/...` branch. Until it's merged:
   - workflows like `publish-release.yml` can't be triggered manually (GitHub only runs `workflow_dispatch` from the
     default branch);
   - new sessions, Claude or ChatGPT, start from the wrong code. That's exactly what happened with this audit.
   Easiest: merge PR #1 into `devel`. Or create `main` from the branch and set it as the default in
   GitHub → Settings → General.
2. **Tag a known-good point**, e.g. `git tag v0.9-before-audit cb5e722 && git push origin v0.9-before-audit`. You can
   always come back to it.
3. From then on, **one branch per task** (e.g. `feat/auto-sources`), merged when its build is green. If a task goes
   wrong, delete the branch and nothing else is affected.
4. Keep the old Kotatsu `devel` history as an archive branch (`git branch archive/kotatsu-original origin/devel`)
   before merging, if you want it.

### Borrowing from Kototoro (instead of switching to it)
Kototoro is Apache-2.0, so you can copy pieces into your GPL-3.0 app with credit (K13). Worth taking:
- **Manga page translation:** `org/skepsun/kototoro/reader/translate/` (see Part N).
- **Mihon extension loading:** `eu/kanade/tachiyomi/` plus `org/skepsun/kototoro/mihon/`. It's an optional extra
  source pool on top of your Mangayomi JS sources (B2).
- **WebDAV sync:** `org/skepsun/kototoro/sync/` (F4).
Kotatsu, Kotatsu-Redo and Kototoro share the same ancestry, so their code fits yours with mostly package and import
changes.

### Copying from Mangayomi
Mangayomi is **Flutter/Dart (plus some Rust), Apache-2.0**. Dart code can't be pasted into Kotlin. It has to be
**translated** by hand or by ChatGPT, function by function. Your branch already reuses Mangayomi in the best possible
way:
- **Its extensions run unmodified.** That is the biggest win, and it's done.
- **Its JS runtime files** are copied verbatim (with attribution).
- **Ported logic:** the EPUB parser contract, the Sendvid extractor and repository safety updates.

What's still worth translating from Mangayomi into Kotlin is listed in **Part O**: the missing JS host functions
(this makes more extensions work), more video extractors, Dart-only sources rewritten as JS, backup import, and UI
patterns. **Dart extensions themselves can never run** (they need Mangayomi's Dart interpreter). That's why your
branch maps them to JS or Kotatsu alternatives, which is the right call.

### How to read Parts A–N now
Those parts were written against plain Kotatsu. The table below says, for each task, whether your branch has
**done** it, done it **partly**, it's still **open**, or it's **obsolete**. Your branch keeps the same Kotlin package
(`org.koitharu.kotatsu`), so the file paths in Parts A–N still apply; only line numbers have moved.

### Task status on `claude/mangayomi-kotatsu-features-fb46y2` (`cb5e722`)
| Task | Status | Notes for your branch |
|---|---|---|
| A1 parser library | **Done** | Redo parsers b3c0000245, Java 17 |
| A2 app identity | **Open (P0)** | `applicationId 'io.github.kotatsuredo.kotatsu'`, `versionName 9.8.5`, sync authorities `io.github.kotatsuredo.kotatsu.*`. That clashes with the real Kotatsu-Redo app. Change it **before** anyone relies on the app (your own audit says the same). `github_updates_repo` is `coolchan03/Manga-Reader`; check that this is the repo you'll publish releases to |
| A3 dead or unsafe servers | **Partly** | Sync servers are now HTTPS (Redo's). Still Kotatsu: `url_weblate`, `url_user_manual`, `discord_app_id`, `app_icon_url`, `account_type_sync org.kotatsu.sync`. App-lock PIN is still unsalted MD5 (`ProtectSetupViewModel.kt` line 42) |
| B1 mirror switching on | **Open** | `AppSettings.kt` line 325, default `false` |
| B2 Mihon extensions | **Open, optional** | You use Mangayomi JS sources instead. Add Mihon later for more manga sources |
| B3 domain overrides | **Open** | Also useful for JS sources (override `baseUrl`) |
| B4 source health | **Partly** | `sourcescore/` ranking plus health sweeps exist. Auto-hiding and auto-repair don't (see M4) |
| B5 built-in legal sources | **Partly** | Public book catalogs and OPDS exist. Check which of Gutenberg, Internet Archive, LibriVox, AO3 and Wattpad are covered |
| B6 LNReader plugins | **Open, low priority** | Mangayomi novel sources already give you novels |
| B7 YouTube/NewPipe | **Open** | Nothing yet. Your Media3 player is ready for it |
| C0 content kinds | **Mostly done** | `MediaType` MANGA/BOOK/VIDEO. Add AUDIO if you want LibriVox |
| C1 novel reader | **Partly** | Text, EPUB, PDF and HTML readers exist. Add TTS, reader settings and word counts |
| C2 reader fixes | **Check** | Inherited Kotatsu code. Re-check `ReaderViewModel` `// TODO` progress drop |
| C3 video player | **Partly** | Media3 playback exists. Add background audio, PiP, skip intro and resume position |
| C4 library by kind | **Partly** | Filters exist. Books shelf is separate from the main library (your audit flagged this) |
| D1 search bugs | **Partly** | Hide-empty restored. "Pinned only" checkbox still not restored. `retry()` still clears the list before the old search stops (`SearchViewModel.kt` ~line 171) |
| D2 BL synonyms | **Open** | `normalizedTag()` ignores punctuation, but "yaoi" still doesn't match "Boys' Love". Add `TagSynonyms` |
| D3 hidden tags, D4 content levels, D5 merge, D6 sort | **Open** | |
| E stats and estimates | **Open** | All five problems still present (stats off by default at line 638, CASCADE delete, idle time, 20-page guess) |
| F1 newest-wins restore | **Open** | `BackupRepository.kt` ~line 183 |
| F2 backup gaps | **Open** | Also back up installed JS sources, their preferences and repo list |
| F3/F4/F5 sync | **Open** | |
| G Mangayomi look | **Partly** | Branding is done. Bottom-nav default still Kotatsu's (`AppSettings.kt` line 75). Books UI is raw views (your audit) |
| H code issues | **Check** | Inherited. Re-check H1–H5 |
| I1 build workflow | **Done** | `build-check.yml` (debug only) |
| I2 signed releases | **Partly** | `publish-release.yml` exists but can't run until merged to the default branch, and keystore secrets aren't set |
| K1 R8 rules | **Open (P0)** | **No release (R8) build has ever been produced.** CI builds debug only. `proguard-rules.pro` has no rules for `quickjs-kt` (it calls into Kotlin from native code through JNI) or for the bridges that JS calls by name. Add `-keep class com.dokar.quickjs.** { *; }` and `-keep class org.koitharu.kotatsu.jsext.** { *; }`, and add an `assembleRelease` step to CI |
| K2 SSL bypass | **Open** | `NetworkModule.kt` line 76, same as Kotatsu |
| K3 developer verification | **Open** | Do after A2 and I2 |
| K4 brand strings | **Partly** | Main strings done ("Finish user-facing Monogatari brand strings"). Check `values-*` translations |
| K5–K12 | **Open** | K7: `isDiscordRpcSkipNsfw` default false (line 599), DoH none (line 512) |
| K14 smoke test | **Open** | Your branch's new "guided feature QA" screen (`cb5e722`) is a good start. Fold K14 into it |
| L generated covers | **Open** | Books without covers especially need it |
| M self-managing sources | **Open** | Easier with JS sources than APKs, see the note at the start of Part M |
| N translation | **Open** | Nothing yet. Port from Kototoro |
| **New: Q1 public repo list** | **Open** | `sourcerepo/data/SourceRepoStore.kt` line 38 hard-codes 9 repo URLs in public code (M1 explains the risk). Move them to the private build config |
| **New: Q2 JS sources and cookies** | **Open** | JS sources use the shared scraping client and cookie jar, so any JS source can send requests carrying cookies for any site you've logged into. Limit each JS source's cookie access to its own domains (a per-source `CookieJar` wrapper in `JsOkHttpTransport`) |
| **New: Q3 sha256 optional** | **Open** | `SourceRepoRepository.kt` line 147: if the index has no `sha256`, the download is accepted unverified. Your own repo should always publish hashes; for others, pin the hash on first install (trust on first use) and warn if it changes without a version bump |
| **New: Q4 community server** | **Note** | `sourcescore/` talks to `community.kotatsuredo.app` (Kotatsu-Redo's server). Score downloads run for everyone; the account features are opt-in, and joining sends `ANDROID_ID` plus the Widevine device ID. Fine if you trust them, but your app depends on their server. Say so in the onboarding text, and handle the server being down gracefully |
| **New: Q5 temporary diagnostics UI** | **Open** | Your audit says to move JS diagnostics out of the visible production UI. Put them under Settings → Advanced |


---

## Part A: Fork hygiene (do first)

### A1. Switch to a parser library that is still maintained
**Problem.** `app/build.gradle` pulls `com.github.KotatsuApp:kotatsu-parsers` at commit `4d1e521aef`
(`gradle/libs.versions.toml` → `parsers`). That repository was **archived on 4 Nov 2025**, so no source fixes will
ever come again. If JitPack drops the old build, your app stops compiling.

**Fix.** Switch to **`Kotatsu-Redo/kotatsu-parsers-redo`**. It's the most active continuation (last commits Sep 2026,
and Kotatsu-Redo, Futon and Kototoro all use it). It keeps the same Kotlin package (`org.koitharu.kotatsu.parsers`),
so it is nearly a drop-in replacement.
1. Fork `Kotatsu-Redo/kotatsu-parsers-redo` to your GitHub account (so it can't vanish on you).
2. `app/build.gradle`, in `dependencies { }`: replace
   `implementation("com.github.KotatsuApp:kotatsu-parsers:$parsersVersion")` with
   `implementation("com.github.coolchan03:kotatsu-parsers-redo:$parsersVersion")`.
3. `gradle/libs.versions.toml`: set `parsers = "<first 10 chars of the newest commit hash in your fork>"`.
4. The redo library is built for **Java 17** (`jvmToolchain(17)` in its `build.gradle.kts`), while this app targets
   Java 11. In `app/build.gradle` change both `JavaVersion.VERSION_11` to `JavaVersion.VERSION_17` and
   `jvmTarget = JavaVersion.VERSION_11.toString()` to `VERSION_17`. Otherwise you'll get "Cannot inline bytecode built
   with JVM target 17" errors.
5. Build. Any compile errors will be small API renames. Paste them to ChatGPT along with the failing file.
6. **Keep it fresh:** once a month, press "Sync fork" on GitHub and bump the hash. Task I1 can automate this.

Note: the redo library has essentially **one maintainer**. That's why task B2 (Mihon extensions, with dozens of
maintainers) is the real answer to "as many working sources as possible".

### A2. Give the app its own identity
**Problem.** The app id and sync provider names are the same as real Kotatsu, so Android refuses to install both.
**Do not** rename the Kotlin package. That would touch 1,800 files for no benefit. Change only:
- `app/build.gradle`: `applicationId 'io.github.coolchan03.monogatari'`, `versionCode 1`, `versionName '0.1.0'`.
- `settings.gradle`: `rootProject.name = "Monogatari"`.
- `app/src/main/res/values/constants.xml`:
  - `account_type_sync` → `io.github.coolchan03.monogatari.sync`
  - `sync_authority_history` → `io.github.coolchan03.monogatari.history`
  - `sync_authority_favourites` → `io.github.coolchan03.monogatari.favourites`
  - `url_github` → `https://github.com/coolchan03/monogatari`
  - `github_updates_repo` → `coolchan03/monogatari`
  - `app_icon_url` → an icon URL in your repo
- `app/src/debug/res/values/constants.xml` and `app/src/nightly/res/values/constants.xml`: same three keys plus
  `.debug` / `.nightly`.
- `app_name` in `app/src/main/res/values/strings.xml`, `app/src/debug/res/values/strings.xml`,
  `app/src/nightly/res/values/strings.xml` → `Monogatari`.
- Launcher icons: `app/src/main/res/mipmap-*` (use Android Studio → New → Image Asset).
- `core/github/AppValidator.kt` (`CERT_SHA256`): replace with your signing key's SHA-256, from
  `keytool -list -v -keystore your.jks`.
- `README.md`, `metadata/`: rewrite for Monogatari. Delete `.github/workflows/trigger-site-deploy.yml` (it pings
  Kotatsu's website repo) and `issue_moderator.yml`.

### A3. Remove dead or unsafe Kotatsu infrastructure

| What | Where | Problem | Fix |
|---|---|---|---|
| Crash reports | `core/BaseApp.kt` (`initAcra { httpSender {…} }`), `constants.xml` `url_error_report`, `acra_login`, `acra_password` | Sends crash logs (which contain manga titles and URLs) to Kotatsu's dead server | Delete the `httpSender` block and the credentials. Keep the ACRA dialog, which lets users share the report themselves |
| Sync server list | `constants.xml` `sync_url_list` | `http://54.254.71.100` and `http://86.57.183.214:8081` are **unencrypted HTTP to unknown third-party IPs**. Your email, password and entire reading history would be sent in plain text. `sync.kotatsu.app` is dead | Delete all four entries (see Part F) |
| HTTP fallback | `sync/data/SyncSettings.kt` line 46 `"http://$this"` | A host typed without `https://` silently becomes HTTP | Change to `"https://$this"` |
| Sync auth client | `sync/domain/SyncHelper.kt` line 65 `SyncAuthApi(OkHttpClient())` | Bare client that ignores the app's proxy, DNS-over-HTTPS and SSL settings | Use `baseHttpClient` |
| OAuth keys | `constants.xml` `anilist_*`, `mal_clientId`, `kitsu_*`, `shikimori_*` | Kotatsu's registered apps, revocable, with `kotatsu://` redirect URIs | Register your own (AniList: anilist.co/settings/developer, MAL: myanimelist.net/apiconfig). Put the keys in `local.properties` and inject them with `resValue` exactly like `tg_backup_bot_token` in `app/build.gradle` |
| Discord app id | `constants.xml` `discord_app_id` | Kotatsu's Discord app | Create your own at discord.com/developers or remove the feature |
| Dead links | `constants.xml` `url_telegram*`, `url_weblate`, `url_user_manual`; `core/model/Manga.kt` line 133 (`https://kotatsu.app/manga`); `core/parser/MangaLinkResolver.kt` line 29 | Point at dead Kotatsu sites | Use your own scheme `monogatari://` and add it to the `<intent-filter>`s in `AndroidManifest.xml` that currently use `android:scheme="kotatsu"` (keep `kotatsu` too so old links still open) |
| App-lock PIN | `settings/protect/ProtectSetupViewModel.kt` line 42: `settings.appPassword = password.md5()` | Unsalted MD5 of a 4–6 digit PIN can be cracked instantly by anyone with the prefs file | Store `PBKDF2WithHmacSHA256` (100k iterations, random 16-byte salt kept next to it), and compare in `main/ui/protect/ProtectViewModel.kt` lines 37–39 (`password.md5()` == `settings.appPassword`). Migrate old MD5 values on the next successful unlock |

---

## Part B: Sources: as many working ones as possible (your top concern)

There are three layers. Do them in order.

### B1. Turn on automatic mirror switching
`core/prefs/AppSettings.kt` line 287: `prefs.getBoolean(KEY_MIRROR_SWITCHING, false)` → change `false` to `true`.
When a source fails, the app then follows redirects and tries every known mirror domain automatically
(`core/parser/MirrorSwitcher.kt`).

### B2. Load Mihon/Tachiyomi extensions (the biggest win by far)
**Why:** the Mihon ecosystem has **hundreds of English sources**, kept working by many volunteers. When a site changes
its domain, they ship an extension update within days. You don't maintain anything; you add a repo URL in settings.
This is how Mihon, Komikku and Kototoro all get their sources.

**What to copy from Kototoro** (Apache-2.0, so keep its licence header in each copied file):
- `app/src/main/kotlin/eu/kanade/tachiyomi/` (whole folder). This holds the classes extensions expect to find
  (`source/`, `network/`, `util/`, `AppInfo.kt`). Skip `animesource/` and `torrentutils/` until task C6.
- `app/src/main/kotlin/org/skepsun/kototoro/mihon/` (whole folder): `MihonExtensionLoader.kt`,
  `MihonExtensionManager.kt`, `MihonMangaRepository.kt`, `MihonFilterMapper.kt`, `MihonModule.kt`,
  `GetMihonSourcesUseCase.kt`, `MihonExceptionUnwrap.kt`, plus `compat/`, `model/`, `util/`.
- `app/src/main/kotlin/org/skepsun/kototoro/extensions/` (repo list, install and update UI).
- `app/src/main/kotlin/app/cash/quickjs/` (some extensions need a small JS engine).
- From Kototoro's `gradle/libs.versions.toml` and `app/build.gradle(.kts)`: copy the dependencies those files import.
  Expect **Injekt**, **RxJava 1**, **jsoup**, **QuickJS** and **kotlinx-serialization**. The compile errors will tell
  you exactly which ones are missing.

**How to adapt (tell ChatGPT this):**
1. Move `org/skepsun/kototoro/mihon` → `org/koitharu/kotatsu/mihon` and fix package/import lines. Leave
   `eu/kanade/tachiyomi` exactly where it is, because extensions look up those class names.
2. `MihonMangaRepository` must implement *this* repo's `core/parser/MangaRepository.kt` interface. Kototoro's version
   has drifted, so ChatGPT will need both files to reconcile them.
3. Add a new source type: `data class MihonMangaSource(val sourceId: Long, val pkg: String, val title: String, val lang: String) : MangaSource`
   in `core/model/`. Register it in:
   - `core/model/MangaSource.kt` → `fun MangaSource(name: String?)` (line 35): parse names like `mihon:<id>`.
   - `core/parser/MangaRepository.kt` → `Factory.createRepository()`: `is MihonMangaSource -> MihonMangaRepository(...)`.
   - `explore/data/MangaSourcesRepository.kt` → `getEnabledSources()`: append installed Mihon sources (same pattern
     as `getExternalSources()` at line 356).
4. `app/src/main/AndroidManifest.xml`: add
   ```xml
   <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
   <queries>
       <intent><action android:name="android.intent.action.MAIN" /></intent>
       <package android:name="eu.kanade.tachiyomi" />
   </queries>
   ```
   Copy the exact `<queries>` and `<meta-data>` entries from Kototoro's `AndroidManifest.xml` (search it for
   `tachiyomi`).
5. Settings → "Extension repos": a text field where **you** paste repo URLs. Don't hard-code any repo. That keeps you
   clear of DMCA trouble, the same way Mihon does it. A Mihon repo is an `index.min.json` with entries like
   `{ name, pkg, apk, lang, code, version, nsfw, sources:[{id,name,lang,baseUrl}] }`. Filter the list to `lang == "en"`
   (plus `"all"`) by default.
6. Update check: compare each installed extension's `versionCode` to `code` in the index, then show "Updates (3)" with
   an "Update all" button. **This is what fixes sources whose URL changed.**

**Check:** add a repo URL, install one English extension, search a title, open a chapter and read it.

### B3. Remote domain overrides for built-in (Kotatsu) parsers
For the ~1,200 built-in parsers, so a site that moves doesn't need an app release:
1. In your repo create `sources/overrides.json`:
   ```json
   { "version": 1, "sources": {
       "EXAMPLE_SOURCE": { "domains": ["new-domain.to", "old-domain.com"] },
       "DEAD_SOURCE":    { "broken": true }
   } }
   ```
   Keys are the parser's enum name, as shown in source settings or `MangaParserSource`.
2. New file `core/parser/SourceOverridesRepository.kt`, `@Singleton`. It downloads
   `https://raw.githubusercontent.com/coolchan03/monogatari/devel/sources/overrides.json` at most every 24 h with the
   `@BaseHttpClient OkHttpClient`, caches it to `filesDir/source_overrides.json`, and exposes
   `extraDomains(source): List<String>` and `isBroken(source): Boolean?`.
3. `core/parser/ParserMangaRepository.kt` lines 60–61 (`val domains`): return
   `(overrides.extraDomains(source) + parser.configKeyDomain.presetValues).distinct().toTypedArray()`. Pass the
   repository in through `MangaRepository.Factory` (`core/parser/MangaRepository.kt`).
4. If the override's first domain differs from the current one and the user never typed a domain by hand, set
   `domain = overrides.first()`.
5. Replace the static "broken" checks with `fun MangaParserSource.isBrokenNow(o) = o.isBroken(this) ?: isBroken`:
   `explore/data/MangaSourcesRepository.kt` lines 57 and 128, `search/ui/multi/SearchViewModel.kt` line 210,
   `remotelist/ui/RemoteListViewModel.kt` line 123, `settings/sources/catalog/SourceCatalogItemAD.kt` line 45.
6. Call the refresh from `core/BaseApp.kt` `onCreate` (in a background coroutine).

Good news: library entries store **relative** URLs and the manga ID is derived from source + relative URL, so a domain
change never breaks favourites or history. Only fetching breaks, and that is what this fixes.

### B4. Automatic source health ("auto-hide things that don't work")
1. Room: add `fail_count INTEGER NOT NULL DEFAULT 0` and `last_success_at INTEGER NOT NULL DEFAULT 0` to
   `core/db/entity/MangaSourceEntity.kt`, bump `DATABASE_VERSION` in `core/db/MangaDatabase.kt` line 73 from 27 to 28,
   and add `core/db/migrations/Migration27To28.kt` (copy the shape of `Migration26To27.kt`; two `ALTER TABLE sources
   ADD COLUMN` statements). Register it where the other migrations are listed in `MangaDatabase.kt`.
2. In `search/ui/multi/SearchViewModel.kt` `searchSource()` and `remotelist/ui/RemoteListViewModel.kt` `loadList()`:
   on success set `fail_count = 0, last_success_at = now`; on failure `fail_count += 1`, **except** for
   `core/exceptions/CloudFlareProtectedException`. That one isn't "dead", it needs the user to solve a captcha.
3. A source is "unhealthy" when `fail_count >= 5` and `last_success_at` is older than 7 days. Unhealthy sources are
   skipped in global search, sink to the bottom of Browse with a ⚠, and show "Try a new domain / Find replacement
   extension".
4. Weekly "Library health check" worker: for each library title on an unhealthy source, offer one-tap migration to
   the same title on a healthy source. The `alternatives/` feature already finds the same title elsewhere; reuse
   `alternatives/domain/` for this.

### B5. Built-in legal English sources (no extensions, no scraping risk)
Implement each one as a small in-app repository. Add a `sealed interface BuiltinSource : MangaSource` in
`core/model/`, register it the same way as `MihonMangaSource` in B2.3, and model each repository on
`core/parser/external/ExternalMangaRepository.kt` (shortest example of implementing `MangaRepository`).

| Source | Kind | API (all free, no key unless noted) | Notes |
|---|---|---|---|
| **Internet Archive: books** | Novel/book | Search `https://archive.org/advancedsearch.php?q=(<q>) AND mediatype:texts AND language:(English OR eng)&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=date&fl[]=subject&sort[]=downloads desc&rows=50&page=<n>&output=json` · Files `https://archive.org/metadata/<id>` · Cover `https://archive.org/services/img/<id>` · File `https://archive.org/download/<id>/<file>` | Skip items whose metadata has `access-restricted-item: true` (borrow-only). Use `sort[]=date desc` for "newest" |
| **Internet Archive: comics** | Manga | Same, with `AND collection:(comics)` | Prefer `.cbz`/`.cbr` files and reuse the existing local CBZ reader (`local/`) |
| **Internet Archive: video** | Video | Same, with `mediatype:movies` | `.mp4`/`.ogv` files. Classic cartoons, old anime, public-domain films |
| **Project Gutenberg** | Novel | `https://gutendex.com/books/?search=<q>&languages=en&page=<n>`; EPUB at `formats["application/epub+zip"]`, cover at `formats["image/jpeg"]` | 75k classics, very clean EPUBs |
| **Standard Ebooks** | Novel | Scrape `https://standardebooks.org/ebooks?query=<q>`; each book page links an `.epub` | Beautifully formatted public-domain books |
| **LibriVox** | **Audiobook** | `https://librivox.org/api/feed/audiobooks/?title=^<q>&format=json&extended=1`; chapter MP3s in `sections[].listen_url` | Free public-domain audiobooks. Plays in the video player (Media3 handles audio) |
| **MangaDex** | Manga | Already a built-in parser | Best English source with an official API and a proper **Boys' Love** tag. Pin it by default |
| **Comic Book Plus** / **Digital Comic Museum** | Comics | Scrape | Public-domain golden-age comics |
| **Webtoon (official)** | Manga | Check whether the redo parsers include it (`site/en`); if not, the Mihon extension | Lots of official English BL webtoons |
| **Archive of Our Own** | Novel | Scrape `https://archiveofourown.org/works/search?work_search[query]=<q>`; EPUB export `https://archiveofourown.org/downloads/<id>/a.epub` | The largest English BL (M/M) fiction archive. Rate-limit to 1 request per 3 s and honour `Retry-After` on 429. Map rating `Explicit` → ADULT |
| **Wattpad** | Novel | Public JSON: `https://www.wattpad.com/api/v3/stories?query=<q>&limit=30&offset=<n>`; parts `https://www.wattpad.com/apiv2/storytext?id=<partId>` | Huge BL community. Endpoints are unofficial, so wrap them in try/catch and let B4 hide the source if they change |
| **Scribble Hub**, **Royal Road** | Novel | Scrape (series page → chapter list → chapter HTML) | Scribble Hub has "Yaoi" and "Shounen Ai" genres |

### B6. LNReader plugins: hundreds of English web-novel sources
LNReader (`github.com/LNReader/lnreader-plugins`, **MIT licence**) has a large set of JavaScript novel plugins,
published as `plugins.min.json` on its `plugins` branch. Running them needs a JS engine. You'll already have QuickJS
from B2, plus small shims for `fetch`, `cheerio` and `dayjs`. It's a big task, so **do it after C1** (novel reader).
Their API docs are in that repo at `docs/docs.md`. Hand that file to ChatGPT. Alternatively, Kototoro's **IReader**
support (`org/skepsun/kototoro/ireader/`) loads IReader's Kotlin novel extensions, which is closer to B2 and easier.

### B7. YouTube (and PeerTube, SoundCloud, Bandcamp) with NewPipeExtractor
NewPipeExtractor is **GPL-3.0**, the same licence as this app, so you can copy code freely.
1. `app/build.gradle`: `implementation 'com.github.TeamNewPipe:NewPipeExtractor:v0.26.5'` (check JitPack or GitHub
   releases for a newer tag; **YouTube breaks it every few weeks, so keep it updated**). If the build complains about
   `java.nio`, change `coreLibraryDesugaring libs.desugar.jdk.libs` to
   `coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs_nio:2.1.5'`.
2. **Copy** `DownloaderImpl.java` from the NewPipe app:
   `github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/DownloaderImpl.java`. Put it at
   `video/newpipe/DownloaderImpl.kt`; ChatGPT can convert it to Kotlin. Make it take this app's
   `@BaseHttpClient OkHttpClient` instead of building its own. Remove the reCAPTCHA activity references (or open the
   app's existing `browser/BrowserActivity` instead).
3. In `core/BaseApp.kt` `onCreate`:
   `NewPipe.init(DownloaderImpl(okHttp), Localization("en", "US"), ContentCountry("US"))`.
4. `video/newpipe/YouTubeRepository.kt` implementing `MangaRepository` (source = `BuiltinSource.YouTube`):
   - **search** (`getList` with a query):
     `SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(q, listOf("videos"), ""))`,
     next page via `SearchInfo.getMoreItems(service, handler, info.nextPage)`. Map each `StreamInfoItem` to a `Manga`
     (title = `name`, cover = `thumbnails.maxBy { it.height }.url`, author = `uploaderName`, url = `url`).
   - Also search channels (`"channels"`) and playlists (`"playlists"`). **Channel or playlist = "manga" and videos =
     "chapters".** Then you can favourite a channel and the existing update tracker notifies you of new uploads.
     Channel videos: `ChannelInfo.getInfo(url)` → the `videos` tab → `ChannelTabInfo.getInfo(service, tab)`.
     Playlist: `PlaylistInfo.getInfo(url).relatedItems`.
   - **play** (`getPages`): `StreamInfo.getInfo(ServiceList.YouTube, url)`. Prefer `info.hlsUrl` or
     `info.dashMpdUrl`; otherwise pick the best of `info.videoOnlyStreams` plus the best of `info.audioStreams` and
     merge them in Media3 with `MergingMediaSource`. Subtitles: `info.subtitles`.
5. Swap `ServiceList.YouTube` for `ServiceList.PeerTube`, `SoundCloud` or `Bandcamp` and you get those sources too.
6. Cheaper update checks: a YouTube channel's RSS feed `https://www.youtube.com/feeds/videos.xml?channel_id=<UC…>`
   lists the latest 15 uploads and needs no API key or extractor. Use it in the tracker to check for new videos.

Caveats you should know: this goes against YouTube's terms of service. That's fine for a personal sideloaded app,
but Google Play would reject it, and as noted, it needs regular updates.

### B8. Aniyomi and CloudStream (more video)
Kototoro also bridges **Aniyomi** (`eu/kanade/tachiyomi/animesource/` plus `org/skepsun/kototoro/aniyomi/`) and
**CloudStream** (`com/lagradost/` plus `org/skepsun/kototoro/cloudstream/runtime/`). Same approach as B2: copy, fix
packages, register a source type. Do this **after** the video player (C3) exists. Like Mihon, these only work with
repos you add yourself. Don't bundle any.

---

## Part C: Novels, video and audio (content kinds and readers)

Kotatsu has **no novel reader and no video player**: the reader (`reader/ui/pager/`) only shows images. Keep
Kotatsu's data model (`Manga` → `MangaChapter` → `MangaPage`) for everything, so favourites, history, categories,
tracking, stats and backups all work for free, and add a "kind":

### C0. Content kind plumbing
1. New `core/model/ContentKind.kt`: `enum class ContentKind { MANGA, NOVEL, VIDEO, AUDIO }`.
2. New `val MangaSource.contentKind: ContentKind` in `core/model/MangaSource.kt`:
   - `MangaParserSource` → `if (contentType == ContentType.NOVEL) NOVEL else MANGA`
   - `BuiltinSource` / `MihonMangaSource` → declared per source (Aniyomi/CloudStream/YouTube = VIDEO, LibriVox = AUDIO).
3. Local files (`local/`): `.epub`/`.txt` → NOVEL, `.mp4`/`.mkv`/`.webm` → VIDEO, `.mp3`/`.m4b` → AUDIO.
4. `core/nav/AppRouter.kt`: the 4 `openReader(...)` functions dispatch on `manga.source.contentKind` to
   `ReaderActivity` / `NovelReaderActivity` / `VideoPlayerActivity`.
5. `download/`: for NOVEL save chapter HTML or EPUB; for VIDEO/AUDIO save the file with progress. Until that's done,
   show "Downloads not supported for this source yet" instead of crashing.

### C1. Novel reader
`reader/ui/novel/NovelReaderActivity.kt` plus `NovelReaderViewModel.kt`:
- A chapter's single `MangaPage.url` points to HTML (web novel) or an EPUB plus a spine index. EPUB = zip:
  read `META-INF/container.xml` → the `.opf` file → `<spine>` order. Use `java.util.zip` (already used in `core/zip/`)
  and jsoup to clean the HTML.
- Render with a `RecyclerView` of paragraphs (fast, keeps scroll position) or a `WebView` with injected CSS.
- Settings sheet: font, size, line height, margins, theme (light/sepia/dark/black), justify, paragraph spacing.
- **Text-to-speech:** Android `TextToSpeech`, reading paragraph by paragraph and highlighting the current one, with a
  sleep timer. Lets you "listen" to any novel.
- Progress = scroll fraction, saved through `history/domain/HistoryUpdateUseCase.kt` into `percent`.
- Word count per chapter (store it, see E2) to give accurate time-left estimates.
- Reference: Kototoro's reader (`org/skepsun/kototoro/reader/`) already does this. Copy if helpful.

### C2. Image reader small fixes
- `reader/ui/ReaderViewModel.kt` line 343: `if (pages.size != content.value.pages.size) return@launchJob // TODO`.
  When the next chapter finishes loading while you're scrolling, the page-position update is **dropped**, so reading
  progress at chapter boundaries may not be saved. Fix: instead of returning, look up the page by `chapterId` and
  `index` in the new `content.value.pages` list and continue.
- `reader/domain/PageLoader.kt` line 107: prefetch limit is fixed (`// TODO adaptive`). Make it larger on Wi-Fi
  (e.g. 6) and smaller on mobile data (2), using `ConnectivityManager.isActiveNetworkMetered`.

### C3. Video and audio player
1. Dependencies: `androidx.media3:media3-exoplayer`, `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-ui`,
   `media3-session` (same version for all, e.g. `1.8.0`).
2. `reader/ui/video/VideoPlayerActivity.kt`:
   - `PlayerView`, landscape fullscreen, double-tap ±10 s, swipe brightness/volume, speed 0.5–3×.
   - Subtitle and audio track picker, "Skip intro +85 s" button, auto-play next episode, picture-in-picture.
   - Resume from position: store `position/duration` in history `percent` plus the exact ms in a new column (see E2).
3. **Audio mode** (LibriVox, SoundCloud, any AUDIO kind): a `MediaSessionService` so playback continues with the
   screen off, with lock-screen controls and a sleep timer.
4. Reference: Kototoro's `org/skepsun/kototoro/video/`.

### C4. Library split by kind
`favourites/ui/container/FavouritesContainerFragment.kt`: add a top segmented control "Manga · Novels · Video ·
Audio" (hide kinds with zero items) filtering on `contentKind`. That matches Mangayomi's separate libraries.

---

## Part D: Search (keep Kotatsu's strengths, fix and extend)

### D1. Bugs in the current search
- `search/ui/multi/SearchMenuProvider.kt` line 21 `onPrepareMenu`: the "Pinned sources only" and "Hide empty sources"
  checkboxes aren't restored, so after rotating the phone the filter is still on but the box shows unchecked. Add
  `menu.findItem(R.id.action_filter_pinned_only)?.isChecked = viewModel.isPinnedOnly` and the same for hide-empty
  (add `val isPinnedOnly get() = pinnedOnly.value` and `val isHideEmpty get() = hideEmpty.value` to the ViewModel).
- `search/ui/multi/SearchViewModel.kt` line 78: "Hide empty sources" actually hides **sources that errored**. Empty
  results are never added in the first place (`searchSource` returns `null`). Rename string `hide_empty_sources` to
  "Hide sources with errors", save it in `AppSettings`, and **default it to on**.
- `SearchViewModel.kt` lines 118–121 (`retry()`): the list is cleared before the old search has stopped, so a late
  result from the old search can appear. Move `results.value = emptyList()` into `doSearch()` right after
  `prevJob?.cancelAndJoin()`.
- `SearchViewModel.kt` lines 64–66: `private var … = MutableStateFlow` → `private val`.
- `SearchViewModel.kt` line 51: `MAX_PARALLELISM = 4` makes search slow with many sources. Raise it to 8, or make it a
  setting.

### D2. Genre synonyms (big win for BL)
Genre search only matches a tag with **exactly** the same name (`search/domain/SearchV2Helper.kt`: `getFilter` →
`tags.find { x -> x.title.equals(query, ignoreCase = true) }`, and `sortByRelevance` line 107). "Yaoi" misses sources
that say "Boys' Love", "BL", "Shounen Ai" or "Danmei". Add `search/domain/TagSynonyms.kt`:
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
Then in `SearchV2Helper` replace both exact comparisons with `tag.title.lowercase() in TagSynonyms.expand(query)`.
Use the same helper in the filter sheet's tag search (`filter/ui/`).

### D3. Global tag blocklist and content warnings
Kotatsu only has a blocklist for *suggestions* (`AppSettings.suggestionsTagsBlacklist`, line 401). Add a **global**
"Hidden tags" setting (e.g. NTR, non-con, gore): apply it in `list/ui/MangaListViewModel.kt` next to
`skipNsfwIfNeeded()` (line 53) and in `SearchV2Helper.postFilter`, expanded through `TagSynonyms`. Kotatsu already
shows tag warnings (`isTagsWarningsEnabled`, line 119); let the user edit that warning list too.

### D4. A finer NSFW switch
Right now NSFW is all-or-nothing, and whole sources are hidden when `contentType == ContentType.HENTAI`
(`core/model/MangaSource.kt` line 55). Many BL sources are flagged that way, so turning NSFW off hides plenty of
non-explicit BL. Replace it with **Content level: Safe / Suggestive / Everything**, filtered per title using
`Manga.contentRating` (`SAFE` / `SUGGESTIVE` / `ADULT`). Keep "Hide adult-only sources" as a separate checkbox.

### D5. Merged results
Toggle in global search: "Group same title". Cluster results with `almostEquals(threshold = 0.15f)` (the same matcher
`SearchV2Helper.matches` uses), show one card with "on 6 sources", and on tap pick the source with the most chapters
or the most recent update.

### D6. Sort and filter in global search
Add a sort chip row: Relevance · Latest update · Most chapters, plus a kind filter (Manga/Novel/Video/Audio) and an
"English only" toggle that hides sources whose `locale` isn't `en`.

---

## Part E: Reading time, statistics and estimates (you asked for this)

### What exists today (and its problems)
- `stats/` records reading sessions: table `stats(manga_id, started_at, duration, pages)`, written by
  `stats/domain/StatsCollector.kt`. There's a Stats screen (`stats/ui/StatsActivity.kt`) and it **is** included in
  backups (`BackupSection.STATS`).
- The details screen shows a "time to finish" estimate (`details/domain/ReadingTimeUseCase.kt`).

Problems:
1. **Stats are off by default.** `core/prefs/AppSettings.kt` line 583: `getBoolean(KEY_STATS_ENABLED, false)`. Most
   users never get any data. Change it to `true`.
2. **Idle time counts as reading.** `StatsCollector.onStateChanged` sets `duration = now - startedAt`. Leave the
   reader open for an hour and that hour counts as reading. Fix: track `lastEventAt`, and add
   `min(now - lastEventAt, 120_000)` to the duration on each page turn instead (any gap over 2 minutes counts as
   2 minutes).
3. **No per-chapter data.** Nothing records which chapter the time was spent on.
4. **Clearing history deletes all stats.** `stats/data/StatsEntity.kt` has a foreign key to history with
   `onDelete = CASCADE`, and "clear history" soft-deletes, then a cleanup job hard-deletes, which wipes the stats.
   Fix: point the foreign key at the `manga` table instead of `history`, in a migration.
5. **The estimate is mostly made up.** `ReadingTimeUseCase` assumes **20 pages per chapter** and **10 s per page**
   (see the comment "Impossible task, I guess. Good luck on this."). It ignores real page counts.

### E1. Better data collection
Migration `Migration28To29` (after B4's 27→28):
- `stats`: add `chapter_id INTEGER NOT NULL DEFAULT 0`, `words INTEGER NOT NULL DEFAULT 0` (novels) and
  `media_ms INTEGER NOT NULL DEFAULT 0` (video/audio watched). Change the FK as described in problem 4.
- New table `chapter_meta(chapter_id INTEGER PRIMARY KEY, manga_id INTEGER, pages INTEGER, words INTEGER, duration_ms INTEGER, updated_at INTEGER)`.
  Fill it whenever a chapter is opened: page count from the image reader, word count from the novel reader, duration
  from the video player. Over time this gives real sizes for chapters you haven't read yet. For unknown chapters, use
  the average of known chapters of the same title.
- `StatsCollector`: include `chapterId` in each session; split a session when the chapter changes.

### E2. Estimates everywhere
New `stats/domain/TimeEstimator.kt`:
- **Speed:**
  - Manga: your ms per page for *this title*, falling back to your ms per page for the *same source content type*,
    then all titles, then 8 s.
  - Novel: your words per minute, starting at 230 wpm and adjusting.
  - Video: remaining duration ÷ playback speed.
  - Use a weighted average that favours recent sessions (e.g. last 30 days weight 2×).
- **Rest of chapter:** remaining pages × ms/page (or remaining words ÷ wpm). Show it in the reader's top bar:
  "~3 min left in chapter".
- **Rest of title:** sum over unread chapters of `chapter_meta` size × speed. Show it on the details screen:
  "~4 h 10 m to finish · ~25 min/day finishes it by Sunday". Replace the body of `ReadingTimeUseCase` with this.
- **Whole library:** sum over all favourites of the rest-of-title time. Show it on the Stats screen: "Backlog: 63 h ·
  BL 41 h · Novels 12 h".
- **Library card badge** (optional, setting): "1h 20m left".

### E3. Statistics screen upgrades (`stats/ui/`)
- Totals by day/week/month/year, split by kind (manga/novels/video/audio).
- Per title: total time, sessions, chapters per hour, first and last read, "you read this 3 times".
- Per tag: "37% of your reading is Boys' Love" (needs the tags join, which `manga_tags` already has).
- Streaks and goals: "Read 20 min a day", current and longest streak, plus a home-screen widget (copy the structure of
  `widget/recent/`).
- Heatmap of time of day and day of week.
- **"Monogatari Wrapped"**: a December year-in-review card set (top titles, hours, top tags, longest binge) that can
  be shared as an image.

### E4. Back all of it up
- `backups/domain/BackupSection.kt`: add `CHAPTER_META("chapter_meta")`.
- `backups/data/BackupRepository.kt`: write and read it the same way `STATS` is handled (lines 126 and 201).
- Bump a `backupVersion` field in `backups/data/model/BackupIndex.kt` so old app versions skip unknown sections
  safely (they already ignore unknown zip entries via `BackupSection.of()`).

---

## Part F: Backup, restore and cross-device sync

### Current state
- Backups (`backups/`) cover history, categories, favourites, settings, reader tap-grid, bookmarks, enabled sources,
  scrobbling, stats and saved filters (`backups/domain/BackupSection.kt`). There are periodic backups to a folder you
  pick (`backups/domain/ExternalBackupStorage.kt`), and Telegram upload works only if you create your own bot token in
  `local.properties`.
- Restore **merges** (`upsert`), so it never wipes your library. But it doesn't check which copy is newer, so restoring
  an older backup overwrites newer reading progress (`backups/data/BackupRepository.kt` ~line 166,
  `getHistoryDao().upsert(...)`).
- **Missing from backups:**
  - Per-source settings: chosen domain, sort order, logins and filters, stored by `core/prefs/SourceSettings.kt` in
    separate SharedPreferences files. After a restore, every domain you fixed by hand is lost.
  - Installed extension list (after B2) and repo URLs.
  - Downloaded file index (`local/data/index/LocalMangaIndex.kt`).
- Backups are **plain unencrypted zips** containing your complete reading history. Worth protecting if they go to a
  cloud folder.
- Live sync (`sync/`) speaks Kotatsu's protocol, covers only history, favourites and categories, and has no working
  server (see A3).

### F1. Newest-wins restore
In `BackupRepository.restoreBackup`:
- HISTORY: skip the incoming row if the existing `updated_at >= incoming.updated_at`.
- FAVOURITES and CATEGORIES: compare `created_at` and `deleted_at`; a newer `deleted_at` wins, so deletions spread
  between devices.
- BOOKMARKS: union.
- STATS: union (primary key `manga_id` + `started_at` already makes it idempotent).
- Add a unit test in `app/src/test/kotlin/…/backups/`.

### F2. Back up what's missing
- New `BackupSection.SOURCE_SETTINGS("source_settings")`: for each `MangaParserSource` with a non-empty prefs file,
  write `{ "source": name, "prefs": {…} }`. The prefs file name is `source.name` (see the `SourceSettings` constructor), so add `fun getAllValues()` / `fun upsertAll(map)` to `core/prefs/SourceSettings.kt` mirroring the ones in `reader/data/TapGridSettings.kt`.
  **Exclude cookie and auth keys.**
- `BackupSection.EXTENSIONS("extensions")`: repo URLs plus installed `{pkg, versionCode}` list. On restore, show
  "Reinstall 23 extensions?".
- Optional **backup encryption**: setting "Encrypt backups with a password". AES-256-GCM, key from PBKDF2-SHA256
  (200k iterations, random salt), file extension `.mbak`. Ask for the password on restore.

### F3. "Sync through a folder" (works with Google Drive, Syncthing, Nextcloud, OneDrive; no server)
1. Generate a `deviceId` UUID once and store it in prefs. Add `deviceId` and `deviceName` (`Build.MODEL`) to
   `BackupIndex.kt`.
2. Periodic backup already writes to a picked folder. Add the setting "Use this folder to sync my devices".
3. A WorkManager job every 6 h, and on app start: `ExternalBackupStorage.list()` → for each device, take its newest
   backup that isn't from this device and is newer than the last one merged → restore it with F1's newest-wins rules
   → write a fresh backup.
4. Keep only the newest 3 backups per device (`ExternalBackupStorage.trim`).
Result: open the app on your phone and tablet, and within minutes both have the same library, progress and stats.

### F4. WebDAV (best "real" sync without running your own server)
- Kototoro already has **WebDAV sync**, so start from its `org/skepsun/kototoro/sync/` and `backups/` folders.
- Or write it yourself: `PUT` the backup zip to `https://<server>/remote.php/dav/files/<user>/Monogatari/<deviceId>.zip`
  with OkHttp and Basic auth over HTTPS; `PROPFIND` (Depth: 1) to list files; then F3's merge logic.
- Works with Nextcloud, Koofr (free 10 GB), Box, many NAS boxes, and InfiniCLOUD.

### F5. Self-hosted live sync (optional, last)
Kotatsu's sync server was open source (`KotatsuApp/kotatsu-syncserver`: Kotlin/Ktor + MySQL, Docker). Fork it, run it
on a cheap VPS or at home behind Tailscale **with HTTPS**, and make it the only `sync_url_list` entry. Then extend
`sync/domain/SyncHelper.kt` to also sync bookmarks, stats and source settings. Today it only sends
`TABLE_HISTORY`, `TABLE_FAVOURITES` and `TABLE_FAVOURITE_CATEGORIES`.

---

## Part G: Look and feel like Mangayomi

Mangayomi's layout: bottom bar **Manga · Anime · Novel · Updates · History · Browse · More** (the first three are
separate libraries that can be hidden), compact cover grids with the title over a dark gradient, a details page with a
blurred cover header, and a big "Resume" button.

1. **Bottom bar.** `core/prefs/AppSettings.kt` line 73: default
   `listOf(NavItem.FAVORITES, NavItem.UPDATED, NavItem.HISTORY, NavItem.EXPLORE)`, then add `NavItem.MORE`.
   In `core/prefs/NavItem.kt` relabel `favourites` → "Library", `explore` → "Browse", `updated` → "Updates". MORE opens
   a list: Downloads, Stats, Bookmarks, Extensions, Backup & sync, Settings, About. If you do C4, Library gets the
   Manga/Novels/Video/Audio switch.
2. **Compact grid.** `res/layout/item_manga_grid.xml`: add a "Compact" variant (new value in
   `core/prefs/ListMode.kt`) with the title over a bottom gradient (`GradientDrawable` from transparent to 80% black),
   an unread-count pill top-left, a "downloaded" pill top-right, and 2:3 covers with 8 dp corners.
3. **Details header.** In the details layout (`details/ui/DetailsActivity.kt` and its XML), add a full-width blurred
   copy of the cover behind the title (Coil `transformations(BlurTransformation)` or `RenderEffect.createBlurEffect`
   on API 31+), genre chips, an expandable description, and a large tonal **Resume Ch. 42** button.
4. **Theme.** Default to dark with pure black (`KEY_THEME_AMOLED`, `AppSettings.kt` line 67 → default `true`). Pick a
   brand accent, e.g. the existing `SAKURA` in `core/prefs/ColorScheme.kt`, and make it the default when dynamic
   colours are off.
5. **Browse.** In `explore/ui/`: tabs "Sources · Extensions · Migrate" (like Mihon and Mangayomi), sources grouped by
   language with "Last used" and "Pinned" on top, a ⚠ for unhealthy sources (B4), and a kind icon per source.
6. **Updates screen.** Group by day ("Today", "Yesterday") with cover thumbnails, plus swipe to mark read or download.

---

## Part H: Other code issues found

| # | Where | Issue | Fix |
|---|---|---|---|
| H1 | `list/ui/config/ListConfigViewModel.kt` line 110 | `runBlocking` database read on the main thread, which can freeze the UI | Make it `suspend` and load it in `init { launchJob { … } }` |
| H2 | `browser/BrowserClient.kt` line 76 | `runBlocking(Dispatchers.Main.immediate)` from WebView's background thread to read `url`. Can deadlock if the main thread is waiting on WebView | Cache the last URL from `onPageStarted` in a `@Volatile var` and read that instead |
| H3 | `download/ui/worker/DownloadNotificationFactory.kt` line 55 | `HashMap<Manga, Drawable>` of covers that is never trimmed, so it leaks memory on big download queues | Use `LruCache<Long, Drawable>(20)` keyed by manga id |
| H4 | `core/util/ext/Preferences.kt` line 22 | `setDefaultValue(defaultValue) // FIXME not working` | Defaults are actually applied via getters in `AppSettings`, so just delete the dead call |
| H5 | `core/network/proxy/ProxyProvider.kt` line 59 | Untranslated error string | Move it to `strings.xml` |
| H6 | `.github/workflows/` | **No workflow builds the app or runs tests** | See I1 |
| H7 | `app/src/test/` | Only 3 unit tests | Add tests for F1 (merge), D2 (synonyms), E2 (estimator) and B3 (overrides parsing). These are all pure Kotlin and easy to test |
| H8 | `gradle.properties` | `org.gradle.jvmargs=-Xmx1536M` is low for this project with KSP and R8 full mode | `-Xmx4g -XX:+UseParallelGC` |

---

## Part I: Build automation

### I1. GitHub Actions: build an APK on every push
Create `.github/workflows/build.yml`:
```yaml
name: Build
on:
  push:
    branches: [ "**" ]
  pull_request:
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - name: Unit tests
        run: ./gradlew testDebugUnitTest --stacktrace
      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: monogatari-debug
          path: app/build/outputs/apk/debug/*.apk
```
Then every push gives you a downloadable APK under the repo's **Actions** tab, and any ChatGPT mistake shows up as a
red ❌ with the error log, which you paste back to it.

### I2. Signed releases
Add a second workflow on `push: tags: ['v*']` that decodes a base64 keystore from repo secrets, runs
`./gradlew assembleRelease`, and attaches the APK to a GitHub Release. The update checker (after A2) then finds it
automatically. Generate the keystore once with `keytool -genkey -v -keystore monogatari.jks -keyalg RSA -keysize 4096 -validity 36500 -alias monogatari`.
**Back that file up somewhere safe.** If you lose it, users can't update without uninstalling.

### I3. Keep dependencies fresh
Add `.github/dependabot.yml` for `gradle` (weekly). NewPipeExtractor and the parsers need frequent bumps.

---

## Part J: Extra features worth adding

**Discovery and news**
- **Discover tab, powered by AniList.** GraphQL at `https://graphql.anilist.co` (already used in `scrobbling/anilist/`):
  - trending manga for a tag: `Page { media(type: MANGA, sort: TRENDING_DESC, tag_in: ["Boys' Love"]) { … } }`
  - "Because you read X": `Media(id) { recommendations { … } }`
  - seasonal BL anime and upcoming adaptations.
  Tapping anything runs a global search across your sources.
- **MangaUpdates** (`https://api.mangaupdates.com/v1/`): has genres "Yaoi" and "Shounen Ai", plus scanlation release
  feeds. Good for "new chapter released somewhere" alerts even before your source has it.
- **News feed tab:** RSS needs no API keys.
  - Anime News Network: `https://www.animenewsnetwork.com/all/rss.xml`
  - Publisher news pages, e.g. SuBLime (VIZ's BL imprint) and Seven Seas, for licensing announcements
  - YouTube channel RSS (B7.6) for creators you follow
  - Subreddit RSS (`https://www.reddit.com/r/<name>/.rss`)
  Keep it filterable by your followed tags.
- **Follow a tag:** a worker (copy the shape of `suggestions/ui/SuggestionsWorker.kt`) that checks each followed tag
  on chosen sources sorted by newest and posts "New in Boys' Love: 5 titles".

**Library and reading**
- **Private categories:** mark a category as private so it's hidden from the library until unlocked with biometrics.
  It's also excluded from widgets, Discord status and screenshots (`main/ui/protect/ScreenshotPolicyHelper.kt` already
  handles `FLAG_SECURE`).
- **Reading lists you can share:** export a category as a small JSON or QR code; importing it searches your sources
  for each title.
- **Duplicate finder:** the same title favourited from two sources gets merged, keeping the higher progress.
- **Per-title notes and ratings:** private 1–10 score plus notes, backed up and optionally synced to AniList.
- **Auto-download on Wi-Fi** of new chapters for chosen categories. `TrackerDownloadStrategy` exists
  (`AppSettings.kt` line 200), so expose it per category.
- **Smart "Up next" shelf** on the Library: continue reading, sorted by "least time left" (from E2). Finish what's
  almost done.
- **Novel extras:** text-to-speech with sleep timer (C1), dictionary long-press, and "convert this manga chapter's
  text with OCR" (Kototoro has OCR and translation; see its `dictionary/`).
- **Video extras:** skip-intro button, remember playback speed per show, auto-mark episodes watched on AniList.

**Quality of life**
- **First-run setup for English readers:** skip the language picker; pre-enable MangaDex, Internet Archive, Gutenberg,
  LibriVox and YouTube; ask "Do you want adult content?" (D4), "Any tags you never want to see?" (D3) and "Pick a few
  favourite genres". The welcome screen is `main/ui/welcome/`.
- **Source "test" button:** in source settings, run popular → details → first chapter → first page and show ✅/❌ for
  each step with the error. Very handy when a source breaks.
- **Home-screen widgets:** "Continue reading" with time-left, and a reading-streak widget.

---

## Part K: Second sweep: more fixes and additions

### K1. Release builds will silently break extensions and NewPipe (critical, do with B2/B7)
Release builds run R8 in full mode (`app/build.gradle` `minifyEnabled true`, `gradle.properties`
`android.enableR8.fullMode=true`). R8 deletes any class or method the app itself doesn't call. Mihon extensions are
loaded at runtime, so R8 can't see that they call into `eu.kanade.tachiyomi.*`, OkHttp, jsoup, kotlinx and RxJava.
Those classes get removed and **every extension crashes in release builds while working fine in debug**. NewPipe has
the same problem (it uses reflection and the Rhino JS engine). Append to `app/proguard-rules.pro`:
```proguard
# --- Mihon / Aniyomi extensions (loaded at runtime, R8 can't see their usages) ---
-keep class eu.kanade.tachiyomi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class rx.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class app.cash.quickjs.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# --- NewPipeExtractor ---
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**
```
Also compare with Kototoro's `app/proguard-rules.pro` (search it for `tachiyomi`) and copy any extra lines. **Test a
release build** (`./gradlew assembleRelease`) with at least one extension before calling B2 done.

### K2. "Ignore SSL errors" turns off security for the whole app (security)
`core/exceptions/resolve/ExceptionResolver.kt` lines 148–163: when one manga site has a certificate problem, the
dialog's "Apply" sets `settings.isSSLBypassEnabled = true`. `core/network/NetworkModule.kt` line 76 then calls
`disableCertificateVerification()` (`core/network/SSLUtils.kt`, which trusts every certificate and every hostname) on
the **base** HTTP client. Every other client is built from that one: sync login, AniList/MAL tokens, backups, and the
**app-update APK download**. Anyone on the same Wi-Fi could then read or alter that traffic.
Fix:
1. Replace the global boolean with a per-source set: `AppSettings.sslBypassSources: Set<String>`.
2. Remove the bypass from `provideBaseHttpClient`. In `provideMangaHttpClient`, add an interceptor or use a separate
   `OkHttpClient` built with `disableCertificateVerification()` **only** for requests whose host belongs to a
   bypassed source.
3. In the dialog, name the source: "Ignore certificate errors for *SiteName* only?"
4. Migration: if the old global flag is on, clear it and show a one-time notice.

### K3. Android developer verification (affects how you distribute the app)
Google now requires apps on certified Android phones to be registered to a verified developer. It has been enforced
in Brazil, Indonesia, Singapore and Thailand since 30 Sep 2026, and goes worldwide in 2027. Unregistered apps can
still be installed with ADB or Google's "advanced" sideloading flow, but that's a hassle.
- For personal use or a few friends: create a **limited-distribution (student/hobbyist) account**. No government
  ID is needed, it allows up to 20 devices, and you register your app's package name and signing key.
- For wider sharing: a full developer verification (ID required). Verification is about who you are, not app review,
  so the app does **not** need to be on the Play Store.
- Either way, **decide on your final `applicationId` (A2) and signing key (I2) before registering**. They're tied
  together.
- Distribution: GitHub Releases, plus **Obtainium** (users add your GitHub repo URL and get updates automatically).
  Consider an F-Droid-style repo later.

### K4. Brand name inside translations
"Kotatsu" appears in strings in **35 languages** (`app/src/main/res/values*/strings.xml`), e.g. "Ask for password when
starting Kotatsu" and "Please select a proper Kotatsu backup file". After A2 run, from the repo root:
```bash
sed -i 's/Kotatsu/Monogatari/g' app/src/main/res/values*/strings.xml
grep -rn "kotatsu://" app/src/main/res/values*/strings.xml   # change these links to monogatari:// as in A3
```
Keep `app_name` `translatable="false"`. To avoid it happening again, use `%1$s` with `@string/app_name` in new
strings.

### K5. Import backups from Mihon/Tachiyomi, Mangayomi and Kotatsu
Makes switching to your app painless, including on a new phone.
- **Kotatsu / Kotatsu-Redo backups:** already work (same format), as long as A1 didn't rename sources (see K6). Keep
  the "unsupported backup" check (`unsupported_backup_message`) accepting them.
- **Mihon/Tachiyomi `.tachibk`:** gzip plus protobuf. Copy the backup model classes from Mihon (Apache-2.0,
  `github.com/mihonapp/mihon`: look for `BackupManga`, `BackupChapter`, `BackupCategory`, `BackupHistory`,
  `BackupSource`; they use `kotlinx.serialization.protobuf` with `@ProtoNumber`). Map each entry: if the Mihon
  extension is installed (B2), the source maps one-to-one by `sourceId`. Otherwise find a built-in parser whose domain
  matches `BackupSource.name` or the manga URL host (see the open-source `nekotatsu` converter for a mapping approach),
  and if nothing matches, put the title in a "Needs migration" category. Restore read chapters, progress, categories
  and dates.
- **Mangayomi:** its backup is JSON. Import library, categories, history and read chapters the same way.
- Add **Export → Mihon format** too, so users never feel locked in.

### K6. Source renames when switching parser libraries
Library entries are tied to a source's enum **name** (`core/model/MangaSource.kt` line 45:
`MangaParserSource.entries.forEach { if (it.name == name) ... }`). If the redo library renamed or removed a source,
those titles become "Unknown source". Add `core/model/SourceAliases.kt` with `mapOf("OLD_NAME" to "NEW_NAME")` and
check it in `MangaSource(name)` before giving up. Fill it from the redo repo's commit history (search for "rename").
Titles that still resolve to `UnknownMangaSource` should show a **Migrate** button rather than an error.

### K7. Privacy defaults
- Discord status: `AppSettings.kt` line 544 `isDiscordRpcSkipNsfw` defaults to `false`, so adult titles show up on your
  Discord profile. Default it to `true`, and add "Hide title, show only 'Reading manga'" as an option.
- DNS-over-HTTPS: `AppSettings.kt` line 457 defaults to `DoHProvider.NONE`. Many ISPs and countries block manga sites
  through DNS. Default to Cloudflare (or ask during first-run setup); it also hides your reading sites from the ISP.
- Image proxies (`res/values/arrays.xml`: `wsrv.nl`, `0ms.dev`): off by default, which is good. Add a one-line warning
  in the setting that the proxy service sees every page you read.
- Backups contain your full reading history in plain text. See F2's encryption option, and default it to on for
  cloud-folder sync (F3).

### K8. Crash reporting you can actually receive
After removing Kotatsu's crash server (A3), replace it with a "Report on GitHub" button in the ACRA dialog that opens:
`https://github.com/coolchan03/monogatari/issues/new?title=<urlencoded>&body=<urlencoded stack trace + app version + Android version + source name>`.
Truncate the body to about 6,000 characters. No server needed, and you get reports where you already work. Strip
manga titles and URLs if the source is NSFW.

### K9. Update checks that don't hammer sites (battery and fewer bans)
With hundreds of extension sources, the update tracker (`tracker/work/TrackWorker.kt`, scheduled around line 288) can
hit the same site dozens of times per run and get you rate-limited or IP-banned.
- Limit concurrency **per source** (one request at a time per host, ~1 s apart); several sources can still run in
  parallel.
- **Smart intervals:** skip titles with status COMPLETED or CANCELLED, and check titles less often if they haven't
  updated in a long time (e.g. not updated in 60 days → check weekly). Mihon does this.
- Wi-Fi-only (`KEY_TRACKER_WIFI_ONLY`) and "only while charging" options in the first-run setup.

### K10. Cross-media links: the feature that makes this better than Mangayomi
Every title in the library gets linked to its AniList entry. The scrobbling code already searches AniList
(`scrobbling/anilist/`), so match automatically by title and alternative titles. Then use AniList's `relations` to
show buttons on the details screen:
- "**Watch the anime**": searches your video sources (YouTube, Aniyomi/CloudStream) for the adaptation.
- "**Read the light novel / original novel**": searches your novel sources.
- "**Sequel / prequel / side story**": opens or searches it.
Also pull the AniList score, tags, banner image and a high-resolution cover when the source's cover is blurry.

### K11. Release calendar ("Upcoming")
The tracker already stores chapter dates. Estimate each ongoing title's update rhythm (median gap between the last
5 chapters) and show a calendar: "Expected this week: 7 titles". Mihon has this; it's a nice home-screen widget too.

### K12. Export your reading data
- CSV export of library plus stats (title, source, status, chapters read, time spent, rating, date added). Handy for
  spreadsheets.
- Novels and books: export to **Goodreads / StoryGraph CSV** format.
- Manga and anime: AniList/MAL already sync (scrobbling). Add "Push my whole library to AniList" as a one-time action.

### K13. Licence housekeeping (GPL-3.0)
- Keep `LICENSE` (GPL-3.0). Add a `NOTICE` file crediting Kotatsu (GPL-3.0), Kototoro and Mihon (Apache-2.0, if code
  is copied), NewPipe/NewPipeExtractor (GPL-3.0) and LNReader (MIT).
- Keep the original copyright lines in copied files, and add a line saying you modified them. The GPL requires
  "prominent notices stating that you modified it".
- An "Open-source licences" screen under About. GPL requires that whoever gets the APK can get the source, and a
  public GitHub repo covers that.

### K14. Release smoke test (do this before every release)
On a real phone, using a **release** build:
1. Fresh install → first-run setup → search "Given" (a popular BL title) → results from at least 3 sources.
2. Open a title → read 3 chapters → progress and time stats recorded → kill app → reopen → resumes correctly.
3. Install a Mihon extension → read a chapter (catches K1).
4. Play a YouTube video and an Internet Archive video; background audio for LibriVox.
5. Back up → uninstall → install → restore → library, progress, stats and source domains are all back (F1/F2).
6. Two devices with the sync folder (F3): read on one, then the other shows the progress within one sync cycle.
7. Airplane mode → downloaded chapters still open.
8. Rotate the phone during search, reading and video (catches state-loss bugs like D1).

---

## Part L: Generated covers for things without a thumbnail

### The problem
Books (Gutenberg, Internet Archive, AO3, Wattpad, local EPUB/TXT), audiobooks, many web novels and some videos have
**no cover image**. Today Kotatsu shows a **plain grey box** (`fallbackDrawable` in `image/ui/CoverImageView.kt`
~line 34), or a reddish box with an error message when loading fails. A library full of those looks broken.

### The design
Instead of shipping 10 image files, **draw** the cover in code. That gives you:
- **10 pattern styles** × **12 colour palettes** = **120 different looks**, with the **title written on the cover**
  and the author underneath.
- The look is picked from a hash of the title, so **the same book always gets the same cover** (on every device, after
  every restore, and across sources). Different books get different covers, so a shelf of 50 coverless books looks
  like a colourful bookshelf instead of 50 copies of the same placeholder.
- It's sharp at any size (grid, list, widget, notification), works offline, and adds zero APK size.
- A small corner badge shows the kind: 📖 book, 🎧 audio, 🎬 video.

The styles are classic book spine, diagonal stripes, polka dots, diagonal split, corner rings, moon, checker, waves,
double frame and chevrons. Palettes are a mix of dark covers (navy/gold, burgundy/blush, forest/mint, plum/lilac,
teal/peach, charcoal/coral, mustard/ink) and light covers (sakura/rose, sand/brown, sky/navy, sage/olive,
lavender/indigo). Text colour is picked automatically (dark text on light covers, light on dark), and the title sits
on a semi-transparent band so it's always readable over the pattern.

**Cover lookup order** (best to worst):
1. The source's own cover.
2. A metadata cover. For books: Open Library
   `https://openlibrary.org/search.json?title=<t>&author=<a>&limit=1` → `docs[0].cover_i` →
   `https://covers.openlibrary.org/b/id/<cover_i>-L.jpg`. For manga and anime: AniList (K10).
3. **The generated cover.**

### L1. Add the drawable (complete file, paste as-is)
Create `app/src/main/kotlin/org/koitharu/kotatsu/image/ui/generated/GeneratedCoverDrawable.kt`:
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
Notes for ChatGPT:
- `minSdk` is 23, and `StaticLayout.Builder` exists from API 23, so this compiles as-is.
- Don't change `seedOf`. If it changes, every generated cover changes.
- The badge is optional. Pass `"📖"`, `"🎧"` or `"🎬"` once C0 (content kinds) exists, or `null` before that.

### L2. Use it whenever a cover is missing or fails to load
Edit `image/ui/CoverImageView.kt`, function `setImageAsync(coverUrl: String?, manga: Manga?)` (~line 128):
```kotlin
fun setImageAsync(
	coverUrl: String?,
	manga: Manga?,
) {
	val generated = manga?.let {
		GeneratedCoverDrawable(title = it.title, subtitle = it.authors.firstOrNull(), badge = null)
	}
	isGeneratedFallback = generated != null
	enqueueRequest(
		newRequestBuilder()
			.data(coverUrl?.takeUnless { it.isBlank() }) // "" must count as "no cover", not as an error
			.mangaExtra(manga)
			.apply {
				if (generated != null) {
					fallback(generated) // coverUrl is null/blank
					error(generated)    // coverUrl failed to load
				}
			}
			.build(),
	)
}
```
- Add `private var isGeneratedFallback = false` to the class.
- `fallback(Drawable)` and `error(Drawable)` are Coil 3's Android extension functions (`import coil3.request.error`,
  `import coil3.request.fallback`). If they don't resolve, use `fallback(generated.asImage())` with
  `import coil3.asImage`. They must be called **after** `newRequestBuilder()` so they override the default grey
  drawables.
- In the same file, inner class `ErrorForegroundListener.onError` (~line 137) draws an error message over the image.
  At the top of `onError` add `if (isGeneratedFallback) { foreground = null; return }` so the nice cover isn't covered
  by error text.
- The other overload `setImageAsync(coverUrl: String?, source: MangaSource)` has no title. Leave it.

### L3. Everywhere else a cover is drawn outside the app
These build bitmaps themselves and currently fall back to a generic icon. Use the generated cover instead:
- `widget/shelf/ShelfListFactory.kt` ~line 84 and `widget/recent/RecentListFactory.kt` (same pattern): in
  `.onFailure { … }`, replace `views.setImageViewResource(R.id.imageView_cover, R.drawable.ic_placeholder)` with
  `views.setImageViewBitmap(R.id.imageView_cover, GeneratedCoverDrawable(item.title, item.authors.firstOrNull()).toBitmap(coverSize.width, coverSize.height))`.
  `toBitmap` is `androidx.core.graphics.drawable.toBitmap`; use the `coverSize` the factory already has, or 300×450.
- `tracker/work/TrackerNotificationHelper.kt` (new-chapter notifications), `core/os/AppShortcutManager.kt`
  (home-screen shortcuts), `download/ui/worker/DownloadNotificationFactory.kt`: wherever the cover load fails or
  `coverUrl` is empty, use the same `toBitmap` call.
- `scrobbling/discord/ui/DiscordRpc.kt`: Discord needs a URL, not a bitmap, so leave it showing the app icon.

### L4. Settings and extras
- Appearance settings → "Covers": **Generated covers for missing images** (on by default).
- Option **"Use generated covers for all books and novels"**: some people prefer a uniform shelf look, since book
  covers from Internet Archive are often blurry scans.
- Long-press a title → **"Change cover"**: pick from the gallery, use a reader page (Kotatsu already has
  `picker/ui/PageImagePickActivity`), or "Shuffle generated style". Store the shuffle as an extra number mixed into
  the seed, saved per manga, and include it in backups.
- When downloading a coverless book (C3/C1 downloads), save the generated cover as `cover.jpg` inside the CBZ/EPUB
  folder, so other apps and file managers show it too.

### L5. Check
- Add a book from Gutenberg or a local `.txt`: it shows a coloured cover with its title.
- 20 different coverless titles: visibly different covers. The same title from two sources: identical covers.
- Dark and light theme both readable. Very long titles shrink and end with "…" instead of overflowing.
- Turn on airplane mode and clear the image cache: manga with real covers now show their generated cover (with the
  title) instead of an error box.

---

## Part M: Self-managing sources (pre-installed, automatic, no extension headaches)

> **For your branch (Mangayomi JS sources):** everything below applies, and it's *easier* than with APK extensions.
> A JS source is just a `.js` file. There's no Android install prompt and no Android 14 read-only rule, because no
> dex code is loaded. Your branch can already download and install them (`sourcerepo/domain/SourceRepoRepository.kt`,
> `jsext/repo/JsSourceStore.kt`). What's missing is the **automation**:
> - first-run auto-install of the English set (M2)
> - a background `SourceUpdateWorker` that compares index versions and updates silently (M4.1)
> - repair-on-failure (M4.2), rollback (M4.5) and auto-migration (M6)
> - sha256 pinning instead of APK signatures (M3, Q3)
> - the repo-URL privacy move (M1, Q1)
>
> Read "APK" as "JS file" and `filesDir/mihon/` as wherever `JsSourceStore` keeps scripts. Only apply the APK-specific
> steps if you also add Mihon extensions (B2).

**Goal:** you never see the word "extension". First launch → sources are already there. Sites that change their
domain or break get fixed in the background. Dead sources quietly disappear, and your library titles move to a
working source automatically.

### M0. How it works (big picture)
```
Repo index (JSON)  ──daily──▶  Source Manager  ──downloads──▶  private folder in the app (files/mihon/, files/aniyomi/)
                                    │                                        │
                                    ├─ verifies signature + hash             ├─ loaded with ChildFirstPathClassLoader
                                    ├─ installs/updates silently             │   (no Android install prompt)
                                    └─ health monitor (B4) ◀── failures ─────┘
```
- Android normally asks the user to confirm every APK install. **Private loading avoids that**: the extension file is
  never installed as an app. It's downloaded into the app's own storage and its code is loaded directly. That's what
  makes fully automatic install and update possible.
- Kototoro's `MihonExtensionLoader` already loads APKs from the private `mihon` folder (`LocalApkExtensionSupport`).
  The Source Manager only has to **put files there** and **ask the loader to reload**.

### M1. Where the repo list comes from (and keeping your public repo safe)
Kotatsu was shut down partly because a publisher (Kakao) threatened its developers. Publishing an app whose **public
source code** hard-codes piracy repos and auto-installs them is the kind of thing that attracts takedowns. Keep the
automation but keep the URLs out of the public code:
1. **Private build config.** Put repo URLs in `local.properties` (already git-ignored):
   `monogatari.repos=https://…/index.min.json,https://…/index.min.json`. In `app/build.gradle(.kts)` read it with the same
   pattern as `tg_backup_bot_token` and expose it as `BuildConfig.DEFAULT_REPOS`, a comma-separated string. **Your**
   builds come fully pre-configured; the public GitHub code contains no URLs.
   For GitHub Actions builds (I1/I2), store the value as a repository secret and write it into `local.properties`
   in a workflow step before building.
2. **Setup link, as a backup option.** Support `monogatari://setup?repos=<urlencoded list>`. Tapping such a link (or
   scanning a QR code) on a new phone configures everything in one go. Handle it in the manifest `<intent-filter>`
   for the `monogatari` scheme (A3).
3. **Legal sources need neither.** YouTube/NewPipe, Internet Archive, Gutenberg, LibriVox, MangaDex and so on are
   built in (B5/B7), so the app is useful even with zero repos.

### M2. First-run auto-install
New `sources/manager/SourceManager.kt` (`@Singleton`, Hilt), with `SourceRepoClient.kt` and `SourceInstaller.kt`:
1. On first launch, after the welcome questions (languages, content level D4, hidden tags D3), fetch each repo's
   index. Mihon/Aniyomi format: `[{ "name", "pkg", "apk", "lang", "code", "version", "nsfw", "sources":[{"id","name","lang","baseUrl"}] }]`.
   Download URL: `<index base>/apk/<apk>`.
2. Select automatically:
   - `lang` in the user's languages (default `en`) **plus** `all`/multi-language extensions
     (where `lang == "all"`), restricted to sources whose own `lang` includes English.
   - `nsfw == 1` only if the content level allows it.
   - **Skip** extensions that a working built-in parser already covers (same `baseUrl` host); see M5.
3. Install them in the background, 4 at a time, with a small progress card on the Library screen ("Setting up
   sources… 37/112"). The app is usable during this; built-in sources work immediately.
4. Per-extension install (`SourceInstaller.install(entry)`):
   1. Download to `cacheDir/ext-tmp/<pkg>.apk`.
   2. **Verify** (see M3). If verification fails, delete the file and skip it.
   3. Move it atomically into `filesDir/mihon/<pkg>.apk` (Aniyomi video extensions go to `filesDir/aniyomi/`).
      Write to `<pkg>.apk.new`, then `renameTo`.
   4. **Android 14+ requirement:** dynamically loaded code must be read-only, or loading throws
      `SecurityException` (this app targets SDK 36). Call `file.setReadOnly()` after the rename and before loading.
   5. Tell the extension manager to reload that one package (`MihonExtensionManager`; for a single package use
      `MihonExtensionLoader.loadExtension(context, pkgName)`).

**Lazy loading (keeps startup fast with hundreds of sources):** don't instantiate every extension at app start. The
repo index already contains each source's id, name, language and base URL, which is enough to list sources in Browse
and global search. Create a placeholder source from the index entry and load the extension's code only the first time
that source is actually used. Kototoro loads everything up front, so this is a change to how
`GetMihonSourcesUseCase` builds its list.

### M3. Security: these files run code inside your app
- **Signature check.** Each APK's signing certificate SHA-256 must match the repo's key. Mihon-style repos publish it
  as `signingKeyFingerprint` in a `repo.json` next to `index.min.json`. Get the APK's certificate with
  `PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` and compare. On first contact, store the
  repo's fingerprint (trust on first use). If it ever changes, stop auto-updating that repo and show a warning.
- **Version sanity.** Never install a lower `code` than the current one (prevents downgrade attacks).
- Extension library version must be in range (Kototoro checks `LIB_VERSION_MIN 1.2` to `LIB_VERSION_MAX 1.9`). Skip
  anything outside it rather than crashing.
- Only HTTPS repo URLs.

### M4. Automatic updates and self-healing
1. **Scheduled check.** WorkManager periodic job (`SourceUpdateWorker`) every 12 h, network required, plus a check on
   app start if the last one was more than 6 h ago. For each repo: fetch the index; for each installed package whose
   index `code` > installed `versionCode`, install it with the M2.4 steps; reload. No notification. Just a quiet line
   on the Sources screen: "4 sources updated today".
2. **Repair on failure.** Hook into the health monitor (B4). When a source fails 3 times in a row, which is often
   because the site moved, immediately re-fetch its repo index (bypassing the 12 h timer) and update if a newer
   version exists, then retry the request once. Most "site changed its URL" breakages are fixed this way within
   minutes of the extension maintainers shipping a fix, without you noticing.
3. **Removed from repo = dead site.** If a package disappears from its repo index, mark its sources as retired: hide
   them from Browse and search, and run M6 for library titles on them. Delete the APK after 30 days.
4. **New sources appear automatically.** When an index gains a new English extension, install it (same filters as
   M2.2) and add it to the "New sources" badge Kotatsu already has in Explore.
5. **Rollback.** Keep the previous APK as `<pkg>.apk.prev`. If an updated version fails its first 3 requests while
   the old one had been healthy, swap back and stop auto-updating that package for 48 h.

### M5. One source list (merge built-in parsers and extensions)
Kototoro and Monogatari can have the **same site twice**: once as a built-in parser and once as an extension. Show it
once:
- Group by normalized host of the source's base URL (strip `www.`, `m.`, TLD changes are fine to ignore).
- Each group shows as **one** source in Browse and search. Internally it keeps the list of implementations.
- Requests go to the implementation with the best health (B4), and **fail over** to the next on error.
- Library entries keep pointing at the implementation they were added from. M6 handles moving them.
- Never show "Mihon", "Aniyomi", "Kotatsu parser" etc. in the normal UI. Put them only in
  Settings → Advanced → Source details, for debugging.

### M6. Auto-migrate library titles off dead sources
When a source is retired (M4.3) or unhealthy for 7+ days (B4):
1. For each library title on it, search the same title on healthy sources using the alternatives matcher
   (`alternatives/domain/`; matches by title and alternative titles). Prefer sources in the same language with the
   most chapters.
2. **Automatically** switch the title to the best match (setting "Auto-move titles off broken sources", default on).
   Keep categories, read-chapter marks (match chapters by number) and stats, and record the old source so it can be
   undone.
3. Show one summary: "Moved 6 titles from SiteX (offline) to SiteY. Undo". Ambiguous matches (two candidates with
   similar scores) go into a "Needs your choice" list instead of moving automatically.

### M7. The Sources screen (what you actually see)
- **Sources** tab in Browse: one list, grouped by language, each row with a status dot (green = working, yellow =
  slow or updating, red = broken with auto-repair pending) and a kind icon (📚 manga, 📖 novel, 🎬 video, 🎧 audio).
- Toggle on/off and pin. That's it, with no install, update or uninstall buttons.
- Settings → Sources → "Check for source updates now", "Re-run source setup" and "Repos" (advanced: paste URLs or
  use a setup link).
- Backup includes enabled/pinned sources and the repo list (F2). After a restore on a new phone, the Source Manager
  reinstalls everything automatically (same as first run).

### M8. YouTube and other built-ins in the same list
NewPipe's YouTube, PeerTube, SoundCloud and Bandcamp (B7), and the legal sources in B5, appear in the same Sources
list with the same status dots. They don't need repos or updates through the Source Manager. They update with the
app, so when NewPipeExtractor needs a fix (YouTube changes often), ship an app update. The in-app updater (A2/I2)
or Obtainium (K3) makes that automatic too.

### M9. Check
- Fresh install with `DEFAULT_REPOS` set: after first-run setup, Browse shows a long English source list without you
  touching any install button, and no Android "Install app?" dialogs appear.
- Bump an extension in a test repo: it updates within one `SourceUpdateWorker` run, or immediately after 3 failures.
- Remove an extension from the test repo: its sources disappear, and library titles on it are moved (or listed under
  "Needs your choice").
- Tamper with an APK's bytes in the test repo: install is refused (signature mismatch).
- Release build (K1 rules) on Android 14+: extensions load (read-only file requirement satisfied).
- Cold start with 300+ sources is still fast (lazy loading).

---

## Part N: AI translation (inside manga pages, titles, descriptions, novels, subtitles)

### What Kototoro already has (keep it)
Kototoro's reader already translates **inside manga pages**. The code is in
`org/skepsun/kototoro/reader/translate/`:
- **Finds speech bubbles and text areas:** `ComicTextDetectorOnnx.kt`, `BubbleReaderTextDetector.kt`,
  `OnnxBubbleDetectorEngine.kt`, `DefaultDbNetTextDetector.kt`.
- **Reads the text (OCR):**
  - `MangaOcrReaderTextRecognizer.kt` (manga-ocr, best for Japanese)
  - `PaddleReaderOcrEngine.kt` (good for Chinese and Korean)
  - `MlKitReaderOcrEngine.kt` (Google ML Kit, on-device)
  - `BaberuOcrReaderTextRecognizer.kt`
- **Translates:**
  - `OnnxReaderTranslationEngine.kt` (offline model on the phone)
  - `GeminiEndToEndTranslator.kt` (Google Gemini reads the image and translates in one step)
  - `TranslationApiProviderCatalog.kt` (other online AI and translation APIs)
- **Draws the translation back into the bubbles:** `ReaderBubbleRenderCoordinator.kt`, `ReaderTranslationRenderStyle.kt`.
- **Caches results** (`data/ReaderTranslationTextCache.kt`) and downloads models on demand
  (`data/OnnxModelManager.kt`, `data/PaddleModelManager.kt`).

So **page translation is done if you fork Kototoro**. If you stay on Kotatsu, copy the whole
`reader/translate/` folder plus its model files and dependencies (ONNX Runtime, Paddle, ML Kit). It's a big port, so
do it after C1/C3. What's missing is everything **outside** the page.

### N1. Make page translation effortless
1. **Per-series auto-translate.** On the details screen: "Translate this series → English: Always / Ask / Never".
   Stored per manga, backed up (F2). When "Always", the reader translates every page without a button press.
2. **Default to on for raws.** If a source's language isn't English (Korean, Chinese and Japanese raw sites are where
   new BL chapters appear first), default the series to "Always". This turns raw sources into **early English
   chapters**. Suggest it in the first-run setup: "Also show Korean/Chinese/Japanese sources and translate them
   automatically?"
3. **Translate ahead.** While you read chapter N, translate chapter N+1 in the background (Wi-Fi only by default),
   so pages appear already translated with no waiting. Use the same prefetch trigger as `reader/domain/PageLoader.kt`.
4. **Translate downloads.** When downloading a chapter of an "Always translate" series, store the translated text
   with it, so offline reading is translated too. Option: save the rendered translated pages into the CBZ.
5. **Glossary per series.** Character names, places and terms ("Hyung", "Senpai", a character's name) shown
   consistently every time. A small editor on the details screen; feed it to the AI providers as part of the prompt
   ("Use these translations: 김민수 → Kim Minsu; keep honorifics"). Store it per manga and back it up.
   This fixes the most annoying machine-translation problem: names changing from page to page.
6. **Honorifics option:** "Keep honorifics (-kun, hyung, gege)" on or off, passed into the prompt.
7. **Comfort:**
   - Long-press a bubble to see the original text and the translation, with a "fix this translation" edit that is
     saved to the glossary.
   - Tap and hold the page to show the original image.
   - Option to skip sound effects.
   - A readable comic font for the overlay. "Comic Neue" and "Bangers" are free (SIL Open Font License) and can be
     bundled in `res/font/`.
8. **Content filters.** Cloud AI providers, Gemini especially, often **refuse or blank out explicit content**, which
   matters for yaoi. When a cloud translation comes back empty or blocked, automatically fall back to the offline
   on-device engine for that page, and show a small "translated offline" note. Let the user choose "Offline only" for
   adult series.

### N2. Translate titles, descriptions, tags and chapter names (new)
Where: library and browse cards, details screen (title, description, genres), chapter list, search results,
notifications, widgets.

**Prefer a real English title before translating anything:**
1. If the source gives alternative titles (`Manga.altTitles`), use the first one written in Latin script.
2. Otherwise, if the title is linked to AniList (K10), use AniList's `title.english`, then `title.romaji`.
3. Only then machine-translate.

**Translation engine for short text:**
- Default: **Google ML Kit on-device translation**. Free, offline, private, fast for short strings, but each language
  model is a ~30 MB one-time download. Dependencies: `com.google.mlkit:translate` and `com.google.mlkit:language-id`
  (check Google's ML Kit release notes for current versions). Kototoro already includes ML Kit for OCR.
- Optional: the same AI provider the user configured for pages (better quality, needs a key and internet).

**Implementation (`translation/` package, new):**
1. `TextLanguageDetector`: quick script check first (Hangul `가-힯`, kana `぀-ヿ`, Han
   `一-鿿`, Cyrillic, etc.); use ML Kit language-id only when the script is ambiguous.
2. `MetadataTranslator` with a Room cache table
   `translations(id TEXT PRIMARY KEY /* sha1(sourceLang + targetLang + text) */, text TEXT, engine TEXT, created_at INTEGER)`.
   Batch the strings visible on screen, debounce 300 ms, translate at most 4 at a time, then cache. A list of 50
   cards never re-translates on scroll.
3. **Never overwrite `manga.title`.** Store translations separately. The original is what matching, sync, migration
   (M6) and backups rely on. Display-time only.
4. UI: translated text shows a tiny 🌐 icon; tap it to toggle the original. Settings → Translation:
   - "Translate titles: Off / Only non-English / Always"
   - "Translate descriptions" (on)
   - "Translate tags" (on). Combine with D2 synonyms so a translated Korean tag "BL" maps to your Boys' Love group.
   - "Show original title below"
   - Target language (default English)
5. Translation cache is **not** backed up. It's cheap to regenerate and keeps backups small.

### N3. Search across languages
Type in English, search raw sources too:
- When global search includes non-English sources, translate the query into each source's language (ML Kit, cached),
  run both the original and translated query, and merge results.
- Translate result titles back to English (N2) so you can read the results list.
- Example: searching "my secretary" also finds the Korean original on Korean sites, and its card shows the English
  title.

### N4. Novels: translate chapters
Text is much easier than images, and it **unlocks raw Chinese danmei and Korean BL web novels**:
- Novel reader (C1) → "Translate chapter" / per-series "Always translate".
- Split into paragraph batches (~1,500 characters), send them with the series glossary (N1.5), cache per paragraph,
  and show the original paragraph on long-press.
- Translate the next chapter ahead (N1.3).
- Text-to-speech (C1) then reads the translated text aloud.

### N5. Video: translate subtitles
- YouTube via NewPipe (B7) provides subtitles, including auto-generated ones (`StreamInfo.subtitles`). Video extensions
  provide `.srt`/`.vtt` tracks.
- Option "Translate subtitles to English": translate each cue (batch ~50 cues per request, cached), and feed Media3 a
  generated `.vtt` track named "English (auto)".

### N6. Keys, cost and privacy
- **Offline (ML Kit / ONNX) is the default** for everything: free, private, and works for explicit content.
- Cloud AI keys (Gemini, OpenAI-compatible, DeepL) are entered by the user and stored with
  `EncryptedSharedPreferences` (`androidx.security:security-crypto`), **never** in backups or crash reports.
- Show a monthly usage counter per provider (characters or images sent) so there are no surprise bills. Add a
  "Cloud only on Wi-Fi" option.
- Never send pages from "private categories" (J) to cloud providers.

### N7. Check
- A Korean raw BL chapter with "Always translate": bubbles appear in English, and names stay consistent with the
  glossary across the chapter.
- An explicit page with a cloud provider: falls back to offline instead of showing blank bubbles.
- Browse a Korean source: titles and descriptions show in English with 🌐; tap shows the original; the title in
  backups is still the original.
- Search "boss" with a Korean source enabled: Korean results appear with English titles.
- Airplane mode: cached translations still show; new ones use the offline engine.

---

## Part O: What else to take from Mangayomi

Mangayomi (`github.com/kodjodevf/mangayomi`, Apache-2.0) is Flutter/Dart with some Rust. Its extensions live in
`github.com/kodjodevf/mangayomi-extensions`. You can't paste Dart into Kotlin: ask ChatGPT to *translate* one Dart
function at a time into Kotlin, keep Mangayomi's licence header, and add "Ported from Mangayomi <file>" (K13).
To find code in Mangayomi, use GitHub's search box on that repo with the function name.

### O1. Finish the missing JS host functions (more extensions start working)
`docs/JS_EXTENSIONS.md` on your branch lists what's **not implemented yet**: legacy video-host extractor calls,
`evaluateJavascriptViaWebview`, `unpackJs*`, `decryptAES*`, `parseDates`, and XPath attribute results. Each missing
function makes every extension that uses it fail.
1. **Measure first.** Your diagnostics (`sourcerepo/ui/JsSourceDiagnostics.kt`) already categorize failures. Add a
   category "missing host function: <name>" and run the full sweep. Implement the functions in order of how many
   sources need them.
2. `decryptAES*`: CryptoJS-compatible AES (OpenSSL "Salted__" format, key and IV from `EVP_BytesToKey` with MD5).
   Use `javax.crypto` in a new `jsext/JsCryptoBridge.kt`. ChatGPT knows this algorithm well; give it Mangayomi's Dart
   version as the reference.
3. `unpackJs*`: Dean Edwards' "p,a,c,k,e,d" unpacker. Port Mangayomi's Dart unpacker to Kotlin (a short function).
   Many video hosts need it.
4. `parseDates`: port Mangayomi's date-parsing helper (relative dates like "3 days ago" plus format strings). Without
   it, chapter dates are missing, and sorting by date and update tracking suffer.
5. `evaluateJavascriptViaWebview`: run the given script in a hidden `WebView` (reuse
   `browser/cloudflare/CloudFlareClient` patterns) and return the result through `evaluateJavascript`. Time out after
   30 s.
6. XPath attributes: Jsoup has `selectXpath()`. Return attribute values when the expression ends with `/@attr`.
Add a unit test per function with a captured example from a real extension, like the existing `wordrain69.js` test.

### O2. More video extractors
Mangayomi has a folder of Dart video-host extractors (search the repo for `extractor`; files are named after hosts,
e.g. dood, streamtape, mp4upload, filemoon, voe, okru). You've already ported Sendvid (`ab56a47`). Port the next ones
**by how often your installed anime sources hit them**: log the host name in `JsVideoExtractorBridge` whenever an
extractor is missing, then port the top five. Each port is one ChatGPT task: give it the Dart file and your Sendvid
port as the example.

### O3. Turn Dart-only sources into JS
Dart extensions can never run in your app. For the ones you care about (your branch already matches Dart sources to
alternatives in `jsext/repo/DartSourceCompatibility.kt`), have ChatGPT **rewrite the Dart source as a JS source**
following the `MProvider` contract (copy the structure of a working JS extension). Publish it in
`coolchan03/Best-BL-Mangayomi-Extensions`. It then works in your app *and* in Mangayomi, and M4 auto-updates it.

### O4. Backup import from Mangayomi
Covered in K5. Mangayomi backups are JSON. Map its manga, chapters, categories, history and tracking into
`BackupRepository` restore.

### O5. Mangayomi UI patterns to copy (design, not code)
- Library tabs per media type (Manga / Anime / Novel) with categories as sub-tabs (G, C4).
- "Updates" grouped by day with a big "Update library" button.
- Long-press multi-select with a bottom action bar (mark read, download, delete, move category).
- Source pages: Popular / Latest / Search tabs on one screen.
- Extension list (in your app, the Sources screen of M7) with language headers.
Screenshot Mangayomi on your phone and give the screenshots to ChatGPT along with the matching Kotatsu layout file.

---

## Suggested order (for your branch `claude/mangayomi-kotatsu-features-fb46y2`)

Each line is one or a few sessions. Use one git branch per line and merge when the build is green.

1. **Part 0 "How to avoid losing anything":** merge PR #1 into your default branch, and tag `cb5e722`.
2. **Ship-ability (P0):** A2 (app identity, before anyone relies on the current id) → K1 (R8 rules) plus an
   `assembleRelease` step in CI → I2 (keystore secrets) → first signed release → **K14 smoke test on your phone**
   (fold it into your guided QA screen).
3. **Safety:** Q1 (repo URLs out of public code), Q2 (per-source cookies), Q3 (hash pinning), K2 (SSL bypass),
   A3 leftovers and the MD5 PIN, K7 privacy defaults, B1 (mirror switching on).
4. **Part M (your biggest headache):** sources install, update, repair and migrate themselves.
5. **O1 → O2** (missing JS host functions and video extractors, so more sources work), then **O3** for favourites.
6. **D1 leftovers, D2** (BL synonyms), **D3, D4**.
7. **F1 → F2** (newest-wins restore, back up JS sources and settings) → **F3** (folder sync).
8. **B7** (YouTube/NewPipe) → **C3** extras (background audio, PiP, resume).
9. **E1–E4** (reading time and stats).
10. **G + L** (Mangayomi look, unify the Books shelf with the library, generated covers).
11. **N** (translation: port Kototoro's `reader/translate/`, then titles, search and novels).
12. As you like: B2 (Mihon extensions), B3, B5 leftovers, C1 extras (TTS), J, K5, K8–K12, Q4, Q5, O4, O5.
