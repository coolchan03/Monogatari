# Monogatari v0.11.0 - Source Reliability Preview

**Public debug-signed Android testing APK.** This is not a production-signed update; it may not install directly over an APK signed with another key. Back up your reading library first.

## New in this batch
- Persistent failure counters and last-success times for website sources (additive Room schema 29 -> 30 migration).
- Global search omits persistently unhealthy sources after five failures and seven days without success. Explore shows them last with a warning while allowing manual retry. Offline/CAPTCHA failures do not count.
- New one-time editable presets: BL & Yaoi, Popular manga, Books & fanfic, Anime & video, Audio. Existing user presets are never replaced or recreated after deletion.
- JavaScript extension updates stage code and metadata, preserve the last working files and recover interrupted installs.
- Installed JS extensions check for updates every 12 hours on an available network. Manual update check is available on the Sources screen. New untrusted sources are not automatically installed.
- A just-updated JS source can roll back after three consecutive failures, if it was previously healthy; automatic updates pause for 48 hours.
- Expanded guided Test Center for source health, preset controls and update recovery; copy a report to your clipboard.

## Verification and limitations
- CI unit tests and debug/unsigned-R8 builds verify compilation, not actual behavior on an Android device.
- No production signing credentials are configured. This preview uses debug signing.
- Installing new extension catalogs automatically (Step 36), complete source deduplication/migration (Step 38), full extension auto-repair and compatibility improvements remain future work.
- On-device migration from previous releases, networking, rollback, reading, sync, CAPTCHA and source behavior require device testing.
- If the APK fails to install due to a signature mismatch, do not uninstall without backing up first.

Open Settings -> Monogatari Test Center and paste the resulting clipboard report into your next conversation.
