# Monogatari 1.2.0

Discovery for every kind of story: books and videos now open full, search can be limited to one type, and feeds work again.

## New

- **Discover books and Discover videos.** Two new pages full of covers. Videos: YouTube trending, anime airing this week, trending and BL anime, Dailymotion, Odysee and Internet Archive films. Books: light novels, Project Gutenberg, Royal Road, AO3, Scribble Hub, Wikisource, Open Library and Internet Archive books. Open them from More, or add them to the bottom bar in navigation settings.
- **Discover is a normal tab.** It no longer opens on top of the app with a back arrow; the navigation bar stays visible. Its Video, Books and Audio sections now open on popular titles from built-in sources.
- **Airing this week + reminders.** A calendar of anime episodes airing in the next 7 days (Discover > Anime & Video). Long-press a show to get a notification when new episodes air.
- **Search by type.** The search bar's filter icon chooses Manga, Books, Video or Audio, and the results screen has one-tap type chips. Only sources of that type are searched.
- **Real YouTube trending.** Browsing YouTube without a search lists trending movies & shows, music and gaming instead of a fixed search; PeerTube shows Trending and SoundCloud New & hot.
- **RSS feeds fixed and pre-filled.** Feeds load through the app's network stack with a browser identity, follow redirects and accept common HTML symbols. Nine more feeds are included (Anime Corner, Otaku USA, Anime Herald, MyAnimeList news, Siliconera, Book Riot, Reactor, NPR Books, The Verge), an *All feeds* view mixes the newest stories, and the last articles are saved so the screen opens with stories. Any public https feed can be added.
- **Sync conflicts.** When Nearby sync finds a title read on both devices, you choose which reading position to keep (newest is pre-selected).
- **Choose settings when restoring a backup.** Restoring Settings asks which groups to apply, like Nearby already did.
- **Books catalog opens with books** (your last search, or Standard Ebooks' newest releases).

## Look

- Backup import, Nearby sync, WebDAV sync, licences, the StoryWeaver reader and the feed player now use the same cards, mascot header and buttons as the More screen.

## Known limits

- Feed sites and YouTube trending could not be reached from the build environment; they are checked on devices.
- YouTube retired its main Trending page in 2025, so trending uses the movies & shows, music and gaming charts.
- The first Nearby sync with a device lists every reading difference; later syncs only list titles changed on both sides.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over 1.0.x and 1.1.0 (same package and signing key). No database changes.
