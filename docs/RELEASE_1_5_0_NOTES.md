# Monogatari 1.5.0

Small touches that make the app easier to find your way around.

## New

- **A short setup for new installs.** The first time Monogatari opens, a four-step *Set up Monogatari* walks through choosing a theme (System, Light, Dark or AMOLED), getting sources, turning on notifications (Android 13 and newer), and where Library, Discover, Search and Settings are. You can skip it at any point, and *Run setup again* is in Settings > Advanced.
- **Pull down to refresh** on Discover, on the Discover books and videos pages, and in the Library (which also reloads Recommended).
- **A Continue button in the Library.** *Continue: <title>* resumes the most recent thing you were reading or watching across manga, anime, books, video and audio.
- **Mascot messages that say what to do next** on screens that used to show a bare "nothing here": news, podcasts, the book catalog and the source log.

## Changed

- **The Explore tab is now called Sources,** and the source-catalog shortcut and page are called *Source catalog*, so there is only one "Sources". Nothing about your saved navigation bar changes.

## Known limits

- The setup flow only appears on brand-new installs; existing users can start it from Settings > Advanced.
- The Search row in the setup's last step reuses an existing icon.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.4.4 build (same package and signing key). No database changes.
