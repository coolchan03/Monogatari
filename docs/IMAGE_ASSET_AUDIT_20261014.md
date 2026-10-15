# Monogatari illustration and cover audit (2026-10-14)

## Static asset inspection
- Pillow inspection of app/src/main/res: 56 PNG/WebP files, 0 exact binary duplicates.
- All 9 Kitsune sticker PNGs are 256x256 under drawable-nodpi. Enlarging them to full-width tablet art will blur; retain at small sticker sizes pending high-resolution source artwork.
- 24px icons in mdpi are normal Android density variants, not low-res replacements for illustrations.
- The inherited ic_empty_common.xml layer-list used Kitsune welcome artwork across multiple unrelated empty screens. More empty states now use distinct existing Kitsune forms, reducing reuse without creating deceptive resolution.
- No additional original source artwork was available in the audited repository. Do not AI-upscale and misrepresent 256px originals as high-resolution art.

## Actual cover improvements
- Open Library book catalog cover URLs now request the provider's large cover variant rather than medium.
- Google Books prefers larger official cover links (extraLarge/large/medium/small) before thumbnail fallbacks.

## Manual QA outstanding
- On Motorola phone and 13-inch Android tablet, check each empty state and More header for scaling, visual duplicates, missing transparent edges, and inherited branding.
- Screenshot each blurry/old asset; identify which app screen and whether it comes from an installed source, catalog, or packaged drawable.
- Replace *particular* obsolete images with approved source artwork at 512px/1024px or vector, after confirming correct identity/licensing.
## Other notes
- Static hashes do not detect visually similar but differently encoded images; only byte-identical images were ruled out.
- Remote manga/anime cover images are downloaded from content providers and may be low-resolution independently of Monogatari; do not stretch them beyond their source image quality.
- Device screenshots and production asset replacements remain pending, not marked complete in the requirements tracker.

## 2026-10-15 screenshot follow-up
- Reviewed completed GitHub Actions emulator screenshots for phone and portrait tablet (run 38667817650).
- The welcome sheet and empty Books shelf still referenced a legacy book/video collage. Replaced them with distinct Kitsune reading and collection stickers at 160dp, not stretched to their old oversized image dimensions. Removed the now-unused collage vector asset.
- The first-run community notice displayed visibly corrupted legacy punctuation (e.g., `â€”`). Fixed the English text resource encodings (33 corrupted punctuation instances), and corrected an outdated history prompt that told users to discover books in Explore instead of Discover.
- High-resolution original Kitsune art and exact on-device visual tests remain outstanding. These changes remove identified old art and reduce scaling, not replace the original 256px sticker masters.
