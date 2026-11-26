# Monogatari for Web (PWA) and Windows (.exe)

The same app, re-built with web technology so it runs in any modern browser (installable as a PWA, hosted on GitHub
Pages) and as a Windows program (Electron: installer + portable `.exe`). It is **not** a line-by-line port of the Android
app: the Android UI is built on Android views and Hilt/Room, none of which exist on Windows or in a browser, so this is a
new front end that reuses what can be reused:

* the **Mangayomi JavaScript runtime** — the six JS shims in `app/src/main/resources/jsext/*.js` are imported verbatim from
  the Android app (one source of truth), and extensions run **unmodified** inside a sandboxed iframe;
* the native half of that runtime, re-implemented for the browser (`src/sandbox/`): HTTP, DOM (`Document`/`Element` with
  Jsoup's selector dialect), XPath, AES/CryptoJS/GCM, the Dean Edwards unpacker, date parsing, EPUB parsing, preferences;
* the same recommended source repositories, the same ids (`jsUid`) and backup layout, so an Android backup can be restored.

## Using it

| | |
|---|---|
| **Web / PWA** | `https://<owner>.github.io/<repo>/` after you enable Pages (below). Use your browser's *Install* button (or Settings → Install Monogatari) to get an app icon, offline start-up and its own window. |
| **Windows** | Download `Monogatari-Setup-<version>.exe` (installer) or `Monogatari-Portable-<version>.exe` from the **Releases** page (tags `win-v*`), or from the *Windows app (.exe)* workflow run's artifacts. |

First start: **Explore → Source repositories** → add one of the suggested repositories → **Install** a few sources.

### Why the browser version needs a relay
Websites decide which other websites may read their pages (CORS). Manga sites do not allow it, so a plain browser tab
cannot scrape them. Two ways round that:

1. **Use the Windows app** — requests are made by the app itself; no relay, extensions can set `Referer`/`Origin`/`User-Agent`/
   `Cookie`, and Cloudflare "verify you are human" pages open in a window and are retried automatically.
2. **Deploy the relay** in `web/proxy/worker.js` to a free Cloudflare Worker, set its `ALLOWED_ORIGIN` variable to your Pages
   origin, and paste `https://<worker>.workers.dev/?url=` into **Settings → Network → Proxy URL**. Only Cloudflare-protected
   challenge pages cannot be passed this way.

## Publishing it yourself (GitHub)

1. **PWA on GitHub Pages**: repository *Settings → Pages → Build and deployment → Source: GitHub Actions*. Pushing to `devel`
   (or `main`) under `web/**` runs `.github/workflows/web-pages.yml`, which tests, builds and deploys.
2. **Windows exe**: run the *Windows app (.exe)* workflow manually, or push a tag:
   ```bash
   git tag win-v1.10.0 && git push origin win-v1.10.0
   ```
   `.github/workflows/windows-exe.yml` builds on a Windows runner and attaches the installer and portable exe to a release.
   The binaries are **unsigned**, so Windows SmartScreen shows "unknown publisher" the first time (More info → Run anyway).
   Code signing needs a certificate and is not configured.

## Develop

```bash
cd web
npm install
npm run dev          # http://localhost:5173
npm test             # unit tests (selector dialect, dates, crypto, unpacker)
npm run build        # production build into web/dist
npm run electron     # run the Windows shell locally (any OS)
npm run dist:win     # build the installer + portable exe (on Windows)
```

End-to-end tests (`web/e2e`) drive a real headless Chromium / Electron against the real `wordrain69.js` extension and the
Android test fixtures: `npx playwright install chromium && npm run e2e`, and `xvfb-run -a node e2e/electron.mjs` on Linux.

## What carries over, what does not

**Works**
- Mangayomi JS sources for **manga, novels and anime**: popular / latest / search / filters, details, chapters, source settings
- Source repositories (add, refresh, install, update, uninstall, SHA-256 check, no silent downgrade), install from a `.js` file
- Library with categories, new-chapter check, history with resume, read marks, bookmarks, global search across sources
- Readers: manga (paged left-to-right, right-to-left, webtoon; fit modes; preloading; keyboard, tap zones, swipe), novels (font, size, spacing,
  dark / sepia / light), video (HLS via hls.js, quality list, subtitles, auto-next)
- Offline chapters (manga pages and novel text), export as `.cbz`
- Backup / restore, including **restoring an Android backup** (favourites, history, bookmarks, categories and sources for `js:` sources)
- Light / dark / AMOLED themes, accent colour, grid size, installable PWA, offline app shell

**Does not carry over (needs the JVM / Android)**
- The ~hundreds of built-in sites that come from the Kotlin `kotatsu-parsers-redo` library, and Dart-only Mangayomi extensions
  (Android-backup entries for those are skipped and counted in the restore summary)
- Some video host extractors (`*Extractor` calls: Sibnet, Okru, Voe, …) and `evaluateJavascriptViaWebview` — an extension that uses
  one fails with a clear error instead of returning nothing
- Tracker scrobbling (Shikimori/AniList/MAL/Kitsu), Nearby/WebDAV/folder sync, widgets, background notifications, biometric lock,
  statistics, local CBZ/EPUB library import, OPDS and the public book catalogs that are Kotlin providers
- In the PWA only: Cloudflare-protected sites, and extensions that depend on `Referer`/`Cookie` unless you use a relay

## Security notes
- Extensions are third-party code. Each one runs in an iframe with `sandbox="allow-scripts"` (opaque origin): it cannot touch your
  library, the page, or other extensions; it can only ask the host to make HTTP requests. Novel HTML is sanitised before display.
- The Electron window has `contextIsolation`, `sandbox`, no Node integration, denies all permission prompts, and opens external links
  in your default browser.
- Only add repositories you trust, and set `ALLOWED_ORIGIN` on your relay.
