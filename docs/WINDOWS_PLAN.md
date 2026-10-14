# Native Windows plan (#71-75) - plan only, nothing built yet

Status: **not started.** This is a plan to decide from, not a commitment. For personal use today, run the Android APK on an
emulator or a Chromebook (see `LARGE_SCREENS_AND_WINDOWS.md`).

## Recommended route: Compose Multiplatform (desktop) with a shared Kotlin core

| Layer | Android app today | Desktop equivalent | Shared? |
|---|---|---|---|
| Parsers (manga sources) | `kotlatsu-parsers-redo` (pure Kotlin/JVM) | same library | yes |
| JS extensions | `quickjs-kt` + `jsext/` bridges | `quickjs-kt` has JVM bindings; bridges are plain Kotlin except OkHttp client wiring | mostly |
| Networking | OkHttp | OkHttp | yes |
| Database | Room | SQLDelight or Room KMP (needs schema port + migration of backups) | no |
| UI | XML views, fragments | Compose Multiplatform | no (rewrite) |
| Video | Media3 / ExoPlayer | VLCJ or JavaFX Media | no |
| OCR / translate | ML Kit | Tesseract or ONNX models | no |
| Background work | WorkManager | coroutine scheduler | no |
| Files / notifications | Android APIs | Windows file dialogs, toast | no |

## Milestones

1. **Extract `:core`** (JVM module): models, parser wrappers, source-health, translation *policies/layout* (already pure Kotlin and unit
   tested), backup format readers. Android app depends on it. *Exit test:* Android CI unchanged and green.
2. **Desktop M1 (#72)** - local EPUB/PDF/CBZ library, login-free search, reading progress and storage. *Exit test:* open a CBZ and an
   EPUB, close, reopen at the same place; library survives restart.
3. **Desktop M2 (#73)** - source repositories, network reading, JS extensions, video playback, optional sync via the existing WebDAV
   format. *Exit test:* install one JS source, search, read a chapter, play one video.
4. **Validate on Windows (#75)** - file dialogs, background updates, multi-window, hardware decoding, portable (MSIX / zip) packaging.
5. **PWA (#74)** - not recommended for sources (browser CORS and cookie rules block most sites); only worth it for local-file reading.

## Decisions needed from you

- Is a native Windows app worth a multi-month second UI, or is the Android emulator / Chromebook route enough?
- If yes: Room KMP vs SQLDelight (SQLDelight is the safer desktop choice).
- Video on desktop: VLCJ needs a VLC install; JavaFX Media supports fewer formats.
