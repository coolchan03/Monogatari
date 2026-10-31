# Monogatari 1.4.3

A fix and a set of diagnostics for offline (Nearby) sync between two of your devices.

## Fixed

- **Nearby sync could be missing Wi-Fi permissions on Android 12L and newer.** Two ordinary Wi-Fi permissions that Nearby Connections uses had been capped at Android 11, so newer phones and tablets dropped them. They are declared for every version now.
- **A second attempt no longer clashes with the first.** Tapping *Receive* or *Send* again stops the previous session first. A connection that drops mid-transfer no longer blocks the next transfer, and *Stop and disconnect* (or leaving the screen) clears everything.

## Improved

- **Nearby tells you exactly what is missing.** Before *Receive* or *Send* starts, the app checks Google Play services, Bluetooth, Wi-Fi, location (Android 12L and older), airplane mode and the Nearby permissions, names the problem and offers a button to fix it. If you denied a permission, it names it and opens the app settings.
- **Plain-language errors with a code.** Failures while becoming visible, finding a device, pairing and sending now explain what went wrong and show the Nearby status code in brackets, for example `[8012]`.
- **A Troubleshooting card** on the Nearby screen with a checklist and a *Check this device* button that lists every requirement as OK or a problem.
- A step indicator (Step 1 of 4 … Step 4 of 4) shows how far pairing has got.

## Known limits

- Nearby sync needs Google Play services on both devices and works only while the app is open on both.
- If sync still fails, the status line and the bracketed code show which step; please report them.
- Passed unit tests, the release build and an Android 15 start-up test; device testing follows.

## Upgrading

Installs over any 1.0.x to 1.4.2 build (same package and signing key). No database changes.
