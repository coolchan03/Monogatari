# Monogatari 1.4.0

A tidy-up release: a clearer Settings screen, a rebuilt Library, safer backups, and fixes from a full code review.

## New

- **Settings in ten clear groups**, each with an icon and a one-line description, and a search that finds every option. Things that used to be reachable only from More or the Test Center (API keys, source log, extension repositories, Nearby and WebDAV sync, Mihon and Mangayomi import) are linked from their group. More is regrouped, with Settings first.
- **A rebuilt Library.** Everything you added now shows up: imported books (including ones with long or non-Latin names that were hidden before), on-device comics and downloads, and your own video and audio files. Search, sorting, type chips with counts, a Continue strip (now including books and audio you started), covers, and the same long-press menu for every item.
- **Recommendations that work.** Discover shows the real error when AniList fails and falls back to MyAnimeList automatically. "For you" now matches your reading history to AniList tags.
- **Auto-download of recommendations (opt-in).** Off until you set it up in Settings > Library & downloads > Downloads: pick a storage limit, chapters per title and number of books. Wi-Fi only, optionally while charging. It only ever removes things it downloaded itself.
- **Theme everywhere.** About twenty screens that ignored your colour scheme and AMOLED setting now follow them, and the mascot matches your theme.
- **Discover books and Discover videos** are listed in More and linked from the Books and Video sections of Discover. Browse sources works even when Explore isn't on your bar.
- **Hide adult content** is now respected by the main Discover screen.

## Safer

- **Backups and imports never mix or lose data.** Restoring matches favourite categories by name instead of by a device-local number. Mihon and Mangayomi imports keep your existing favourites and respect deletions. Update rollback keeps your old data until the replacement is verified. Nearby sync stops instead of merging if the conflict review fails.
- **Automatic backups no longer include your community account secret.** The manual *Create backup* still does, so you can recover your account.
- **Extension and catalog downloads are size-limited**, and installing the same extension twice at once can no longer end on the older version. The release workflow now tags the commit that was actually built.

## Fixed

- Downloaded novels show under Books, not Manga.
- Two airing episodes can no longer replace each other's reminder.
- Recommendations from AniList and MyAnimeList no longer mix in the cache.
- EPUBs open without freezing the screen, and slow sources no longer stall Discover shelves.

## Known limits

- Auto-download covers manga chapters and free Standard Ebooks only (no video), and its storage limit is best-effort while a download is in progress.
- Encrypted Mangayomi backups can't be read: turn off *Encrypt backups* in Mangayomi and back up again.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over 1.0.x–1.3.0 (same package and signing key). No database changes.
