# Monogatari 0.13.8 beta — catalog-source integration

This development update extends the Android-only 83-feature roadmap.

- Four previously standalone book catalogs now appear as selectable built-in sources in unified search: Faded Page, Project Gutenberg Australia, Global Grey Ebooks, and Baen Free Library.
- Book detail pages offer safe links to each provider's official reading/catalog pages. Rights and regional availability must be checked by the reader.
- Large Gutenberg Australia and Global Grey catalogs are cached for up to six hours with a 6 MB response limit. News and new-release feeds remain uncached.
- Global Grey now recognizes the publisher's /online-books-2/ URLs and preserves underscore-separated title words.
- Incoming official book URLs are restricted to the correct HTTPS hosts, with no userinfo, URL fragments or nonstandard ports.
- Earlier 0.13.7 changes remain: nearby manual Android-to-Android transfer, simplified Kitsune icon, media-specific search, RSS features, and Internet Archive placeholder covers.

## Verification status
- GitHub Actions unit tests, Android debug APK build, optimized R8 build and cloud emulator startup must succeed before beta publication.
- No Android SDK/emulator is installed on the user's devices.
- Four catalogs still need physical Android device/region/provider testing.
- StoryWeaver, FictionPress/FanFiction.net, and Bilibili may require official browser playback/search or account permissions. They are not falsely considered completed solely from source registration.
- Native Windows/PWA work has been removed from the active roadmap, by user decision.
