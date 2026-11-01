# Monogatari future-version roadmap (v1.0 → v2.0)

Created 2026-10-18. This file keeps the post-1.0 plans in one place so they are not lost between chats.
It **adds to** the detailed records already in the repository and does not replace them; follow the
links for the full notes.

Rules:

- **v1.0 comes first.** Do not start major future-version development until the 83-item v1.0 roadmap
  (`ROADMAP_83_TRACKER.md`) is complete in code, verified, and released as the signed v1.0.0 APK.
- **Advanced synchronization is v1.1 work**, not a v1.0 release blocker. v1.0 keeps the manual,
  foreground-only Nearby transfer that already exists.
- **Maintenance releases** (v1.0.1, v1.0.2, … and v1.1.1 etc.) carry bug, security, performance and
  compatibility fixes only, including source/extension repairs found after release. They never hold
  feature work back from the next minor version, and they never break backups or source IDs.
- Monogatari stays an **entertainment** app (manga/manhwa/manhua/webtoons/BL, books/novels/fanfiction,
  anime/video/personal servers, audio/podcasts, entertainment news). No education features. The
  Windows/PWA project stays abandoned; it is not part of this roadmap.
- Status words: **Exists** = code exists today (possibly partial; it says so); **Groundwork** = helper
  code or design exists but no user feature; **Planned** = not started.

## Release overview

| Version | Planned focus |
|---|---|
| v1.0 | Complete and stabilize the current entertainment app, including basic Smart Library Import |
| v1.0.x | Maintenance: bugs, security, performance, compatibility, source repairs |
| v1.1 | Advanced Kotatsu/Mangayomi migration, nearby cross-device synchronization, library and settings transfers |
| v1.2 | Better pairing, selective file transfers, sync conflict handling and phone/tablet continuity |
| v1.3 | Personalized Discover, improved recommendations and richer entertainment browsing |
| v1.4 | Advanced manga translation, readers, subtitles and media playback |
| v1.5 | Expanded themes, customization, organization and optional automation |
| v2.0 | Comprehensive entertainment hub, expanded integrations and self-hosted features |

## v1.0: complete and stabilize (current work)

Scope is the 83-item tracker. Smart Library Import in v1.0 is the **basic** level only:

| Feature | Purpose | Dependencies | Status |
|---|---|---|---|
| Kotatsu backup restore | Bring an existing Kotatsu/Kotatsu-Redo library over unchanged | Upstream backup format | Exists (inherited, Monogatari sections added) |
| Mangayomi JSON backup import | Favourites, categories and progress from Mangayomi; installs missing JS extensions from the user's repositories; never overwrites newer local data | Mangayomi backup schema, JS repos | Exists (`backups/domain/MangayomiImporter.kt`); provider/device test pending |
| Foreign backup preflight and review | Detect Kotatsu ZIP, Mihon/Tachiyomi `.tachibk` and Mangayomi JSON by name and signature; show what can and cannot be imported | none | Exists (`ForeignBackupPreflight.kt`, `ForeignBackupReview*.kt`); Mihon is **detected and integrity-checked only, not decoded** |
| Manual Nearby offline transfer | Foreground, both-sides-confirmed library/settings snapshot and single-file transfer with SHA-256 checks | Google Play services Nearby | Exists (`docs/NEARBY_OFFLINE_SYNC.md`); two-device test pending |
| Manual WebDAV snapshot upload/download | User-triggered backup copy to the user's own WebDAV server | User WebDAV server | Exists as manual activity; never auto-restores |

