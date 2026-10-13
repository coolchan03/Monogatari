# Monogatari Step 44 partial engineering checkpoint — 2026-10-13

Branch: batch-overnight-stageg-stageh, local committed HEAD af76c052c.
Public verified release remains v0.12.0; v0.13.0 has no combined CI run and is not released.

## Uncommitted Step 44 work
- WebDavEndpoint.kt: validates HTTPS-only URL without embedded credentials; uses a Monogatari collection; reconstructs UUID-only snapshot paths from local endpoint instead of trusting remote hrefs.
- WebDavSnapshotTransport.kt: independent OkHttp client with redirects disabled; MKCOL, PROPFIND Depth:1, PUT and bounded GET; XML external entity protection, bounded remote XML and archive sizes; reads UUID-named backup list from DAV:multistatus.
- WebDavAccountStore.kt is deliberately DISABLED and returns null. Only the incomplete no-op skeleton exists. Do not expose WebDAV configuration or enable synchronization until encrypted credential persistence is implemented and verified.
- No WebDAV UI, secure credential persistence, merge integration or Worker scheduling exists yet. Step 44 is PARTIAL and NONFUNCTIONAL.
- Step 45 imports have not started.

## Tool and verification blockers
- The earlier staged ExternalBackupStorage.kt retention fix and existing staged integration checkpoint were preserved unchanged.
- Tunnel git commit was denied by OpenAI safety checks; not bypassed.
- Attempts to write full encrypted credential storage and unit tests were denied by OpenAI safety checks. Only the harmless no-op account skeleton is present; do not mistake it for working credential storage.
- No JDK/Android SDK installed locally; do not claim Kotlin compilation, tests, R8, network operation or Android device verification.
- Do not push, merge, or publish before authorization and an exact-commit green CI build.

## Recovery instructions
- Inspect git status and read the three new WebDav source files before editing.
- Do not discard or overwrite staged changes. Remove or replace the no-op WebDavAccountStore only when authorized.
- Review transport security and Android XML parser compatibility; add tests, HTTPS credential storage, UI, secure data-extraction exclusions and merge-safe integration.
- Then pursue Step 45 foreign-backup import safely, with unmatched-source review and rollback.

## Follow-up isolated hardening batch — 2026-10-13
- New untracked `WebDavEndpointTest.kt` adds JVM safety cases for HTTPS-only configuration, credentials-in-URL rejection, canonical UUID snapshot filenames, foreign hrefs, traversal, unexpected query strings, and invalid IDs.
- Hardened untracked `WebDavEndpoint.kt` to reject query, fragment and embedded credentials from remote DAV hrefs before extracting device IDs.
- These files are NOT in the running CI commit `d01583fd109e584e27242d1957790eae8cd8bd97`; no local JDK or Android toolchain was installed. The new test is authored, not executed. WebDAV remains disabled and incomplete (credential store is no-op).
- Running exact-commit Build check `38392011683` passed JVM tests and debug APK assembly and artifact upload; R8 still pending on last check. Do not start competing CI, merge, or release before the full result.
