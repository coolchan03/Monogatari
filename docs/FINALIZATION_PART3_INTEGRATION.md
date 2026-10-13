# Monogatari finalization Part 3/5 — integration and simplification

## Implemented
- Moved the expanded More destinations catalog from MainActivity into one typed `MoreDestinations` object with an explicit ordering of screens. MainActivity now delegates rather than maintaining an inline string array and fragile numeric mapping.
- Retained all seven destination actions, with no duplication of activity targets.
- Added an explicit remote-overwrite confirmation before the manual WebDAV ZIP picker. The existing account encryption, explicit user action, manual download, and no-auto-restore constraints remain.

## Scope of remaining work
- Part 2 is **not fully done**: imports, full sync/merge, on-device translation, private-category enforcement, and automated notification integrations are still incomplete. Do not claim otherwise.
- Part 3 has started but still needs duplicate UI and source-bridge review, dependency/license audits, design consistency, CI, and Android acceptance.
- No destructive Git commands, no SDK install, no automatic credential transmission.

## Foreign backup identification repair (Part 2 + Part 3)
- Reduced false-positive recognition of arbitrary `.json` as Mangayomi backup: preflight now requires a JSON object with at least two recognizable collection keys within a small bounded header; generic JSON is UNKNOWN.
- Updated header-detection tests and added a generic-JSON rejection regression case. This remains a format **hint**, not a safe parser or transactionally reviewed import.

## Async lifecycle and UI simplification
- Unified Library now cancels a previous in-flight refresh before starting another, preventing older results from overwriting the latest list on repeated resumes.
- RSS refresh cancels the previous fetch, clears stale articles immediately and rejects results whose source URL differs from the current field. The saved-feeds dialog heading no longer misleadingly instructs users to long-press (selection opens an Open/Remove confirmation).
- These changes address stale-state and UI integrity regressions; still need exact-SHA CI and Android device smoke tests.

## Consolidated reliability pass — Oct 13, 2026
- Manual WebDAV transfer screen now prevents simultaneous upload/list/download operations and validates a selected device UUID again before download. This avoids racing activity status and competing operations, but does not add conflict reconciliation or automatic sync.
- RSS parser now rejects well-formed non-feed XML roots, rejects oversized article links and embedded credentials, and accepts HTTPS scheme case-insensitively. Added regression tests for non-feed and credential-bearing links.
- These updates do not make encrypted private categories, foreign imports, live translation, or automated cross-device synchronization complete. New exact-commit CI and Android device acceptance are still required.

## Consolidated integration and audit batch — 2026-10-13
- Hardened both Android backup exclusion manifests for WebDAV device UUIDs and encrypted account preferences. A second device restored via Google/Android transfer must not inherit the first device's archive name; the existing device UUID is still retained on an ordinary app update.
- The manual WebDAV UI now validates a persisted UUID before use, preserves the pending download ID across activity recreation, and rejects parallel transfer/list operations. Automatic sync/merge remains unimplemented.
- Converted More from an inline destination dialog into a dedicated full-screen RecyclerView hub, preserving typed destinations and adding an offline-readable open-source licences screen.
- Unified Library now reuses the Books shelf's MaterialCardView row and media-specific icons instead of an unrelated plain TextView renderer. The catalog, shelf and unified library share row binding and click semantics.
- Consolidated missing JS utility host-call diagnostics at the main dispatch boundary so unsupported calls are counted once and reported with a more precise error. Added a utility bridge regression test including an AES-CBC round trip.
- RSS parser now rejects unrelated XML roots and credential-bearing or oversized links, with tests. Source code notice summary is packaged offline; dependency licence verification remains a release acceptance gate.
- Added `MASTER_STEP_STATUS_2026-10-13.md` reconciling **74 numbered steps**, not 75: 2 confirmed complete, 3 implemented/CI milestones with device checks, 64 started/partial/research, 5 blocked/deferred/not executed.
- Whitespace checks and all four modified XML files parse successfully; Kotlin/JVM/R8 and device acceptance must pass on the new exact commit before merging or releasing.
