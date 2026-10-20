# Monogatari 1.1.0

The first feature update after 1.0: moving your library between apps and devices, smarter browsing and nicer reading.

## New

- **Import from Mihon / Tachiyomi.** More > Import accepts `.tachibk` backups: a preview shows titles, favourites and reading progress, then favourites on sources Monogatari also has are found by exact title, put in the same-named categories, and your last read chapter is carried over (only where Monogatari has no reading history for that title yet). Titles that can't be matched are listed so you can add them by hand.
- **Nearby sync both ways.** After you merge a library received from another device, Monogatari offers *Send back*, so both devices end up with the same library.
- **Choose which settings travel.** When a Nearby transfer includes settings, pick the groups to take (appearance, reader, library, sources, network, tracking, other). Network starts unticked; app lock, proxy password, tokens and storage folders never travel.
- **Smart collections.** Library > ⋮ > New smart collection: save a rule like `media:manga tag:romance -tag:horror status:ongoing` and it fills itself.
- **Discover "For you".** On Manga and Anime, a For you chip re-ranks popular titles by the tags you read most. It runs on your phone and says which tags it used.
- **Side-by-side translation.** The translated page viewer can show the original and the translation together (side by side in landscape or on tablets, stacked in portrait).
- **Play next automatically.** Episodes and audiobook chapters continue to the next one (toggle in the player menu, on by default).
- **EPUB read-aloud.** Read aloud with a 15-minute sleep timer; continues into the next chapter.

## Also included (from 1.0.2 work)

- Screen text moved into the app's text resources (ready for translation).
- Build system moved to the current GitHub Actions versions.

## Known limits

- Mihon import needs the source to exist in Monogatari with the same name, and matches titles exactly.
- Reading position moves by chapter number; sources that number chapters differently may land on a nearby chapter.
- New features passed automated tests and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over 1.0.x (same package and signing key). Your data is copied before the update.
