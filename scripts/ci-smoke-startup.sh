#!/usr/bin/env bash
set -euo pipefail

PKG="io.github.coolchan03.monogatari.debug"
ACTIVITY="org.koitharu.kotatsu.main.ui.MainActivity"
APK="$(find smoke-apk -type f -name '*.apk' -print -quit)"
test -n "$APK" || { echo 'Missing debug APK'; exit 1; }

adb install -r "$APK"
adb shell pm list packages "$PKG"
adb shell am force-stop "$PKG" || true
adb logcat -c
adb shell am start -W -n "$PKG/$ACTIVITY" | tee smoke-start-output.txt
sleep 12
adb logcat -d -v threadtime > smoke-logcat.txt
adb logcat -d -b crash -v threadtime > smoke-crash-buffer.txt || true

if ! adb shell pidof "$PKG" | tr -d '\r' | grep -q '[0-9]'; then
  echo 'FAIL: Monogatari process exited during cold startup.'
  grep -A 65 -B 8 -E 'FATAL EXCEPTION|Process: io.github.coolchan03.monogatari|AndroidRuntime|Caused by:' smoke-logcat.txt | tail -n 180 || true
  echo 'CRASH BUFFER:'
  tail -n 180 smoke-crash-buffer.txt || true
  exit 1
fi

if grep -Fq "Process: $PKG," smoke-crash-buffer.txt; then
  echo 'FAIL: Android recorded a Monogatari fatal exception, even if a background service restarted it.'
  tail -n 180 smoke-crash-buffer.txt
  exit 1
fi

echo 'PASS: Monogatari cold launch survived for 12 seconds without a Java crash.'
