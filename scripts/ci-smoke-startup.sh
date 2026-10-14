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

adb exec-out screencap -p > smoke-phone.png

# A single Android emulator can also smoke-test 600dp+ tablet configurations.
reset_window() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
}
trap reset_window EXIT
adb shell wm size 1280x1920
adb shell wm density 180
adb shell am force-stop "$PKG" || true
adb logcat -c
adb shell am start -W -n "$PKG/$ACTIVITY" | tee smoke-tablet-start.txt
sleep 9
adb exec-out screencap -p > smoke-tablet.png
adb logcat -d -b crash -v threadtime > smoke-tablet-crash.txt || true

if ! adb shell pidof "$PKG" | tr -d '\r' | grep -q '[0-9]'; then
  echo 'FAIL: Monogatari exited during tablet-width cold startup.'
  tail -n 120 smoke-tablet-crash.txt
  exit 1
fi
if grep -Fq "Process: $PKG," smoke-tablet-crash.txt; then
  echo 'FAIL: Monogatari recorded a tablet-width startup fatal exception.'
  tail -n 120 smoke-tablet-crash.txt
  exit 1
fi

echo 'PASS: phone and simulated tablet cold starts survived without fatal Java crashes.'
