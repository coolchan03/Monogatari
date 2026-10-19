# Monogatari 1.0.0

First official release of Monogatari: one Android app for manga, manhwa, manhua, webtoons and BL; books, novels, EPUBs and fanfiction; anime, video and your own media servers; audiobooks and podcasts; and entertainment news.

## What's in 1.0

- One global search across Kotatsu-Redo built-in sources, installed Mangayomi JavaScript extensions and public book catalogs, with media scopes (All, Manga, Books, Video, Audio).
- Filters: publication years (oldest/newest, ranges, unknown dates), genre and demographic tags, language, format (book formats and each source's declared Manga/Manhwa/Manhua/Comics type), reading direction you have used, and read/unread history.
- Cross-source duplicates are grouped conservatively and you can pick a preferred source per title.
- Per-source Retry, or Solve / Sign in / Open in browser when a website needs its own verification. Monogatari never bypasses CAPTCHAs, sign-in, paywalls or DRM.
- Source Test Center: concurrent source scans, a copyable report that separates dead websites, access restrictions, moved pages and real parser/extension defects, and a guided device checklist.
- Unified Library with Manga/Books/Video/Audio tabs, collections, Continue strip and a two-pane tablet layout.
- Books: Standard Ebooks, Project Gutenberg (+ Australia), Open Library, Google Books (legal previews only), Wikisource, Faded Page, Global Grey, Baen Free Library, StoryWeaver, OPDS catalogs; offline EPUB save for approved free EPUBs; local EPUB/PDF/text readers that keep your place across rotation and folding.
- Video and audio: Internet Archive, Dailymotion, Bilibili, Odysee, Vimeo, Twitch, Jellyfin and Plex (your own servers), direct HLS/DASH links, podcast/video RSS with chapters, captions and resume.
- Manga translation on device (ML Kit): bubble-shaped text fitting, vertical Japanese/Chinese, font choice, tap-to-correct, translated/original toggle. Pages never leave the phone.
- Themes, accent colours and palettes; Kitsune mascot; Nearby offline transfer between two devices; backups with automatic pre-update copies.

## Known limits

- Many third-party websites are offline, moved or protected; the Test Center report explains each failure rather than hiding it.
- FictionPress and FanFiction.net open as their official websites inside the app; there is no native parser.
- Plex and Jellyfin need your own server; codec support depends on the device.
- This release passed automated tests and an Android 15 start-up test. Real-device testing on phone and tablet follows; fixes ship as 1.0.x updates.

## Upgrading

Install over an existing Monogatari beta (same package and signing key). The app copies your database and settings before the update; Settings > Backup > Undo an app update restores them.
