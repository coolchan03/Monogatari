param(
    [ValidateSet('Auto','Debug','Release')]
    [string]$Variant = 'Auto'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'Android platform-tools (adb) not found; no SDK install is required.' }
$devices = @(& $adb devices -l | Where-Object { $_ -match '\sdevice\s' })
if ($devices.Count -eq 0) { throw 'Connect and authorize the Android phone for USB debugging, then retry.' }
$packages = @(& $adb shell pm list packages 'io.github.coolchan03.monogatari')
$debugPkg = 'io.github.coolchan03.monogatari.debug'
$releasePkg = 'io.github.coolchan03.monogatari'
$pkg = switch ($Variant) {
    'Debug' { $debugPkg }
    'Release' { $releasePkg }
    default { if ($packages -contains "package:$debugPkg") { $debugPkg } else { $releasePkg } }
}
if ($packages -notcontains "package:$pkg") { throw "Monogatari package $pkg is not installed." }
$out = Join-Path $env:USERPROFILE 'Desktop\Monogatari-Diagnostics'
New-Item -ItemType Directory -Path $out -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Write-Host "Launching $pkg without deleting user data..."
& $adb shell am force-stop $pkg | Out-Null
& $adb shell am start -W -n "$pkg/org.koitharu.kotatsu.main.ui.MainActivity" | Out-File -FilePath (Join-Path $out "$stamp-start.txt") -Encoding utf8
Start-Sleep -Seconds 8
$lines = @(& $adb logcat -d -b crash -v threadtime)
$hits = @($lines | Select-String -Pattern ([regex]::Escape("Process: $pkg")))
$report = Join-Path $out "$stamp-monogatari-crash.txt"
if ($hits.Count -gt 0) {
    $last = $hits[-1]
    $start = [Math]::Max(0, $last.LineNumber - 9)
    $end = [Math]::Min($lines.Count - 1, $last.LineNumber + 100)
    $lines[$start..$end] | Set-Content -Path $report -Encoding utf8
    Write-Host "Crash captured: $report"
} else {
    @(
        "No Monogatari Java crash was found in Android's crash buffer.",
        "Package: $pkg",
        "Device: $(& $adb shell getprop ro.product.model)",
        "Android: $(& $adb shell getprop ro.build.version.release)",
        "An early native crash or process kill may require full logcat."
    ) | Set-Content -Path $report -Encoding utf8
    Write-Host "No Java exception found. Diagnostic note: $report"
}
Write-Host 'Do not post full device logs publicly; inspect the report before sharing.'
