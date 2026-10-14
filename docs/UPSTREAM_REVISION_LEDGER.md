# Upstream code and dependency revision ledger

Checked: October 13, 2026 (GitHub repository metadata and head commits). These are **upstream HEADs**, not proof Monogatari has copied or integrated those revisions. Keep attribution and redistribution notices in `OpenSourceLicensesActivity` and license files.

| Upstream | Current HEAD (at check time) | License | Monogatari integration | Update action |
|---|---|---|---|---|
| [Kotatsu-Redo](https://github.com/Kotatsu-Redo/Kotatsu-Redo) | [`6e0cfdc55d`](https://github.com/Kotatsu-Redo/Kotatsu-Redo/commit/6e0cfdc55dac5f27873f2e3634e1191eeb07132d) | GPL-3.0 | Android base and shared UI; exact fork baseline not reconstructed | Compare upstream feature-level patches and retain Monogatari-specific UI and Hilt startup fixes |
| [kotatsu-parsers-redo](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo) | [`b3c0000245`](https://github.com/Kotatsu-Redo/kotatsu-parsers-redo/commit/b3c000024563920d3eecde5816f2e9d3734fa467) | GPL-3.0 | **Pinned to `b3c0000245`** in `gradle/libs.versions.toml` (JitPack dependency `clquwu:kotatsu-parsers-redo`) | Confirm JitPack fork commit parity and review source-specific fixes; do not update blindly |
| [Mangayomi](https://github.com/kodjodevf/mangayomi) | [`a4b9ca4380`](https://github.com/kodjodevf/mangayomi/commit/a4b9ca43805fd230653b97fb45bd9fcead803761) | Apache-2.0 | JS-style source compatibility and multi-media concepts; no generic Dart runtime | Compare source APIs and extension format compatibility, preserve Apache notices |
| [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) | [`8443c91bf4`](https://github.com/TeamNewPipe/NewPipeExtractor/commit/8443c91bf4276c326f7b33e30a36d88e0ab94cfe) | GPL-3.0 | Gradle dependency pinned to `v0.26.5` | Review relevant extractor fixes and regression-test hosts; current HEAD is a Checkstyle bump, not necessarily a player fix |

## Review and merge gate

1. Identify precisely which upstream commit(s), file(s), extension schemas and source parsers changed.
2. Check licenses, contributor attribution and app-specific patches. In particular, don't restore the removed sponsor solicitation, disable the new cold-start test, or reintroduce main-thread Hilt assertions.
3. Apply small, inspectable patches only after testing. Preserve Hilt/QuickJS, media filters, translations, security and custom navigation.
4. Run `testDebugUnitTest`, `assembleDebug`, unsigned R8 release build and the Android 15 cold-start smoke test; verify individual source behavior separately.
5. Update this ledger with *the actually integrated revision*, test evidence, changes and owner. Updating this markdown file alone is never evidence an upstream feature has been ported.

An automated daily watch is configured to report meaningful changes only. **There is no automatic upstream merge or push.**
