# Source compatibility status

Evidence level: **static reading of kotatsu-parsers-redo `68f3fed395` (master HEAD, 2026-11-10) only.** No site was contacted, no chapter or image was loaded, nothing was fixed. Fixes for these sites would be changes to the parser library (a separate repository), not to Monogatari. The app has no per-source overrides for any site below and honours the library's `@Broken` flag (`isBroken`: skipped in multi-source search, warning in the source list).

Library-wide: 428 sources are marked `@Broken` at this revision.

| Site (assignment) | Library id / class at HEAD | State at HEAD | What the assignment says changed | Gap |
|---|---|---|---|---|
| ManhuaTop | `TOPMANHUA` (`en/TopManhua`, domain manhuatop.org) | not marked broken | moved from Madara to a JSON API | not checked whether the class already uses the JSON API |
| MG Komik | `MGKOMIK` (`madara/id/Mgkomik`) | not marked broken | new domain, HTML, endpoints | still a Madara subclass |
| ManhwaIndo | `MANHWAINDO` (`mangareader/id`) | not marked broken | chapter API + listing selectors | unverified |
| RocksManga | `ROCKSMANGA` (`madara/ar`) | not marked broken | Next.js migration | class is still Madara-based |
| MilfToon | `MILFTOON` (`madara/en`) | not marked broken | Vue app | class is still Madara-based |
| Softkomik | `SOFTKOMIK` (`id/SoftKomik`) | not marked broken | encrypted Next.js data | unverified |
| SkyMangas | `SKYMANGAS` | `@Broken` | dedicated API, tokens | needs rewrite upstream |
| Temple Scan | `TEMPLESCAN` ("Not dead, changed template"), `TEMPLESCANESP` | `@Broken` | new site + verification | needs rewrite upstream |
| Catharsis World | `CATHARSISWORLD` ("Not dead, changed template"), `CATHARSISFANTASY` | `@Broken` | reader session + chapter API | needs rewrite upstream |
| AComics | `ACOMICS` (`ru`, acomics.ru) | not marked broken | selectors | unverified |
| WestManga | `WESTMANGA` (`mangareader/id`) | not marked broken | domain, verification | unverified |
| LikeManga | `LIKEMANGA` OK; `LIKEMANGAIN` | `LIKEMANGAIN` `@Broken` | domain, reader | unverified |
| Kagane | `KAGANE` (kagane.moe) | not broken; source comments record earlier domains | tokens, Origin, quality | unverified; domain history lives in the library |
| Minda Fansub | `MINDAFANSUB` (`madara/tr`) | not broken | domain | unverified |
| Soul Scans | `SOULSCANS` (v1.soulscans.org) | not broken | domain | unverified |
| Hwago | `HWAGO` (02.hwago.xyz) | not broken | domain | unverified |
| ManhwaZ | `MANHWAZ` (`madara/en`) | not broken | domain | unverified |
| Mangago | `MANGAGO` (www.mangago.me) | not broken | descrambling keys, ids | unverified |
| Atsu.moe | `ATSUMOE` (atsu.moe) | not broken | endpoints | unverified |
| Comix | `COMIX` (comix.to) | `@Broken("Disabled until updates are made faster")` | investigate | stays disabled; not re-enabled |
| Baozi Manhua | `BAOZIMH` (`zh/Baozimh`) | not broken | placeholder images | unverified |
| Yona Bar, Shinigami, Holotoon, ManhuaPlus, Natsu, MangaCloud | `YONABAR`, `SHINIGAMI`, `HOTOON`, `MANHUAPLUSORG`, `NATSU`, `MANGACLOUD` | not broken | image hosts, covers, headers, selectors | unverified |

Saved-library safety: moving from parsers `b3c0000245` to `68f3fed395` removed no `MangaParserSource` id (1370 -> 1385, +15). `RaijinScans`' class file left the jar but its enum id remains; its behaviour was not checked.

Next step for this area: for each row, diff the library's history for that source against the live site (needs network access to the sites), then either fix upstream or mark `@Broken`, then bump the pin. Add Monogatari-side fixture tests only where the app itself changes.
