# Ported and adapted components (#83)

For every piece of code or format that originates outside Monogatari: where it comes from, the revision it was taken at (or the
pinned dependency), its licence, what Monogatari changed, and which tests cover it. "Revision" means what is actually in this
repository, not the upstream HEAD (see `UPSTREAM_REVISION_LEDGER.md` for HEADs).

| Component | Upstream and revision | Licence | Local modifications | Tests |
|---|---|---|---|---|
| Android app base (reader, library, history, tracker, settings, DI, Room schema) | Kotatsu-Redo (fork of Kotatsu). The exact fork commit was never recorded; the package name `org.koitharu.kotatsu` is kept on purpose so upstream patches stay mergeable. | GPL-3.0 | Monogatari chrome (`core/ui/MonogatariChrome`), media types (`core/media`), unified library, Discover, News, Test Center, translation, large-screen layouts, sponsor prompt removed | `reader/`, `stats/`, `tracker/`, `search/`, `backups/` suites |
| Manga source parsers | `kotatsu-parsers-redo` (installed as `com.github.clquwu:kotatsu-parsers-redo`), pinned by JitPack commit `b3c0000245` in `gradle/libs.versions.toml` (`parsers`) | GPL-3.0 | None to the library; Monogatari wraps it (`core/parser`, source grouping, health, retirement) | `sourcehealth/`, `explore/data/DefaultSourcePresetsTest` |
| Mangayomi JavaScript extension host (`jsext/`) | Written for Monogatari to run Mangayomi's extension format unmodified (`kodjodevf/mangayomi`; revision at the time of adaptation not recorded). **Adapted code:** crypto helpers in `JsCrypto.kt` (MBridge/CryptoAES/Deobfuscator) and the Sendvid extractor in `JsVideoExtractorBridge.kt`; both carry an Apache-2.0 header comment. The rest follows Mangayomi's documented behaviour (Dart `http`/`Client`/DOM semantics re-implemented on QuickJS); file-by-file provenance of the other bridges has not been audited, so treat Apache-2.0 attribution as required. | Apache-2.0 | HTTP, DOM, date, unpacker and EPUB bridges, source store with rollback, integrity checks | `jsext/` (16 test classes), `sourcerepo/` |
| QuickJS engine | `quickjs-kt` 1.0.4 (dependency) | MIT | None | via `jsext/` tests |
| YouTube / PeerTube / SoundCloud extraction | `NewPipeExtractor` v0.26.5 (dependency) | GPL-3.0 | `builtin/newpipe/` adapters: search planning, paging, channel feeds, downloader | `builtin/newpipe/` |
| Built-in public sources | Public documented APIs only (Gutendex, Open Library, Internet Archive, LibriVox, AO3, Royal Road, Scribble Hub, Dailymotion, Wikisource/MediaWiki, Odysee/LBRY, Jellyfin REST). Written for Monogatari; no third-party source code copied. | Monogatari (GPL-3.0) | n/a | `builtin/**` (one test class per source) |
| Page translation | ML Kit text recognition (Latin, Japanese, Korean, Chinese), ML Kit Translate and Language ID (dependencies). Architecture inspired by Kototoro (Apache-2.0); **no Kototoro code is copied** (see `NOTICE`). | Google ML Kit terms; Apache-2.0 (reference) | `reader/translate/`: bubble grouping, layout, sidecar record, tap-to-correct viewer | `reader/translate/` |
| Subsampling image view | `KotatsuApp/subsampling-scale-image-view` at `376930523c` (dependency) | Apache-2.0 | None | none (UI) |
| Crash reporting | ACRA 5.13.1 (dependency, guarded lifecycle callbacks) | Apache-2.0 | Startup-safe guards | `core/util/SentryStackTraceParserTest` |
| Foreign backup import | Kotatsu and Mangayomi backup formats (read-only parsers) | n/a (file formats) | `backups/domain/ForeignBackup*`, `MangayomiBackup` | `backups/` |

## What to do when you port something new

1. Add a row above in the same change, with the exact upstream commit or tag and licence.
2. Keep the upstream copyright header in any copied file; add a `Modified for Monogatari:` line describing the change.
3. Add or extend a unit test for the pure logic, and a Test Center step if users can see it.
4. Never copy upstream files over local ones: see the audit gate in `UPSTREAM_REVISION_LEDGER.md`.
