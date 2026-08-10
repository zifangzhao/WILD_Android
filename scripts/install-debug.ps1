param(
    [string]$ApkPath = ".\app\build\outputs\apk\debug\app-debug.apk",
    [string]$Serial,
    [switch]$Launch,
    [switch]$ListDevices
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    $pathCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($pathCommand -and $pathCommand.Source) {
        return $pathCommand.Source
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:ANDROID_SDK_ROOT) {
        $candidates.Add((Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"))
    }
    if ($env:ANDROID_HOME) {
        $candidates.Add((Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"))
    }
    if ($env:LOCALAPPDATA) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"))
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "adb.exe was not found. Install Android SDK Platform-Tools or set ANDROID_SDK_ROOT."
}

function Get-AdbDeviceTable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath
    )

    $lines = & $AdbPath devices
    return $lines |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "\S" } |
        ForEach-Object {
            $parts = ($_ -split "\s+") | Where-Object { $_ }
            if ($parts.Count -ge 2) {
                [PSCustomObject]@{
                    Serial = $parts[0]
                    State = $parts[1]
                }
            }
        }
}

function Resolve-TargetSerial {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AdbPath,
        [string]$RequestedSerial
    )

    $devices = @(Get-AdbDeviceTable -AdbPath $AdbPath)
    $readyDevices = @($devices | Where-Object { $_.State -eq "device" })

    if ($RequestedSerial) {
        $match = $devices | Where-Object { $_.Serial -eq $RequestedSerial } | Select-Object -First 1
        if (-not $match) {
            throw "Requested device '$RequestedSerial' was not found in 'adb devices'."
        }
        if ($match.State -ne "device") {
            throw "Requested device '$RequestedSerial' is '$($match.State)', not ready for install."
        }
        return $RequestedSerial
    }

    if ($readyDevices.Count -eq 1) {
        return $readyDevices[0].Serial
    }

    if ($readyDevices.Count -eq 0) {
        if ($devices.Count -gt 0) {
            $deviceSummary = ($devices | ForEach-Object { "$($_.Serial) [$($_.State)]" }) -join ", "
            $hasUnauthorized = @($devices | Where-Object { $_.State -eq "unauthorized" }).Count -gt 0
            if ($hasUnauthorized) {
                throw "No ready adb device found. Current adb entries: $deviceSummary. Unlock the phone and accept the USB debugging prompt, then re-run."
            }
            throw "No ready adb device found. Current adb entries: $deviceSummary"
        }
        throw "No adb device found. Connect a phone with USB debugging or wireless debugging first."
    }

    $choices = ($readyDevices | ForEach-Object { $_.Serial }) -join ", "
    throw "Multiple adb devices found: $choices. Re-run with -Serial."
}

$adbPath = Resolve-AdbPath
$apkFullPath = if ([System.IO.Path]::IsPathRooted($ApkPath)) {
    $ApkPath
} else {
    Join-Path (Get-Location) $ApkPath
}

if ($ListDevices) {
    Write-Host "adb: $adbPath"
    $devices = @(Get-AdbDeviceTable -AdbPath $adbPath)
    if ($devices.Count -eq 0) {
        Write-Host "No adb devices detected."
        exit 0
    }
    $devices | Format-Table -AutoSize
    exit 0
}

if (-not (Test-Path -LiteralPath $apkFullPath)) {
    throw "APK not found at '$apkFullPath'. Build the debug app first with '.\gradlew.bat :app:assembleDebug'."
}

$targetSerial = Resolve-TargetSerial -AdbPath $adbPath -RequestedSerial $Serial

Write-Host "adb: $adbPath"
Write-Host "device: $targetSerial"
Write-Host "apk: $apkFullPath"

& $adbPath -s $targetSerial install -r $apkFullPath

if ($LASTEXITCODE -ne 0) {
    throw "adb install failed."
}

if ($Launch) {
    & $adbPath -s $targetSerial shell am start -n "com.wild.android/.MainActivity"
    if ($LASTEXITCODE -ne 0) {
        throw "App install succeeded but launch failed."
    }
}
