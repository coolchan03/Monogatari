# Monogatari-maintained extensions

Mangayomi-format JavaScript extensions for sites whose built-in parsers broke after the site changed.
Index: `extensions/index.json` (served from the `devel` branch via raw.githubusercontent.com).

| Source | Why it exists | How it was checked |
|---|---|---|
| SkyMangas | the library parser targets a WordPress theme the site no longer uses | `tools/run-extension.mjs` against the live API: popular, latest, search, details + chapters, pages, image download |
| Rocks Manga | the library parser targets Madara; the site is now Next.js with a JSON API | same |

`tools/run-extension.mjs` is a small Node host (not the app's QuickJS runtime) for checking JSON-API extensions
against a live site: `node tools/run-extension.mjs javascript/manga/src/es/skymangas.js dragon`.
Passing it is evidence the extension logic matches the live API, not proof it runs inside the app.

Rules: bump `version` (in both the `.js` header and `index.json`) on every change, otherwise installs will
not update (and changed code under the same version is rejected by the app's integrity check).
