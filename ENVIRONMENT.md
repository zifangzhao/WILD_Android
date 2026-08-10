# Development Environment

## Host computer

Develop on Windows 10 or Windows 11 with Android Studio installed. Visual Studio
is not required for the Android build. Android Studio includes the supported JDK
(Java 17); use that JDK rather than installing a second Java version.

## Android Studio and SDK

In Android Studio, install these components from **SDK Manager**:

- Android SDK Platform 35 and current Android SDK Build-Tools
- Android SDK Platform-Tools (`adb`)
- Android SDK Command-line Tools (latest)
- Android Emulator, only if a physical phone is not available

The app is configured with `compileSdk` and `targetSdk` 35, and supports devices
from `minSdk` 21 (Android 5.0) onward. Compose and the Android Gradle plugin
require Java 17.

Android Studio normally creates the untracked `local.properties` file. For a
command-line-only setup, set it to the local SDK path, for example:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

## Physical Android device

- A phone or tablet with Bluetooth Low Energy and Android 5.0 or later
- Developer options and USB debugging enabled
- A USB data cable and a standard Android Debug Bridge driver

Verify the connection before building or installing:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

The device must appear as `device`, not as an offline entry. On Huawei phones,
select a USB mode that exposes standard ADB. A Huawei HDC-only connection is not
sufficient for Android Studio or `adb` deployment. Wireless debugging may be
used after Android's pairing flow has completed.

## CE BLE device

For live tests, power a CE device that advertises a name beginning with one of:

`CE32`, `CE64`, `CE128`, `WILD`, or `XENP`.

The current client expects the CE GATT service and characteristics used by the
Windows console: service `FFF0`, notification characteristic `FFF1`, and write
characteristic `FFF2`. Device discovery and a successful GATT connection do not
prove that sync, recording, channel switching, or preview frames are available;
those require a responsive firmware data protocol.

## Build and install

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain -g .gradle-user-build
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Or open this folder in Android Studio and run the `app` configuration. The
manifest forces the phone UI into landscape mode for the oscilloscope preview.

## Live test boundary

Start with discovery, connection, and read-only parameter/sync checks. Do not
start recording or alter acquisition settings on a live experiment without the
operator's explicit confirmation. If the device accepts BLE writes but does not
emit protocol responses, retain the transport logs and investigate the firmware
state before treating the Android client as the cause.