## v1.1: advanced migration and nearby sync

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Mihon/Tachiyomi `.tachibk` import | Decode the gzip-protobuf backup, map sources by unique host/name, put unmatched titles in a "Needs migration" category | v1.1 | Redacted sample backups, protobuf models, `ForeignSourceMatcher.kt` | Groundwork (preflight + matcher only; see `OVERNIGHT_IMPORTS_v013_PARTIAL.md`) |
| Reversible import journal | Every foreign import can be previewed, applied in one transaction and rolled back | v1.1 | Room migration for a journal table | Planned |
| Advanced Kotatsu/Mangayomi migration | Per-title source re-matching, tracker links, Mangayomi anime/novel progress, history timestamps | v1.1 | Mangayomi importer, alternatives/auto-fix | Groundwork (basic importer exists) |
| Nearby two-way library sync | One session merges both directions instead of reversing roles manually | v1.1 | Existing Nearby protocol, merge policies | Planned (manual one-way exists) |
| Settings transfer profiles | Choose which settings groups travel (appearance, reader, sources, network) | v1.1 | Backup sections | Planned |
| Source and extension list transfer | Send installed JS extensions and repository list with the library | v1.1 | JS source store, repo store | Planned |
| Scheduled delta sync (opt-in) | Periodic sync of only changed records, after battery/privacy policy is defined | v1.1 (may slip to v1.2) | Conflict policy (v1.2), WorkManager | Planned; listed as "next phase" in `NEARBY_OFFLINE_SYNC.md` |

## v1.2: pairing, selective transfers, conflicts and continuity

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Remembered paired devices | Trusted pairs without re-typing codes; revocable | v1.2 | Nearby, keystore-held pairing secret | Planned |
| Selective file transfers | Choose several books/CBZ/audio files or whole downloaded chapters in one transfer | v1.2 | Nearby file transfer, download index | Planned (single-file exists) |
| Bulk downloaded-chapter cache transfer | Move offline manga chapters between devices | v1.2 | Selective transfer, storage checks | Planned (explicitly not in v1.0 backups) |
| Sync conflict handling | Show per-title conflicts (progress, favourites, categories) and let the user choose; never silent overwrite | v1.2 | Merge policies, journal | Planned |
| Phone/tablet continuity | "Continue on other device" handoff of the current chapter/episode/position | v1.2 | Paired devices | Planned |
| WebDAV encrypted credentials and scheduled sync | Keystore-encrypted account, XML compatibility tests, optional schedule | v1.2 | `WebDavAccountStore.kt` | Groundwork (manual transfer exists) |

