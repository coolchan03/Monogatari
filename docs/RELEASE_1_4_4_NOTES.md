# Monogatari 1.4.4

More sources for anime, books and manga, and a search bar that follows the media you are browsing.

## New

- **More extension repositories.** Twelve recommended Mangayomi-format repositories (manga, anime and novels) are added once to every install, including existing ones. A repository you remove stays removed. They hold, after removing duplicates, about 183 extensions that can run in Monogatari: roughly 131 manga, 40 anime and 12 novel sources, including Anna's Archive and NovelFire. Extensions written in Dart cannot run here and are skipped.
- **Get more sources.** On *Source repositories*, tap *Get more sources*, tick *Anime & video*, *Book & novel* and/or *Manga* (each shows how many are available), optionally include adult sources or other languages, and the app installs them in the background. Torrent and debrid sources, which need an outside account to do anything, are left for manual install. A summary row reports how many of each kind installed and links to the source log for failures.
- **Search follows your chip.** Choose Manga, Books, Video or Audio on Discover, in the Library, or open the Discover books or videos page, and the main search bar searches only that kind by default. The hint changes right away, for example to "Search books". *All* and *News* search everything, and the filter icon still lets you override the type.

## Known limits

- The counts in *Get more sources* can be slightly higher than what installs, because they do not subtract sites the app already covers with built-in sources.
- The source log is kept in memory only, so failure entries are gone after the app restarts.
- The Keiyoushi and Secozzi repositories are not included: they now hold only "outdated app" notices and use Android APK formats Monogatari cannot run.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.4.3 build (same package and signing key). No database changes.
