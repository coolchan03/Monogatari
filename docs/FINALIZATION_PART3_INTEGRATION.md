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
