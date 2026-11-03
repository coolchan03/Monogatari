# Monogatari 1.6.0

Offline video, better stats, a tidier download queue, easier library clean-up and quicker ways in.

## New

- **Download videos for offline.** *Download for offline* in the video player (and a chip in the feed player) saves an episode or audio file for watching with no network. It handles plain files, HLS and DASH streams, shows a progress notification with Pause and Cancel, and keeps the copies in a private cache of up to 4 GB. Saved episodes show an *Offline* chip and appear in the Library under *On this device*. DRM-protected, live and Smooth Streaming streams are refused with a clear message.
- **Listen in background.** Hand a video or audio episode to the audio player and keep listening with the screen off.
- **Activity stats for every medium.** The Stats screen has a new *Activity* card: this week and this month per medium (manga, books, video, audio), your current and longest streak, and a 30-day chart. Book readers, videos and audio now count your time.
- **Import YouTube and NewPipe subscriptions.** Settings > Backup, sync & import reads a Google Takeout `subscriptions.csv`, a NewPipe export or a list of channel links, previews how many channels it found, and adds them to a *YouTube subscriptions* category. Re-importing adds only new ones.
- **Download queue summary.** A card at the top of Downloads shows how many are downloading, queued, paused and failed, with the time left. *Retry failed* re-queues every failed download. Rows show the source and media type, and auto-downloads carry an *Auto* chip.
- **Undo and multi-select in the Library.** Removing an item shows an Undo for about eight seconds. *Select…* lets you pick several items to add to a collection or remove together.
- **Launcher shortcuts and Share to Monogatari.** Long-press the app icon for Continue, Search and Library. Share a link or text from any app: repository links go to the usual consent dialog, YouTube links open the YouTube source, recognised titles open directly, and anything else becomes a search.
- **Storage overview.** Settings > Advanced > Storage and network shows where your space went (downloaded manga, imported books, caches, auto-downloads, feed caches, extension code, offline videos) with confirmed clear actions and a hint when space is low.

## Known limits

- Deleting a downloaded manga or a saved offline video cannot be undone, so it asks first.
- Offline downloads: no subtitles, no HLS streams with separate audio renditions, no DRM or live streams.
- "Books finished this month" is not shown yet: books do not record when you finish them.
- SoundCloud and PeerTube entries in a subscription file, and YouTube `@handle` links, are skipped by the import.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.5.0 build (same package and signing key). No database changes.
