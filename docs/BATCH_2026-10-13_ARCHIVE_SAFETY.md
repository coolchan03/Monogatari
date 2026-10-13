# Monogatari — archive safety and foreign-backup review batch

Date: 2026-10-13
Base commit: `63b3e22c6b7c41bb88917e65f8b7f57fac0cdc89` (exact-sha CI passed)
Development branch: `batch-backup-preflight-webarth-20261013`

## Changes included

1. **Native archive validator:** `BackupArchiveInspector` validates entire ZIPs before restoring/transferring them. It rejects duplicate or path-like names, excessive entries, oversized compressed/expanded payloads, incorrect entry sizes, and CRC mismatches. It uses streaming reads and never extracts files.
2. **Restore review and restore service:** Both copy the selected SAF input with a strict size cap into a disposable staging file. They validate the full archive before reading its index or writing database records. A missing requested restore section fails before restore.
3. **Android system backup agent:** Validates the ZIP from the backup descriptor before applying app-data sections. Keeps Android-managed preferences out of the archive restore, as before.
4. **Folder sync:** Checks complete ZIP integrity before starting a merge and verifies its device-index identity. Locally published snapshots are also validated before copying them into the shared folder. A rejected archive cannot advance the sync journal cursor.
5. **Manual WebDAV:** Selected uploads and downloaded ZIPs must pass the archive validator before replacing a device backup or saving a downloaded file. Existing credential encryption, explicit account setup, and user-driven file pickers are unchanged.
6. **Foreign backup inspection:** `ForeignBackupReview` and `ForeignBackupReviewActivity` provide a bounded, read-only preview: native Kotatsu ZIP sections, recognizable Mangayomi JSON collection counts, and Mihon/Tachiyomi gzip container integrity. Available from More and Settings > Backup & Restore.
7. **Automated regression tests:** Pure JVM cases cover accepted archives, legacy section-only backups, duplicate/path-like entries, index-size and total-size limits, corruption/CRC, temporary-file cleanup, read-only foreign JSON shape review, and corrupted Mihon gzip.

## Boundaries and user-data safety

- No foreign backup is imported into the database by the new preview. Mihon protobuf decoding, mapping of foreign source IDs to installed parsers, transactional staging/rollback, and Mangayomi schema-specific restore remain unfinished Step 45 work.
- Native restore still applies selected sections using the existing repository transactions. The new full-archive preflight prevents malformed ZIPs from partially applying, **but application-level validation errors mid-restore are not yet an all-or-nothing rollback across every section.**
- Folder sync is the existing opt-in snapshot merge, not conflict-safe automatic WebDAV sync; WebDAV remains a manually controlled ZIP transfer system.
- Inspection bounds: archive compressed bytes <=256 MiB, expanded bytes <=512 MiB, section entries <=256 and individual index <=16 KiB. WebDAV manual transfers remain <=128 MiB compressed. Older or larger user archives may be rejected with an error instead of partially restored.
- No Android SDK or JDK was installed on Windows. Other worktrees and untracked user data must not be reset or cleaned.

## Verification

Run the commit-specific GitHub Actions Build check: JVM unit tests, debug APK assembly, and unsigned R8 release assembly. These are necessary but do **not** replace Android-device tests of SAF, restore recovery, Android Auto Backup, local folder sync, or WebDAV with real servers.

Current status: implement-and-test batch. Update the exact commit SHA, CI conclusion, and any repairs after the GitHub build completes. Do not merge `devel` or publish a release without green exact-sha CI and device acceptance.

## Next high-value work

- Step 45: bounded Mihon protobuf and Mangayomi backup readers backed by real redacted format fixtures, source-migration review, a proper staging transaction, and rollback on failure.
- Steps 41–44: restore conflict tests with an older database, tombstone/update policies, encrypted WebDAV conditional revision checks, and two-device sync acceptance.
- Step 65: enforce private-category authentication and exclusion across all library and widget surfaces.
- Steps 54–61: operational translation runtimes and video/JS extension functions.
- Steps 62–72: AniList discovery, followed-tag notifications, remaining reader extras and comprehensive QA.
- Step 74: device smoke tests and signed release checklist (user-driven at the end).
