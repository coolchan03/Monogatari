# Monogatari Step 45 safe migration groundwork — 2026-10-13

Branch: `batch-overnight-stageg-stageh`; latest known committed local HEAD `af76c052cb48dd1af7330836eda06d7a525aba94`.

## Added but not committed or compiled
- `ForeignBackupPreflight.kt`: identifies Kotatsu ZIP, Mihon/Tachiyomi gzip-protobuf `.tachibk`, or Mangayomi JSON backups using both the filename and file signature. It does not parse or apply foreign data.
- `ForeignSourceMatcher.kt`: pure, read-only source resolution by unique HTTPS website host or unique source name; ambiguous or invalid hosts remain unmatched. Foreign source IDs are untrusted by default, and trusting canonical IDs requires an explicit opt-in for same-ecosystem backups.
- `ForeignBackupPreflightTest.kt`: JUnit scenarios for matching/mismatching magic bytes, unique mobile/WWW host resolution, duplicate names, invalid URL safety, and ID trust policy.

These files are ONLY planning/preflight primitives. **Step 45 is still partial/non-operational**: no Mihon protobuf models or reader, Mangayomi JSON schema parser, import UI, `Needs migration` category creation, rollback journal or database import exists. Do not claim migration works or accept foreign archives into live database.

## Existing work preserved
- Earlier staged `ExternalBackupStorage.kt` full-backup retention fix and `OVERNIGHT_INTEGRATION_v013.md` remain untouched.
- Earlier untracked Step 44 `WebDavEndpoint.kt`, `WebDavSnapshotTransport.kt` and disabled `WebDavAccountStore.kt` remain untouched; WebDAV is still nonfunctional and must not be enabled.
- Untracked user `.release-temp/` and `source-audit/` in main repository must remain intact.

## Verification and blockers
- Source files inspected through Tunnel; a premature return/brace in the initial preflight append was identified and corrected with a surgical edit.
- A read-only `git diff --check` command was denied by OpenAI safety checks during this iteration. No retry or workaround. No Gradle/JUnit/R8 or Android device testing was performed; no new exact-commit GitHub CI, branch push, merge or release.
- Do not attempt to bypass previous denied credential-store/test writes or git commit/push restrictions. Last verified public release remains v0.12.0.

## Next
- When authorized, review and stage these 3 new files and run static checks, CI and tests. Do not merge or release without exact-head green checks.
- For Step 45 obtain representative **redacted** exports and source model schemas for Mihon and Mangayomi, implement bounded parser and staging review; create a user-approved, reversible import path before any database mutation.
- Complete encrypted Keystore WebDAV credentials, XML parser compatibility tests, settings UI and Step 43 merge integration only when permitted. Protect credentials from backups and diagnostics.
