# Monogatari 1.3.0

Your own files, a roomier navigation bar and a proper move from Mangayomi.

## New

- **Add files to your library.** An *Add files* button on All media and the Books tab (and in the Favourites menu) takes books (EPUB, PDF, TXT, Markdown, HTML), comics (CBZ, ZIP), videos (MP4, MKV, WebM, MOV, 3GP) and audio (MP3, M4A/M4B, FLAC, OGG/Opus, AAC, WAV). Books and comics are imported; videos and audio stay where they are and play from the library with resume. Long-press to remove.
- **Add to library from Discover.** Long-press any card on Discover, Discover books or Discover videos. Titles from built-in sources go straight to your favourites; AniList suggestions are looked up by exact title on your sources first.
- **A bigger navigation bar.** Up to 8 items (the floating bar compacts and scrolls when needed; anything that doesn't fit is listed in More). New tabs: News & RSS, Podcasts, All media, Book catalog. New shortcuts: Search, Downloads, Statistics, Sources, Settings.
- **Move from Mangayomi.** Import now reads Mangayomi's current backup format, including large backups. Every JavaScript extension you had installed is installed here (from your repositories, or from the code saved in the backup), your extension repositories are added, and your manga, anime and novels come in with their categories, even when an extension can't be installed.

## Fixed

- A slow source no longer makes a whole Discover shelf fail, and Books/Videos shelves no longer keep spinning after a timeout.

## Known limits

- Encrypted Mangayomi backups can't be read: turn off *Encrypt backups* in Mangayomi and back up again.
- Mangayomi's Dart, Mihon and Aidoku extensions can't run in Monogatari and are listed as unavailable; extension settings aren't carried over.
- Comic import supports CBZ/ZIP only.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over 1.0.x–1.2.0 (same package and signing key). No database changes.
