# Monogatari 1.11.0 — Parser update and library safety

## Updates

- **Parser library update** to kotatsu-parsers-redo `b049587f96`: repairs and new sources from upstream (for example Naver Webtoon, Comizy.io, XComic, HizoManga; BatoTo, MangaPark, LegionScans, KlikManga, KomikLokal, HentaiOrigines are no longer marked broken). New sources start **disabled**. Comix stays off.
- **Saved titles from removed sources are kept.** 39 sources were removed upstream. Their favourites and history stay and show as "<name> (retired)". Open a title and choose **Alternatives** to look for it on another source; nothing moves unless you confirm each title. Hide-adult settings still apply to retired adult sources.
- **Migration keeps more.** Moving a title to another source now also carries its reading statistics and its per-title settings (custom cover, title override, content rating, reader mode, colour filter), as well as favourites (with category), history, tracking and scrobbling as before.
- **Possibly missing chapters.** The chapter list can show a line such as "Possibly missing chapters (3): 12–14". It only appears for plain ascending numbering, ignores decimal and part chapters, duplicates from several groups and per-volume numbering, and is hidden while searching or in downloaded-only view.
- **Continue** is no longer offered when the last-read title is finished. A new chapter brings it back.
- **Library** reopens on the last category tab you used.
- **Suggestions: Not interested.** Select titles in Suggestions and choose Not interested; Undo is offered. Kept on this device only and not included in backups.
- **NewPipeExtractor v0.27.1.**
- **MangaPark is now classed as adult** by its parser, so it is hidden when "hide adult sources" is on.

## Under the hood

- The new parser contract is linked into the app; no parser bundle is downloaded at runtime and no upstream signing key is trusted.
- Unit tests: 990+ pass, including guards for all 1385 historical source ids and the missing-chapter rules. R8 release shrink passes.

## Known limitations

- **Not tested on a device or emulator, and not tested against live sites.** Please check your library, a few sources you use, and a backup restore before relying on this build. Back up first.
- Room migrations and backup restore with retired ids were not exercised (no schema change in this release).
- Not done in this release: persistent, reorderable download queue; reader responsiveness work beyond an audit; device sync (QR/LAN); GIF/WebP playback in the reader; sync reconciliation fixes; Keiyoushi source fixes (HoneyManga, ZeistManga, DoujinDesu refresh, Merlin, Softkomik, Ikiru) beyond whatever the new parser already contains; signed parser bundles. See docs/MODERNIZATION_STATUS_20261121.md.
- Lint was not run.
