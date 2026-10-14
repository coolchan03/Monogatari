# Monogatari builtin source QA

Thirteen built-in adapters are declared in `builtin/BuiltinSource.kt` and constructed in `builtin/BuiltinRepositoryFactory.kt`. A declared adapter is NOT proof it works online today.

| Media | Sources in current code | Count |
|---|---|---:|
| Books | Gutenberg, Open Library, AO3, Royal Road, Scribble Hub, Internet Archive Books | 6 |
| Video | YouTube, PeerTube, Internet Archive Video | 3 |
| Audio | LibriVox, SoundCloud, Internet Archive Audio | 3 |
| Comics | Internet Archive Comics | 1 |

For **every** built-in source, test source classification, public search, metadata, covers, language, date when known, reading/playback, permitted access fallback, error handling, rate limits and mobile/tablet UI. Distinguish source metadata, valid titles and actual playable/readable content. Never count a proposed source as installed before verifying its adapter and provider permissions.

Additional candidate sources #38-61 appear in [the roadmap tracker](ROADMAP_88_TRACKER.md), and imported/upstream code revisions in [the revision ledger](UPSTREAM_REVISION_LEDGER.md).
