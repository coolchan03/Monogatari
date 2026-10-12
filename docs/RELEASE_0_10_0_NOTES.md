# Monogatari v0.10.0 Sources & Video Search Preview

Public **debug-signed Android APK** for guided testing. This is not a production-signed APK and may not install as an update over a differently signed debug APK. The debug package has the .debug application ID suffix; back up your library before changing installs.

## Included changes
- Unified manga, books, video and audio source handling, including Gutenberg, Open Library, Internet Archive, AO3, Royal Road, Scribble Hub and LibriVox.
- PeerTube and SoundCloud via NewPipeExtractor, in addition to YouTube.
- Video search menu selects Videos, Channels or Playlists without typing special prefixes.
- Channel and playlist continuation pages are fetched with strict limits and duplicates removed.
- Followed YouTube channels can refresh recent uploads from the public RSS/Atom feed.
- LibriVox background audio, media controls, cover artwork and position resumption.
- Guided Test Center adds targeted video and audio checks. Open Settings > Monogatari Test Center, run the checks and copy the full report.

## Still requiring verification or future work
- Real Android tests for all new sources and media playback; automated CI does not guarantee every host works.
- Signed production release (requires private release signing credentials).
- Every remote site, DRM content, age-gated video, unavailable stream, and extension compatibility cannot be assured by these checks.
- Complete media downloads/offline playback, source repair, and cross-device sync still require additional testing or implementation.

## Build evidence
The release APK is published only after GitHub Actions unit tests, debug assembly, and unsigned R8 release build succeed on its exact commit. Download the debug APK from this GitHub Release. If updating fails with a signature mismatch, back up your data and install this test package separately instead of overwriting the previous app.
