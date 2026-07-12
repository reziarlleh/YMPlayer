# YMPlayer 0.5.1

Debug build for Android 10+ (`minSdk 29`), versionCode 61.

## APK

- File: `YMPlayer-v0.5.1-debug-b61.apk`
- Version: `0.5.1`
- Build type: `debug`
- SHA-256: `8C73A46C39175AB8EB309285AC113C768B4F4D15DF98833F35D95B848ECFC6F9`

## Changes

- Replaced the unsuccessful embedded SideBar shutdown button with a clear
  Sleep button and crescent-moon icon.
- Restored the original proven TS18 sleep command:
  `com.nwd.action.ACTION_KEY_VALUE` with byte `extra_key_value=0`.
- SideBar collapses to its edge handle after sending the sleep command.
- Removed all inactive shutdown-dialog, hidden Android API, `sys.powerctl`,
  vendor property, and long-power-key experiments from the APK.
- Kept the confirmed separate TS18 reboot button and its direct/launcher
  `RebootActivity` paths.
- Audio playback and the 0.5.0 Clip Wave implementation are unchanged.

## Verification

- `.\gradlew.bat :app:lintDebug :app:assembleDebug --no-daemon`
- `aapt dump badging`: `versionName=0.5.1`, `versionCode=61`, `minSdk=29`
- `apksigner verify --verbose`: APK Signature Scheme v2 verified
- Signing certificate matches YMPlayer 0.5.0 and earlier debug releases.
