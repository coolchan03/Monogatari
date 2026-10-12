# Instructions for AI assistants

PROJECT CONTEXT - Monogatari (Android app, Kotlin)
- Fork of Kotatsu-Redo (a fork of Kotatsu manga reader). Kotlin package: org.koitharu.kotatsu (do NOT rename).
- Source paths: app/src/main/kotlin/org/koitharu/kotatsu/... ; resources in app/src/main/res/.
- Build: Gradle, Java 17, minSdk 23, targetSdk 37, Hilt (DI), Room (DB, current DATABASE_VERSION in
  core/db/MangaDatabase.kt), Coil 3 (images), OkHttp, kotlinx.serialization, Media3 (video), quickjs-kt (JS engine).
- Manga sources: Kotatsu-Redo parser library (com.github.clquwu:kotatsu-parsers-redo).
- Mangayomi JavaScript extensions run unmodified via QuickJS: package jsext/ (JsMangaSource "js:<id>",
  JsMangaRepository, JsExtensionProvider, JsSourceRegistry), repos in sourcerepo/.
- Media types: core/media/MediaType.kt (MANGA, BOOK, VIDEO). Novel/video reader: reader/ui/MediaReaderActivity.kt.
- Every source is a MangaSource with a MangaRepository (core/parser/MangaRepository.kt, Factory.createRepository).
- Style: tabs for indentation, match surrounding code, user-visible text in res/values/strings.xml.
RULES FOR YOU (ChatGPT):
1. Return COMPLETE files, never snippets or "...rest unchanged". One file per message; wait for NEXT.
2. Only change what the step asks. Don't rename, reformat or "improve" unrelated code.
3. Never change existing Manga/source IDs, database column names, or preference keys unless the step says so.
4. Any Room schema change = bump DATABASE_VERSION + add a Migration class + register it. Never destructive migration.
5. Don't add libraries unless the step says so. If you must, say exactly which line goes in gradle/libs.versions.toml
   and app/build.gradle.
6. If you are unsure how an existing class works, ASK me to paste it instead of guessing.
7. Add or update a unit test when the step says "test".

MONOGATARI ASSISTANT WORKFLOW (user preference, 2026-10-12):
- FIRST tool call in any new Monogatari work session must request Tunnel to Surface access; if denied, ask for authorization.
- Never use the GitHub connector to write, merge, change branches, or publish. Edit locally and commit/push exclusively via Tunnel to Surface.
- Combine adjacent compatible steps into substantial work batches rather than stopping after every small change; preserve step-level traceability.
- Before long/risky commands, record the precise current operation in docs/WORK_STATUS.md; after each verified milestone record resulting commit, push and test outcome.
- Report exactly which step(s), files, operation and test are active; do not claim to detect an unexpected timeout in real time.
- Protect user data and existing untracked local work; never install a renamed-package APK before backup/restore instructions are satisfied.
