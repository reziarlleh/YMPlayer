# YMPlayer 0.4.12

Debug build for Android 10+ (`minSdk 29`), versionCode 52.

## APK

- File: `YMPlayer-v0.4.12-debug-b52.apk`
- Version: `0.4.12`
- Build type: `debug`
- SHA-256: `78FE365821E66BDEE3DD7F004BB0FD35754B4C855895AAB78C0AC3EA1F3AE7EE`

## Changes

- YMPlayer-owned shutdown confirmation overlay for the embedded SideBar.
- TS18/NWD privileged `sys.powerctl` shutdown bridge found in the factory
  firmware libraries.
- Typed NWD long-power fallback (`DOWN -> LONGPRESS -> UP`).
- Existing reboot button and player behavior remain unchanged.

## Verification

- `.\gradlew.bat assembleDebug lintDebug --no-daemon`
- `apksigner verify --verbose`
