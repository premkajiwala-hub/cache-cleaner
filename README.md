# Cache Cleaner

A small personal Android app that shows how much storage every app uses
(App / Data / Cache, the same split as Settings → Apps → Storage usage) and
clears **every app's cache with one tap**. Built for a OnePlus Nord 2T, whose
OxygenOS has no "clear all caches" option.

- Runs fully on the phone. No internet permission, no ads, no tracking.
- Only cache is removed. Logins, chats, photos, game worlds and other app data stay.

## Features

| Tab | What it shows |
|---|---|
| **Overview** | Total cache across all apps, a **Clear all caches** button, phone storage used/free, totals for app files / data / cache, and the apps using the most *data* (e.g. WhatsApp media), with shortcuts to open them. |
| **Apps** | Every installed app with its icon and App / Data / Cache sizes. Search, sort by cache, data, total or name, and optionally show system apps. Tap an app to open its App info page. |
| **History** | Each clean-up, with how much it freed and from how many apps, plus the total freed so far. |

## How clearing works

Android does not let normal apps clear other apps' caches. This app uses
[Shizuku](https://shizuku.rikka.app/), a free open-source helper that lets an
app run commands with the same rights as `adb shell`. Through Shizuku the app
runs:

```
pm trim-caches 999G
```

This asks Android to free more space than the phone has, so the system deletes
**every app's cache**. It never touches app data. The app measures every
app's cache before and after to report how much was freed.

Storage sizes are read with Android's `StorageStatsManager`, which needs the
**Usage access** permission.

## One-time setup on the phone

1. **Install the APK.** Copy `app-debug.apk` to the phone (or download it from
   this repo's Releases in the phone's browser) and open it. Allow "Install
   unknown apps" for your browser or file manager when asked.
2. **Grant Usage access.** Open Cache Cleaner → *Grant* → turn on Cache Cleaner.
3. **Install Shizuku** from the Play Store.
4. **Turn on Developer options.** Settings → About device → Version → tap
   *Build number* 7 times.
5. **Start Shizuku.** In Settings → System → Developer options, turn on
   *Wireless debugging* (Wi-Fi must be on). Open Shizuku → *Pairing* and follow
   its steps to enter the pairing code from Wireless debugging, then tap
   *Start*.
6. **Allow it.** Back in Cache Cleaner, tap *Allow* so it can use Shizuku.

After every **reboot**, Shizuku stops. Open Shizuku and tap *Start* again
(pairing is only needed once). Wi-Fi must be on to start it, but it keeps
working after you turn Wi-Fi off.

## Building

Requirements: JDK 17+ (21 recommended) and the Android SDK (platform 36,
build-tools 35). Point `local.properties` at the SDK:

```
sdk.dir=D\:/Android/Sdk
```

Then, in PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tech

Kotlin, Jetpack Compose (Material 3, Material You colours), `StorageStatsManager`,
Shizuku API 13, SharedPreferences for the history log. minSdk 26, targetSdk 36.

## Ideas for later

- Exclude list: keep some apps' cache (needs per-app clearing, `pm clear --cache-only`, Android 14+).
- Scheduled weekly clean-up while Shizuku is running.
- Accessibility-service fallback for when Shizuku is stopped.
