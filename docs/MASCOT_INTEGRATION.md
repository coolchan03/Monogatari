# Monogatari - Kitsune mascot integration

This set replaces the generated placeholders with the approved simplified fox design. The 8 PNG sticker assets in app/src/main/res/drawable-nodpi are small, transparent, intentionally flat-colour illustrations: welcome, reading, search, celebration, resting, sad, collection and hidden human.

Mascot.bind() supplies accessible descriptions and calls MascotArtwork, which remaps only saturated cyan/teal pixels to the current Material primary colour. It preserves white fur, gold accents and skin. Results are cached by drawable/theme colour. The installed theme and system light/dark palette are respected on new activity creation.

## Locations
- Home library masthead, Settings and More: simple welcome fox
- Discover: per-media fox pose
- Books catalog and saved-books fragment: reading fox
- Unified Library: per-media empty state
- Library favourites/history/local and news empty states: collection, book, sleeping and searching poses
- Launcher, launch splash, Play Store icon and app-widget previews: the same identity

## Easter egg
Tap the mascot seven times quickly in More, or in the empty Unified Library, to open the hidden adult male Kitsune-kun. No credential or internet access is required.

## Test checklist
Open Home, Discover tabs, Books, More and empty Library on both phone and tablet. Check dark and light themes, then switch the app accent from teal to another colour and reopen each screen. Confirm white/gold and skin stay unchanged. Check seven quick taps in More and empty Library and the test-center report. Check launcher, splash and widget previews.

The old shape generator has been retired. The compact original atlas is stored in scripts/kitsune/sticker_atlas.b64; run the small exporter there to rebuild sticker resources.
