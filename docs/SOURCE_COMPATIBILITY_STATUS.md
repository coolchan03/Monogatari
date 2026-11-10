# Source compatibility status

Evidence level: **static reading of kotatsu-parsers-redo `68f3fed395` (master HEAD, 2026-11-10) plus a one-request home-page reachability probe (section at the end).** No search, chapter or image was loaded and nothing was fixed. Fixes for these sites would be changes to the parser library (a separate repository), not to Monogatari. The app has no per-source overrides for any site below and honours the library's `@Broken` flag (`isBroken`: skipped in multi-source search, warning in the source list).

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

## Live reachability probe (2026-11-10, one GET to each home page)

Method: a single HTTPS GET of `/` per domain, domains taken from the parser library at `68f3fed395`, from a cloud container (datacenter IP, so anti-bot results may differ from a phone). No search, chapter or image was loaded and no challenge was bypassed. A 200 only means the home page answered.

| Source id | Library domain | Result |
|---|---|---|
| `TOPMANHUA` | manhuatop.org | HTTP 403 Cloudflare challenge: needs interactive verification in the app |
| `MGKOMIK` | id.mgkomik.cc | HTTP 403 Cloudflare challenge: needs interactive verification in the app |
| `MANHWAINDO` | www.manhwaindo.my | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `ROCKSMANGA` | rocksmanga.com | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `MILFTOON` | milftoon.xxx | not probed (adult or no single domain) |
| `SOFTKOMIK` | softkomik.co | HTTP 200 but redirected to `https://softkomik.app/`: domain/site moved |
| `SKYMANGAS` | skymangas.com | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `TEMPLESCAN` | templetoons.com | HTTP 200 only after redirecting to `/challenge?redirect=%2F` (a verification page), so the real home page was not reached |
| `CATHARSISWORLD` | catharsisworld.dig-it.info | HTTP 522: site or origin down at probe time |
| `ACOMICS` | acomics.ru | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `WESTMANGA` | v1.westmanga.my | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `LIKEMANGA` | - | not probed (adult or no single domain) |
| `KAGANE` | kagane.moe | HTTP 403 Cloudflare challenge: needs interactive verification in the app |
| `MINDAFANSUB` | mindafansub.online | not probed (adult or no single domain) |
| `SOULSCANS` | v1.soulscans.org | no connection (curl exit 56); dead domain OR blocked by this container's proxy, unknown |
| `HWAGO` | 02.hwago.xyz | no connection (curl exit 35); dead domain OR blocked by this container's proxy, unknown |
| `MANHWAZ` | manhwaz.cc | HTTP 503: site or origin down at probe time |
| `MANGAGO` | www.mangago.me | HTTP 403 Cloudflare challenge: needs interactive verification in the app |
| `ATSUMOE` | atsu.moe | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `COMIX` | comix.to | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `BAOZIMH` | www.baozimh.com | HTTP 403, no Cloudflare challenge header: access denied to this client |
| `YONABAR` | yonabar.xyz | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `SHINIGAMI` | id.shinigami.asia | HTTP 403 Cloudflare challenge: needs interactive verification in the app |
| `HOTOON` | v1.holotoon.site | HTTP 200 but redirected to `https://t.me/holotoon_world/`: domain/site moved |
| `MANHUAPLUSORG` | manhuaplus.org | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |
| `NATSU` | natsu.tv | no connection (curl exit 60); dead domain OR blocked by this container's proxy, unknown |
| `MANGACLOUD` | mangacloud.org | HTTP 200 on the home page (reachable; says nothing about search, chapters or images) |

Findings worth acting on upstream:
- `SOFTKOMIK`: `softkomik.co` now redirects to `softkomik.app` (domain migration not yet in the library).
- `HOTOON` (Holotoon): `v1.holotoon.site` redirects to a Telegram channel; the site has probably moved away.
- `CATHARSISWORLD` (522), `MANHWAZ` (503): down at probe time.
- `TEMPLESCAN`: `templetoons.com` redirects to a `/challenge` page (verification handling).
- `SOULSCANS`, `HWAGO`, `NATSU`: no connection; needs a re-check from another network before calling them dead.
- Cloudflare challenge (interactive verification needed): `TOPMANHUA`, `MGKOMIK`, `KAGANE`, `MANGAGO`, `SHINIGAMI`.

These are inputs for parser-library fixes (a separate repository this session cannot modify). No Monogatari code was changed for them: swapping a default domain in the app on the strength of a home-page redirect could break a source that still works through the redirect.
