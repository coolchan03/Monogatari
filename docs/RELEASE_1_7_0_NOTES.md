# Monogatari 1.7.0

More sources and integrations for books, video and audio, plus safer, simpler syncing and backups.

## New sources and integrations

- **Your own audio servers.** *Audiobookshelf* (audiobooks and podcasts) and *Subsonic / Navidrome* (music) can be set up on the *API keys & accounts* screen, each with a *Test connection* button. They stay out of search and Explore until you set them up. Audiobookshelf uses your server address and API token; Subsonic uses your address, username and password (the password is never sent: each request carries a fresh salted token).
- **media.ccc.de and Bandcamp** are built in through NewPipe (conference talks as video, Bandcamp radio and music as audio).
- **Find podcasts.** The Podcasts screen can search Apple's free podcast directory, show top podcasts and subscribe to a feed in one tap.
- **Popular free catalogs.** The Books catalog has a *Popular free catalogs* menu that adds public OPDS catalogs in one tap (Project Gutenberg and Unglue.it).

## Sync and safety

- **Trusted devices for Nearby sync.** After a code-confirmed pairing, if both people agree, the two devices remember each other. Next time they connect without the code, but nothing is transferred until each device proves it holds the shared secret; if that check fails you are asked to pair again with the code. A *Trusted devices* list lets you forget a device.
- **Send several files at once.** Pick many files; they send one after another ("Sending 2 of 5"), with *Skip this file* and *Cancel remaining*, and a summary at the end. The receiving device asks for a destination folder once and remembers it. Limits: 256 MB per file, 2 GB in total, 100 files.
- **Favourites conflicts.** When the same title was put in different categories on two devices since the last sync, a second review sheet (after the history one) lets you keep this device's, take the other's, or keep both.
- **Backup reminders.** The app now shows *Last backup: 3 days ago* on Settings > Backup and the Backup dialog, and can remind you weekly or monthly (weekly by default, can be turned off) when you are overdue. A library backup sent over Nearby counts as a backup.
- **Restore preview.** The restore dialog says what each section would change before you restore, for example *240 history entries: 31 newer than yours, 20 older (kept yours), 5 new*. It is a read-only check.

## Known limits

- The Audiobookshelf and Subsonic integrations were built from the official documentation and tested on made-up responses only; please use *Test connection* and report anything odd. Covers, progress reporting and scrobbling are not included.
- Standard Ebooks now requires a paid login for its feed, so it is not a preset; adding it by hand is described in docs/SOURCE_REPOS.md. Several other catalogs could not be verified and are left out.
- Nearby trusted devices: two phones with the same model name both fall back to the code, and a reinstalled device leaves a stale entry until you Forget it. Older builds show an error if they receive a pairing request.
- Bandcamp album and artist pages and live playback were not run.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.6.0 build (same package and signing key). No database changes.
