# YMPlayer 0.4.10

Debug build for Android 10+ (`minSdk 29`), versionCode 50.

## APK

- File: `YMPlayer-v0.4.10-debug-b50.apk`
- Version: `0.4.10`
- Build code: `50`

## What changed

- Added a non-Accessibility shutdown button path for the embedded SideBar.
- The shutdown button now first tries to open Android SystemUI `GlobalActions`
  through hidden `statusbar` service calls found during TS18 firmware analysis.
- The separate TS18 reboot button is unchanged and still uses the previously
  confirmed `RebootActivity` path.
- Direct MCU power-off broadcasts remain disabled because they are not a
  confirmation dialog.

## Verification

- `.\gradlew.bat assembleDebug lintDebug --no-daemon`
