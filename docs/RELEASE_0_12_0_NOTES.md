# Monogatari v0.12.0 - First-run Sources and Site Reliability

**Public debug-signed Android test APK.** Back up your library before replacing an earlier testing version; an APK signed by a different key may not install in place.

## Improvements
- First-run setup asks whether to install additional runnable Mangayomi JavaScript sources from configured catalogs. It respects the chosen adult-content setting, skips installed/known-duplicate hosts and includes only English or all-language extensions.
- Android WorkManager installs up to four extensions concurrently, even if Explore is left open or the app process is stopped. Explore displays attempted/total/failed progress. Sources screen includes a manual install/retry option.
- Each source checks package integrity using the existing JS source validation and preserves local user source settings.
- At three qualifying consecutive errors, an installed JavaScript source gets a targeted update check, with bounded retry. The v0.11.0 last-working-version rollback and update pause remain available.
- Unified search and Explore group only confidently known website hosts (including installed JS and known built-ins), separated by content type. Search tries an alternate implementation when the preferred one fails or returns no results.
- New guided tests cover onboarding, source deduplication, failover and targeted repair; copy the full Test Center report to clipboard.

## What is still incomplete
- Actual installation, playback, update and repair behavior on an Android device remains unverified until user testing.
- Unknown or federated Kotatsu parser hosts are not blindly grouped; complete cross-engine deduplication remains future work.
- Automatic migration of dead-library titles to alternatives with preserved progress, user undo and ambiguous-choice prompts is not yet implemented.
- Third-party index health statuses (Step 40) and full compatibility of Dart-only extensions are not yet guaranteed.
- Full offline media reliability, multi-device sync, and signed production releases remain under development.

## Verification
GitHub Actions must pass JVM tests, debug APK assembly and R8 optimization on the exact release commit. This preview is debug signed; a production-signed APK requires signing credentials.
