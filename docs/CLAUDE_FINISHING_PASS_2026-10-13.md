# Claude finishing pass — 2026-10-13

Branch: `ccr-7d1c1ef4-qm7uux` (based on `devel` at `f128edb`). Every change below is unit-tested where it
is pure logic, and the whole app was built by the GitHub **Build check** (unit tests, debug APK, unsigned R8
release APK). Nothing here has been tried on a real phone yet — see "Please test on your phone" at the end.

## What was added or finished

### More sources work (Mangayomi extensions)
- **Missing JavaScript host functions implemented** (`jsext/JsCrypto.kt`, `JsUnpacker.kt`, `JsDateParser.kt`,
  wired in `JsUtilsBridge.kt`): `encryptAESCryptoJS` / `decryptAESCryptoJS` (OpenSSL "Salted__" format),
  `decryptAESGCM`, `deobfuscateJsPassword`, `unpackJs` / `unpackJsAndCombine` (Dean Edwards packer) and
  `parseDates` (epoch, ISO, "3 days ago"-style phrases in many languages, explicit and common patterns).
  Extensions that called these used to fail with "unsupported host call".
- **Video-host extractors** (`jsext/JsVideoExtractors.kt`): mp4Upload, StreamTape, Dood, StreamWish, Filemoon,
  Voe, OK.ru, Sibnet, YourUpload, VidBom and Streamlare, alongside the existing Sendvid. Many anime sources
  rely on these. A host that changes its page returns "no videos" instead of crashing the source.
- **XPath** `/@attr` and `/text()` results now work in the HTML bridge.
- Also fixed: Base64 used `java.util.Base64`, which crashes on Android 6–7 (minSdk 23); it now uses okio.

### Translation (on-device, nothing sent to a server)
- **Reader → options → "Translate page"** (`reader/translate/`): Google ML Kit reads the text on the current page
  (Latin alphabet, Japanese, Korean or Chinese), translates it into your phone's language and draws the
  translation over the speech bubbles. The result opens in the zoomable image viewer. Tall vertical
  Japanese/Chinese bubbles are widened so English fits. The language is taken from the source or asked once per
  title; translated pages are cached, so reopening one is instant. The first use downloads the models
  (text recognition through Google Play services; about 30 MB per translation language).
- **Details → menu → "Translate title and description"**: shows a translated title and description (tap again to
  show the original). Each text's language is detected separately, a real English alternative title is preferred
  over a machine translation, and the stored title is never changed.

### Sources that break
- **Broken-source alerts** (`sourcehealth/BrokenLibrarySourcesWorker.kt`): once a day the app checks for library
  titles whose source has failed for a week or was removed from its extension repo, and posts one notification
  that opens the existing "replace source" screen for those titles. It only reminds again when the list changes
  and never moves anything by itself.

### Video player
- Skip intro (+85 s), playback speed (0.75×–2×, remembered per show) and **picture-in-picture**, in a proper menu
  that only shows for video/audio. Leaving the app while a video plays switches to picture-in-picture.

### Privacy
- **Private categories**: long-press a library tab → "Make private". Private categories and their titles are
  hidden from the library tabs, All favourites, global search and the home-screen shelf widget until you choose
  "Show private categories" and confirm with your fingerprint/face or phone PIN. They lock again whenever the
  app goes to the background. (History and "continue reading" still show titles you open — use incognito for
  those.)

### Discover
- **More → Discover**: AniList lists — trending manga, trending/popular Boys' Love, BL 18+, trending anime, BL anime
  and light novels — with covers, scores and an 18+ badge. Tap a title to search all your sources for it
  (long-press searches the romaji title). No account needed; adult titles are included.

### Importing from other apps
- **More → Import from Kotatsu / Mangayomi** (`backups/domain/MangayomiBackup.kt`, `MangayomiImporter.kt`):
  - Kotatsu backups use Monogatari's own format and open the normal Restore dialog.
  - Mangayomi `.backup` files are read, previewed (how many titles, which extensions get installed, which have no
    extension here) and imported on "Import": favourites into matching categories (created by name), reading
    position and progress. Monogatari runs the same Mangayomi JS extensions, so titles keep their exact links and
    open straight away; missing extensions are installed from your repositories first. Titles whose extension
    isn't available are still added (as an unavailable source) so "Find on other sources" can move them.
    Newer local progress is never overwritten.
  - Mihon/Tachiyomi backups are recognised but can't be imported (their extensions don't run here).

### Test Center spotlight
- "Open feature" in **More → Source test center** now circles the control being tested with a pulsing ring and
  shows a small card on every screen with the step's instructions and **👍 Looks good / 👎 Needs fixing** (with an
  optional note). The answer goes straight into the copied test report. Tap the card's title to move it.
- New steps: page translation, title translation, private categories, video extras, Discover, import.

## Reviewed and found OK
- Room migrations 28→29→30→31 match their entity definitions exactly (column types, defaults, keys, indexes),
  so upgrading users shouldn't hit a "migration didn't properly handle" crash.

## Still open (needs you, or a later pass)
- **Signed releases** (Step 6): needs your keystore and GitHub secrets.
- **Google developer verification** (Step 7): outside the app.
- **Importing Mihon backups**: not supported (Mihon extensions are Android apps and their links don't match).
- `evaluateJavascriptViaWebview` and the GogoCDN/Quark/UC extractors are still unsupported.
- Wattpad (Step 33).

## Please test on your phone (debug APK from the build's "Artifacts")
1. Open a Japanese/Korean raw chapter → options → Translate page.
2. Open an English title with a Japanese/Korean name → menu → Translate title and description.
3. Play a JS anime episode that uses one of the new hosts; try picture-in-picture by pressing Home.
4. Make a category private, leave the app, come back: it should be hidden until you unlock it.
5. More → Discover → Trending BL / BL 18+ → tap a title.
6. More → Import from Kotatsu / Mangayomi → pick a backup → Import/Restore.
7. More → Source test center → any new step → Open feature: the ring and approval card should appear.
