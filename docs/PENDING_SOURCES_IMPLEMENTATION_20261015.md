# Monogatari Android — missing-source implementation pass

**Branch:** `feat-pending-catalogs-20261015` (GitHub PR #5)
**Scope:** Seven previously untouched provider requests from the active 83-item Android roadmap.

## Provider coverage

| Former missing # | Provider | Implementation | Limitations |
|---|---|---|---|
| 40 | Faded Page | Actual public website search POST; title/author/year/cover in Books Catalog and first-class Books source for unified search | Canadian public-domain rules; reading on provider site; local copyright differs |
| 41 | Project Gutenberg Australia | Search official `catalogue.txt` across thousands of entries, choose HTML over duplicate plain-text listing | Publication is under Australian law; user must check local rights |
| 42 | Global Grey Ebooks | Search entries listed in official `sitemap.xml`; no site-wide crawling | Metadata derived from book-page URLs; publisher site handles downloads |
| 45 | StoryWeaver | Official-site search and browse reachable in Books Catalog | Full in-app catalog/API and image-book reader not implemented |
| 47 | Baen Free Library | Parse only *free-library* product listings (first two catalog pages) and open publisher's listing | Commercial titles elsewhere are not included; access per publisher rules |
| 48 | FictionPress / FanFiction.net | Official web-search discovery link | Websites returned browser verification (403) to automated requests; no CAPTCHA bypass or unsupported scraping |
| 53 | Bilibili | First-class built-in video source; public search/ranking metadata, thumbnails, titles and creators; opens official video page | Playback is handled by Bilibili because regional restrictions, authentication and DRM must not be bypassed |

## Visual polish

Internet Archive audio and comic listings no longer display generic file-type thumbnails from the Archive image service by default. Monogatari draws local title-based artwork, with a media-type label, controlled colors and simple typography. In book-catalog listings missing covers or failed cover loads get the same clean generated treatment. Other providers' genuine covers are preserved.

## Testing and scope notes

- Source examples and HTTP access were checked directly from a temporary external lookup; local test fixtures cover parsing, safe destination domains, duplicate formats and encoded queries.
- The Android CI unit/build/R8 and GitHub-hosted cold-start test are the release gate. Neither Android SDK nor an emulator is installed on the user's laptop or devices.
- The repository's Windows desktop/PWA milestones were removed from the active numbered roadmap and the standalone Windows plan deleted. The new tracker has 83 Android-only entries.
- All seven previously PENDING sources now have code paths, but the tracker marks them PARTIAL until external/provider behavior is reviewed on physical devices.
