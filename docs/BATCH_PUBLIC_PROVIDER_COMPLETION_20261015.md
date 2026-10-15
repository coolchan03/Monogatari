# Monogatari - public sources and CI finishing batch

**Branch:** `finish-public-provider-qa-20261015`
**Purpose:** Close code-only provider gaps before the first official two-device Android test.
No local Android SDK or emulator installation.

## Public catalog verification (2026-10-15)

Provider endpoints were accessed directly without accounts or browser bypasses:

| Source | Live result | App implementation |
| --- | --- | --- |
| Faded Page | HTTP 200, real Sherlock Holmes row with year, author, title | Official public title search and verified book pages |
| Project Gutenberg Australia | HTTP 200, 5,861 official CSV entries | Full searchable index, deduplicated HTML and TXT versions |
| Global Grey | HTTP 200, 4,007 sitemap links | Search real public book pages with safe HTTPS URL parsing |
| Baen Free Library | HTTP 200, three pages containing 36 + 36 + 10 items | Browse the entire free-only listing, real covers/links, up to 120 results |
| Bilibili | HTTP 200 and 20 public video results | First-class search/ranking source, real covers/creators, official playback links |

Any geographical, copyright, authentication, licensing or publishing conditions remain controlled by the individual providers. **Physical-device/provider acceptance testing is reserved for the user after v1.0.0.**

## Vimeo public browser functionality

Public-scope Vimeo access token is entered via More > API keys & accounts.
Video search supports plain video title search and structured browsing:

- `channel:12345` - channel videos by numeric channel ID
- `category:animation` - public curated category videos
- `showcase:12345/67890` - videos in an authorized public user showcase
- `collection:12345/67890` - alias for the showcase query

All paths use Vimeo official API endpoints, strict identifier checks and pagination, with browser playback on the publisher site. Content that requires private credentials, geo access or DRM authorization is never extracted. Token-authenticated live device verification remains a launch test.

## CI: startup retry, without masking application crashes

Previously, GitHub sometimes failed to download the Android 15 system image with an unknown ZIP error **before** the app was launched. The CI workflow now makes **one extra attempt only if** the startup script produced no log marker; a genuine app startup failure fails the workflow immediately. Unit tests, debug APK, release/R8 builds and portrait/landscape phone/tablet startup remain mandatory. Temporary GitHub-hosted runners only.

## Verification and release

Fixture/unit tests cover invalid domains/ports/credentials, live public catalog formats, Vimeo browsing URL construction and unsafe identifiers. The four book catalogs, Vimeo, Bilibili and CI are code-complete subject to this branch's final GitHub CI pass. The main 83-item Android roadmap will continue with its remaining 16 partial tasks before the v1.0.0 release.
