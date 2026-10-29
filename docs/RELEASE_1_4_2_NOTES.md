# Monogatari 1.4.2

Search now works the same way everywhere.

## Changed

- **One media type for search.** The filter icon in the search bar and the chips on the results screen (All · Manga · Books · Video · Audio) now share a single setting, so they always agree. Explore keeps its own media filter for its own list; it no longer changes what search looks in.
- **Results chips switch in place.** Tapping a chip re-runs the search on the same screen and keeps your query and filters. It survives rotating the phone.
- **The search bar says what it will do.** The hint reads "Search manga", "Search books", "Search video", "Search audio" or "Search all media". On History and Favourites, when you switch the bar to filter that list, it says "Filter history" or "Filter favourites", and a *Search all sources instead* row searches every source with the current type.
- **Every "Search…" button leads to the same search.** Discover's six search pop-ups are gone: each section's search button opens the main search with that type chosen, and so do the search fields on Discover books and Discover videos. The Books catalog has a *Search all book sources* button, and the Library offers *Search all sources for "…"* under your local results and when nothing matches.
- **Discover remembers the chip you used last** (a fresh install still opens on All).

## Known limits

- Retrying a single source while switching type may briefly show a stale result section.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.4.1 build (same package and signing key). No database changes.
