# Monogatari 1.11.1-rc1 (prerelease) — sync merge fix, Mangayomi-import safety, live source checks

**This is a prerelease. It is not offered as the latest release.** Install it only if you want to help test; v1.11.0 remains the current release.

## Updates

- **Sync: a favourite you removed on one device no longer comes back from another.** Ported from Kotatsu-Redo (GPL-3.0): the app now asks the server what it has, takes whichever copy of a favourite or category was changed last before sending, and no longer overwrites a row you changed while a sync was running. Only affects Kotatsu sync-server users. The upstream unit tests for the merge rule are included; the server round trip has not been run.
- **Titles imported from Mangayomi keep their source name.** Titles imported without their extension are stored as `MANGAYOMI_<SOURCE>`. Those names used to be treated as unknown, so saving such a title again could overwrite the name and lose where it came from. They now show as "<Source> (extension not installed)" and keep their name; open one and use **Alternatives** to move it to a source that works.
- **Live source checks (developer tooling).** Opt-in test classes (`livetest/`, off unless `MONOGATARI_LIVE_TESTS=1`) run a source or a JS extension against the real site: list, details, chapters, pages, with timeouts and no retries. They are not part of the normal test run.

## Live results (2026-11-22, from a datacenter network, no browser verification available)

- Full chain OK (list, details, chapters, pages): MangaPark, BatoTo (XBatCat), Naver Webtoon, Comizy.io, Shinigami, SoulScans, AinzScans, LegionScans, KomikLokal, HoneyManga, Ikiru, Raw1001, and these other sources: MangaKatana, MangaPill, MangaKio, AtsuMoe, MangaTaro, PariteHaber, MangaGo, MangaGG, VyManga, ManhuaHot.
- JavaScript extensions through the app's QuickJS host: Weeb Central OK, MangaDex OK.
- **Not working:** Softkomik (list loads, details/pages fail with HTTP 404 on its session API); MangaDotNet (the site now sends a numeric chapter id and the parser reads it as text; this was already broken before the parser update).
- **Inconclusive:** VoraToon and MerlinScans answer HTTP 403 without a browser (they need the in-app verification flow); ManhuaScan and the parser-based MangaDex were unreachable from the test network.

## Known limitations

- Not tested on a device. Back up your library before installing.
- Still not done: persistent/reorderable download queue, reader lock-screen/navigation-bar work, GIF/WebP playback, device sync over LAN/Tailscale, Keiyoushi source fixes for sites whose parsers live in the external library. See docs/MODERNIZATION_STATUS_20261121.md.
