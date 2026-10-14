#!/usr/bin/env bash
set -euo pipefail
PKG="io.github.coolchan03.monogatari.debug"
ACTIVITY="org.koitharu.kotatsu.main.ui.MainActivity"
APK="$(find smoke-apk -type f -name '*.apk' -print -quit)"
test -n "$APK" || { echo 'Missing debug APK'; exit 1; }

# The hosted emulator occasionally disconnects ADB during graphics initialization.
# Retry transport commands; never convert a real missing process/crash into a pass.
adb_try() {
  local attempt
  for attempt in 1 2 3; do
    if timeout 25s adb "$@"; then return 0; fi
    echo "ADB command failed (attempt $attempt): $*" >&2
    sleep 3
  done
  return 1
}

reset_window() {
  adb shell wm size reset >/dev/null 2>&1 || true
  adb shell wm density reset >/dev/null 2>&1 || true
}
trap reset_window EXIT

capture_logs() {
  local prefix="$1"
  # Bound the large system log; avoid 255 errors from draining all boot events.
  adb_try logcat -d -t 1200 -v threadtime > "$prefix-logcat.txt" ||
    echo 'WARN: main logcat unavailable (ADB transport)' >&2
  adb_try logcat -d -b crash -t 400 -v threadtime > "$prefix-crash-buffer.txt" ||
    { echo 'FAIL: crash buffer unavailable after retries'; exit 1; }
}

verify_launch() {
  local prefix="$1"
  local pid
  if ! pid="$(adb_try shell pidof "$PKG" | tr -d '\r')" ||
     ! grep -Eq '[0-9]+' <<< "$pid"; then
    echo "FAIL: Monogatari process missing after $prefix startup."
    tail -n 120 "$prefix-crash-buffer.txt" || true
    exit 1
  fi
  if grep -Fq "Process: $PKG," "$prefix-crash-buffer.txt"; then
    echo "FAIL: Monogatari fatal crash recorded after $prefix startup."
    tail -n 120 "$prefix-crash-buffer.txt"
    exit 1
  fi
  echo "PASS: $prefix cold startup: PID $pid, no app crash recorded."
}

screenshot() {
  local file="$1"
  if ! adb_try exec-out screencap -p > "$file"; then
    echo "WARN: optional screenshot unavailable ($file)" >&2
    rm -f "$file"
  fi
}

adb_try install -r "$APK"
adb_try shell pm list packages "$PKG"
# The notification permission prompt would otherwise stay on top and swallow later launches.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb_try shell am force-stop "$PKG" || true
adb_try logcat -c || true
adb_try shell am start -W -n "$PKG/$ACTIVITY" | tee smoke-start-output.txt
sleep 10
capture_logs smoke
verify_launch smoke
screenshot smoke-phone.png

# Resize the same emulator to real tablet windows. Tablet layouts use -w600dp resources, so both a portrait
# tablet (800x1280dp) and a landscape tablet/laptop window (1280x800dp) must start cleanly.
tablet_run() {
  local name="$1" size="$2"
  adb_try shell wm size "$size"
  adb_try shell wm density 240
  adb_try shell am force-stop "$PKG" || true
  # Leave any leftover system dialog (permissions, ANR) before relaunching.
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  adb_try logcat -c || true
  adb_try shell am start -S -W -n "$PKG/$ACTIVITY" | tee "$name-start.txt"
  sleep 10
  capture_logs "$name"
  verify_launch "$name"
  screenshot "$name.png"
}
tablet_run smoke-tablet 1200x1920
tablet_run smoke-tablet-land 1920x1200
echo 'PASS: phone, portrait tablet and landscape tablet cold launches, no Monogatari crashes.'
