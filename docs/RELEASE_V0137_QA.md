# Monogatari 0.13.7 — nearby transfer and Kitsune icon QA

**Based on:** default `devel` at `818e76b`, integrating the prior 0.13.6 entertainment improvements.

## Implementation added
- Nearby Connections 19.3 Android-to-Android secure, foreground-only transfers; supported back to Android 6 / minSdk 23.
- Pairing code displayed on both phones. Explicit accept/reject. No silent device discovery or background transfers.
- One-shot library/settings transfer reusing existing backup ZIP and timestamp-aware merge, with CRC and SHA-256 checks.
- Individual selected EPUB, PDF and other accessible files; received books can be added to Books shelf.
- User chooses sections before restoring and community identity cannot be transferred through nearby sync.
- Size cap: 256 MiB per transfer; remove partial files on failure; on-stop advertising/discovery cleanup.
- New compact flat Kitsune book-and-fox icon for adaptive, round, legacy and nightly variants, plus monochrome themed-icon mask.
- Guided test-center entry for the nearby transfer and safe manual import workflow.
- In-app version updated to 0.13.7 (versionCode 12).

## Automated gates
- XML/TOML parse, legacy icon dimension validation, ZIP-transfer protocol unit tests.
- GitHub Actions `Build check` runs unit tests, debug APK, unsigned R8 release build and emulator cold-start test.
- No claims of physical nearby radio transport or nonpublic provider API tests being completed.

## Known boundaries
- **Not** continuous synchronization: a sender starts a transfer and receiver explicitly reviews import.
- Existing reading progress/favourites/categories merge; offline chapters and large image cache are not bulk synced.
- Selected offline books can be sent separately. No automatic discovery when application is closed.
- Devices require a compatible Google Play services Nearby Connections provider, Bluetooth and Wi-Fi enabled.
- Host credentials/private identity not sent as part of the one-shot transfer. Backups may still include ordinary app settings.
- Test on the actual Motorola Razr and onn tablet before marking near-field sync fully validated.
- The broader 88-item feature tracker still contains PARTIAL and PENDING items, including some provider credentials, manga source preferred provider integration and high-resolution illustration sources.

## Final user-facing device test
1. Install same test version on two physical Android devices.
2. Open More > Nearby offline sync. One device: Receive. The other: Find.
3. Tap the discovered device; compare codes on both devices; accept only a match.
4. Send library; select only favourites/history/settings and import. Restart receiving app; confirm.
5. Repeat in reverse, confirm no unexpected data loss.
6. Send 1 EPUB and 1 PDF; select Add to Books and test local open, then Save elsewhere.
7. Reject pairing, cancel in transfer, and disable Bluetooth to check cleanup.
8. Check main and nightly icon appearance, search mode filters, pre-added feeds and guided source tests.
