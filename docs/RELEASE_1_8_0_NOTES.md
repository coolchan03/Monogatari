# Monogatari 1.8.0

Small touches for getting around and fitting more on screen.

## New

- **Start page.** *Settings > Appearance & navigation > Start page* chooses which tab the app opens on: *First tab* (the default, so nothing changes unless you pick something), *Last used tab*, or any tab on your navigation bar. If the tab you chose is no longer on the bar, the first tab opens instead. Launcher shortcuts and shared links still open where they say.
- **Compact view.** A *Compact view* toggle in the menu of the Library, Discover and the Discover books and videos pages fits about 1.3 times as many, smaller covers per row (three columns instead of two on a phone in portrait). It is one setting for all three screens.
- **Tap the tab you are on to scroll to the top,** now on every tab: Library, Discover, Podcasts, Books catalog and News.
- **Light haptic feedback** when you start selecting in the Library, toggle a selection, trigger pull-to-refresh or tap Undo (it follows the system touch-feedback setting).

## Known limits

- Back from another tab still goes to the first tab, not the start page.
- Compact view changes only the cover grids, not the lists.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.7.0 build (same package and signing key). No database changes.
