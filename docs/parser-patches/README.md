# Parser patches

Patches against Kotatsu-Redo/kotatsu-parsers-redo `8e4b04f3df` (the revision `gradle/libs.versions.toml` pins).
They are NOT in the Monogatari APK yet: the app consumes the parser library as a JitPack dependency, so these need a
fork of the parser library (apply with `git am`), after which `parsers` in `libs.versions.toml` is pointed at that fork.

Each patch was built and run against the live site with the library's own tests
(`./gradlew test --tests '*MangaParserTest*' -PtestSources=<SOURCE>`; JDK 21 needs a toolchain override).

| Patch | Source | Live result |
|---|---|---|
| 0001 | MangaDotNet | 11/11 + 2 regression tests pass |
| 0002 | Softkomik | 9/11 pass; `tags` and `tags_multiple` fail on a framework limit for single-genre sources (also failed before the change) |
| 0003 | Ikiru | 11/11 pass (rewrite for the `/api/v1` platform; old `/manga/<slug>` urls and chapter ids kept) |
| 0004 | AquaManga | 11/11 pass (domain `aquamanga.io`; `aquareader.org` answers 410) |

## Live baseline of sources NOT fixed (parsers 8e4b04f3df, JVM test harness)
| Source | Result | Finding |
|---|---|---|
| DoujinDesu | 11/11 | Works. The image-expiry (HTTP 410) retry from the Keiyoushi change is not implemented. |
| Manganato | 3/11 | saiyanmanga.com is a new Next.js site; the MangaBox selectors do not match. Needs a port of Keiyoushi's modern MangaBox layout. Parser is still `@Broken`. |
| HoneyManga | 4/11 | API v3 changes (cursor-list returns 400, duplicate chapter numbers). Not started. |
| MadaraDex | 2/11 | Site redesign (admin-ajax 400, root not found). Not started. |
| CatharsisWorld | 1/11 | Origin down (HTTP 522) at test time. Cannot be fixed or judged now. |
| MerlinScans | 1/11 | Cloudflare challenge: inconclusive in the JVM harness. |
| MangaLib | 0/11 | The harness JS engine has no `window`; its `site_ids` fix cannot be verified here. |
| DuaLeoTruyen, RinkoComics, MgKomik, AtsuMoe, DrakeScans | not in results | The run produced results for only 8 of the 14 requested sources; these 5 were not reported. Re-run them separately. |
