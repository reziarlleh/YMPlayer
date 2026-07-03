# YMPlayer 0.4.11

Debug build for Android 10+ (`minSdk 29`), versionCode 51.

## APK

- File: `YMPlayer-v0.4.11-debug-b51.apk`
- Version: `0.4.11`
- Build type: `debug`

## Changes

- Shared artwork cache for the main screen, notification, and MediaSession
  metadata.
- Bounded artwork retry after wake-up/network/storage timing failures.
- Downsampled artwork bitmaps before passing them to Android UI and
  MediaSession surfaces.
- SideBar shutdown/reboot behavior is unchanged from 0.4.10 for real-device
  testing.

## Verification

- `./gradlew.bat assembleDebug lintDebug --no-daemon`
- `apksigner verify --verbose`
