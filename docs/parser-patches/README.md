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
