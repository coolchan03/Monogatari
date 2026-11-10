# Source repositories

Settings -> Manga sources -> **Source repositories** lets you add a repository by URL, browse the
sources it offers, and install or update them. A repository is just a JSON file hosted anywhere
over **https** (GitHub Pages, a raw GitHub URL, your own server).

## Index format

An object with a `plugins` list, or a bare list of the same entries
(see [`sample-repo/index.json`](sample-repo/index.json)):

| Field | Required | Notes |
| --- | --- | --- |
| `pkg` (or `package`) | yes | Android package name of the plugin APK |
| `version` (or `versionCode`) | yes | Integer; must match the APK's `versionCode`. Higher = newer |
| `apk` (or `url`) | yes | **https** link to the APK. `http://` entries are ignored |
| `name` | no | Display name; defaults to `pkg` |
| `versionName` | no | Display version; defaults to `version` |
| `type` (or `mediaType`) | no | `manga` (default), `book`/`novel`, `video`/`anime` |
| `sha256` | no | 64 hex chars of the APK. If present, the download is verified before the system installer opens, and a mismatch is discarded |

Invalid entries are skipped individually, so one typo does not hide the rest of the repo.
If several repos list the same `pkg`, the highest `version` wins.

## Publishing a repo

1. Build your plugin APK (a Kotatsu-Redo parser plugin exposing the
   `app.kotatsu.parser.PROVIDE_MANGA` content provider).
2. `sha256sum my-plugin.apk` and put the hash in the entry (recommended).
3. Host `index.json` and the APKs over https and give users the `index.json` URL.
4. To ship an update, raise `version` (and the APK's `versionCode`) and change `apk`/`sha256`.

## Security notes

- Android's installer still verifies that an update is signed by the same key as the installed
  plugin, so a repo cannot silently replace a plugin with one signed by someone else.
- Only add repositories you trust: a plugin runs inside the app's process once installed.
- Plain `http://` repo and APK URLs are refused.

## Mangayomi repositories

Settings -> Source repositories also accepts **Mangayomi-format indexes** (`index.json`,
`novel_index.json`, `anime_index.json`, ...), such as `https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/main/novel_index.json`.
Their **JavaScript** sources can be installed and updated from the same screen; see
[JS_EXTENSIONS.md](JS_EXTENSIONS.md). **Dart** sources cannot run here and are counted but not listed.
Each index file is a separate URL. Mangayomi's official repository currently publishes active manga and novel links while its anime link is commented out, so the app does not hard-code that inactive feed. Compatible third-party Mangayomi-format anime indexes can still be added normally.

## Recommended default repositories

Monogatari ships a built-in list of Mangayomi-format repositories (`sourcerepo/data/RecommendedRepos.kt`,
merged with the build-time `DEFAULT_REPOS`). Each default is offered to an install **once**: the
`seeded` set in the `source_repos` prefs records what was offered, so a repository you remove is never
re-added, while new defaults reach existing installs on the next release.

- Manga: kodjodevf, m2k3a (GitHub Pages mirror), Swakshan, Schnitzel5 `index.json`
- Anime: m2k3a, Swakshan, Schnitzel5, gato404 (kegareta-sauces) `anime_index.json`
- Novel: Schnitzel5, m2k3a, kodjodevf, Swakshan `novel_index.json`

The m2k3a.github.io novel index is byte-identical to the raw m2k3a one, so only one copy is listed.
Keiyoushi and Secozzi are **not** included: their Mihon/Aniyomi indexes are Android APK extensions
(only "Outdated app" stubs here) that Monogatari cannot run.

**Get more sources** (Source repositories screen) installs runnable JavaScript sources in one step for
the chosen types (anime and video, book and novel, manga), English and multilingual (`all`) by
default, with optional "Include other languages" and "Include adult sources". Sources that need an
external torrent or debrid account (name contains Torrent or Debrid) are left out of the bulk install
and can still be installed one by one. A result summary (installed per type, failures) stays on the
screen, and failures are written to the Source log.

## More non-manga sources and integrations

- **media.ccc.de** (`builtin:mediaccc`, video) and **Bandcamp** (`builtin:bandcamp`, audio) run on the
  bundled NewPipeExtractor (`ServiceList.MediaCCC` / `ServiceList.Bandcamp`). The popular list uses the
  "recent" kiosk (media.ccc.de) and the "Radio" kiosk (Bandcamp); search and playback use the same code
  as YouTube, PeerTube and SoundCloud.
- **Popular free catalogs** (Books catalog, overflow menu): one-tap OPDS presets from
  `builtin/opds/OpdsPresets.kt` (Project Gutenberg, Gutenberg latest, Unglue.it). Standard Ebooks is not
  listed because its OPDS feed now requires a Patrons Circle login; add it manually as
  `https://email:@standardebooks.org/feeds/opds` if you are a patron.
- **Find podcasts** (Podcasts screen, overflow menu): searches the free Apple iTunes Search API
  (`itunes.apple.com/search`) and the top-podcasts chart, and subscribes through the normal feed
  loader. Only https feeds are offered.

## Verification 2026-11-10 (live fetch of every seeded index)

All 12 URLs in `RecommendedRepos` returned HTTP 200 and parsed. `sourceCodeLanguage` 1 = JavaScript (runs in Monogatari), 0 = Dart (does not).

| Repository | Last commit | JS manga / anime / novel entries |
|---|---|---|
| `m2k3a/mangayomi-extensions` (+ GitHub Pages mirror) | 2026-11-03 | 84 / 24 / 8 |
| `Swakshan/mangayomi-swak-extensions` | 2026-10-24 | 7 / 14 / 4 |
| `Schnitzel5/sugoi-modules` | 2026-04-08 | 76 / 22 / 7 |
| `kodjodevf/mangayomi-extensions` | **2025-10-01 (stale)** | 114 / 0 / 5 |
| `gato404/kegareta-sauces` (anime only seeded) | **2025-06-14 (stale)** | - / 2 / - |

Observations:
- The maintained repos are already seeded. No repo was added: the only other recently updated hits found by search were unvetted personal repos with 0 stars, and adding unreviewed code publishers as defaults would contradict the publisher-provenance rules.
- No seeded repo was removed: removing a default only affects fresh installs and could hide sources that still work. `kodjodevf` still has 41 JS sources found nowhere else but its versions of shared sources are old (Mangafire 0.1.25 vs 0.2.20 in `m2k3a`, Asura Scans 0.1.7 vs 0.2.15).
- Swakshan alone provides JS `Atsumaru`, `Mangafire`, `Mangapark`, `WeLoMa`, and the anime/novel sources `AnimeGG`, `Animetsu`, `KickAssAnime`, `RoyalRoad`, `NovelFire`, ... (`m2k3a` lists Atsumaru as Dart only).
- No JavaScript replacement exists in any seeded repo for the library parsers that are broken (Softkomik absent; SkyMangas, Rocks Manga, MG Komik, Natsu, Hwago are Dart-only). `Comix` (JS 0.1.0, `m2k3a`/`sugoi`) and Baozi (JS 0.0.1) are available as JS alternatives to the library parsers.
- Consequence of publisher pinning: an install made from the stale `kodjodevf` repo stays pinned to it. A newer version of the same source from `m2k3a`/`sugoi` is now shown as a "Publisher changed" offer that needs approval (fixed 2026-11-10; before that fix such an install could be left stuck without any offer).