## v1.3: Discover and recommendations

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Personalized Discover across media | Recommendations from local history/favourites for manga, books, video and audio, on-device | v1.3 | Discover (AniList), history, tags | Groundwork (AniList Discover exists) |
| Cross-media links | "Read the manga / watch the anime / sequel" actions from AniList relations | v1.3 | AniList relation fetch | Groundwork (`CrossMediaRelationPolicy.kt`, no UI) |
| Release calendar | Expected next chapters/episodes from release rhythm | v1.3 | Chapter dates, tracker | Groundwork (`ReleaseRhythmEstimator.kt`, no calendar view) |
| Richer entertainment news | More curated feeds, per-topic digests | v1.3 | News feed | Groundwork (News & Followed Tags exists) |
| Per-title publication-date confidence | Show how sure a year estimate is | v1.3 | Year metadata | Planned (tracker #31 note) |
| Kitsune first-run tips | Short mascot tips on first launch and empty News | v1.3 | Mascot art | Planned (`MASCOT_IDEAS.md` "Not done yet") |

## v1.4: translation, readers, subtitles and playback

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Advanced bubble segmentation and inpainting | Learned bubble masks and true art inpainting instead of edge-colour fill | v1.4 | v1.0 #21/#22 code, on-device model size budget | Groundwork (v1.0 has flood mask + smooth fill) |
| Side-by-side original/translation | Compare pages directly | v1.4 | Translated page viewer | Planned (toggle exists, side-by-side not) |
| Novel TTS upgrades | Highlighted paragraph, sleep timer, background playback, EPUB reader support | v1.4 | Android TextToSpeech, media session | Groundwork (plaintext/HTML reader TTS exists) |
| Video player extras | Persisted per-show speed, auto-next episode, better subtitle styling and track choice | v1.4 | Media3 player | Groundwork (PiP, skip intro and speed exist) |
| Audiobook improvements | Chapters, bookmarks, sleep timer for audiobooks | v1.4 | Media3, podcast player | Groundwork (podcast player exists) |

## v1.5: themes, customization, organization, automation

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Expanded theme presets | More Kotatsu/Mangayomi-inspired presets, per-media accent sets, custom fonts | v1.5 | Accent colour system | Groundwork (presets + accent exist) |
| Library organization | Smart collections (rules by tag/status/source), bulk edit | v1.5 | Collections | Groundwork (manual collections exist) |
| Exports | Save library/history CSV through SAF; Goodreads/StoryGraph mapping | v1.5 | `CsvExportFormatter.kt` | Groundwork (CSV in Stats and Books; no Goodreads mapping) |
| Optional automation | User-defined rules, e.g. auto-download new chapters of a collection on Wi-Fi | v1.5 | Download manager, WorkManager | Planned |
| Crash report attachment | Sanitized, size-limited stack attached to the GitHub issue link | v1.5 (or a maintenance release) | Crash dialog | Groundwork (Report issue button exists) |

## v2.0: comprehensive entertainment hub

| Feature | Purpose | Planned version | Dependencies | Status |
|---|---|---|---|---|
| Expanded self-hosted servers | Komga/Kavita/Audiobookshelf/Emby alongside Jellyfin and Plex | v2.0 | Server APIs, credentials storage | Planned (Jellyfin/Plex exist) |
| Expanded integrations | More trackers and catalogs with documented APIs | v2.0 | Provider terms | Planned |
| Extra source pools | Evaluate Mihon/Aniyomi/CloudStream-style sources only after license, dependency and security review; no blanket imports | v2.0 | Runtime compatibility | Deferred by decision (`BATCH_v014_STEPS_67_74.md` step 73) |
| Wattpad | Only through a verified, permitted integration | v2.0 | Provider permission | Blocked (`MASTER_STEP_STATUS_2026-10-13.md` step 33) |
| Illustrator-quality mascot art | Replace hand-coded vector Kitsune with commissioned art of the same file names | any version | Artist | Planned (`MASCOT_IDEAS.md` "Honest limits") |

## Maintenance-release backlog (v1.0.x)

These are known non-feature items that should ship in maintenance releases rather than wait:

- Source repairs found by the Test Center sweep (code defects only; dead domains are documented, not "fixed").
- GitHub Actions: Node.js 24 transition, `actions/setup-java@v5` everywhere, the `ubuntu-latest` move to Ubuntu 26.
- Any Android/OEM compatibility issue reported from the Moto Razr Ultra 2025 and onn Pro 13.2 tablet tests.

## Draft progress (2026-10-19, branch `claude/monogatari-v1-release-sq1t2k`)

Rough drafts started after v1.0.0 at the owner's request. "Draft" = code exists and has unit tests; not device-tested.

| Feature | Version | Draft status |
|---|---|---|
| Mihon/Tachiyomi `.tachibk` decode + preview + favourites import by exact title | v1.1 | Wired into the backup review screen; reading position moves by chapter number for titles without local history. |
| Settings transfer groups (`SettingsTransferProfile`) | v1.1 | Wired: Nearby receive asks which settings groups to merge (network unticked by default); restore API accepts groups. Normal restore screen not yet. |
| Sync conflict planning (`SyncConflictPlanner`) | v1.2 | Pure core + tests; needs a per-device "last sync" record and UI. |
| Taste profile for personalized Discover (`TasteProfile`) | v1.3 | Pure core + tests; Discover does not use it yet. |
| Side-by-side original/translation | v1.4 | Wired into the translated page viewer. |
| Auto-next episode / chapter | v1.4 | Wired into the media player menu (on by default). |
| EPUB read-aloud with sleep timer, continues into next chapter | v1.4 | Wired into the EPUB reader menu. |
| Smart collections (`SmartCollectionRule`) | v1.5 | Pure core + tests; no UI yet. |
| Komga client (`KomgaApi`) | v2.0 | Request/parse core + tests; not registered as a source. |

## Released in v1.1.0 (2026-10-20)

Mihon/Tachiyomi import with reading position, two-way Nearby sync, settings-group choice in Nearby, smart collections,
Discover "For you", side-by-side translation, auto-next, EPUB read-aloud. Still open for later 1.x: settings-group choice
in the normal restore screen, sync conflict review UI (`SyncConflictPlanner`), remembered pairing and multi-file
transfers (v1.2), release calendar (v1.3), Komga as a source (v2.0).


## Released in v1.2.0 (2026-10-20)

- Discover books / Discover videos pages, Discover as a tab, airing calendar and reminders (v1.3 items pulled forward), search type picker, NewPipe trending kiosks, feed fixes with cache, Nearby history conflict review, settings groups on normal restore, restyled secondary screens.
- Still open: remembered Nearby pairing, multi-file transfers, Komga as a source (v2.0), conflict review for favourites/categories.

## Released in v1.3.0 (2026-10-21)

- Add files (books, comics, local video/audio), Add to library from Discover, 8-item navigation with new tabs and shortcuts, Mangayomi import of extensions/repos/library from the current backup format, Discover shelf timeout fix.
- Still open: remembered Nearby pairing, multi-file transfers, Komga as a source (v2.0), conflict review for favourites/categories, CBR/image-folder import from Add files, encrypted Mangayomi backups.

## Released in v1.4.0 (2026-10-23)

- Settings overhaul, rebuilt Library, AniList + MyAnimeList fallback, themed screens and mascot, opt-in auto-download, backup/import/download safety fixes from the v1.4 audit (docs/MONOGATARI_V1_4_AUDIT_FINDINGS_20261022.md).
- Still open from that audit: child auto-downloads don't inherit the Wi-Fi/charging constraints, a hard storage cap while writing, richer backup category identity than title matching, tests for the Mihon/Mangayomi merge fixes, encrypted Mangayomi backups, auto-download of video.

## Released in v1.4.1 (2026-10-24)

- Library covers every medium (On this device section, Recommended strip), Library replaces On device on the default bar (one-time migration), Discover opens on All.
- Still open: remember the last Discover chip; hard storage cap and constraint inheritance for auto-download; remaining audit items listed under v1.4.0.

## Released in v1.4.2 (2026-10-29)

- Search clean-up: shared media type, in-place chips, honest hint, entry points routed to the main search, Discover remembers its last chip.
- Still open: rename Explore to Sources/Browse, pull-to-refresh on Discover/Recommended, a Continue button in the Library header, aligned card sizes between Discover and Library grids, NewPipe extras (downloads, comments, subscription import).

## Released in v1.4.3 (2026-10-31)

- Nearby sync: Wi-Fi permissions no longer capped at Android 11, prerequisite check (Play services, Bluetooth, Wi-Fi, location, airplane mode, permissions), status-code messages, state fixes, Troubleshooting card.
- Still open: verify Nearby sync end to end on the Razr and the tablet; more book/video sources; vetted Mangayomi anime/novel repository defaults (13 JavaScript-capable links checked on 2026-10-30: 183 runnable extensions); a source picker in search; rename Explore; pull-to-refresh; Library Continue button.

## Released in v1.4.4 (2026-11-01)

- 12 recommended extension repositories seeded once (about 183 runnable extensions), Get more sources bulk install, search type follows the Discover/Library chip.
- Still open: more built-in book/video sources, a source picker in search, verify Nearby sync on the Razr and tablet, rename Explore, pull-to-refresh, Library Continue button.

## Released in v1.5.0 (2026-11-02)

- Sources tab rename, pull-to-refresh, Library Continue button, first-run setup, shared mascot empty states.
- Next batches the owner approved: reading and media (one Continue shelf across media already started; download queue screen, NewPipe downloads/background audio/subscription import, reading stats), sync and safety (Nearby remembered pairing/multi-file/favourites conflicts, backup reminders, restore preview), content (more built-in book/video sources, Komga/Kavita).
