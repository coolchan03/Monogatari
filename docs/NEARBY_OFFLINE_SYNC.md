# Monogatari nearby offline transfer

## Goal and scope

Two Android devices with Google Play services can directly exchange their Monogatari
library/settings snapshot or any user-selected offline file, without a cloud server or
internet connection. Nearby Connections negotiates encrypted Bluetooth/Wi-Fi transport.
This is **manual, foreground-only peer-to-peer sync**, not an always-on background mirror.

The existing safe backup format and merge policies are reused. No unreviewed network
archive is restored automatically.

## How to use

1. On both devices open More > Nearby offline sync (also Settings > Sync).
2. Enable Bluetooth and Wi-Fi if disabled, grant the prompted nearby permissions.
3. On receiving device choose Receive. On sending device choose Find.
4. Tap the discovered device, compare the pairing code on both screens and accept both.
5. Select Send library and settings or Send an offline book or file.
6. Receiver checks the SHA-256 and backup ZIP structure/CRC before offering a multi-section
   merge. Choose sections explicitly. The community identity is *not sent by default*.
7. For received books, choose Add to Books; for other files, choose Save elsewhere. Reverse
   sender and receiver roles (or send in the other direction on the same connection) to
   synchronize changes in both directions.

## Data included and limitations

- Library ZIP includes favourites, history, reading positions, categories, source
  registrations, settings, bookmarks and other standard backup sections. No cloud account.
- Downloaded manga image/chapter caches are **not** in the normal library backup.
  Transfer a selected EPUB/PDF/CBZ or other accessible file separately.
- Imported local ebook files are also transferred separately; metadata must not pretend to
  include binary ebooks.
- No automatic scheduling, background discovery, silent overwrite, or cross-platform peer.
- Nearby requires Google Play services and Bluetooth/Wi-Fi availability on both devices.
- Each file/ZIP is bounded to 256 MiB. The same validated ZIP inspection/merge policy
  used for ordinary restore applies.
- Pairing MUST be confirmed on both screens. The SDK provides transport encryption; the
  app uses additional SHA-256 checks and does not trust the filename from the sender.
- App-private identity remains separate unless a user explicitly elects it during manual
  restore from another backup. A nearby export never carries identity in the first place.

## Test matrix before stable release

- Two physical devices: sender/receiver discovery, matching code accepted on both sides,
  rejected and mismatched codes, Wi-Fi/Bluetooth radio disabled, denied permissions.
- Duplicate/modified favourites, timestamp-based history and settings merge.
- Import EPUB/PDF, file-saving with same name, device rotation, app close mid-transfer,
  interrupted connection, transfer canceled.
- Empty/oversized/malformed archive, CRC corruption, unknown section, checksum mismatch,
  256 MiB limit, path traversal/file name spoofing.
- Ensure no automatic restore, hidden/background scanning or credentials accidentally sent.
- Emulator cold-start alone DOES NOT validate local Bluetooth/Wi-Fi transport.

Potential next phase: true scheduled delta sync and automatic bulk chapter cache transfer, after
cross-device conflict-resolution and battery/privacy policies are specified.
